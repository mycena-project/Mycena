// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation
import os.log

/// CoreBluetooth peripheral: invite read (F1) + mesh data write (F2/F3 chunks) + connectable advertising.
///
/// Uses State Preservation/Restoration when started with [`BleMeshHost.peripheralRestoreId`].
final class BleInviteGattServer: NSObject {
    private let log = Logger(subsystem: "io.github.mycena_project", category: "BleInviteGatt")
    private let bridge: MycenaNodeBridge
    private let restoreIdentifier: String
    private var peripheralManager: CBPeripheralManager?
    private var inviteCharacteristic: CBMutableCharacteristic?
    private var cachedInvite = ""
    private var isAdvertising = false
    private var assemblers: [UUID: BleMeshChunker.Assembler] = [:]
    private let queue = DispatchQueue(label: "io.github.mycena_project.ble.gatt-server")

    init(bridge: MycenaNodeBridge, restoreIdentifier: String) {
        self.bridge = bridge
        self.restoreIdentifier = restoreIdentifier
        super.init()
    }

    var isRunning: Bool { peripheralManager?.state == .poweredOn && inviteCharacteristic != nil }
    var hasInvite: Bool { cachedInvite.hasPrefix("mycena://") }

    func start() {
        queue.async { [weak self] in
            guard let self, self.peripheralManager == nil else { return }
            let options: [String: Any] = [
                CBPeripheralManagerOptionRestoreIdentifierKey: self.restoreIdentifier,
                CBPeripheralManagerOptionShowPowerAlertKey: true,
            ]
            self.peripheralManager = CBPeripheralManager(delegate: self, queue: self.queue, options: options)
        }
    }

    func stop() {
        queue.async { [weak self] in
            guard let self else { return }
            self.peripheralManager?.stopAdvertising()
            self.isAdvertising = false
            self.peripheralManager?.removeAllServices()
            self.peripheralManager = nil
            self.inviteCharacteristic = nil
            self.cachedInvite = ""
            self.assemblers.removeAll()
            self.log.info("GATT server stopped")
        }
    }

    @discardableResult
    func refreshInviteValue() -> String {
        let invite =
            bridge.getShareableMultiaddrs().first { $0.hasPrefix("mycena://") } ?? ""
        cachedInvite = invite
        if let data = invite.data(using: .utf8), let char = inviteCharacteristic {
            char.value = data
        }
        return invite
    }

    private func publishService(on manager: CBPeripheralManager) {
        guard inviteCharacteristic == nil else { return }
        let invite = CBMutableCharacteristic(
            type: BleMeshGatt.inviteCharUUID,
            properties: [.read],
            value: nil,
            permissions: [.readable],
        )
        let mesh = CBMutableCharacteristic(
            type: BleMeshGatt.meshDataCharUUID,
            properties: [.write, .writeWithoutResponse],
            value: nil,
            permissions: [.writeable],
        )
        let service = CBMutableService(type: BleMeshGatt.serviceUUID, primary: true)
        service.characteristics = [invite, mesh]
        inviteCharacteristic = invite
        manager.add(service)
        refreshInviteValue()
        log.info("GATT service publishing (invite + mesh data)")
    }

    private func startAdvertising(on manager: CBPeripheralManager) {
        guard !isAdvertising else { return }
        if refreshInviteValue().isEmpty {
            log.warning("no signed invite yet — retry after node ready")
        }
        let advertisement: [String: Any] = [
            CBAdvertisementDataServiceUUIDsKey: [BleMeshGatt.serviceUUID],
            CBAdvertisementDataLocalNameKey: BleMeshGatt.advertiseLocalName,
        ]
        manager.startAdvertising(advertisement)
        isAdvertising = true
        log.info("connectable BLE advertising started (F1 handoff)")
    }

    private func ingestMeshChunk(central: CBCentral, chunk: Data) {
        let deviceId = central.identifier
        var assembler = assemblers[deviceId] ?? BleMeshChunker.Assembler()
        assemblers[deviceId] = assembler
        guard let frame = assembler.push(chunk) else { return }

        guard let peer = resolvePeer(central: central, frame: frame) else {
            log.warning("mesh frame from unknown central \(central.identifier.uuidString)")
            return
        }

        do {
            if frame.prefix(4) == Data([0x4D, 0x59, 0x4C, 0x50]) { // MYLP F3
                try bridge.ingestBleStreamFrame(fromPeer: peer, frame: frame)
            } else {
                try bridge.ingestBleFrame(fromPeer: peer, frame: frame)
            }
            log.info("ingested BLE frame from \(peer, privacy: .public) (\(frame.count) bytes)")
        } catch {
            log.warning("ingest BLE frame: \(error.localizedDescription)")
        }
    }

    private func resolvePeer(central: CBCentral, frame: Data) -> String? {
        if let existing = BleMeshLinkRegistry.peer(forDevice: central.identifier) {
            return existing
        }
        guard let peerId = bridge.peerIdFromBleFrame(frame), !peerId.isEmpty else {
            return nil
        }
        BleMeshLinkRegistry.register(peerId: peerId, deviceId: central.identifier)
        bridge.registerBleLink(peerId: peerId, deviceId: central.identifier.uuidString)
        log.info("registered central \(peerId, privacy: .public) @ \(central.identifier.uuidString)")
        return peerId
    }
}

extension BleInviteGattServer: CBPeripheralManagerDelegate {
    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        switch peripheral.state {
        case .poweredOn:
            publishService(on: peripheral)
        case .poweredOff, .unauthorized, .unsupported:
            log.warning("peripheral manager state=\(String(describing: peripheral.state))")
        default:
            break
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, willRestoreState dict: [String: Any]) {
        log.info("peripheral manager state restoration")
        if let services = dict[CBPeripheralManagerRestoredStateServicesKey] as? [CBMutableService] {
            for service in services where service.uuid == BleMeshGatt.serviceUUID {
                inviteCharacteristic = service.characteristics?
                    .first { $0.uuid == BleMeshGatt.inviteCharUUID } as? CBMutableCharacteristic
            }
        }
        refreshInviteValue()
        if inviteCharacteristic != nil {
            startAdvertising(on: peripheral)
        }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error {
            log.error("addService failed: \(error.localizedDescription)")
            return
        }
        startAdvertising(on: peripheral)
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        didReceiveRead request: CBATTRequest,
    ) {
        guard request.characteristic.uuid == BleMeshGatt.inviteCharUUID else {
            peripheral.respond(to: request, withResult: .attributeNotFound)
            return
        }
        refreshInviteValue()
        guard let data = cachedInvite.data(using: .utf8) else {
            peripheral.respond(to: request, withResult: .unlikelyError)
            return
        }
        let offset = request.offset
        guard offset <= data.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }
        request.value = data.subdata(in: offset ..< data.count)
        peripheral.respond(to: request, withResult: .success)
    }

    func peripheralManager(
        _ peripheral: CBPeripheralManager,
        didReceiveWrite requests: [CBATTRequest],
    ) {
        for request in requests {
            guard request.characteristic.uuid == BleMeshGatt.meshDataCharUUID else {
                peripheral.respond(to: request, withResult: .attributeNotFound)
                continue
            }
            if let value = request.value {
                ingestMeshChunk(central: request.central, chunk: value)
            }
            if request.characteristic.properties.contains(.write) {
                peripheral.respond(to: request, withResult: .success)
            }
        }
    }
}

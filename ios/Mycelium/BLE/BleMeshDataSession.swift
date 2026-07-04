// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import CoreBluetooth
import Foundation
import os.log

/// Long-lived GATT client for writing mesh frames to a peer's mesh-data characteristic.
final class BleMeshDataSession: NSObject, CBPeripheralDelegate {
    private static let log = Logger(subsystem: "network.mycelium", category: "BleMeshData")
    private static let lock = NSLock()
    private static var sessions: [UUID: BleMeshDataSession] = [:]

    private let central: CBCentralManager
    private let peripheral: CBPeripheral
    let peerId: String
    private var meshChar: CBCharacteristic?
    private var connectContinuation: CheckedContinuation<Bool, Never>?
    private var writeContinuation: CheckedContinuation<Bool, Never>?
    private var pendingChunks: [Data] = []
    private var connectObserver: NSObjectProtocol?

    private init(central: CBCentralManager, peripheral: CBPeripheral, peerId: String) {
        self.central = central
        self.peripheral = peripheral
        self.peerId = peerId
        super.init()
    }

    static func open(
        central: CBCentralManager,
        peripheral: CBPeripheral,
        peerId: String,
    ) async -> BleMeshDataSession? {
        let id = peripheral.identifier
        lock.lock()
        if let existing = sessions[id] {
            lock.unlock()
            return existing
        }
        lock.unlock()

        let session = BleMeshDataSession(central: central, peripheral: peripheral, peerId: peerId)
        guard await session.connect() else { return nil }

        lock.lock()
        sessions[id] = session
        lock.unlock()
        return session
    }

    static func closeAll() {
        lock.lock()
        let all = sessions.values
        sessions.removeAll()
        lock.unlock()
        for session in all {
            session.close()
        }
    }

    private func connect() async -> Bool {
        await withCheckedContinuation { cont in
            connectContinuation = cont
            peripheral.delegate = self
            if peripheral.state == .connected {
                peripheral.discoverServices([BleMeshGatt.serviceUUID])
                return
            }
            connectObserver = NotificationCenter.default.addObserver(
                forName: .bleCentralDidConnect,
                object: central,
                queue: nil,
            ) { [weak self] note in
                guard let self,
                      let connected = note.userInfo?["peripheral"] as? CBPeripheral,
                      connected.identifier == self.peripheral.identifier
                else { return }
                connected.discoverServices([BleMeshGatt.serviceUUID])
            }
            central.connect(peripheral, options: nil)
        }
    }

    func sendFrame(_ frame: Data) async -> Bool {
        let chunks = BleMeshChunker.chunkFrame(frame)
        for chunk in chunks {
            guard await writeChunk(chunk) else { return false }
            try? await Task.sleep(nanoseconds: 25_000_000)
        }
        Self.log.debug("sent \(frame.count) byte frame to \(self.peerId, privacy: .public) via BLE")
        return true
    }

    private func writeChunk(_ chunk: Data) async -> Bool {
        guard let meshChar else { return false }
        return await withCheckedContinuation { cont in
            writeContinuation = cont
            pendingChunks = [chunk]
            peripheral.writeValue(chunk, for: meshChar, type: .withResponse)
        }
    }

    func close() {
        if let connectObserver {
            NotificationCenter.default.removeObserver(connectObserver)
        }
        if peripheral.state == .connected || peripheral.state == .connecting {
            central.cancelPeripheralConnection(peripheral)
        }
        meshChar = nil
        Self.lock.lock()
        Self.sessions.removeValue(forKey: peripheral.identifier)
        Self.lock.unlock()
    }

    private func finishConnect(_ ok: Bool) {
        guard let cont = connectContinuation else { return }
        connectContinuation = nil
        cont.resume(returning: ok)
    }

    private func finishWrite(_ ok: Bool) {
        guard let cont = writeContinuation else { return }
        writeContinuation = nil
        cont.resume(returning: ok)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if error != nil {
            finishConnect(false)
            return
        }
        guard let service = peripheral.services?.first(where: { $0.uuid == BleMeshGatt.serviceUUID }) else {
            finishConnect(false)
            return
        }
        peripheral.discoverCharacteristics([BleMeshGatt.meshDataCharUUID], for: service)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?,
    ) {
        if error != nil {
            finishConnect(false)
            return
        }
        meshChar = service.characteristics?.first(where: { $0.uuid == BleMeshGatt.meshDataCharUUID })
        finishConnect(meshChar != nil)
    }

    func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?,
    ) {
        guard characteristic.uuid == BleMeshGatt.meshDataCharUUID else { return }
        finishWrite(error == nil)
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import CoreBluetooth
import Foundation
import os.log

/// Scans for Mycena GATT service and emits handoff targets (F1 central role).
final class BleBootstrapScanner: NSObject {
    struct HandoffTarget {
        let peripheral: CBPeripheral
        let rssi: Int
    }

    struct NearbyNode {
        let deviceId: UUID
        let rssi: Int
        let firstSeen: Date
    }

    private let log = Logger(subsystem: "io.github.mycena_project", category: "BleScanner")
    private let restoreIdentifier: String
    private var centralManager: CBCentralManager?
    private var isScanning = false
    private var seen: [UUID: NearbyNode] = [:]

    var onHandoffTarget: ((HandoffTarget) -> Void)?
    var onNearbyUpdate: (([NearbyNode]) -> Void)?

    init(restoreIdentifier: String) {
        self.restoreIdentifier = restoreIdentifier
        super.init()
    }

    func startScanning() {
        guard centralManager == nil else {
            centralManager?.scanForPeripherals(withServices: [BleMeshGatt.serviceUUID], options: scanOptions)
            return
        }
        let options: [String: Any] = [
            CBCentralManagerOptionRestoreIdentifierKey: restoreIdentifier,
            CBCentralManagerOptionShowPowerAlertKey: true,
        ]
        centralManager = CBCentralManager(delegate: self, queue: nil, options: options)
    }

    func stopScanning() {
        centralManager?.stopScan()
        isScanning = false
        seen.removeAll()
        onNearbyUpdate?([])
    }

    var central: CBCentralManager? { centralManager }

    private var scanOptions: [String: Any] {
        [CBCentralManagerScanOptionAllowDuplicatesKey: false]
    }
}

extension BleBootstrapScanner: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard central.state == .poweredOn else {
            log.warning("central manager state=\(String(describing: central.state))")
            return
        }
        central.scanForPeripherals(withServices: [BleMeshGatt.serviceUUID], options: scanOptions)
        isScanning = true
        log.info("BLE scan started (service filter)")
    }

    func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        log.info("central manager state restoration")
        if let services = dict[CBCentralManagerRestoredStateScanServicesKey] as? [CBUUID],
           services.contains(BleMeshGatt.serviceUUID)
        {
            central.scanForPeripherals(withServices: [BleMeshGatt.serviceUUID], options: scanOptions)
            isScanning = true
        }
        if let peripherals = dict[CBCentralManagerRestoredStatePeripheralsKey] as? [CBPeripheral] {
            for peripheral in peripherals where peripheral.state == .connected {
                log.info("restored connected peripheral \(peripheral.identifier.uuidString)")
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        NotificationCenter.default.post(
            name: .bleCentralDidConnect,
            object: central,
            userInfo: ["peripheral": peripheral],
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?,
    ) {
        NotificationCenter.default.post(
            name: .bleCentralDidFailToConnect,
            object: central,
            userInfo: ["peripheral": peripheral, "error": error as Any],
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?,
    ) {
        NotificationCenter.default.post(
            name: .bleCentralDidDisconnect,
            object: central,
            userInfo: ["peripheral": peripheral, "error": error as Any],
        )
    }

    func centralManager(
        _ central: CBCentralManager,
        didDiscover peripheral: CBPeripheral,
        advertisementData: [String: Any],
        rssi RSSI: NSNumber,
    ) {
        let rssi = RSSI.intValue
        if seen[peripheral.identifier] == nil {
            seen[peripheral.identifier] = NearbyNode(
                deviceId: peripheral.identifier,
                rssi: rssi,
                firstSeen: Date(),
            )
            onNearbyUpdate?(Array(seen.values))
            log.info("Mycena BLE peer \(peripheral.identifier.uuidString) RSSI=\(rssi)")
        }
        onHandoffTarget?(HandoffTarget(peripheral: peripheral, rssi: rssi))
    }
}

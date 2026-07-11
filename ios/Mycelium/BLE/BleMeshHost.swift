// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import CoreBluetooth
import Foundation
import os.log

/// Orchestrates iOS BLE mesh bootstrap (F1–F3): peripheral host, central scan, handoff, relay.
///
/// Wire from `MeshHost` when BLE bootstrap is enabled in settings. See
/// [docs/IOS_BLE_CONSTRAINTS.md](../../../docs/IOS_BLE_CONSTRAINTS.md) for background limits.
final class BleMeshHost {
    static let peripheralRestoreId = "network.mycelium.ble.peripheral"
    static let centralRestoreId = "network.mycelium.ble.central"

    private let log = Logger(subsystem: "network.mycelium", category: "BleMeshHost")
    private let bridge: MyceliumNodeBridge
    private var gattServer: BleInviteGattServer?
    private var advertiser: BleBootstrapAdvertiser?
    private var scanner: BleBootstrapScanner?
    private var inviteTimer: Timer?
    private var relayTimer: Timer?
    private var isRunning = false
    private var thermalScanSuspended = false

    /// Defaults to live UniFFI bridge; inject a test double in unit tests.
    init(bridge: MyceliumNodeBridge = UniffiMyceliumBridge()) {
        self.bridge = bridge
    }

    var peripheralHasInvite: Bool { gattServer?.hasInvite ?? false }
    var isAdvertising: Bool { advertiser?.isActive ?? false }
    var isScanning: Bool { scanner?.central?.state == .poweredOn }

    /// Start connectable peripheral + central scan + relay/handoff loops.
    func start(enabled: Bool = true) {
        guard enabled else {
            stop()
            return
        }
        guard !isRunning else { return }
        isRunning = true

        BleMeshHandoff.resetSession()

        let server = BleInviteGattServer(bridge: bridge, restoreIdentifier: Self.peripheralRestoreId)
        let adv = BleBootstrapAdvertiser(gattServer: server)
        let scan = BleBootstrapScanner(restoreIdentifier: Self.centralRestoreId)

        gattServer = server
        advertiser = adv
        scanner = scan

        adv.startAdvertising()
        scan.startScanning()

        inviteTimer = Timer.scheduledTimer(withTimeInterval: 45, repeats: true) { [weak self] _ in
            self?.advertiser?.refreshInvite()
        }
        relayTimer = Timer.scheduledTimer(withTimeInterval: 2, repeats: true) { [weak self] _ in
            guard let self, let central = self.scanner?.central else { return }
            Task {
                await BleMeshRelay.flushOutbound(bridge: self.bridge, central: central)
            }
        }

        scan.onHandoffTarget = { [weak self] target in
            guard let self, let central = self.scanner?.central else { return }
            Task {
                let result = await BleMeshHandoff.tryHandoff(
                    bridge: self.bridge,
                    central: central,
                    target: target,
                )
                switch result {
                case .connected:
                    self.log.info("BLE handoff connected")
                case .alreadyConnected, .skippedDuplicate:
                    break
                case let .failed(reason):
                    self.log.debug("BLE handoff failed: \(reason, privacy: .public)")
                }
            }
        }

        log.info("BLE mesh host started (peripheral + central, restoration enabled)")
    }

    func stop() {
        isRunning = false
        inviteTimer?.invalidate()
        inviteTimer = nil
        relayTimer?.invalidate()
        relayTimer = nil
        thermalScanSuspended = false
        scanner?.onHandoffTarget = nil
        scanner?.stopScanning()
        advertiser?.stopAdvertising()
        BleMeshDataSession.closeAll()
        BleMeshLinkRegistry.clear()
        scanner = nil
        advertiser = nil
        gattServer = nil
        log.info("BLE mesh host stopped")
    }

    /// Called from [ThermalMonitor] — no user notification.
    func applyThermalThrottle(level: ThermalEnforcementLevel) {
        guard isRunning else { return }
        switch level {
        case .critical:
            if !thermalScanSuspended {
                scanner?.stopScanning()
                thermalScanSuspended = true
                log.info("thermal critical — BLE scan suspended")
            }
        case .warm:
            if thermalScanSuspended {
                scanner?.startScanning()
                thermalScanSuspended = false
                log.info("thermal eased — BLE scan resumed")
            }
        case .normal:
            if thermalScanSuspended {
                scanner?.startScanning()
                thermalScanSuspended = false
                log.info("thermal eased — BLE scan resumed")
            }
        }
    }
}

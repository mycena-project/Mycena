// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation
import os.log

/// SwiftUI / app lifecycle host for the Rust mesh node + BLE bootstrap (mirrors Android `MeshService`).
final class MeshHost: ObservableObject {
    static let shared = MeshHost()

    private let log = Logger(subsystem: "network.mycelium", category: "MeshHost")
    private let bridge: MyceliumNodeBridge
    private var bleHost: BleMeshHost?
    @Published private(set) var bleBootstrapEnabled = false
    @Published private(set) var nodeRunning = false

    init(bridge: MyceliumNodeBridge = UniffiMyceliumBridge()) {
        self.bridge = bridge
    }

    /// Call after onboarding with user consent for Bluetooth.
    func startNode(bleBootstrap: Bool) {
        bleBootstrapEnabled = bleBootstrap
        nodeRunning = true
        updateBleBootstrap()
        log.info("MeshHost started (ble=\(bleBootstrap, privacy: .public))")
    }

    func setBleBootstrapEnabled(_ enabled: Bool) {
        bleBootstrapEnabled = enabled
        updateBleBootstrap()
    }

    func stop() {
        bleHost?.stop()
        bleHost = nil
        nodeRunning = false
        log.info("MeshHost stopped")
    }

    var nodeBridge: MyceliumNodeBridge { bridge }

    private func updateBleBootstrap() {
        guard nodeRunning else {
            bleHost?.stop()
            bleHost = nil
            return
        }
        if bleBootstrapEnabled {
            if bleHost == nil {
                bleHost = BleMeshHost(bridge: bridge)
            }
            bleHost?.start(enabled: true)
        } else {
            bleHost?.stop()
            bleHost = nil
        }
    }
}

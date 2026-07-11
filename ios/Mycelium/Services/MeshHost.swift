// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation
import os.log

#if MYCELIUM_RUST_FFI
private enum RustNode {
    static func initialize(_ config: NodeConfig) { initNode(config: config) }
    static func shutdown() { stopNode() }
    static func peerId() -> String { localPeerId() }
    static func connected() -> [String] { connectedPeers() }
    static func registerCallback(_ callback: NodeEventCallback) { setEventCallback(callback: callback) }
}
#endif

/// SwiftUI / app lifecycle host for the Rust mesh node + BLE bootstrap (mirrors Android `MeshService`).
@MainActor
final class MeshHost: ObservableObject {
    static let shared = MeshHost()

    private let log = Logger(subsystem: "network.mycelium", category: "MeshHost")
    private let bridge: MyceliumNodeBridge
    private var bleHost: BleMeshHost?
    private var eventCallback: MeshNodeCallback?
    private var thermalMonitorStarted = false

    @Published private(set) var bleBootstrapEnabled = false
    @Published private(set) var nodeRunning = false
    @Published private(set) var nodeInitialized = false
    @Published private(set) var isStarting = false
    @Published private(set) var localPeerId = ""
    @Published private(set) var connectedPeerCount = 0
    @Published private(set) var connectivityLabel = "Starting…"

    init(bridge: MyceliumNodeBridge = UniffiMyceliumBridge()) {
        self.bridge = bridge
    }

    /// Boot the Rust node once per process, then optional BLE bootstrap.
    func startNode(bleBootstrap: Bool) async {
        if nodeInitialized {
            bleBootstrapEnabled = bleBootstrap
            updateBleBootstrap()
            return
        }
        guard !isStarting else { return }

        isStarting = true
        defer { isStarting = false }

        let config = Self.makeNodeConfig()
        log.info("initNode db=\(config.dbPath, privacy: .public)")

        await Task.detached(priority: .userInitiated) {
            #if MYCELIUM_RUST_FFI
            RustNode.initialize(config)
            #endif
        }.value

        #if MYCELIUM_RUST_FFI
        let callback = MeshNodeCallback(host: self)
        eventCallback = callback
        RustNode.registerCallback(callback)
        localPeerId = RustNode.peerId()
        connectedPeerCount = RustNode.connected().count
        #endif

        nodeInitialized = true
        nodeRunning = true
        bleBootstrapEnabled = bleBootstrap
        startThermalMonitorIfNeeded()
        updateBleBootstrap()
        log.info("MeshHost started peer=\(self.localPeerId.prefix(12), privacy: .public)… ble=\(bleBootstrap, privacy: .public)")
    }

    func setBleBootstrapEnabled(_ enabled: Bool) {
        bleBootstrapEnabled = enabled
        updateBleBootstrap()
    }

    func stop() {
        ThermalMonitor.shared.stop()
        thermalMonitorStarted = false
        bleHost?.stop()
        bleHost = nil
        #if MYCELIUM_RUST_FFI
        if nodeInitialized {
            RustNode.shutdown()
        }
        #endif
        eventCallback = nil
        nodeInitialized = false
        nodeRunning = false
        localPeerId = ""
        connectedPeerCount = 0
        connectivityLabel = "Stopped"
        log.info("MeshHost stopped")
    }

    func refreshPeerState() {
        #if MYCELIUM_RUST_FFI
        connectedPeerCount = RustNode.connected().count
        #endif
    }

    func updateConnectivity(_ mode: ConnectivityMode) {
        connectivityLabel = mode == .internet ? "Internet + mesh" : "Mesh only"
    }

    var nodeBridge: MyceliumNodeBridge { bridge }

    private func startThermalMonitorIfNeeded() {
        guard !thermalMonitorStarted else { return }
        thermalMonitorStarted = true
        ThermalMonitor.shared.start { [weak self] level in
            self?.bleHost?.applyThermalThrottle(level: level)
        }
    }

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
            bleHost?.applyThermalThrottle(level: ThermalEnforcement.level)
        } else {
            bleHost?.stop()
            bleHost = nil
        }
    }

    private static func makeNodeConfig() -> NodeConfig {
        let dbPath = databasePath()
        let storageKeyHex = StorageKeyHelper.getOrCreateStorageKeyHex()
        return NodeConfig(
            dbPath: dbPath,
            listenAddr: "/ip4/0.0.0.0/tcp/7761",
            displayName: MeshSettings.displayName,
            bootstrapPeers: MeshSettings.bootstrapPeers,
            storageKeyHex: storageKeyHex,
            rendezvousEnabled: MeshSettings.rendezvousEnabled && MeshSettings.internetEnabled,
            internetEnabled: MeshSettings.internetEnabled,
            bulletinSubscriptions: MeshSettings.bulletinSubscriptions,
            crowdMode: MeshSettings.crowdModeEnabled,
        )
    }

    private static func databasePath() -> String {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("mycelium", isDirectory: true)
        try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
        return base.path
    }
}

#if !MYCELIUM_RUST_FFI
/// Placeholder when the xcframework is not linked (Tier 0 stub builds).
struct NodeConfig {
    var dbPath: String = ""
    var listenAddr: String = ""
    var displayName: String = ""
    var bootstrapPeers: [String] = []
    var storageKeyHex: String? = nil
    var rendezvousEnabled: Bool = false
    var internetEnabled: Bool = false
    var bulletinSubscriptions: [String] = []
    var crowdMode: Bool = false
}

enum ConnectivityMode {
    case internet
    case meshOnly
}
#endif

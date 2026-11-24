// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import SwiftUI

@main
struct MycenaApp: App {
    @StateObject private var meshHost = MeshHost.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(meshHost)
                .task {
                    #if targetEnvironment(simulator)
                    await meshHost.startNode(bleBootstrap: false)
                    #else
                    await meshHost.startNode(bleBootstrap: true)
                    #endif
                }
        }
    }
}

private struct RootView: View {
    @EnvironmentObject var meshHost: MeshHost

    var body: some View {
        Group {
            if meshHost.isStarting || !meshHost.nodeInitialized {
                VStack(spacing: 16) {
                    ProgressView()
                    Text("Starting mesh node…")
                        .foregroundStyle(.secondary)
                }
            } else {
                ContentView()
            }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var meshHost: MeshHost

    var body: some View {
        NavigationStack {
            List {
                Section("Identity") {
                    LabeledContent("Peer ID") {
                        Text(shortPeerId(meshHost.localPeerId))
                            .font(.caption.monospaced())
                            .textSelection(.enabled)
                    }
                    LabeledContent("Connectivity", value: meshHost.connectivityLabel)
                    LabeledContent("Connected peers", value: "\(meshHost.connectedPeerCount)")
                }
                Section("Mesh") {
                    LabeledContent("Node", value: meshHost.nodeRunning ? "Running" : "Stopped")
                    Toggle(
                        "Bluetooth mesh",
                        isOn: Binding(
                            get: { meshHost.bleBootstrapEnabled },
                            set: { meshHost.setBleBootstrapEnabled($0) }
                        )
                    )
                    .disabled(!meshHost.nodeInitialized)
                }
                Section {
                    Text("Rust node via UniFFI. Test BLE mesh on a physical iPhone with a second device.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Mycena")
        }
    }

    private func shortPeerId(_ id: String) -> String {
        guard id.count > 16 else { return id.isEmpty ? "—" : id }
        return "\(id.prefix(8))…\(id.suffix(6))"
    }
}

#if DEBUG
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(MeshHost.shared)
    }
}
#endif

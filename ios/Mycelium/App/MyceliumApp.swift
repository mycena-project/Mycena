// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import SwiftUI

@main
struct MyceliumApp: App {
    @StateObject private var meshHost = MeshHost.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(meshHost)
                .onAppear {
                    #if targetEnvironment(simulator)
                    meshHost.startNode(bleBootstrap: false)
                    #else
                    meshHost.startNode(bleBootstrap: true)
                    #endif
                }
        }
    }
}

struct ContentView: View {
    @EnvironmentObject var meshHost: MeshHost

    var body: some View {
        NavigationStack {
            List {
                Section("Mesh") {
                    LabeledContent("Node", value: meshHost.nodeRunning ? "Running" : "Stopped")
                    Toggle(
                        "Bluetooth mesh",
                        isOn: Binding(
                            get: { meshHost.bleBootstrapEnabled },
                            set: { meshHost.setBleBootstrapEnabled($0) }
                        )
                    )
                }
                Section {
                    Text("Native SwiftUI shell over UniFFI + Rust core.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Mycelium")
        }
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

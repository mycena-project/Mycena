// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import CoreBluetooth
import Foundation
import os.log

/// Polls the unified Rust bearer outbound queue (F2 + F3) and writes via GATT.
enum BleMeshRelay {
    private static let log = Logger(subsystem: "network.mycelium", category: "BleMeshRelay")

    static func flushOutbound(bridge: MyceliumNodeBridge, central: CBCentralManager) async {
        let connected = Set(bridge.connectedPeers())
        let bleAddrs = bridge.blePeerAddrs()
        let items = bridge.drainBearerOutbound(toPeer: nil, max: 16)
        for item in items {
            if !shouldSendOverBle(peerId: item.toPeer, connected: connected, bleAddrs: bleAddrs) {
                continue
            }
            guard let deviceId = BleMeshLinkRegistry.deviceId(forPeer: item.toPeer),
                  let peripheral = retrievePeripheral(central: central, id: deviceId)
            else { continue }
            guard let session = await BleMeshDataSession.open(
                central: central,
                peripheral: peripheral,
                peerId: item.toPeer,
            ) else { continue }
            if !(await session.sendFrame(item.frame)) {
                log.warning("bearer send failed to \(item.toPeer, privacy: .public)")
            }
        }
    }

    static func shouldSendOverBle(
        peerId: String,
        connected: Set<String>,
        bleAddrs: [String],
    ) -> Bool {
        if !connected.contains(peerId) { return true }
        return bleAddrs.contains { $0.contains(peerId) }
    }

    private static func retrievePeripheral(central: CBCentralManager, id: UUID) -> CBPeripheral? {
        central.retrievePeripherals(withIdentifiers: [id]).first
    }
}

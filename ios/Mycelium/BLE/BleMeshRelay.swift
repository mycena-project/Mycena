// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import CoreBluetooth
import Foundation
import os.log

/// Polls Rust BLE outbound queues (F2 side-channel + F3 libp2p stream) and writes via GATT.
enum BleMeshRelay {
    private static let log = Logger(subsystem: "network.mycelium", category: "BleMeshRelay")

    static func flushOutbound(bridge: MyceliumNodeBridge, central: CBCentralManager) async {
        let connected = Set(bridge.connectedPeers())
        await flushF3Stream(bridge: bridge, central: central, connected: connected)
        await flushF2Frames(bridge: bridge, central: central, connected: connected)
    }

    /// F3: libp2p direct protocol over MYLP stream frames.
    private static func flushF3Stream(
        bridge: MyceliumNodeBridge,
        central: CBCentralManager,
        connected: Set<String>,
    ) async {
        let bleAddrs = bridge.blePeerAddrs()
        for peerId in BleMeshLinkRegistry.allPeers() {
            if connected.contains(peerId),
               !bleAddrs.contains(where: { $0.contains(peerId) })
            {
                continue
            }
            let frames = bridge.drainBleStreamOutbound(toPeer: peerId, max: 4)
            if frames.isEmpty { continue }
            guard let deviceId = BleMeshLinkRegistry.deviceId(forPeer: peerId),
                  let peripheral = retrievePeripheral(central: central, id: deviceId)
            else { continue }
            guard let session = await BleMeshDataSession.open(
                central: central,
                peripheral: peripheral,
                peerId: peerId,
            ) else { continue }
            for frame in frames {
                if !(await session.sendFrame(frame)) {
                    log.warning("F3 stream send failed to \(peerId, privacy: .public)")
                    break
                }
            }
        }
    }

    /// F2 fallback: signed WireMessage MYBL frames when F3 path unavailable.
    private static func flushF2Frames(
        bridge: MyceliumNodeBridge,
        central: CBCentralManager,
        connected: Set<String>,
    ) async {
        let items = bridge.drainBleOutbound(toPeer: nil, max: 8)
        for item in items {
            if connected.contains(item.toPeer) { continue }
            guard let deviceId = BleMeshLinkRegistry.deviceId(forPeer: item.toPeer),
                  let peripheral = retrievePeripheral(central: central, id: deviceId)
            else { continue }
            guard let session = await BleMeshDataSession.open(
                central: central,
                peripheral: peripheral,
                peerId: item.toPeer,
            ) else { continue }
            if !(await session.sendFrame(item.frame)) {
                log.warning("F2 frame send failed to \(item.toPeer, privacy: .public)")
            }
        }
    }

    private static func retrievePeripheral(central: CBCentralManager, id: UUID) -> CBPeripheral? {
        central.retrievePeripherals(withIdentifiers: [id]).first
    }
}

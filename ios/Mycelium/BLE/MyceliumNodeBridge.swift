// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation

struct BleOutboundItem {
    let toPeer: String
    let frame: Data
    let medium: UInt8
}

/// Rust node surface used by the BLE host (mirrors Android `mycelium` UniFFI facade).
protocol MyceliumNodeBridge: AnyObject {
    func getShareableMultiaddrs() -> [String]
    func peerIdFromBleFrame(_ frame: Data) -> String?
    func registerBleLink(peerId: String, deviceId: String)
    func ingestBleFrame(fromPeer: String, frame: Data) throws
    func ingestBleStreamFrame(fromPeer: String, frame: Data) throws
    func connectTransportInvite(_ raw: String) throws
    func connectedPeers() -> [String]
    func drainBleStreamOutbound(toPeer: String, max: UInt32) -> [Data]
    func drainBleOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem]
    func drainBearerOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem]
    func blePeerAddrs() -> [String]
}

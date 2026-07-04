// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
import Foundation

#if canImport(MyceliumFFI)
import MyceliumFFI

/// Live bridge to `mycelium-ffi` (generated from `mycelium.udl`).
final class UniffiMyceliumBridge: MyceliumNodeBridge {
    func getShareableMultiaddrs() -> [String] {
        MyceliumFFI.getShareableMultiaddrs()
    }

    func peerIdFromBleFrame(_ frame: Data) -> String? {
        MyceliumFFI.peerIdFromBleFrame(frame: [UInt8](frame))
    }

    func registerBleLink(peerId: String, deviceId: String) {
        MyceliumFFI.registerBleLink(peerId: peerId, deviceId: deviceId)
    }

    func ingestBleFrame(fromPeer: String, frame: Data) throws {
        try MyceliumFFI.ingestBleFrame(fromPeer: fromPeer, frame: [UInt8](frame))
    }

    func ingestBleStreamFrame(fromPeer: String, frame: Data) throws {
        try MyceliumFFI.ingestBleStreamFrame(fromPeer: fromPeer, frame: [UInt8](frame))
    }

    func connectTransportInvite(_ raw: String) throws {
        try MyceliumFFI.connectTransportInvite(raw: raw)
    }

    func connectedPeers() -> [String] {
        MyceliumFFI.connectedPeers()
    }

    func drainBleStreamOutbound(toPeer: String, max: UInt32) -> [Data] {
        MyceliumFFI.drainBleStreamOutbound(toPeer: toPeer, max: max).map { Data($0) }
    }

    func drainBleOutbound(toPeer: String?, max: UInt32) -> [(toPeer: String, frame: Data)] {
        MyceliumFFI.drainBleOutbound(toPeer: toPeer, max: max).map { item in
            (item.toPeer, Data(item.frame))
        }
    }

    func blePeerAddrs() -> [String] {
        MyceliumFFI.blePeerAddrs()
    }
}

#else

/// Placeholder until `MyceliumFFI` xcframework is linked — see `ios/README.md`.
final class UniffiMyceliumBridge: MyceliumNodeBridge {
    func getShareableMultiaddrs() -> [String] { [] }
    func peerIdFromBleFrame(_ frame: Data) -> String? { nil }
    func registerBleLink(peerId: String, deviceId: String) {}
    func ingestBleFrame(fromPeer: String, frame: Data) throws {}
    func ingestBleStreamFrame(fromPeer: String, frame: Data) throws {}
    func connectTransportInvite(_ raw: String) throws {}
    func connectedPeers() -> [String] { [] }
    func drainBleStreamOutbound(toPeer: String, max: UInt32) -> [Data] { [] }
    func drainBleOutbound(toPeer: String?, max: UInt32) -> [(toPeer: String, frame: Data)] { [] }
    func blePeerAddrs() -> [String] { [] }
}

#endif

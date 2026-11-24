// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
import Foundation

#if MYCENA_RUST_FFI

/// Live bridge to `mycena-ffi` (generated from `mycena.udl`).
final class UniffiMycenaBridge: MycenaNodeBridge {
    func getShareableMultiaddrs() -> [String] { rustGetShareableMultiaddrs() }
    func peerIdFromBleFrame(_ frame: Data) -> String? { rustPeerIdFromBleFrame(frame) }
    func registerBleLink(peerId: String, deviceId: String) { rustRegisterBleLink(peerId, deviceId) }
    func ingestBleFrame(fromPeer: String, frame: Data) throws { try rustIngestBleFrame(fromPeer, frame) }
    func ingestBleStreamFrame(fromPeer: String, frame: Data) throws { try rustIngestBleStreamFrame(fromPeer, frame) }
    func connectTransportInvite(_ raw: String) throws { try rustConnectTransportInvite(raw) }
    func connectedPeers() -> [String] { rustConnectedPeers() }
    func drainBleStreamOutbound(toPeer: String, max: UInt32) -> [Data] { rustDrainBleStreamOutbound(toPeer, max) }
    func drainBleOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem] { rustDrainBleOutbound(toPeer, max) }
    func drainBearerOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem] { rustDrainBearerOutbound(toPeer, max) }
    func blePeerAddrs() -> [String] { rustBlePeerAddrs() }
}

private func rustGetShareableMultiaddrs() -> [String] { getShareableMultiaddrs() }
private func rustPeerIdFromBleFrame(_ frame: Data) -> String? { peerIdFromBleFrame(frame: frame) }
private func rustRegisterBleLink(_ peerId: String, _ deviceId: String) { registerBleLink(peerId: peerId, deviceId: deviceId) }
private func rustIngestBleFrame(_ fromPeer: String, _ frame: Data) throws { try ingestBleFrame(fromPeer: fromPeer, frame: frame) }
private func rustIngestBleStreamFrame(_ fromPeer: String, _ frame: Data) throws { try ingestBleStreamFrame(fromPeer: fromPeer, frame: frame) }
private func rustConnectTransportInvite(_ raw: String) throws { try connectTransportInvite(raw: raw) }
private func rustConnectedPeers() -> [String] { connectedPeers() }
private func rustDrainBleStreamOutbound(_ toPeer: String, _ max: UInt32) -> [Data] {
    drainBleStreamOutbound(toPeer: toPeer, max: max)
}
private func rustDrainBleOutbound(_ toPeer: String?, _ max: UInt32) -> [BleOutboundItem] {
    drainBleOutbound(toPeer: toPeer, max: max).map {
        BleOutboundItem(toPeer: $0.toPeer, frame: $0.frame, medium: $0.medium)
    }
}
private func rustDrainBearerOutbound(_ toPeer: String?, _ max: UInt32) -> [BleOutboundItem] {
    drainBearerOutbound(toPeer: toPeer, max: max).map {
        BleOutboundItem(toPeer: $0.toPeer, frame: $0.frame, medium: $0.medium)
    }
}
private func rustBlePeerAddrs() -> [String] { blePeerAddrs() }

#else

/// Placeholder until `MycenaFFI` xcframework is linked — run `./scripts/build-ios-xcframework.sh`.
final class UniffiMycenaBridge: MycenaNodeBridge {
    func getShareableMultiaddrs() -> [String] { [] }
    func peerIdFromBleFrame(_ frame: Data) -> String? { nil }
    func registerBleLink(peerId: String, deviceId: String) {}
    func ingestBleFrame(fromPeer: String, frame: Data) throws {}
    func ingestBleStreamFrame(fromPeer: String, frame: Data) throws {}
    func connectTransportInvite(_ raw: String) throws {}
    func connectedPeers() -> [String] { [] }
    func drainBleStreamOutbound(toPeer: String, max: UInt32) -> [Data] { [] }
    func drainBleOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem] { [] }
    func drainBearerOutbound(toPeer: String?, max: UInt32) -> [BleOutboundItem] { [] }
    func blePeerAddrs() -> [String] { [] }
}

#endif

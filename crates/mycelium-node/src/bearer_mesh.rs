// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Multi-bearer router: BLE (discovery + idle) and Wi‑Fi Direct (bulk upgrade path).

use crate::bearer_ble::{BearerBleRouter, BearerIngestAction};
use crate::behaviour::DirectMessageRequest;
use crate::ble_transport::BlePeerInfo;
use mycelium_core::bearer_transport::{
    self, BearerMedium, BearerPayloadKind, BearerTransport, BearerWireVersion, ReliabilityClass,
};
use mycelium_core::ble_stream::encode_stream_frame_with_version;
use mycelium_core::transport::{TransportEvent, WireMessage};
use std::collections::{HashMap, VecDeque};

const WIFI_QUEUE_CAP: usize = 64;

/// Per-peer negotiated bearer caps (wire version + Wi‑Fi Direct upgrade).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct PeerBearerCaps {
    pub wire_version: BearerWireVersion,
    pub wifi_direct: bool,
}

/// BLE + Wi‑Fi Direct outbound routing behind one ingest/decode API.
#[derive(Debug)]
pub struct BearerMeshRouter {
    ble: BearerBleRouter,
    wifi_outbound: HashMap<String, VecDeque<Vec<u8>>>,
    peer_caps: HashMap<String, PeerBearerCaps>,
}

impl Default for BearerMeshRouter {
    fn default() -> Self {
        Self::new()
    }
}

impl BearerMeshRouter {
    pub fn new() -> Self {
        Self {
            ble: BearerBleRouter::new(),
            wifi_outbound: HashMap::new(),
            peer_caps: HashMap::new(),
        }
    }

    pub fn set_peer_caps(&mut self, peer: &str, wire_version: BearerWireVersion, wifi_direct: bool) {
        self.peer_caps.insert(
            peer.to_string(),
            PeerBearerCaps {
                wire_version,
                wifi_direct,
            },
        );
    }

    fn peer_caps(&self, peer: &str) -> PeerBearerCaps {
        self.peer_caps.get(peer).copied().unwrap_or_default()
    }

    fn peer_wire_version(&self, peer: &str) -> BearerWireVersion {
        let caps = self.peer_caps(peer);
        if caps.wire_version == BearerWireVersion::V1 {
            BearerWireVersion::V1
        } else {
            BearerWireVersion::Legacy
        }
    }

    fn peer_wifi_allowed(&self, peer: &str) -> bool {
        self.peer_caps(peer).wifi_direct
    }

    #[allow(dead_code)]
    pub fn decode_ingest(bytes: &[u8]) -> anyhow::Result<BearerIngestAction> {
        BearerBleRouter::decode_ingest(bytes)
    }

    pub fn queue_side_channel_wire(
        &mut self,
        to_peer: String,
        wire: &WireMessage,
    ) -> anyhow::Result<()> {
        let payload = bincode::serialize(wire)?;
        let style = self.peer_wire_version(&to_peer);
        let wifi_ok = self.peer_wifi_allowed(&to_peer);
        let max_len = if wifi_ok
            && bearer_transport::should_upgrade_to_wifi_direct(payload.len(), false)
        {
            bearer_transport::WIFI_DIRECT_MAX_FRAME_LEN
        } else {
            bearer_transport::MAX_FRAME_LEN
        };
        let encoded = bearer_transport::encode_bearer_frame_with_limit(
            BearerPayloadKind::SideChannelWire,
            &payload,
            style,
            max_len,
        )?;
        self.route_outbound_frame(&to_peer, encoded, false)
    }

    /// Force Wi‑Fi Direct (mini-app bundles, explicit bulk).
    pub fn queue_bulk_frame(&mut self, to_peer: String, frame: Vec<u8>) -> anyhow::Result<()> {
        self.route_outbound_frame(&to_peer, frame, true)
    }

    fn route_outbound_frame(
        &mut self,
        peer: &str,
        frame: Vec<u8>,
        force_wifi: bool,
    ) -> anyhow::Result<()> {
        let wifi_ok = self.peer_wifi_allowed(peer);
        if wifi_ok && bearer_transport::should_upgrade_to_wifi_direct(frame.len(), force_wifi) {
            self.queue_wifi_frame(peer, frame);
        } else {
            BearerTransport::send_frame(&mut self.ble, peer, &frame)?;
        }
        Ok(())
    }

    fn queue_wifi_frame(&mut self, peer: &str, frame: Vec<u8>) {
        let q = self.wifi_outbound.entry(peer.to_string()).or_default();
        q.push_back(frame);
        while q.len() > WIFI_QUEUE_CAP {
            q.pop_front();
        }
    }

    pub fn drain_bearer_outbound(
        &mut self,
        to_peer: Option<&str>,
        max: usize,
    ) -> Vec<(String, Vec<u8>, BearerMedium)> {
        let mut out = Vec::new();
        self.drain_wifi_outbound(to_peer, max, &mut out);
        let remaining = max.saturating_sub(out.len());
        if remaining > 0 {
            for (peer, frame) in self.ble.drain_bearer_outbound(to_peer, remaining) {
                out.push((peer, frame, BearerMedium::Ble));
            }
        }
        out
    }

    fn drain_wifi_outbound(
        &mut self,
        to_peer: Option<&str>,
        max: usize,
        out: &mut Vec<(String, Vec<u8>, BearerMedium)>,
    ) {
        if let Some(peer) = to_peer {
            drain_wifi_peer(&mut self.wifi_outbound, peer, max, out);
            return;
        }
        let peers: Vec<String> = self.wifi_outbound.keys().cloned().collect();
        for peer in peers {
            if out.len() >= max {
                break;
            }
            let take = max.saturating_sub(out.len());
            drain_wifi_peer(&mut self.wifi_outbound, &peer, take, out);
        }
    }

    pub fn register_link(&mut self, peer_id: String, device_id: &str) -> Option<TransportEvent> {
        self.ble.register_link(peer_id, device_id)
    }

    pub fn remove_link(&mut self, peer_id: &str) -> Option<TransportEvent> {
        self.peer_caps.remove(peer_id);
        self.ble.remove_link(peer_id)
    }

    pub fn is_connected(&self, peer_id: &str) -> bool {
        self.ble.is_connected(peer_id)
    }

    pub fn known_peers(&self) -> Vec<String> {
        self.ble.known_peers()
    }

    pub fn queue_dial(&mut self, multiaddr: &str) -> anyhow::Result<()> {
        self.ble.queue_dial(multiaddr)
    }

    pub fn send_direct(&mut self, to_peer: &str, message: WireMessage) -> anyhow::Result<()> {
        let request = DirectMessageRequest { message };
        let cbor = serde_cbor::to_vec(&request)?;
        let style = self.peer_wire_version(to_peer);
        let frame = encode_stream_frame_with_version(&cbor, style)?;
        let force = self.peer_wifi_allowed(to_peer)
            && frame.len() >= bearer_transport::bearer_wifi_upgrade_threshold();
        self.route_outbound_frame(to_peer, frame, force)
    }

    pub fn try_recv_event(&mut self) -> Option<TransportEvent> {
        self.ble.try_recv_event()
    }

    pub fn ingest_stream_frame(&mut self, from_peer: &str, frame: &[u8]) -> anyhow::Result<()> {
        self.ble.ingest_stream_frame(from_peer, frame)
    }

    pub fn all_peer_infos(&self) -> Vec<BlePeerInfo> {
        self.ble.all_peer_infos()
    }

    pub fn drain_side_outbound(
        &mut self,
        to_peer: Option<&str>,
        max: usize,
    ) -> Vec<(String, Vec<u8>)> {
        self.ble.drain_side_outbound(to_peer, max)
    }

    pub fn drain_ble_stream_outbound(&mut self, to_peer: &str, max: usize) -> Vec<Vec<u8>> {
        self.ble.drain_stream_outbound(to_peer, max)
    }
}

fn drain_wifi_peer(
    map: &mut HashMap<String, VecDeque<Vec<u8>>>,
    peer: &str,
    max: usize,
    out: &mut Vec<(String, Vec<u8>, BearerMedium)>,
) {
    let Some(q) = map.get_mut(peer) else {
        return;
    };
    while out.len() < max {
        match q.pop_front() {
            Some(f) => out.push((peer.to_string(), f, BearerMedium::WifiDirect)),
            None => break,
        }
    }
    if q.is_empty() {
        map.remove(peer);
    }
}

impl BearerTransport for BearerMeshRouter {
    fn mtu(&self) -> bearer_transport::BearerMtu {
        self.ble.mtu()
    }

    fn reliability_class(&self) -> ReliabilityClass {
        ReliabilityClass::BestEffort
    }

    fn send_frame(&mut self, peer: &str, frame: &[u8]) -> anyhow::Result<()> {
        self.route_outbound_frame(peer, frame.to_vec(), false)
    }

    fn recv_frame(&mut self, _peer: &str) -> anyhow::Result<Option<Vec<u8>>> {
        Ok(None)
    }

    fn drain_outbound(&mut self, peer: &str, max: usize) -> Vec<Vec<u8>> {
        let mut tagged = self.drain_bearer_outbound(Some(peer), max);
        tagged.drain(..).map(|(_, f, _)| f).collect()
    }
}

#[cfg(test)]
mod tests {
    #![allow(clippy::unwrap_used, clippy::expect_used)]

    use super::*;
    use mycelium_core::data::Envelope;
    use mycelium_core::protocol_capabilities::NegotiatedProtocol;
    use mycelium_core::transport::DirectMessage;

    #[test]
    fn large_side_channel_routes_to_wifi_when_peer_allows() {
        let mut router = BearerMeshRouter::new();
        router.set_peer_caps("peerA", BearerWireVersion::V1, true);
        let big_body = "x".repeat(20 * 1024);
        let wire = WireMessage::Data(DirectMessage {
            envelope: Envelope::new("p".into(), None, vec![]),
            body: big_body,
        });
        router
            .queue_side_channel_wire("peerA".into(), &wire)
            .unwrap();
        let wifi = router.drain_bearer_outbound(None, 4);
        assert_eq!(wifi.len(), 1);
        assert_eq!(wifi[0].0, "peerA");
        assert_eq!(wifi[0].2, BearerMedium::WifiDirect);
    }

    #[test]
    fn large_side_channel_stays_on_ble_without_wifi_cap() {
        let mut router = BearerMeshRouter::new();
        router.set_peer_caps("peerA", BearerWireVersion::V1, false);
        let big_body = "x".repeat(20 * 1024);
        let wire = WireMessage::Data(DirectMessage {
            envelope: Envelope::new("p".into(), None, vec![]),
            body: big_body,
        });
        let err = router
            .queue_side_channel_wire("peerA".into(), &wire)
            .unwrap_err();
        assert!(err.to_string().contains("too large"));
    }

    #[test]
    fn small_side_channel_stays_on_ble() {
        let mut router = BearerMeshRouter::new();
        router.set_peer_caps("peerB", BearerWireVersion::V1, true);
        let wire = WireMessage::Data(DirectMessage {
            envelope: Envelope::new("p".into(), None, vec![]),
            body: "hi".into(),
        });
        router
            .queue_side_channel_wire("peerB".into(), &wire)
            .unwrap();
        let drained = router.drain_bearer_outbound(None, 4);
        assert_eq!(drained.len(), 1);
        assert_eq!(drained[0].2, BearerMedium::Ble);
    }

    #[test]
    fn negotiated_caps_map_to_bearer_wire_and_wifi() {
        use mycelium_core::protocol_capabilities::ProtocolCapabilities;

        let legacy = NegotiatedProtocol::from_legacy_assumption();
        assert_eq!(legacy.bearer_wire_version(), BearerWireVersion::Legacy);
        assert!(!legacy.wifi_direct_allowed());

        let mut local = ProtocolCapabilities::current(true, true);
        local.bearer_wire_max = mycelium_core::protocol_capabilities::bearer::V1_MYBR;
        let caps = ProtocolCapabilities::negotiate(&local, &local);
        assert_eq!(caps.bearer_wire_version(), BearerWireVersion::V1);
        assert!(caps.wifi_direct_allowed());
    }
}

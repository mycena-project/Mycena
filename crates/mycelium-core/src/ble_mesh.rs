// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! BLE side-channel framing for wire messages (ADR 002 F2).

use crate::contact_hello::ContactHello;
use crate::transport::WireMessage;

pub const FRAME_MAGIC: [u8; 4] = *b"MYBL";
pub const CHUNK_MAGIC: [u8; 4] = *b"MYLC";
/// Max encoded wire frame (excluding chunk wrappers).
pub const MAX_FRAME_LEN: usize = 4096;
/// Payload bytes per GATT chunk (after 4-byte chunk header).
pub const GATT_CHUNK_PAYLOAD: usize = 476;

/// Encode a [`WireMessage`] for BLE transfer: `MYBL | u32 BE len | bincode(wire)`.
pub fn encode_wire(wire: &WireMessage) -> anyhow::Result<Vec<u8>> {
    let payload = bincode::serialize(wire)?;
    if payload.len() > MAX_FRAME_LEN {
        anyhow::bail!(
            "wire message too large for BLE ({payload_len} > {MAX_FRAME_LEN})",
            payload_len = payload.len()
        );
    }
    let mut out = Vec::with_capacity(8 + payload.len());
    out.extend_from_slice(&FRAME_MAGIC);
    out.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    out.extend_from_slice(&payload);
    Ok(out)
}

/// Sender libp2p peer id from a reassembled MYBL frame (contact hello or envelope).
pub fn sender_peer_from_frame(bytes: &[u8]) -> Option<String> {
    let wire = decode_frame(bytes).ok()?;
    match wire {
        WireMessage::Data(dm) => {
            if dm.body == "[contact:hello]" {
                ContactHello::decode(&dm.envelope.payload)
                    .ok()
                    .filter(|h| h.verify())
                    .map(|h| h.peer_id)
            } else if !dm.envelope.from_peer.is_empty() {
                Some(dm.envelope.from_peer)
            } else {
                None
            }
        }
        _ => None,
    }
}

/// Decode a full frame produced by [`encode_wire`].
pub fn decode_frame(bytes: &[u8]) -> anyhow::Result<WireMessage> {
    if bytes.len() < 8 {
        anyhow::bail!("BLE frame too short");
    }
    if bytes[..4] != FRAME_MAGIC {
        anyhow::bail!("invalid BLE frame magic");
    }
    let len = u32::from_be_bytes(bytes[4..8].try_into()?) as usize;
    if len > MAX_FRAME_LEN {
        anyhow::bail!("BLE frame length exceeds max");
    }
    if bytes.len() < 8 + len {
        anyhow::bail!("BLE frame truncated");
    }
    let wire: WireMessage = bincode::deserialize(&bytes[8..8 + len])?;
    Ok(wire)
}

/// Split a frame into GATT-sized chunks: `MYLC | idx u8 | total u8 | payload`.
pub fn chunk_frame(frame: &[u8]) -> Vec<Vec<u8>> {
    if frame.is_empty() {
        return Vec::new();
    }
    let chunk_count = frame.len().div_ceil(GATT_CHUNK_PAYLOAD).min(255) as u8;
    let mut out = Vec::with_capacity(chunk_count as usize);
    for (idx, piece) in frame.chunks(GATT_CHUNK_PAYLOAD).enumerate() {
        if idx >= 255 {
            break;
        }
        let mut chunk = Vec::with_capacity(4 + 2 + piece.len());
        chunk.extend_from_slice(&CHUNK_MAGIC);
        chunk.push(idx as u8);
        chunk.push(chunk_count);
        chunk.extend_from_slice(piece);
        out.push(chunk);
    }
    out
}

/// Incrementally reassemble chunked frames; returns full frame when complete.
#[derive(Debug, Default)]
pub struct ChunkAssembler {
    total: u8,
    parts: Vec<Option<Vec<u8>>>,
}

impl ChunkAssembler {
    pub fn reset(&mut self) {
        self.total = 0;
        self.parts.clear();
    }

    /// Feed one GATT chunk; `Some(frame)` when reassembly completes.
    pub fn push_chunk(&mut self, chunk: &[u8]) -> anyhow::Result<Option<Vec<u8>>> {
        if chunk.len() < 6 || chunk[..4] != CHUNK_MAGIC {
            anyhow::bail!("invalid BLE chunk");
        }
        let idx = chunk[4] as usize;
        let total = chunk[5];
        if total == 0 {
            anyhow::bail!("invalid chunk total");
        }
        if idx >= total as usize {
            anyhow::bail!("chunk index out of range");
        }
        if self.total == 0 {
            self.total = total;
            self.parts = vec![None; total as usize];
        } else if self.total != total {
            self.reset();
            self.total = total;
            self.parts = vec![None; total as usize];
        }
        self.parts[idx] = Some(chunk[6..].to_vec());
        if self.parts.iter().any(|p| p.is_none()) {
            return Ok(None);
        }
        let mut frame = Vec::new();
        for part in self.parts.drain(..) {
            // INVARIANT: every slot is Some — we return early above if any part is missing.
            frame.extend_from_slice(&part.unwrap_or_default());
        }
        self.total = 0;
        Ok(Some(frame))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::data::Envelope;
    use crate::transport::DirectMessage;

    #[test]
    fn roundtrip_wire_frame() {
        let msg = DirectMessage {
            envelope: Envelope::new("from".into(), Some("to".into()), b"hi".to_vec()),
            body: "hello".into(),
        };
        let wire = WireMessage::Data(msg);
        let frame = encode_wire(&wire).expect("encode");
        let back = decode_frame(&frame).expect("decode");
        match back {
            WireMessage::Data(m) => assert_eq!(m.body, "hello"),
            _ => panic!("wrong variant"),
        }
    }

    #[test]
    fn chunk_reassembly() {
        let frame = encode_wire(&WireMessage::Data(DirectMessage {
            envelope: Envelope::new("a".into(), None, vec![0u8; 900]),
            body: "x".into(),
        }))
        .unwrap();
        let chunks = chunk_frame(&frame);
        assert!(chunks.len() > 1);
        let mut asm = ChunkAssembler::default();
        let mut done = None;
        for c in chunks {
            if let Some(f) = asm.push_chunk(&c).unwrap() {
                done = Some(f);
            }
        }
        assert_eq!(done.as_deref(), Some(frame.as_slice()));
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Length-prefixed stream frames for libp2p direct protocol over BLE (ADR 002 F3).

use crate::ble_mesh::{chunk_frame, ChunkAssembler, MAX_FRAME_LEN};

pub const STREAM_MAGIC: [u8; 4] = *b"MYLP";
pub const MAX_STREAM_PAYLOAD: usize = MAX_FRAME_LEN;

/// Encode payload: `MYLP | u32 BE len | bytes`.
pub fn encode_stream_frame(payload: &[u8]) -> anyhow::Result<Vec<u8>> {
    if payload.len() > MAX_STREAM_PAYLOAD {
        anyhow::bail!("BLE stream payload too large");
    }
    let mut out = Vec::with_capacity(8 + payload.len());
    out.extend_from_slice(&STREAM_MAGIC);
    out.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    out.extend_from_slice(payload);
    Ok(out)
}

pub fn decode_stream_frame(bytes: &[u8]) -> anyhow::Result<Vec<u8>> {
    if bytes.len() < 8 {
        anyhow::bail!("BLE stream frame too short");
    }
    if bytes[..4] != STREAM_MAGIC {
        anyhow::bail!("invalid BLE stream magic");
    }
    let len = u32::from_be_bytes(bytes[4..8].try_into()?) as usize;
    if len > MAX_STREAM_PAYLOAD {
        anyhow::bail!("BLE stream length exceeds max");
    }
    if bytes.len() < 8 + len {
        anyhow::bail!("BLE stream frame truncated");
    }
    Ok(bytes[8..8 + len].to_vec())
}

pub fn chunk_stream_frame(frame: &[u8]) -> Vec<Vec<u8>> {
    chunk_frame(frame)
}

pub type StreamAssembler = ChunkAssembler;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stream_roundtrip_and_chunk() {
        let payload = b"libp2p-cbor-direct-request".repeat(40);
        let frame = encode_stream_frame(&payload).unwrap();
        assert_eq!(decode_stream_frame(&frame).unwrap(), payload);
        let chunks = chunk_stream_frame(&frame);
        assert!(chunks.len() > 1);
        let mut asm = StreamAssembler::default();
        let mut done = None;
        for c in chunks {
            if let Some(f) = asm.push_chunk(&c).unwrap() {
                done = Some(f);
            }
        }
        assert_eq!(done.as_deref(), Some(frame.as_slice()));
    }
}

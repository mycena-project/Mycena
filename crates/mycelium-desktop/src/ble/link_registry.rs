// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
//! Maps libp2p peer id ↔ BLE device id after F1 handoff (Android `BleMeshLinkRegistry` parity).

use std::collections::HashMap;
use std::sync::Mutex;

pub struct LinkRegistry {
    peer_to_device: Mutex<HashMap<String, String>>,
    device_to_peer: Mutex<HashMap<String, String>>,
}

impl Default for LinkRegistry {
    fn default() -> Self {
        Self {
            peer_to_device: Mutex::new(HashMap::new()),
            device_to_peer: Mutex::new(HashMap::new()),
        }
    }
}

impl LinkRegistry {
    pub fn register(&self, peer_id: &str, device_id: &str) {
        let device_id = device_id.to_string();
        let peer_id = peer_id.to_string();
        self.peer_to_device
            .lock()
            .expect("link registry")
            .insert(peer_id.clone(), device_id.clone());
        self.device_to_peer
            .lock()
            .expect("link registry")
            .insert(device_id, peer_id);
    }

    pub fn peer_for_device(&self, device_id: &str) -> Option<String> {
        self.device_to_peer
            .lock()
            .expect("link registry")
            .get(device_id)
            .cloned()
    }

    pub fn device_for_peer(&self, peer_id: &str) -> Option<String> {
        self.peer_to_device
            .lock()
            .expect("link registry")
            .get(peer_id)
            .cloned()
    }

    #[allow(dead_code)] // Used when desktop BLE drain enumerates linked peers.
    pub fn all_peers(&self) -> Vec<String> {
        self.peer_to_device
            .lock()
            .expect("link registry")
            .keys()
            .cloned()
            .collect()
    }
}

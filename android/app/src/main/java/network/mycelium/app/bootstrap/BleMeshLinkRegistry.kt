// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothDevice
import java.util.concurrent.ConcurrentHashMap

/** Maps libp2p peer id ↔ BLE device after F1 handoff. */
object BleMeshLinkRegistry {

    private val peerToAddress = ConcurrentHashMap<String, String>()
    private val addressToPeer = ConcurrentHashMap<String, String>()

    fun register(peerId: String, device: BluetoothDevice) {
        val address = device.address ?: return
        peerToAddress[peerId] = address
        addressToPeer[address] = peerId
    }

    fun peerForAddress(address: String): String? = addressToPeer[address]

    fun addressForPeer(peerId: String): String? = peerToAddress[peerId]

    fun allPeers(): List<String> = peerToAddress.keys.toList()

    fun clear() {
        peerToAddress.clear()
        addressToPeer.clear()
    }
}

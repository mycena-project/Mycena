// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.mycelium.bindings.mycelium

/**
 * Polls the unified Rust bearer outbound queue (F2 + F3) and writes via GATT.
 */
object BleMeshRelay {

    private const val TAG = "BleMeshRelay"

    suspend fun flushOutbound(context: Context) {
        withContext(Dispatchers.IO) {
            val adapter =
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager)
                    .adapter
                    ?: return@withContext
            val connected = mycelium.connectedPeers().toSet()
            val bleAddrs = mycelium.blePeerAddrs()
            val items = mycelium.drainBearerOutbound(null, 16u)
            for (item in items) {
                if (!shouldSendOverBle(item.toPeer, connected, bleAddrs)) continue
                val address = BleMeshLinkRegistry.addressForPeer(item.toPeer) ?: continue
                val device = adapter.getRemoteDevice(address)
                val session = openSession(context, device, item.toPeer) ?: continue
                if (!session.sendFrame(item.frame)) {
                    Log.w(TAG, "bearer send failed to ${item.toPeer}")
                }
            }
        }
    }

    /** Send when offline, or when the peer is reachable over an active BLE link. */
    internal fun shouldSendOverBle(
        peerId: String,
        connected: Set<String>,
        bleAddrs: List<String>,
    ): Boolean {
        if (!connected.contains(peerId)) return true
        return bleAddrs.any { it.contains(peerId) }
    }

    private suspend fun openSession(
        context: Context,
        device: BluetoothDevice,
        peerId: String,
    ): BleMeshDataSession? {
        return try {
            BleMeshDataSession.open(context, device, peerId)
        } catch (e: Exception) {
            Log.w(TAG, "session open $peerId: ${e.message}")
            null
        }
    }
}

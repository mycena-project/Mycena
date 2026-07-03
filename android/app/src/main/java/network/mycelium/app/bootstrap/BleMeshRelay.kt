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
 * Polls Rust BLE outbound queues (F2 side-channel + F3 libp2p stream) and writes via GATT.
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
            flushF3Stream(context, adapter, connected)
            flushF2Frames(context, adapter, connected)
        }
    }

    /** F3: libp2p direct protocol over MYLP stream frames. */
    private suspend fun flushF3Stream(
        context: Context,
        adapter: android.bluetooth.BluetoothAdapter,
        connected: Set<String>,
    ) {
        for (peerId in BleMeshLinkRegistry.allPeers()) {
            if (connected.contains(peerId) && !mycelium.blePeerAddrs().any { it.contains(peerId) }) {
                continue
            }
            val frames = mycelium.drainBleStreamOutbound(peerId, 4u)
            if (frames.isEmpty()) continue
            val address = BleMeshLinkRegistry.addressForPeer(peerId) ?: continue
            val device = adapter.getRemoteDevice(address)
            val session = openSession(context, device, peerId) ?: continue
            for (frame in frames) {
                if (!session.sendFrame(frame)) {
                    Log.w(TAG, "F3 stream send failed to $peerId")
                    break
                }
            }
        }
    }

    /** F2 fallback: signed WireMessage MYBL frames when F3 path unavailable. */
    private suspend fun flushF2Frames(
        context: Context,
        adapter: android.bluetooth.BluetoothAdapter,
        connected: Set<String>,
    ) {
        val frames = mycelium.drainBleOutbound(null, 8u)
        for (item in frames) {
            if (connected.contains(item.toPeer)) {
                continue
            }
            val address = BleMeshLinkRegistry.addressForPeer(item.toPeer) ?: continue
            val device = adapter.getRemoteDevice(address)
            val session = openSession(context, device, item.toPeer) ?: continue
            if (!session.sendFrame(item.frame)) {
                Log.w(TAG, "F2 frame send failed to ${item.toPeer}")
            }
        }
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

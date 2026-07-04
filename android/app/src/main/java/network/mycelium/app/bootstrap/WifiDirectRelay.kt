// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.mycelium.bindings.mycelium

/** Drains Wi‑Fi Direct bearer frames from Rust and sends via [WifiDirectBearerHost]. */
object WifiDirectRelay {
    private const val TAG = "WifiDirectRelay"
    /** Matches Rust `BearerMedium::WifiDirect`. */
    private const val MEDIUM_WIFI_DIRECT: UByte = 1u

    suspend fun flushOutbound(context: Context, host: WifiDirectBearerHost) {
        withContext(Dispatchers.IO) {
            val items = mycelium.drainBearerOutbound(null, 8u)
            val byPeer = items.filter { it.medium == MEDIUM_WIFI_DIRECT }.groupBy { it.toPeer }
            for ((peerId, frames) in byPeer) {
                val address = BleMeshLinkRegistry.addressForPeer(peerId) ?: continue
                val payloads = frames.map { it.frame }
                if (!host.sendFrames(peerId, address, payloads)) {
                    Log.w(TAG, "wifi direct send failed for $peerId")
                }
            }
        }
    }
}

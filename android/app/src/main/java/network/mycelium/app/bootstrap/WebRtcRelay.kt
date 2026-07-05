// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import network.mycelium.bindings.mycelium

/** Drains WebRTC bearer frames (medium=3) from Rust and sends via RTCDataChannel. */
object WebRtcRelay {
    private const val TAG = "WebRtcRelay"
    private const val MEDIUM_WEBRTC: UByte = 3u

    suspend fun flushOutbound(host: WebRtcDiscoveryHost) {
        withContext(Dispatchers.IO) {
            val items = mycelium.drainBearerOutbound(null, 12u)
            val byPeer = items.filter { it.medium == MEDIUM_WEBRTC }.groupBy { it.toPeer }
            for ((peerId, frames) in byPeer) {
                for (frame in frames) {
                    if (!host.sendFrame(peerId, frame.frame)) {
                        Log.w(TAG, "webrtc send failed for $peerId")
                        break
                    }
                }
            }
        }
    }
}

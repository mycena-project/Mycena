// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Temporarily disconnects from the current Wi‑Fi so [LocalHotspotHelper] can start on some OEMs,
 * then reconnects to the saved network when sharing stops.
 */
class WifiPauseHelper(private val context: Context) {

    companion object {
        private const val TAG = "WifiPauseHelper"
    }

    private var savedNetworkId: Int? = null
    private var paused = false

    /** @return true if we disconnected from an active Wi‑Fi network. */
    @Suppress("DEPRECATION")
    fun pauseIfConnected(): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wifiManager.connectionInfo ?: return false
        if (info.networkId == -1) return false
        savedNetworkId = info.networkId
        paused = true
        Log.i(TAG, "Pausing Wi‑Fi (networkId=${info.networkId}, ssid=${info.ssid})")
        wifiManager.disconnect()
        return true
    }

    @Suppress("DEPRECATION")
    fun restore() {
        if (!paused) return
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val networkId = savedNetworkId
        if (networkId != null) {
            Log.i(TAG, "Restoring Wi‑Fi (networkId=$networkId)")
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
        }
        savedNetworkId = null
        paused = false
    }
}

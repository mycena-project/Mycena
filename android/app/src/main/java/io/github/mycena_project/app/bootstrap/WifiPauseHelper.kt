// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

/**
 * Temporarily disconnects from the current Wi‑Fi so [LocalHotspotHelper] can start on some OEMs,
 * then reconnects to the saved network when sharing stops.
 *
 * On API 29+ this is a no-op: apps cannot disconnect/reconnect STA Wi‑Fi.
 */
class WifiPauseHelper(private val context: Context) {

    companion object {
        private const val TAG = "WifiPauseHelper"

        fun canPauseStaWifi(sdkInt: Int): Boolean =
            ShareTransportPreconditions.canPauseStaWifi(sdkInt)
    }

    private var savedNetworkId: Int? = null
    private var paused = false

    /** @return true if we disconnected from an active Wi‑Fi network. */
    @Suppress("DEPRECATION")
    fun pauseIfConnected(): Boolean {
        if (!canPauseStaWifi(android.os.Build.VERSION.SDK_INT)) {
            Log.i(TAG, "Skipping STA Wi‑Fi pause on API ${android.os.Build.VERSION.SDK_INT}")
            return false
        }
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return false
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
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return
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

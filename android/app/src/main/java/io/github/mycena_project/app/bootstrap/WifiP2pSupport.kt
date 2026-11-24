// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import io.github.mycena_project.app.stability.StabilityDiagnostics

/** Null-safe Wi‑Fi Direct manager + channel. `initialize()` is nullable in the framework. */
object WifiP2pSupport {

    data class Session(
        val manager: WifiP2pManager,
        val channel: WifiP2pManager.Channel,
    )

    fun isFeaturePresent(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    fun open(context: Context): Session? {
        val app = context.applicationContext
        if (!isFeaturePresent(app)) {
            Log.i(StabilityDiagnostics.TAG, "op=WifiP2p.open detail=no_feature")
            return null
        }
        val manager =
            app.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager == null) {
            StabilityDiagnostics.info("WifiP2p.open", "service_null")
            return null
        }
        val channel =
            try {
                manager.initialize(app, app.mainLooper, null)
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("WifiP2p.initialize", e)
                null
            } catch (e: RuntimeException) {
                StabilityDiagnostics.record("WifiP2p.initialize", e)
                null
            }
        if (channel == null) {
            StabilityDiagnostics.info("WifiP2p.open", "channel_null")
            return null
        }
        return Session(manager, channel)
    }
}

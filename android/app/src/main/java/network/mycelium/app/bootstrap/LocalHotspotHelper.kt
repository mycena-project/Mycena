// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Creates a phone-local Wi‑Fi hotspot so another device can connect without internet or a router.
 * Requires the app to stay in the foreground while active.
 */
class LocalHotspotHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocalHotspot"
    }

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    suspend fun start(): ApkSharePayload.HotspotCredentials =
        suspendCancellableCoroutine { cont ->
            if (reservation != null) {
                cont.resume(readCredentials(reservation!!))
                return@suspendCancellableCoroutine
            }

            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = res
                        Log.i(TAG, "Local-only hotspot started")
                        cont.resume(readCredentials(res))
                    }

                    override fun onFailed(reason: Int) {
                        Log.e(TAG, "Local-only hotspot failed: reason=$reason")
                        if (cont.isActive) {
                            cont.resumeWithException(
                                HotspotStartException(reason),
                            )
                        }
                    }

                    override fun onStopped() {
                        Log.i(TAG, "Local-only hotspot stopped")
                        reservation = null
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )

            cont.invokeOnCancellation { stop() }
        }

    fun stop() {
        try {
            reservation?.close()
        } catch (_: Exception) {
        }
        reservation = null
    }

    @Suppress("DEPRECATION")
    private fun readCredentials(
        res: WifiManager.LocalOnlyHotspotReservation,
    ): ApkSharePayload.HotspotCredentials {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val config = res.softApConfiguration
            return ApkSharePayload.HotspotCredentials(
                ssid = config.ssid?.toString().orEmpty(),
                password = config.passphrase.orEmpty(),
            )
        }
        val legacy = res.wifiConfiguration
            ?: return ApkSharePayload.HotspotCredentials("", "")
        return ApkSharePayload.HotspotCredentials(
            ssid = legacy.SSID?.trim('"').orEmpty(),
            password = legacy.preSharedKey?.trim('"').orEmpty(),
        )
    }

    class HotspotStartException(val reason: Int) :
        Exception("Could not start local hotspot (reason=$reason)")

    /** Android [WifiManager.LocalOnlyHotspotCallback] failure reasons (API 26+). */
    object HotspotFailureReason {
        const val NO_CHANNEL = 1
        const val GENERIC_ERROR = 2
        const val INCOMPATIBLE_MODE = 3
        const val TETHERING_DISALLOWED = 4
    }
}

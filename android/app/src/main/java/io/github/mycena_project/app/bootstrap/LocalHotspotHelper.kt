// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import io.github.mycena_project.app.stability.StabilityDiagnostics
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Creates a phone-local Wi‑Fi hotspot so another device can connect without internet or a router.
 * Requires the app to stay in the foreground while active.
 */
class LocalHotspotHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocalHotspot"
        const val REASON_SECURITY = -10
        const val REASON_BLANK_SSID = -11
        const val REASON_UNSUPPORTED = -12
    }

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var onStoppedListener: (() -> Unit)? = null

    fun setOnStoppedListener(listener: (() -> Unit)?) {
        onStoppedListener = listener
    }

    suspend fun start(): ApkSharePayload.HotspotCredentials =
        suspendCancellableCoroutine { cont ->
            val existing = reservation
            if (existing != null) {
                val creds = readCredentials(existing)
                if (creds.ssid.isBlank()) {
                    stop()
                    cont.resumeWithException(HotspotStartException(REASON_BLANK_SSID))
                } else {
                    cont.resume(creds)
                }
                return@suspendCancellableCoroutine
            }

            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: run {
                        cont.resumeWithException(HotspotStartException(REASON_UNSUPPORTED))
                        return@suspendCancellableCoroutine
                    }

            val callback =
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                        if (!cont.isActive) {
                            try {
                                res.close()
                            } catch (e: RuntimeException) {
                                StabilityDiagnostics.record("LocalHotspot.close", e)
                            }
                            return
                        }
                        val creds = readCredentials(res)
                        if (creds.ssid.isBlank()) {
                            try {
                                res.close()
                            } catch (e: RuntimeException) {
                                StabilityDiagnostics.record("LocalHotspot.close", e)
                            }
                            Log.e(TAG, "Local-only hotspot started with blank SSID")
                            cont.resumeWithException(HotspotStartException(REASON_BLANK_SSID))
                            return
                        }
                        reservation = res
                        Log.i(TAG, "Local-only hotspot started ssid=${creds.ssid}")
                        if (cont.isActive) {
                            cont.resume(creds)
                        } else {
                            try {
                                res.close()
                            } catch (e: RuntimeException) {
                                StabilityDiagnostics.record("LocalHotspot.close", e)
                            }
                            reservation = null
                        }
                    }

                    override fun onFailed(reason: Int) {
                        Log.e(TAG, "Local-only hotspot failed: reason=$reason")
                        if (cont.isActive) {
                            cont.resumeWithException(HotspotStartException(reason))
                        }
                    }

                    override fun onStopped() {
                        Log.i(TAG, "Local-only hotspot stopped")
                        reservation = null
                        onStoppedListener?.invoke()
                    }
                }

            try {
                wifiManager.startLocalOnlyHotspot(
                    callback,
                    Handler(Looper.getMainLooper()),
                )
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("LocalHotspot.start", e)
                Log.e(TAG, "startLocalOnlyHotspot SecurityException", e)
                if (cont.isActive) {
                    cont.resumeWithException(HotspotStartException(REASON_SECURITY, e))
                }
            } catch (e: UnsupportedOperationException) {
                StabilityDiagnostics.record("LocalHotspot.start", e)
                Log.e(TAG, "startLocalOnlyHotspot unsupported", e)
                if (cont.isActive) {
                    cont.resumeWithException(HotspotStartException(REASON_UNSUPPORTED, e))
                }
            }

            cont.invokeOnCancellation { stop() }
        }

    fun stop() {
        try {
            reservation?.close()
        } catch (e: RuntimeException) {
            StabilityDiagnostics.record("LocalHotspot.close", e)
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

    class HotspotStartException(val reason: Int, cause: Throwable? = null) :
        Exception("Could not start local hotspot (reason=$reason)", cause)

    /** Android [WifiManager.LocalOnlyHotspotCallback] failure reasons (API 26+). */
    object HotspotFailureReason {
        const val NO_CHANNEL = 1
        const val GENERIC_ERROR = 2
        const val INCOMPATIBLE_MODE = 3
        const val TETHERING_DISALLOWED = 4
        const val SECURITY = REASON_SECURITY
        const val BLANK_SSID = REASON_BLANK_SSID
        const val UNSUPPORTED = REASON_UNSUPPORTED
    }
}

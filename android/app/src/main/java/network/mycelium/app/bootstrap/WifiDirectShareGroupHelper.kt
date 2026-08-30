// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Creates a Wi‑Fi Direct group (this phone is group owner) so another device can join and
 * download the APK from [WifiDirectSharer] on port 8765.
 */
class WifiDirectShareGroupHelper(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectShare"
        private const val START_TIMEOUT_MS = 20_000L
        const val REASON_TIMEOUT = -1
        const val REASON_NO_GROUP = -2
        const val REASON_BLANK_SSID = -3
        const val REASON_SECURITY = -10
        const val REASON_WIFI_DISABLED = -13
        const val REASON_UNAVAILABLE = -15
    }

    data class StartedGroup(
        val credentials: ApkSharePayload.HotspotCredentials,
        val ownerHost: String?,
    )

    private val appContext = context.applicationContext
    private val p2p = WifiP2pSupport.open(appContext)
    private val manager: WifiP2pManager? = p2p?.manager
    private val channel: WifiP2pManager.Channel? = p2p?.channel

    private var receiverRegistered = false
    private var pendingStart: CompletableDeferred<StartedGroup>? = null

    private val intentFilter =
        IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> onConnectionChanged(intent)
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> onP2pStateChanged(intent)
                }
            }
        }

    suspend fun start(): StartedGroup {
        val manager = manager ?: throw WifiDirectGroupException(REASON_UNAVAILABLE)
        val channel = channel ?: throw WifiDirectGroupException(REASON_UNAVAILABLE)
        registerReceiver()
        return try {
            val deferred = CompletableDeferred<StartedGroup>()
            pendingStart = deferred
            try {
                removeExistingGroupThenCreate(manager, channel)
            } catch (e: SecurityException) {
                throw WifiDirectGroupException(REASON_SECURITY, e)
            }
            withTimeoutOrNull(START_TIMEOUT_MS) { deferred.await() }
                ?: throw WifiDirectGroupException(REASON_TIMEOUT)
        } finally {
            pendingStart = null
        }
    }

    fun stop() {
        val manager = manager
        val channel = channel
        if (manager != null && channel != null) {
            try {
                manager.removeGroup(channel, null)
            } catch (_: SecurityException) {
            } catch (e: RuntimeException) {
                network.mycelium.app.stability.StabilityDiagnostics.record("WifiDirectShare.stop", e)
            }
        }
        unregisterReceiver()
    }

    private fun removeExistingGroupThenCreate(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ) {
        manager.removeGroup(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() = createGroup(manager, channel)
                override fun onFailure(reason: Int) = createGroup(manager, channel)
            },
        )
    }

    private fun createGroup(
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
    ) {
        try {
            manager.createGroup(
                channel,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Wi‑Fi Direct group create initiated")
                        manager.requestConnectionInfo(channel) { info -> onGroupReady(info) }
                    }

                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "createGroup failed: $reason")
                        pendingStart?.completeExceptionally(WifiDirectGroupException(reason))
                    }
                },
            )
        } catch (e: SecurityException) {
            pendingStart?.completeExceptionally(WifiDirectGroupException(REASON_SECURITY, e))
        }
    }

    private fun onP2pStateChanged(intent: Intent) {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
        if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
            pendingStart?.completeExceptionally(WifiDirectGroupException(REASON_WIFI_DISABLED))
        }
    }

    private fun onConnectionChanged(intent: Intent) {
        val networkInfo =
            if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(
                    WifiP2pManager.EXTRA_NETWORK_INFO,
                    android.net.NetworkInfo::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
            }
        if (networkInfo?.isConnected == true) {
            val manager = manager ?: return
            val channel = channel ?: return
            manager.requestConnectionInfo(channel) { info -> onGroupReady(info) }
        }
    }

    private fun onGroupReady(info: WifiP2pInfo) {
        if (!info.groupFormed || !info.isGroupOwner) return
        val deferred = pendingStart ?: return
        if (deferred.isCompleted) return
        val manager = manager ?: return
        val channel = channel ?: return
        val ownerHost =
            info.groupOwnerAddress?.hostAddress?.substringBefore('%')?.takeIf {
                ShareLinkAddresses.isUsableIpv4(it)
            }
        manager.requestGroupInfo(channel) { group ->
            if (group == null) {
                deferred.completeExceptionally(WifiDirectGroupException(REASON_NO_GROUP))
                return@requestGroupInfo
            }
            val ssid = group.networkName.orEmpty()
            val password = group.passphrase.orEmpty()
            if (ssid.isBlank()) {
                deferred.completeExceptionally(WifiDirectGroupException(REASON_BLANK_SSID))
                return@requestGroupInfo
            }
            Log.i(TAG, "Wi‑Fi Direct group ready: ssid=$ssid owner=$ownerHost")
            deferred.complete(
                StartedGroup(
                    ApkSharePayload.HotspotCredentials(ssid, password),
                    ownerHost,
                ),
            )
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        } catch (e: RuntimeException) {
            network.mycelium.app.stability.StabilityDiagnostics.record("WifiDirectShare.registerReceiver", e)
            throw e
        }
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        runCatching { appContext.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    class WifiDirectGroupException(val reason: Int, cause: Throwable? = null) :
        Exception("Could not start Wi‑Fi Direct group (reason=$reason)", cause)
}

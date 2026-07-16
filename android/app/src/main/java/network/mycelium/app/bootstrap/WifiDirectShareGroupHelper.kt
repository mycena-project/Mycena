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
    }

    private val manager =
        context.applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(context.applicationContext, context.mainLooper, null)

    private var receiverRegistered = false
    private var pendingStart: CompletableDeferred<ApkSharePayload.HotspotCredentials>? = null

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
                }
            }
        }

    suspend fun start(): ApkSharePayload.HotspotCredentials {
        registerReceiver()
        return try {
            val deferred = CompletableDeferred<ApkSharePayload.HotspotCredentials>()
            pendingStart = deferred
            removeExistingGroupThenCreate()
            withTimeoutOrNull(START_TIMEOUT_MS) { deferred.await() }
                ?: throw WifiDirectGroupException(-1)
        } finally {
            pendingStart = null
        }
    }

    fun stop() {
        manager.removeGroup(channel, null)
        unregisterReceiver()
    }

    private fun removeExistingGroupThenCreate() {
        manager.removeGroup(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() = createGroup()
                override fun onFailure(reason: Int) = createGroup()
            },
        )
    }

    private fun createGroup() {
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
            manager.requestConnectionInfo(channel) { info -> onGroupReady(info) }
        }
    }

    private fun onGroupReady(info: WifiP2pInfo) {
        if (!info.groupFormed || !info.isGroupOwner) return
        val deferred = pendingStart ?: return
        if (deferred.isCompleted) return
        manager.requestGroupInfo(channel) { group ->
            if (group == null) {
                deferred.completeExceptionally(WifiDirectGroupException(-2))
                return@requestGroupInfo
            }
            val ssid = group.networkName.orEmpty()
            val password = group.passphrase.orEmpty()
            if (ssid.isBlank()) {
                deferred.completeExceptionally(WifiDirectGroupException(-3))
                return@requestGroupInfo
            }
            Log.i(TAG, "Wi‑Fi Direct group ready: ssid=$ssid")
            deferred.complete(ApkSharePayload.HotspotCredentials(ssid, password))
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
    }

    class WifiDirectGroupException(val reason: Int) :
        Exception("Could not start Wi‑Fi Direct group (reason=$reason)")
}

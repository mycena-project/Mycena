// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import network.mycelium.app.bootstrap.BleBootstrapAdvertiser
import network.mycelium.app.bootstrap.BleBootstrapScanner
import network.mycelium.app.bootstrap.BleBootstrapPermissions
import network.mycelium.app.bootstrap.WifiMulticastLock
import network.mycelium.app.viewmodel.SettingsViewModel
import network.mycelium.app.viewmodel.SosViewModel
import network.mycelium.bindings.AppStoreListing
import network.mycelium.bindings.BulletinPost
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.ConnectivityMode
import network.mycelium.bindings.EnergyState
import network.mycelium.bindings.MailMessage
import network.mycelium.bindings.NodeConfig
import network.mycelium.bindings.NodeEventCallback
import network.mycelium.bindings.SosPost
import network.mycelium.bindings.mycelium
import java.io.File

class MeshService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private var bleAdvertiser: BleBootstrapAdvertiser? = null
    private var bleScanner: BleBootstrapScanner? = null
    private var bleCollectJob: kotlinx.coroutines.Job? = null
    private val notifiedPeerPrefixes = mutableSetOf<String>()
    private var nodeInitialized = false
    private var wifiMulticastLock: WifiMulticastLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        if (intent?.action == ACTION_UPDATE_BLE) {
            if (nodeInitialized) {
                updateBleBootstrap()
            }
            return START_STICKY
        }

        if (nodeInitialized) {
            return START_STICKY
        }

        val dbPath = filesDir.absolutePath + "/mycelium"
        File(dbPath).mkdirs()
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val displayName = prefs.getString("display_name", null)?.takeIf { it.isNotBlank() }
            ?: "android-user"
        val bootstrapPeers = prefs.getString("bootstrap_peers", "")
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val storageKeyHex = StorageKeyHelper.getOrCreateStorageKeyHex(this)
        wifiMulticastLock = WifiMulticastLock(this).also { it.acquire() }
        val rendezvousEnabled = prefs.getBoolean("rendezvous_enabled", true)
        val bulletinSubscriptions =
            prefs.getStringSet(SettingsViewModel.KEY_BULLETIN_SUBSCRIPTIONS, emptySet())
                ?.toList()
                .orEmpty()
        mycelium.initNode(
            NodeConfig(
                dbPath = dbPath,
                listenAddr = "/ip4/0.0.0.0/tcp/7761",
                displayName = displayName,
                bootstrapPeers = bootstrapPeers,
                storageKeyHex = storageKeyHex,
                rendezvousEnabled = rendezvousEnabled,
                bulletinSubscriptions = bulletinSubscriptions,
            ),
        )
        mycelium.setDisplayName(displayName)
        mycelium.setRendezvousEnabled(rendezvousEnabled)
        when (prefs.getString("energy_state", "Active")) {
            "Intermittent" -> mycelium.setEnergyState(EnergyState.INTERMITTENT)
            "Passive" -> mycelium.setEnergyState(EnergyState.PASSIVE)
            else -> mycelium.setEnergyState(EnergyState.ACTIVE)
        }
        if (prefs.getBoolean(SosViewModel.KEY_LISTEN_SOS, false)) {
            mycelium.setListenSos(true)
        }
        mycelium.setEventCallback(
            object : NodeEventCallback {
                override fun onPeerDiscovered(peerId: String) {
                    sendBroadcast(Intent(MeshEvents.PEER_UP).putExtra("peer_id", peerId))
                }

                override fun onPeerLost(peerId: String) {
                    sendBroadcast(Intent(MeshEvents.PEER_DOWN).putExtra("peer_id", peerId))
                }

                override fun onChatReceived(message: ChatMessage) {
                    showChatNotification(message)
                    sendBroadcast(
                        Intent(MeshEvents.CHAT)
                            .putExtra(MeshEvents.EXTRA_FROM_PEER, message.fromPeer)
                            .putExtra(MeshEvents.EXTRA_BODY, message.body),
                    )
                }

                override fun onMailReceived(message: MailMessage) {
                    showMailNotification(message)
                    sendBroadcast(Intent(MeshEvents.MAIL))
                }

                override fun onBulletinReceived(post: BulletinPost) {
                    sendBroadcast(Intent(MeshEvents.BULLETIN))
                }

                override fun onSosReceived(post: SosPost) {
                    showSosNotification(post)
                    sendBroadcast(Intent(MeshEvents.SOS))
                }

                override fun onAppStoreListing(listing: AppStoreListing) {
                    sendBroadcast(Intent(MeshEvents.APPSTORE))
                }

                override fun onConnectivityChanged(mode: ConnectivityMode) {
                    sendBroadcast(
                        Intent(MeshEvents.CONNECTIVITY).putExtra(
                            MeshEvents.EXTRA_INTERNET,
                            mode == ConnectivityMode.INTERNET,
                        ),
                    )
                }

                override fun onContactRequest(peerId: String, displayName: String) {
                    sendBroadcast(
                        Intent(MeshEvents.CONTACTS)
                            .putExtra("peer_id", peerId)
                            .putExtra("display_name", displayName),
                    )
                }
            },
        )

        nodeInitialized = true
        nodeReady = true
        updateBleBootstrap()
        return START_STICKY
    }

    private fun updateBleBootstrap() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val enabled = prefs.getBoolean(SettingsViewModel.KEY_BLE_BOOTSTRAP, false)
        stopBleBootstrap()

        if (!enabled || !BleBootstrapPermissions.hasAll(this)) {
            return
        }

        bleAdvertiser = BleBootstrapAdvertiser(this)
        bleScanner = BleBootstrapScanner(this)

        val peerId = mycelium.localPeerId()
        bleAdvertiser?.startAdvertising(peerId)
        bleScanner?.startScanning()

        bleCollectJob = serviceScope.launch {
            bleScanner!!.nearbyNodes
                .map { nodes -> nodes.map { it.peerIdPrefix } }
                .distinctUntilChanged()
                .collect { prefixes ->
                    prefixes.forEach { prefix ->
                        if (notifiedPeerPrefixes.add(prefix)) {
                            val node = bleScanner!!.nearbyNodes.value.find { it.peerIdPrefix == prefix }
                            if (node != null) {
                                showNearbyNodeNotification(node.peerIdPrefix, node.rssi)
                            }
                        }
                    }
                }
        }
    }

    private fun stopBleBootstrap() {
        bleCollectJob?.cancel()
        bleCollectJob = null
        bleAdvertiser?.stopAdvertising()
        bleScanner?.stopScanning()
        bleAdvertiser = null
        bleScanner = null
        notifiedPeerPrefixes.clear()
    }

    private fun showNearbyNodeNotification(peerIdPrefix: String, rssi: Int) {
        val strength = when {
            rssi > -60 -> getString(R.string.discovery_very_close)
            rssi > -80 -> getString(R.string.discovery_nearby)
            else -> getString(R.string.discovery_in_range)
        }
        val channel = NotificationChannel(
            DISCOVERY_CHANNEL_ID,
            getString(R.string.discovery_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java)
            .putExtra("action", "connect_peer")
            .putExtra("peer_prefix", peerIdPrefix)
        val pendingIntent = PendingIntent.getActivity(
            this,
            peerIdPrefix.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val shortPrefix = peerIdPrefix.take(8)
        val notification = NotificationCompat.Builder(this, DISCOVERY_CHANNEL_ID)
            .setContentTitle(getString(R.string.discovery_notification_title, strength))
            .setContentText(getString(R.string.discovery_notification_body, shortPrefix))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(DISCOVERY_NOTIFICATION_BASE + peerIdPrefix.hashCode(), notification)
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            "mycelium_bg",
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, "mycelium_bg")
            .setContentTitle(getString(R.string.notification_running))
            .setContentText(getString(R.string.app_tagline))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private fun showChatNotification(message: ChatMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, "mycelium_bg")
            .setContentTitle(getString(R.string.chat_title))
            .setContentText(
                getString(R.string.notification_new_message_from, message.fromDisplayName),
            )
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(1001, notification)
    }

    private fun showSosNotification(post: SosPost) {
        val manager = getSystemService(NotificationManager::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, "mycelium_bg")
            .setContentTitle(getString(R.string.sos_notification_title, post.geohash))
            .setContentText(post.message.take(120))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(1002, notification)
    }

    private fun showMailNotification(message: MailMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            1002,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, "mycelium_bg")
            .setContentTitle(getString(R.string.mail_title))
            .setContentText(
                getString(R.string.notification_new_message_from, message.fromDisplayName),
            )
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(1002, notification)
    }

    override fun onDestroy() {
        stopBleBootstrap()
        wifiMulticastLock?.release()
        wifiMulticastLock = null
        serviceScope.cancel()
        mycelium.stopNode()
        nodeReady = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_UPDATE_BLE = "network.mycelium.app.UPDATE_BLE"
        private const val NOTIFICATION_ID = 7761
        private const val DISCOVERY_CHANNEL_ID = "mycelium_discovery"
        private const val DISCOVERY_NOTIFICATION_BASE = 2000

        @Volatile
        var nodeReady: Boolean = false
            private set

        fun isNodeReady(): Boolean = nodeReady
    }
}

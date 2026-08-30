// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import network.mycelium.app.settings.ActivityProfileRuntime
import network.mycelium.app.stability.StabilityDiagnostics
import network.mycelium.app.thermal.ThermalEnforcement
import network.mycelium.bindings.mycelium
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Android Wi‑Fi Direct bearer (WifiP2pManager): bulk MYBR frames over a TCP socket.
 *
 * Discovery stays on BLE; this host upgrades only when Rust queues `medium=wifi_direct`.
 */
class WifiDirectBearerHost(
    private val context: Context,
) {
    private val logTag = "WifiDirectBearer"
    private val p2p = WifiP2pSupport.open(context)
    private val manager: WifiP2pManager? = p2p?.manager
    private val channel: WifiP2pManager.Channel? = p2p?.channel

    private val sessions = ConcurrentHashMap<String, PeerSession>()
    private val connectMutex = Mutex()
    private val lastActivityMs = AtomicLong(0)

    private var receiverRegistered = false
    private var pendingConnect: CompletableDeferred<Boolean>? = null
    private var targetDeviceAddress: String? = null

    private val intentFilter =
        IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> onPeersChanged()
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
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
                            manager.requestConnectionInfo(channel) { info ->
                                onConnectionInfo(info)
                            }
                        } else {
                            pendingConnect?.complete(false)
                            pendingConnect = null
                        }
                    }
                }
            }
        }

    fun register() {
        if (receiverRegistered) return
        if (manager == null || channel == null) {
            StabilityDiagnostics.info("WifiDirectBearer.register", "p2p_unavailable")
            return
        }
        try {
            ContextCompat.registerReceiver(
                context.applicationContext,
                receiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        } catch (t: Throwable) {
            if (t is VirtualMachineError) throw t
            StabilityDiagnostics.record("WifiDirectBearer.registerReceiver", t)
        }
    }

    fun unregister() {
        if (!receiverRegistered) return
        runCatching { context.applicationContext.unregisterReceiver(receiver) }
            .onFailure { e ->
                if (e is IllegalArgumentException) {
                    Log.d(logTag, "unregister ignored: ${e.javaClass.simpleName}")
                } else {
                    StabilityDiagnostics.record("WifiDirectBearer.unregisterReceiver", e)
                }
            }
        receiverRegistered = false
    }

    /** Ensure P2P link to peer (BLE MAC address) and send length-prefixed bearer frames. */
    suspend fun sendFrames(peerId: String, deviceAddress: String, frames: List<ByteArray>): Boolean =
        withContext(Dispatchers.IO) {
            if (frames.isEmpty()) return@withContext true
            if (manager == null || channel == null) return@withContext false
            connectMutex.withLock {
                val session = sessions[peerId]
                if (session?.isAlive() != true) {
                    val ok = ensureConnected(deviceAddress)
                    if (!ok) {
                        Log.w(logTag, "P2P connect failed for $peerId @ $deviceAddress")
                        return@withContext false
                    }
                }
                val active = sessions[peerId] ?: return@withContext false
                for (frame in frames) {
                    if (!active.writeFrame(frame)) {
                        sessions.remove(peerId)
                        return@withContext false
                    }
                }
                lastActivityMs.set(System.currentTimeMillis())
                true
            }
        }

    fun noteIdleDowngradeIfNeeded(onDowngrade: () -> Unit) {
        val idle = System.currentTimeMillis() - lastActivityMs.get()
        if (lastActivityMs.get() == 0L ||
            idle < ThermalEnforcement.effectiveWifiDirectIdleMs(
                ActivityProfileRuntime.wifiDirectIdleDowngradeMs,
            )
        ) {
            return
        }
        if (sessions.isEmpty()) return
        Log.i(logTag, "downgrading to BLE idle after ${idle}ms")
        teardownAll()
        lastActivityMs.set(0)
        onDowngrade()
    }

    fun teardownAll() {
        sessions.values.forEach { it.closeQuietly() }
        sessions.clear()
        val manager = manager
        val channel = channel
        if (manager != null && channel != null) {
            try {
                manager.removeGroup(channel, null)
            } catch (e: SecurityException) {
                StabilityDiagnostics.record("WifiDirectBearer.removeGroup", e)
            }
        }
    }

    private suspend fun ensureConnected(deviceAddress: String): Boolean {
        val manager = manager ?: return false
        val channel = channel ?: return false
        targetDeviceAddress = deviceAddress
        val deferred = CompletableDeferred<Boolean>()
        pendingConnect = deferred
        manager.discoverPeers(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() = Unit
                override fun onFailure(reason: Int) {
                    Log.w(logTag, "discoverPeers failed: $reason")
                    pendingConnect?.complete(false)
                    pendingConnect = null
                }
            },
        )
        val connected = withTimeoutOrNull(12_000) { deferred.await() } ?: false
        pendingConnect = null
        return connected
    }

    private fun onPeersChanged() {
        val manager = manager ?: return
        val channel = channel ?: return
        val target = targetDeviceAddress ?: return
        manager.requestPeers(channel) { peers ->
            val device =
                peers.deviceList.firstOrNull { dev ->
                    dev.deviceAddress.equals(target, ignoreCase = true)
                } ?: return@requestPeers
            connectTo(device)
        }
    }

    private fun connectTo(device: WifiP2pDevice) {
        val manager = manager ?: return
        val channel = channel ?: return
        val config =
            WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = android.net.wifi.WpsInfo.PBC
            }
        manager.connect(
            channel,
            config,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(logTag, "connect initiated to ${device.deviceAddress}")
                }
                override fun onFailure(reason: Int) {
                    Log.w(logTag, "connect failed: $reason")
                    pendingConnect?.complete(false)
                    pendingConnect = null
                }
            },
        )
    }

    private fun onConnectionInfo(info: WifiP2pInfo) {
        val peerId =
            BleMeshLinkRegistry.peerForAddress(targetDeviceAddress ?: "") ?: "unknown-peer"
        try {
            if (info.isGroupOwner) {
                startGroupOwnerServer(peerId)
            } else {
                val host =
                    info.groupOwnerAddress?.hostAddress ?: run {
                        pendingConnect?.complete(false)
                        return
                    }
                startClient(peerId, host)
            }
            pendingConnect?.complete(true)
        } catch (e: Exception) {
            Log.w(logTag, "connection info: ${e.message}")
            pendingConnect?.complete(false)
        } finally {
            pendingConnect = null
        }
    }

    private fun startGroupOwnerServer(peerId: String) {
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(WifiDirectBearer.SOCKET_PORT))
        Thread {
            try {
                val socket = server.accept()
                socket.tcpNoDelay = true
                val session = PeerSession(peerId, socket, DataOutputStream(socket.getOutputStream()))
                sessions[peerId] = session
                session.startInboundReader()
                Log.i(logTag, "GO accepted client for $peerId")
            } catch (e: Exception) {
                Log.w(logTag, "GO accept: ${e.message}")
            }
        }.start()
    }

    private fun startClient(peerId: String, host: String) {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, WifiDirectBearer.SOCKET_PORT), 8_000)
        socket.tcpNoDelay = true
        val session = PeerSession(peerId, socket, DataOutputStream(socket.getOutputStream()))
        sessions[peerId] = session
        session.startInboundReader()
        Log.i(logTag, "client connected to GO $host for $peerId")
    }

    private class PeerSession(
        private val peerId: String,
        private val socket: Socket,
        private val out: DataOutputStream,
    ) {
        @Volatile
        private var readerStarted = false

        fun startInboundReader() {
            if (readerStarted) return
            readerStarted = true
            Thread {
                val input = DataInputStream(socket.getInputStream())
                try {
                    while (socket.isConnected && !socket.isClosed) {
                        val len = input.readInt()
                        if (len <= 0 || len > 512 * 1024) break
                        val frame = ByteArray(len)
                        input.readFully(frame)
                        mycelium.ingestBleFrame(peerId, frame)
                    }
                } catch (e: Exception) {
                    Log.d("WifiDirectBearer", "inbound reader $peerId: ${e.message}")
                }
            }.start()
        }
        @Synchronized
        fun writeFrame(frame: ByteArray): Boolean {
            return try {
                out.writeInt(frame.size)
                out.write(frame)
                out.flush()
                true
            } catch (e: Exception) {
                false
            }
        }

        fun isAlive(): Boolean = socket.isConnected && !socket.isClosed

        fun closeQuietly() {
            runCatching { out.close() }
            runCatching { socket.close() }
        }
    }
}

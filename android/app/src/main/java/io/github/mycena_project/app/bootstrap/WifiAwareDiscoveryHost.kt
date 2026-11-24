// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.mycena_project.app.stability.StabilityDiagnostics
import io.github.mycena_project.bindings.mycena
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wi-Fi Aware (primary) and NSD (fallback) discovery bearer — publishes transport invite URIs only.
 * Bulk data stays on BLE / Wi-Fi Direct ([ADR 009](../../../../../docs/adr/009-wifi-aware-discovery-bearer.md)).
 */
class WifiAwareDiscoveryHost(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "WifiAwareDiscovery"
        private const val AWARE_SERVICE = "mycena-mesh"
        private const val NSD_TYPE = "_mycena._tcp"
        private const val MAX_INVITE_BYTES = 240
    }

    private val running = AtomicBoolean(false)
    private var awareSession: WifiAwareSession? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistration: NsdManager.RegistrationListener? = null
    private var nsdDiscovery: NsdManager.DiscoveryListener? = null
    private var usingAware = false

    fun start(inviteProvider: () -> String) {
        if (!running.compareAndSet(false, true)) return
        if (!BleBootstrapPermissions.hasWifiDiscovery(context)) {
            Log.w(TAG, "Wi‑Fi discovery permission missing — using NSD only")
            usingAware = false
            mycena.setWifiAwareAvailable(false)
            startNsdFallback(inviteProvider)
            return
        }
        val awareManager =
            context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && awareManager?.isAvailable == true) {
            usingAware = true
            mycena.setWifiAwareAvailable(true)
            attachWifiAware(awareManager, inviteProvider)
        } else {
            usingAware = false
            mycena.setWifiAwareAvailable(false)
            startNsdFallback(inviteProvider)
        }
    }

    fun refreshAnnouncement(inviteProvider: () -> String) {
        if (!running.get()) return
        val invite = inviteProvider().trim()
        if (invite.isEmpty() || invite.length > MAX_INVITE_BYTES) return
        mycena.queueDiscoveryAnnouncement(invite)
        if (!usingAware) {
            publishNsd(invite)
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        awareSession?.close()
        awareSession = null
        nsdRegistration?.let { nsdManager?.unregisterService(it) }
        nsdDiscovery?.let { nsdManager?.stopServiceDiscovery(it) }
        nsdRegistration = null
        nsdDiscovery = null
        nsdManager = null
        mycena.setWifiAwareAvailable(false)
    }

    private fun attachWifiAware(manager: WifiAwareManager, inviteProvider: () -> String) {
        manager.attach(
            object : AttachCallback() {
                override fun onAttached(session: WifiAwareSession) {
                    awareSession = session
                    try {
                        publishWifiAware(session, inviteProvider)
                        subscribeWifiAware(session)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Wi‑Fi Aware publish blocked — falling back to NSD", e)
                        fallbackFromAware(session, inviteProvider)
                    }
                }

                override fun onAttachFailed() {
                    Log.w(TAG, "Wi-Fi Aware attach failed — falling back to NSD")
                    usingAware = false
                    mycena.setWifiAwareAvailable(false)
                    startNsdFallback(inviteProvider)
                }
            },
            null,
        )
    }

    private fun fallbackFromAware(session: WifiAwareSession, inviteProvider: () -> String) {
        usingAware = false
        mycena.setWifiAwareAvailable(false)
        runCatching { session.close() }
        awareSession = null
        startNsdFallback(inviteProvider)
    }

    private fun publishWifiAware(session: WifiAwareSession, inviteProvider: () -> String) {
        val invite = inviteProvider().trim()
        if (invite.isEmpty()) return
        val raw = invite.toByteArray(StandardCharsets.UTF_8)
        val info = raw.copyOf(minOf(MAX_INVITE_BYTES, raw.size))
        mycena.queueDiscoveryAnnouncement(invite)
        val config =
            PublishConfig.Builder()
                .setServiceName(AWARE_SERVICE)
                .setServiceSpecificInfo(info)
                .build()
        session.publish(
            config,
            object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: android.net.wifi.aware.PublishDiscoverySession) {
                    Log.i(TAG, "Wi-Fi Aware publish started")
                }

                override fun onSessionConfigFailed() {
                    Log.w(TAG, "Wi-Fi Aware publish config failed")
                }
            },
            null,
        )
    }

    private fun subscribeWifiAware(session: WifiAwareSession) {
        val config =
            SubscribeConfig.Builder()
                .setServiceName(AWARE_SERVICE)
                .build()
        session.subscribe(
            config,
            object : DiscoverySessionCallback() {
                override fun onServiceDiscovered(
                    peerHandle: android.net.wifi.aware.PeerHandle,
                    serviceSpecificInfo: ByteArray?,
                    matchFilter: List<ByteArray>?,
                ) {
                    val uri =
                        serviceSpecificInfo
                            ?.let { String(it, Charsets.UTF_8) }
                            ?.trim()
                            .orEmpty()
                    if (uri.isEmpty()) return
                    val hint = peerHandle.hashCode().toString(16)
                    scope.launch(Dispatchers.IO) {
                        mycena.ingestWifiAwareDiscovery(hint, uri, 25u)
                        when (TransportInviteHandoff.tryInviteHandoff(uri, hint)) {
                            TransportInviteHandoff.Result.Connected ->
                                Log.i(TAG, "Wi-Fi Aware handoff ok hint=$hint")
                            is TransportInviteHandoff.Result.Failed ->
                                Log.d(TAG, "Wi-Fi Aware handoff deferred hint=$hint")
                            else -> Unit
                        }
                    }
                }
            },
            null,
        )
    }

    private fun startNsdFallback(inviteProvider: () -> String) {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (nsdManager == null) {
            StabilityDiagnostics.info("WifiAware.startNsdFallback", "nsd_null")
            return
        }
        publishNsd(inviteProvider().trim())
        val listener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.i(TAG, "NSD discovery started type=$serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.serviceType != NSD_TYPE) return
                    nsdManager?.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val uri =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        info.attributes?.get("invite")?.let { raw ->
                                            String(raw, Charsets.UTF_8).trim()
                                        }.orEmpty()
                                    } else {
                                        ""
                                    }
                                if (uri.isEmpty()) return
                                val hint = info.serviceName ?: "nsd"
                                scope.launch(Dispatchers.IO) {
                                    mycena.ingestWifiAwareDiscovery(hint, uri, 40u)
                                    TransportInviteHandoff.tryInviteHandoff(uri, hint)
                                }
                            }
                        },
                    )
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            }
        nsdDiscovery = listener
        nsdManager?.discoverServices(NSD_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun publishNsd(invite: String) {
        if (invite.isEmpty()) return
        val mgr = nsdManager ?: return
        nsdRegistration?.let { mgr.unregisterService(it) }
        val info =
            NsdServiceInfo().apply {
                serviceName = "myc-${mycena.localPeerId().take(12)}"
                serviceType = NSD_TYPE
                port = 0
                setAttribute("invite", invite)
            }
        val reg =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.i(TAG, "NSD registered ${info.serviceName}")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                    Log.w(TAG, "NSD registration failed code=$code")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            }
        nsdRegistration = reg
        mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg)
        mycena.queueDiscoveryAnnouncement(invite)
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.app.bootstrap.ApkSharePayload
import network.mycelium.app.bootstrap.LocalHotspotHelper
import network.mycelium.app.bootstrap.OfflineBootstrap
import network.mycelium.app.bootstrap.ShareApkArtifact
import network.mycelium.app.bootstrap.ShareLinkAddresses
import network.mycelium.app.bootstrap.ShareTransportPreconditions
import network.mycelium.app.bootstrap.WifiDirectShareGroupHelper
import network.mycelium.app.bootstrap.WifiDirectSharer
import network.mycelium.app.bootstrap.WifiPauseHelper
import network.mycelium.bindings.mycelium

enum class ShareAppMode {
    /** Local hotspot on this phone — no router or internet. */
    PHONE_TO_PHONE,
    /** Both phones already on the same Wi‑Fi or hotspot. */
    LOCAL_NETWORK,
}

enum class SharePhoneTransport {
    /** [LocalHotspotHelper] local-only hotspot. */
    LOCAL_HOTSPOT,
    /** Wi‑Fi Direct P2P group (fallback when hotspot fails). */
    WIFI_DIRECT,
}

data class ShareAppSession(
    val mode: ShareAppMode,
    val installUrl: String,
    val hotspot: ApkSharePayload.HotspotCredentials? = null,
    /** Set when phone-to-phone uses Wi‑Fi Direct instead of a local hotspot. */
    val phoneTransport: SharePhoneTransport? = null,
    /** True when we briefly disconnected from home Wi‑Fi to start sharing. */
    val wifiWasPaused: Boolean = false,
    val sidecarReady: Boolean = false,
    val meshInviteUri: String? = null,
    val apkSha256: String? = null,
)

class ShareAppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ShareAppViewModel"
        private const val SIDECAR_WAIT_MS = 10_000L
        private const val SIDECAR_POLL_MS = 250L
    }

    private val sharer = WifiDirectSharer()
    private val hotspotHelper = LocalHotspotHelper(application)
    private val wifiDirectGroup = WifiDirectShareGroupHelper(application)
    private val wifiPause = WifiPauseHelper(application)

    private val _session = MutableStateFlow<ShareAppSession?>(null)
    val session: StateFlow<ShareAppSession?> = _session

    private val _isServing = MutableStateFlow(false)
    val isServing: StateFlow<Boolean> = _isServing

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting

    private val _transferCount = MutableStateFlow(0)
    val transferCount: StateFlow<Int> = _transferCount

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Last local-only hotspot failure reason (Android API constant), if any. */
    private val _hotspotFailureReason = MutableStateFlow<Int?>(null)
    val hotspotFailureReason: StateFlow<Int?> = _hotspotFailureReason

    @Volatile
    private var tearingDown = false
    private var lastSidecar: ByteArray? = null

    init {
        sharer.setOnTransferListener {
            _transferCount.value = sharer.getTransferCount()
        }
        hotspotHelper.setOnStoppedListener {
            if (!tearingDown && _isServing.value) {
                viewModelScope.launch {
                    Log.w(TAG, "P2P-MT hotspot stopped by OS while serving")
                    _error.value = "hotspot_stopped"
                    stopSharingKeepingError()
                }
            }
        }
    }

    fun startPhoneToPhone() {
        startSharing(ShareAppMode.PHONE_TO_PHONE)
    }

    fun startOnLocalNetwork() {
        startSharing(ShareAppMode.LOCAL_NETWORK)
    }

    private fun startSharing(mode: ShareAppMode) {
        if (_isStarting.value || _isServing.value) return
        viewModelScope.launch {
            _error.value = null
            _hotspotFailureReason.value = null
            _isStarting.value = true
            try {
                val app = getApplication<Application>()
                val blocker =
                    if (mode == ShareAppMode.PHONE_TO_PHONE) {
                        ShareTransportPreconditions.phoneToPhoneBlocker(app)
                    } else {
                        ShareTransportPreconditions.localNetworkBlocker(app)
                    }
                if (blocker != null) {
                    _error.value = blocker
                    return@launch
                }

                val apkFile = ShareApkArtifact.materializeInstalledApk(app)
                val hostsBefore = ShareLinkAddresses.currentIpv4Hosts()

                val phoneLink =
                    if (mode == ShareAppMode.PHONE_TO_PHONE) {
                        startPhoneToPhoneLink()
                    } else {
                        null
                    }
                val preferredHost =
                    phoneLink?.advertisedHost
                        ?: ShareLinkAddresses.preferredLanHost(app)

                val url =
                    sharer.startApkServer(
                        preferredHost = preferredHost,
                        hostsBeforeLink = hostsBefore,
                        apkFile = apkFile,
                    )
                val sidecarReady = refreshBootstrapSidecar(url)
                val invite = if (sidecarReady) parseInviteFromSidecar() else null
                _session.value =
                    ShareAppSession(
                        mode = mode,
                        installUrl = url,
                        hotspot = phoneLink?.credentials,
                        phoneTransport = phoneLink?.transport,
                        wifiWasPaused = phoneLink?.wifiWasPaused == true,
                        sidecarReady = sidecarReady,
                        meshInviteUri = invite,
                        apkSha256 = sharer.apkSha256Hex(),
                    )
                _isServing.value = true
                _transferCount.value = 0
                Log.i(TAG, "P2P-MT-04 serving url=$url sidecar=$sidecarReady")
            } catch (e: CancellationException) {
                cleanupPhoneToPhone()
                throw e
            } catch (e: LocalHotspotHelper.HotspotStartException) {
                Log.e(TAG, "All phone-to-phone transports failed after hotspot: ${e.reason}")
                _hotspotFailureReason.value = e.reason
                _error.value = "hotspot_failed"
                cleanupPhoneToPhone()
            } catch (e: WifiDirectShareGroupHelper.WifiDirectGroupException) {
                Log.e(TAG, "Wi‑Fi Direct fallback failed: ${e.reason}")
                _error.value = "wifi_direct_failed"
                cleanupPhoneToPhone()
            } catch (e: ShareApkArtifact.SplitApkNotSupportedException) {
                _error.value = "split_apk"
                cleanupPhoneToPhone()
            } catch (e: ShareApkArtifact.DebugApkNotShareableException) {
                _error.value = "debug_apk"
                cleanupPhoneToPhone()
            } catch (e: ShareApkArtifact.NotTechnicalBetaException) {
                _error.value = "not_technical_beta"
                cleanupPhoneToPhone()
            } catch (e: ShareApkArtifact.IncompleteApkException) {
                _error.value = "incomplete_apk"
                cleanupPhoneToPhone()
            } catch (e: WifiDirectSharer.NoReachableShareAddressException) {
                _error.value = "no_reachable_address"
                cleanupPhoneToPhone()
            } catch (t: Throwable) {
                Log.e(TAG, "share start failed", t)
                _error.value = t.message ?: "share_failed"
                cleanupPhoneToPhone()
            } finally {
                _isStarting.value = false
            }
        }
    }

    private data class PhoneLink(
        val credentials: ApkSharePayload.HotspotCredentials,
        val transport: SharePhoneTransport,
        val wifiWasPaused: Boolean,
        val advertisedHost: String?,
    )

    private suspend fun startPhoneToPhoneLink(): PhoneLink {
        val app = getApplication<Application>()
        var wifiWasPaused = false
        try {
            val credentials = hotspotHelper.start()
            return PhoneLink(credentials, SharePhoneTransport.LOCAL_HOTSPOT, wifiWasPaused, null)
        } catch (first: LocalHotspotHelper.HotspotStartException) {
            Log.w(TAG, "Local hotspot failed (reason=${first.reason}), trying Wi‑Fi pause + retry")
            _hotspotFailureReason.value = first.reason
        }

        if (ShareTransportPreconditions.canPauseStaWifi()) {
            wifiWasPaused = wifiPause.pauseIfConnected()
            if (wifiWasPaused) {
                delay(800)
                try {
                    val credentials = hotspotHelper.start()
                    return PhoneLink(
                        credentials,
                        SharePhoneTransport.LOCAL_HOTSPOT,
                        wifiWasPaused,
                        null,
                    )
                } catch (second: LocalHotspotHelper.HotspotStartException) {
                    Log.w(
                        TAG,
                        "Local hotspot failed after Wi‑Fi pause (reason=${second.reason}), trying Wi‑Fi Direct",
                    )
                    _hotspotFailureReason.value = second.reason
                    hotspotHelper.stop()
                }
            } else {
                hotspotHelper.stop()
            }
        } else {
            hotspotHelper.stop()
        }

        val directBlocker = ShareTransportPreconditions.directFallbackBlocker(app)
        if (directBlocker != null) {
            Log.w(TAG, "Wi‑Fi Direct fallback blocked: $directBlocker")
            throw WifiDirectShareGroupHelper.WifiDirectGroupException(-14)
        }
        val started = wifiDirectGroup.start()
        return PhoneLink(
            started.credentials,
            SharePhoneTransport.WIFI_DIRECT,
            wifiWasPaused,
            started.ownerHost,
        )
    }

    fun stopSharing() {
        _error.value = null
        _hotspotFailureReason.value = null
        stopSharingKeepingError()
    }

    private fun stopSharingKeepingError() {
        tearingDown = true
        try {
            sharer.stopServer()
            cleanupPhoneToPhone()
            _isServing.value = false
            _session.value = null
        } finally {
            tearingDown = false
        }
    }

    private fun cleanupPhoneToPhone() {
        lastSidecar = null
        sharer.stopServer()
        hotspotHelper.stop()
        wifiDirectGroup.stop()
        wifiPause.restore()
    }

    fun clearError() {
        _error.value = null
        _hotspotFailureReason.value = null
    }

    private suspend fun refreshBootstrapSidecar(installUrl: String): Boolean {
        val deadline = System.currentTimeMillis() + SIDECAR_WAIT_MS
        while (!MeshService.isNodeReady() && System.currentTimeMillis() < deadline) {
            delay(SIDECAR_POLL_MS)
        }
        if (!MeshService.isNodeReady()) {
            Log.w(TAG, "P2P-MT-05 sidecar skipped: node not ready")
            lastSidecar = null
            sharer.setBootstrapSidecar(null)
            return false
        }
        return try {
            val bytes = mycelium.buildOfflineShareBootstrapSidecar(installUrl)
            if (bytes.isEmpty()) {
                lastSidecar = null
                sharer.setBootstrapSidecar(null)
                false
            } else {
                lastSidecar = bytes
                sharer.setBootstrapSidecar(bytes)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "sidecar build failed: ${e.message}")
            lastSidecar = null
            sharer.setBootstrapSidecar(null)
            false
        }
    }

    private fun parseInviteFromSidecar(): String? =
        lastSidecar?.let { OfflineBootstrap.parseInviteUri(it) }

    override fun onCleared() {
        tearingDown = true
        sharer.stopServer()
        cleanupPhoneToPhone()
        super.onCleared()
    }
}

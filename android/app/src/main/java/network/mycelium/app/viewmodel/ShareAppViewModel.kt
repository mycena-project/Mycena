// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.app.bootstrap.ApkSharePayload
import network.mycelium.app.bootstrap.LocalHotspotHelper
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
)

class ShareAppViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ShareAppViewModel"
    }

    private val sharer = WifiDirectSharer(application)
    private val hotspotHelper = LocalHotspotHelper(application)
    private val wifiDirectGroup = WifiDirectShareGroupHelper(application)
    private val wifiPause = WifiPauseHelper(application)

    private val _session = MutableStateFlow<ShareAppSession?>(null)
    val session: StateFlow<ShareAppSession?> = _session

    private val _isServing = MutableStateFlow(false)
    val isServing: StateFlow<Boolean> = _isServing

    private val _transferCount = MutableStateFlow(0)
    val transferCount: StateFlow<Int> = _transferCount

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Last local-only hotspot failure reason (Android API constant), if any. */
    private val _hotspotFailureReason = MutableStateFlow<Int?>(null)
    val hotspotFailureReason: StateFlow<Int?> = _hotspotFailureReason

    init {
        sharer.setOnTransferListener {
            _transferCount.value = sharer.getTransferCount()
        }
    }

    fun startPhoneToPhone() {
        startSharing(ShareAppMode.PHONE_TO_PHONE)
    }

    fun startOnLocalNetwork() {
        startSharing(ShareAppMode.LOCAL_NETWORK)
    }

    private fun startSharing(mode: ShareAppMode) {
        viewModelScope.launch {
            _error.value = null
            _hotspotFailureReason.value = null
            try {
                val phoneLink =
                    if (mode == ShareAppMode.PHONE_TO_PHONE) {
                        startPhoneToPhoneLink()
                    } else {
                        null
                    }
                val url = sharer.startApkServer()
                refreshBootstrapSidecar(url)
                _session.value =
                    ShareAppSession(
                        mode = mode,
                        installUrl = url,
                        hotspot = phoneLink?.credentials,
                        phoneTransport = phoneLink?.transport,
                        wifiWasPaused = phoneLink?.wifiWasPaused == true,
                    )
                _isServing.value = true
                _transferCount.value = 0
            } catch (e: LocalHotspotHelper.HotspotStartException) {
                Log.e(TAG, "All phone-to-phone transports failed after hotspot: ${e.reason}")
                _hotspotFailureReason.value = e.reason
                _error.value = "hotspot_failed"
                cleanupPhoneToPhone()
            } catch (e: WifiDirectShareGroupHelper.WifiDirectGroupException) {
                Log.e(TAG, "Wi‑Fi Direct fallback failed: ${e.reason}")
                _error.value = "wifi_direct_failed"
                cleanupPhoneToPhone()
            } catch (e: Exception) {
                _error.value = e.message ?: "share_failed"
                cleanupPhoneToPhone()
            }
        }
    }

    private data class PhoneLink(
        val credentials: ApkSharePayload.HotspotCredentials,
        val transport: SharePhoneTransport,
        val wifiWasPaused: Boolean,
    )

    private suspend fun startPhoneToPhoneLink(): PhoneLink {
        var wifiWasPaused = false
        try {
            val credentials = hotspotHelper.start()
            return PhoneLink(credentials, SharePhoneTransport.LOCAL_HOTSPOT, wifiWasPaused)
        } catch (first: LocalHotspotHelper.HotspotStartException) {
            Log.w(TAG, "Local hotspot failed (reason=${first.reason}), trying Wi‑Fi pause + retry")
            _hotspotFailureReason.value = first.reason
        }

        wifiWasPaused = wifiPause.pauseIfConnected()
        if (wifiWasPaused) {
            delay(800)
            try {
                val credentials = hotspotHelper.start()
                return PhoneLink(credentials, SharePhoneTransport.LOCAL_HOTSPOT, wifiWasPaused)
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

        val directCredentials = wifiDirectGroup.start()
        return PhoneLink(directCredentials, SharePhoneTransport.WIFI_DIRECT, wifiWasPaused)
    }

    fun stopSharing() {
        sharer.stopServer()
        cleanupPhoneToPhone()
        _isServing.value = false
        _session.value = null
        _error.value = null
        _hotspotFailureReason.value = null
    }

    private fun cleanupPhoneToPhone() {
        sharer.stopServer()
        hotspotHelper.stop()
        wifiDirectGroup.stop()
        wifiPause.restore()
    }

    fun clearError() {
        _error.value = null
        _hotspotFailureReason.value = null
    }

    private fun refreshBootstrapSidecar(installUrl: String) {
        if (!MeshService.isNodeReady()) {
            sharer.setBootstrapSidecar(null)
            return
        }
        try {
            val bytes = mycelium.buildOfflineShareBootstrapSidecar(installUrl)
            sharer.setBootstrapSidecar(bytes)
        } catch (_: Exception) {
            sharer.setBootstrapSidecar(null)
        }
    }

    override fun onCleared() {
        sharer.stopServer()
        cleanupPhoneToPhone()
        super.onCleared()
    }
}

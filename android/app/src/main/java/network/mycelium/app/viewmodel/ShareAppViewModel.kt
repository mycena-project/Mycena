// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.app.bootstrap.ApkSharePayload
import network.mycelium.app.bootstrap.LocalHotspotHelper
import network.mycelium.app.bootstrap.WifiDirectSharer
import network.mycelium.bindings.mycelium

enum class ShareAppMode {
    /** Local hotspot on this phone — no router or internet. */
    PHONE_TO_PHONE,
    /** Both phones already on the same Wi‑Fi or hotspot. */
    LOCAL_NETWORK,
}

data class ShareAppSession(
    val mode: ShareAppMode,
    val installUrl: String,
    val hotspot: ApkSharePayload.HotspotCredentials? = null,
)

class ShareAppViewModel(application: Application) : AndroidViewModel(application) {

    private val sharer = WifiDirectSharer(application)
    private val hotspotHelper = LocalHotspotHelper(application)

    private val _session = MutableStateFlow<ShareAppSession?>(null)
    val session: StateFlow<ShareAppSession?> = _session

    private val _isServing = MutableStateFlow(false)
    val isServing: StateFlow<Boolean> = _isServing

    private val _transferCount = MutableStateFlow(0)
    val transferCount: StateFlow<Int> = _transferCount

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

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
            try {
                val credentials =
                    if (mode == ShareAppMode.PHONE_TO_PHONE) {
                        hotspotHelper.start()
                    } else {
                        null
                    }
                val url = sharer.startApkServer()
                refreshBootstrapSidecar(url)
                _session.value =
                    ShareAppSession(
                        mode = mode,
                        installUrl = url,
                        hotspot = credentials,
                    )
                _isServing.value = true
                _transferCount.value = 0
            } catch (e: LocalHotspotHelper.HotspotStartException) {
                _error.value = "hotspot_failed"
                hotspotHelper.stop()
                sharer.stopServer()
            } catch (e: Exception) {
                _error.value = e.message ?: "share_failed"
                hotspotHelper.stop()
                sharer.stopServer()
            }
        }
    }

    fun stopSharing() {
        sharer.stopServer()
        hotspotHelper.stop()
        _isServing.value = false
        _session.value = null
        _error.value = null
    }

    fun clearError() {
        _error.value = null
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
        hotspotHelper.stop()
        super.onCleared()
    }
}

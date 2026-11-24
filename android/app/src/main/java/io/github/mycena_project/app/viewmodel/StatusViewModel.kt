// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import io.github.mycena_project.app.MeshEvent
import io.github.mycena_project.app.MeshService
import io.github.mycena_project.app.meshEvents
import io.github.mycena_project.bindings.ConnectivityMode
import io.github.mycena_project.bindings.mycena
import uniffi.mycena.InternalException

/** How long to show the animated “searching” banner after start or connectivity change. */
private const val SEARCHING_BANNER_INITIAL_MS = 25_000L
private const val SEARCHING_BANNER_PULSE_MS = 20_000L

class StatusViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _connectivityMode =
        MutableStateFlow(ConnectivityMode.INTERNET)
    val connectivityMode: StateFlow<ConnectivityMode> = _connectivityMode.asStateFlow()

    /** True only briefly while actively discovering peers (not permanently when idle). */
    private val _showSearchingBanner = MutableStateFlow(false)
    val showSearchingBanner: StateFlow<Boolean> = _showSearchingBanner.asStateFlow()

    private val _crisisActive = MutableStateFlow(false)
    val crisisActive: StateFlow<Boolean> = _crisisActive.asStateFlow()

    private val _offlineMeshActive = MutableStateFlow(false)
    val offlineMeshActive: StateFlow<Boolean> = _offlineMeshActive.asStateFlow()

    private var hideSearchingJob: Job? = null

    init {
        viewModelScope.launch {
            while (!MeshService.isNodeReady()) {
                delay(50)
            }
            refreshPeerCount()
            refreshCrisisActive()
            refreshOfflineMesh()
            runCatching { mycena.currentConnectivityMode() }.getOrNull()?.let {
                _connectivityMode.value = it
            }
            pulseSearchingBanner(initial = true)

            appContext.meshEvents().collect { event ->
                when (event) {
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat -> {
                        refreshPeerCount()
                        refreshOfflineMesh()
                        if (_peerCount.value > 0) {
                            stopSearchingBanner()
                        }
                    }
                    is MeshEvent.Connectivity -> {
                        _connectivityMode.value = event.mode
                        refreshCrisisActive()
                        refreshOfflineMesh()
                        pulseSearchingBanner()
                    }
                    MeshEvent.Sos -> refreshCrisisActive()
                    else -> Unit
                }
            }
        }
    }

    private fun refreshCrisisActive() {
        _crisisActive.value = runCatching { mycena.isCrisisModeActive() }.getOrDefault(false)
    }

    private fun refreshOfflineMesh() {
        val prefs = appContext.getSharedPreferences(SettingsViewModel.PREFS_NAME, Application.MODE_PRIVATE)
        val bleOn = prefs.getBoolean(SettingsViewModel.KEY_BLE_BOOTSTRAP, false)
            || prefs.getBoolean(SettingsViewModel.KEY_MAX_CONNECTIVITY, false)
        val meshOnly = _connectivityMode.value == ConnectivityMode.MESH_ONLY
        _offlineMeshActive.value = meshOnly && bleOn && _peerCount.value > 0
    }

    private fun refreshPeerCount() {
        _peerCount.value =
            runCatching {
                mycena.connectedPeers().size
            }.getOrElse { e ->
                if (e is InternalException && e.message?.contains("node not initialized") == true) 0
                else throw e
            }
    }

    private fun pulseSearchingBanner(initial: Boolean = false) {
        if (_peerCount.value > 0) return
        _showSearchingBanner.value = true
        hideSearchingJob?.cancel()
        val duration = if (initial) SEARCHING_BANNER_INITIAL_MS else SEARCHING_BANNER_PULSE_MS
        hideSearchingJob =
            viewModelScope.launch {
                delay(duration)
                if (_peerCount.value == 0) {
                    _showSearchingBanner.value = false
                }
            }
    }

    private fun stopSearchingBanner() {
        hideSearchingJob?.cancel()
        _showSearchingBanner.value = false
    }
}

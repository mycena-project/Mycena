// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import io.github.mycena_project.app.MeshEvent
import io.github.mycena_project.app.discovery.MeshDiscoverySnapshot
import io.github.mycena_project.app.discovery.MeshDiscoveryState
import io.github.mycena_project.app.discovery.PeersDiscoveryPresentation
import io.github.mycena_project.app.discovery.PeersDiscoveryUi
import io.github.mycena_project.app.meshEvents
import io.github.mycena_project.bindings.NodeMetrics
import io.github.mycena_project.bindings.PeerReputationData
import io.github.mycena_project.bindings.mycena

class PeersViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    private val _networkPeers = MutableStateFlow<List<String>>(emptyList())
    val networkPeers: StateFlow<List<String>> = _networkPeers.asStateFlow()

    val discoveryPresentation: StateFlow<PeersDiscoveryPresentation> =
        combine(MeshDiscoveryState.snapshot, _networkPeers) { snap, peers ->
            PeersDiscoveryUi.reduce(
                snap.copy(connectedPeerCount = peers.size),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PeersDiscoveryUi.reduce(MeshDiscoverySnapshot(
                bleBootstrapEnabled = false,
                blePermissionsGranted = false,
                scanActive = false,
                scanEpochStartedAtMs = 0L,
                lastScanRestartAtMs = 0L,
                lastNearbySeenAtMs = null,
                nearbyDeviceCount = 0,
                connectedPeerCount = 0,
                connectingFromInvite = false,
                nowMs = System.currentTimeMillis(),
            )),
        )

    val metrics: StateFlow<NodeMetrics> =
        flow {
            emit(mycena.metrics())
            while (true) {
                delay(3_000)
                emit(mycena.metrics())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mycena.metrics())

    init {
        viewModelScope.launch {
            refresh()
            appContext.meshEvents().collect {
                when (it) {
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat, is MeshEvent.Connectivity -> refresh()
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val peers = mycena.connectedPeers()
            _networkPeers.value = peers
            MeshDiscoveryState.setConnectedPeerCount(peers.size)
            if (peers.isNotEmpty()) {
                MeshDiscoveryState.setConnectingFromInvite(false)
            }
        }
    }

    fun onInviteConnectStarted() {
        MeshDiscoveryState.setConnectingFromInvite(true)
        viewModelScope.launch {
            delay(45_000)
            MeshDiscoveryState.setConnectingFromInvite(false)
        }
    }

    fun onInviteConnectFinished() {
        MeshDiscoveryState.setConnectingFromInvite(false)
    }

    fun endorsePeer(peerId: String, strength: UByte) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.endorsePeer(peerId, strength)
        }
    }

    fun peerReputation(peerId: String): PeerReputationData? = mycena.peerReputation(peerId)
}

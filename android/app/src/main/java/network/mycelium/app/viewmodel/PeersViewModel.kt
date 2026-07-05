// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

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
import network.mycelium.app.MeshEvent
import network.mycelium.app.discovery.MeshDiscoverySnapshot
import network.mycelium.app.discovery.MeshDiscoveryState
import network.mycelium.app.discovery.PeersDiscoveryPresentation
import network.mycelium.app.discovery.PeersDiscoveryUi
import network.mycelium.app.meshEvents
import network.mycelium.bindings.NodeMetrics
import network.mycelium.bindings.PeerReputationData
import network.mycelium.bindings.mycelium

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
            emit(mycelium.metrics())
            while (true) {
                delay(3_000)
                emit(mycelium.metrics())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mycelium.metrics())

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
            val peers = mycelium.connectedPeers()
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
            mycelium.endorsePeer(peerId, strength)
        }
    }

    fun peerReputation(peerId: String): PeerReputationData? = mycelium.peerReputation(peerId)
}

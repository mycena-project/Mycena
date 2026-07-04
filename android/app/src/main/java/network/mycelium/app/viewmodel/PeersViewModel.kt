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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.NodeMetrics
import network.mycelium.bindings.PeerReputationData
import network.mycelium.bindings.mycelium

class PeersViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    private val _networkPeers = MutableStateFlow<List<String>>(emptyList())
    val networkPeers: StateFlow<List<String>> = _networkPeers.asStateFlow()

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
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat -> refresh()
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _networkPeers.value = mycelium.connectedPeers()
        }
    }

    fun endorsePeer(peerId: String, strength: UByte) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.endorsePeer(peerId, strength)
        }
    }

    fun peerReputation(peerId: String): PeerReputationData? = mycelium.peerReputation(peerId)
}

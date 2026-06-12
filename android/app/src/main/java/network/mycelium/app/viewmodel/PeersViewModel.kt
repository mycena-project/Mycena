package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import network.mycelium.app.MeshEvent
import network.mycelium.app.bootstrap.RelayBootstrap
import network.mycelium.app.meshEvents
import network.mycelium.bindings.NodeMetrics
import network.mycelium.bindings.mycelium

class PeersViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    val networkPeers: StateFlow<List<String>> =
        flow {
            emit(refresh())
            appContext.meshEvents().collect {
                when (it) {
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat -> emit(refresh())
                    else -> Unit
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val metrics: StateFlow<NodeMetrics> =
        flow {
            emit(mycelium.metrics())
            while (true) {
                delay(3_000)
                emit(mycelium.metrics())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), mycelium.metrics())

    fun refresh(): List<String> =
        mycelium.knownPeers().filter { it != RelayBootstrap.RELAY_PEER_ID }
}

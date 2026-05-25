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
import network.mycelium.app.meshEvents
import network.mycelium.bindings.ConnectivityMode
import network.mycelium.bindings.mycelium

class StatusViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    val peerCount: StateFlow<Int> =
        flow {
            emit(mycelium.knownPeers().size)
            appContext.meshEvents().collect { event ->
                when (event) {
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat -> {
                        emit(mycelium.knownPeers().size)
                    }
                    else -> Unit
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val connectivityMode: StateFlow<ConnectivityMode> =
        flow {
            while (true) {
                emit(mycelium.currentConnectivityMode())
                delay(5_000)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConnectivityMode.INTERNET)
}

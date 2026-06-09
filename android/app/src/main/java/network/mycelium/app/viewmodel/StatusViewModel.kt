package network.mycelium.app.viewmodel

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
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.ConnectivityMode
import network.mycelium.app.bootstrap.RelayBootstrap
import network.mycelium.bindings.mycelium

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

    private var hideSearchingJob: Job? = null

    init {
        viewModelScope.launch {
            refreshPeerCount()
            _connectivityMode.value = mycelium.currentConnectivityMode()
            pulseSearchingBanner(initial = true)

            appContext.meshEvents().collect { event ->
                when (event) {
                    MeshEvent.PeerUp, MeshEvent.PeerDown, MeshEvent.Chat -> {
                        refreshPeerCount()
                        if (_peerCount.value > 0) {
                            stopSearchingBanner()
                        }
                    }
                    is MeshEvent.Connectivity -> {
                        _connectivityMode.value = event.mode
                        pulseSearchingBanner()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun refreshPeerCount() {
        _peerCount.value =
            mycelium.knownPeers().count { it != RelayBootstrap.RELAY_PEER_ID }
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

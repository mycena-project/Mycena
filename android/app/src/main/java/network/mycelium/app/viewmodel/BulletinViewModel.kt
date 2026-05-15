package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.BulletinPost
import network.mycelium.bindings.mycelium

class BulletinViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    val posts: StateFlow<List<BulletinPost>> =
        flow {
            emit(mycelium.bulletinsForScope("mycelium/global"))
            appContext.meshEvents().collect {
                if (it == MeshEvent.Bulletin) {
                    emit(mycelium.bulletinsForScope("mycelium/global"))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

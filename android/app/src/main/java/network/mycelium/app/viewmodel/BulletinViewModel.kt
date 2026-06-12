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

    val isEnabled: StateFlow<Boolean> =
        flow {
            emit(mycelium.isBulletinEnabled())
            appContext.meshEvents().collect {
                if (it == MeshEvent.Bulletin) {
                    emit(mycelium.isBulletinEnabled())
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val posts: StateFlow<List<BulletinPost>> =
        flow {
            emit(loadPosts())
            appContext.meshEvents().collect {
                if (it == MeshEvent.Bulletin) {
                    emit(loadPosts())
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun loadPosts(): List<BulletinPost> {
        if (!mycelium.isBulletinEnabled()) return emptyList()
        return mycelium.getBulletinSubscriptions()
            .flatMap { scope -> mycelium.bulletinsForScope(scope) }
            .distinctBy { it.id }
            .sortedByDescending { it.timestampMs }
    }
}

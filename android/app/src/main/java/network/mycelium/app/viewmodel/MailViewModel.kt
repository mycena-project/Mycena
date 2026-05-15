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
import network.mycelium.bindings.MailMessage
import network.mycelium.bindings.mycelium

class MailViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    val inbox: StateFlow<List<MailMessage>> =
        flow {
            emit(mycelium.mailInbox(100u))
            appContext.meshEvents().collect {
                if (it == MeshEvent.Mail) {
                    emit(mycelium.mailInbox(100u))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

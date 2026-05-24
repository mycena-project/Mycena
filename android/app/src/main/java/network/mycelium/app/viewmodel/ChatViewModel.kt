package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.mycelium
import uniffi.mycelium.MyceliumException

sealed class ChatUiEvent {
    data class ShowError(val message: String) : ChatUiEvent()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    val localPeerId: String get() = mycelium.localPeerId()

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<ChatUiEvent> = _uiEvent.asSharedFlow()

    val peers: StateFlow<List<String>> =
        flow {
            emit(mycelium.knownPeers())
            appContext.meshEvents().collect {
                when (it) {
                    MeshEvent.Chat, MeshEvent.PeerUp, MeshEvent.PeerDown -> emit(mycelium.knownPeers())
                    else -> Unit
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chatHistory(peerId: String): Flow<List<ChatMessage>> =
        flow {
            emit(mycelium.chatHistory(peerId, 100u))
            appContext.meshEvents().collect {
                if (it == MeshEvent.Chat) {
                    emit(mycelium.chatHistory(peerId, 100u))
                }
            }
        }

    fun peerHasEncKey(peerId: String): StateFlow<Boolean> =
        flow {
            emit(mycelium.hasEncKeyFor(peerId))
            appContext.meshEvents().collect {
                when (it) {
                    MeshEvent.Chat, MeshEvent.PeerUp -> emit(mycelium.hasEncKeyFor(peerId))
                    else -> Unit
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun sendChat(toPeer: String, body: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mycelium.sendChatEncrypted(toPeer, body)
                onSuccess()
            } catch (e: MyceliumException.ChatException) {
                val message =
                    if (e.detail.contains("enc_key_not_yet_exchanged")) {
                        appContext.getString(network.mycelium.app.R.string.chat_key_not_yet_exchanged)
                    } else {
                        e.detail
                    }
                _uiEvent.emit(ChatUiEvent.ShowError(message))
            } catch (e: Exception) {
                _uiEvent.emit(ChatUiEvent.ShowError(e.message ?: "Unknown error"))
            }
        }
    }
}

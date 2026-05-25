package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.GroupInfo
import network.mycelium.bindings.mycelium

class GroupViewModel(application: Application) : AndroidViewModel(application) {
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    val groups: StateFlow<List<GroupInfo>> =
        flow {
            while (true) {
                emit(mycelium.listGroups())
                delay(2_000)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun groupChatHistory(groupId: String): Flow<List<ChatMessage>> =
        flow {
            while (true) {
                emit(mycelium.groupChatHistory(groupId, 100u))
                delay(1_000)
            }
        }

    fun createGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.createGroup(name.trim())
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.deleteGroup(groupId)
        }
    }

    suspend fun importInviteAsync(json: String): Boolean =
        withContext(Dispatchers.IO) {
            mycelium.importGroupInvite(json) != null
        }

    fun exportInvite(groupId: String): String = mycelium.exportGroupInvite(groupId)

    fun sendGroupMessage(groupId: String, body: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.sendGroupMessage(groupId, body)
        }
    }

    fun sendLocation(
        context: android.content.Context,
        onLoading: (Boolean) -> Unit = {},
        onMessage: (String) -> Unit,
    ) {
        viewModelScope.launch {
            network.mycelium.app.util.shareCurrentLocation(
                context = context,
                onLoading = onLoading,
                onDeliver = onMessage,
                onError = { msg ->
                    launch { _errors.emit(msg) }
                },
            )
        }
    }
}

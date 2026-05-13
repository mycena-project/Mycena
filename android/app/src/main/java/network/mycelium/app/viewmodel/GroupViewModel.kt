package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.GroupInfo
import network.mycelium.bindings.mycelium

class GroupViewModel(application: Application) : AndroidViewModel(application) {
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
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.app.MeshService
import network.mycelium.app.MeshUiState
import network.mycelium.app.R
import network.mycelium.app.util.shareCurrentLocation
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.PeerReputationData
import network.mycelium.bindings.mycelium
import uniffi.mycelium.ContactStatus
import uniffi.mycelium.MyceliumException

data class ChatListEntry(
    val peerId: String,
    val displayName: String,
    val online: Boolean,
    val lastMessagePreview: String = "",
    val lastMessageTimeMs: Long = 0L,
)

sealed class ChatUiEvent {
    data class ShowError(val message: String) : ChatUiEvent()
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val chatHistoryFlows = mutableMapOf<String, StateFlow<List<ChatMessage>>>()

    val localPeerId: String
        get() = runCatching { mycelium.localPeerId() }.getOrDefault("")

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<ChatUiEvent> = _uiEvent.asSharedFlow()

    private suspend fun awaitNodeReady() {
        while (!MeshService.isNodeReady()) {
            delay(50)
        }
    }

    private fun refreshContacts(): Pair<List<ChatListEntry>, List<ChatListEntry>> =
        runCatching {
            val online = mycelium.connectedPeers().toSet()
            val accepted =
                mycelium.listAcceptedContacts().map { c ->
                    val last = mycelium.chatHistory(c.peerId, 1u).lastOrNull()
                    ChatListEntry(
                        peerId = c.peerId,
                        displayName = c.displayName.ifBlank { c.peerId.take(16) },
                        online = c.peerId in online,
                        lastMessagePreview = last?.body?.take(80).orEmpty(),
                        lastMessageTimeMs = last?.timestampMs?.toLong() ?: 0L,
                    )
                }
            val pending =
                mycelium.listPendingContacts().map { c ->
                    ChatListEntry(
                        peerId = c.peerId,
                        displayName = c.displayName.ifBlank { c.peerId.take(16) },
                        online = c.peerId in online,
                    )
                }
            accepted to pending
        }.getOrElse { emptyList<ChatListEntry>() to emptyList() }

    val acceptedContacts: StateFlow<List<ChatListEntry>> =
        flow {
            awaitNodeReady()
            val (accepted, _) = refreshContacts()
            emit(accepted)
            MeshUiState.chatRevision.collect {
                val (a, _) = refreshContacts()
                emit(a)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingContacts: StateFlow<List<ChatListEntry>> =
        flow {
            awaitNodeReady()
            val (_, pending) = refreshContacts()
            emit(pending)
            MeshUiState.chatRevision.collect {
                val (_, p) = refreshContacts()
                emit(p)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** @deprecated use [acceptedContacts] — kept for any legacy callers */
    val peers: StateFlow<List<String>> =
        flow {
            awaitNodeReady()
            emit(mycelium.listAcceptedContacts().map { it.peerId })
            MeshUiState.chatRevision.collect {
                emit(mycelium.listAcceptedContacts().map { it.peerId })
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun acceptContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.acceptContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun rejectContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.rejectContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun blockContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.blockContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun peerReputation(peerId: String): StateFlow<PeerReputationData?> =
        flow {
            emit(mycelium.peerReputation(peerId))
            MeshUiState.chatRevision.collect {
                emit(mycelium.peerReputation(peerId))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun chatHistory(peerId: String): StateFlow<List<ChatMessage>> =
        chatHistoryFlows.getOrPut(peerId) {
            flow {
                awaitNodeReady()
                emit(mycelium.chatHistory(peerId, 100u))
                MeshUiState.chatRevision.collect {
                    emit(mycelium.chatHistory(peerId, 100u))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun peerHasEncKey(peerId: String): StateFlow<Boolean> =
        flow {
            emit(mycelium.hasEncKeyFor(peerId))
            MeshUiState.chatRevision.collect {
                emit(mycelium.hasEncKeyFor(peerId))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun sendChat(toPeer: String, body: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mycelium.sendChatEncrypted(toPeer, body)
                MeshUiState.bumpChatRevision()
                onSuccess()
            } catch (e: MyceliumException.ChatException) {
                val message =
                    when {
                        e.detail.contains("enc_key_not_yet_exchanged") ->
                            appContext.getString(R.string.chat_key_not_yet_exchanged)
                        e.detail.contains("peer_not_connected") ->
                            appContext.getString(R.string.chat_peer_not_connected)
                        e.detail.contains("channel closed", ignoreCase = true) ||
                            e.detail.contains("not connected to peer", ignoreCase = true) ->
                            appContext.getString(R.string.chat_peer_not_connected)
                        else -> e.detail
                    }
                _uiEvent.emit(ChatUiEvent.ShowError(message))
            } catch (e: Exception) {
                _uiEvent.emit(ChatUiEvent.ShowError(e.message ?: "Unknown error"))
            }
        }
    }

    fun sendLocation(
        context: Context,
        onLoading: (Boolean) -> Unit = {},
        onMessage: (String) -> Unit,
    ) {
        viewModelScope.launch {
            shareCurrentLocation(
                context = context,
                onLoading = onLoading,
                onDeliver = onMessage,
                onError = { msg ->
                    launch { _uiEvent.emit(ChatUiEvent.ShowError(msg)) }
                },
            )
        }
    }
}

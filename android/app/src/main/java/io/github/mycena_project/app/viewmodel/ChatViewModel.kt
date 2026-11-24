// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.viewmodel

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
import io.github.mycena_project.app.MeshService
import io.github.mycena_project.app.MeshUiState
import io.github.mycena_project.app.R
import io.github.mycena_project.app.util.shareCurrentLocation
import io.github.mycena_project.app.util.userVisibleContactName
import io.github.mycena_project.bindings.ChatMessage
import io.github.mycena_project.bindings.ConnectivityMode
import io.github.mycena_project.bindings.CustodyPendingStatus
import io.github.mycena_project.bindings.PeerReputationData
import io.github.mycena_project.bindings.mycena
import uniffi.mycena.ContactStatus
import uniffi.mycena.MycenaException

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
        get() = runCatching { mycena.localPeerId() }.getOrDefault("")

    private val _uiEvent = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<ChatUiEvent> = _uiEvent.asSharedFlow()

    private suspend fun awaitNodeReady() {
        while (!MeshService.isNodeReady()) {
            delay(50)
        }
    }

    private fun visibleName(displayName: String, peerId: String): String =
        userVisibleContactName(
            displayName,
            peerId,
            appContext.getString(R.string.contact_unnamed),
        )

    private fun refreshContacts(): Pair<List<ChatListEntry>, List<ChatListEntry>> =
        runCatching {
            val online = mycena.connectedPeers().toSet()
            val accepted =
                mycena.listAcceptedContacts().map { c ->
                    val last = mycena.chatHistory(c.peerId, 1u).lastOrNull()
                    ChatListEntry(
                        peerId = c.peerId,
                        displayName = visibleName(c.displayName, c.peerId),
                        online = c.peerId in online,
                        lastMessagePreview = last?.body?.take(80).orEmpty(),
                        lastMessageTimeMs = last?.timestampMs?.toLong() ?: 0L,
                    )
                }
            val pending =
                mycena.listPendingContacts().map { c ->
                    ChatListEntry(
                        peerId = c.peerId,
                        displayName = visibleName(c.displayName, c.peerId),
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
            emit(mycena.listAcceptedContacts().map { it.peerId })
            MeshUiState.chatRevision.collect {
                emit(mycena.listAcceptedContacts().map { it.peerId })
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun acceptContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.acceptContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun rejectContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.rejectContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun blockContact(peerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycena.blockContact(peerId)
            MeshUiState.bumpChatRevision()
        }
    }

    fun peerReputation(peerId: String): StateFlow<PeerReputationData?> =
        flow {
            emit(mycena.peerReputation(peerId))
            MeshUiState.chatRevision.collect {
                emit(mycena.peerReputation(peerId))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun chatHistory(peerId: String): StateFlow<List<ChatMessage>> =
        chatHistoryFlows.getOrPut(peerId) {
            flow {
                awaitNodeReady()
                emit(mycena.chatHistory(peerId, 100u))
                MeshUiState.chatRevision.collect {
                    emit(mycena.chatHistory(peerId, 100u))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        }

    fun peerHasEncKey(peerId: String): StateFlow<Boolean> =
        flow {
            emit(mycena.hasEncKeyFor(peerId))
            MeshUiState.chatRevision.collect {
                emit(mycena.hasEncKeyFor(peerId))
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun pendingCustodyForPeer(peerId: String): StateFlow<List<CustodyPendingStatus>> =
        flow {
            awaitNodeReady()
            emit(pendingCustodyEntries(peerId))
            MeshUiState.chatRevision.collect {
                emit(pendingCustodyEntries(peerId))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val meshOnlyConnectivity: StateFlow<Boolean> =
        flow {
            awaitNodeReady()
            emit(isMeshOnly())
            MeshUiState.chatRevision.collect {
                emit(isMeshOnly())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private fun isMeshOnly(): Boolean =
        runCatching { mycena.currentConnectivityMode() == ConnectivityMode.MESH_ONLY }
            .getOrDefault(false)

    private fun pendingCustodyEntries(peerId: String): List<CustodyPendingStatus> =
        runCatching {
            mycena.listPendingCustody().filter { it.destPeer == peerId }
        }.getOrDefault(emptyList())

    fun sendChat(toPeer: String, body: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mycena.sendChatEncrypted(toPeer, body)
                MeshUiState.bumpChatRevision()
                onSuccess()
            } catch (e: MycenaException.ChatException) {
                val message =
                    when {
                        e.detail.contains("enc_key_not_yet_exchanged") ->
                            appContext.getString(R.string.chat_key_not_yet_exchanged)
                        e.detail.contains("peer_not_connected") ->
                            if (isMeshOnly()) {
                                null
                            } else {
                                appContext.getString(R.string.chat_peer_not_connected)
                            }
                        e.detail.contains("channel closed", ignoreCase = true) ||
                            e.detail.contains("not connected to peer", ignoreCase = true) ->
                            if (isMeshOnly()) {
                                null
                            } else {
                                appContext.getString(R.string.chat_peer_not_connected)
                            }
                        else -> appContext.getString(R.string.chat_send_failed)
                    }
                if (message != null) {
                    _uiEvent.emit(ChatUiEvent.ShowError(message))
                }
            } catch (_: Exception) {
                _uiEvent.emit(ChatUiEvent.ShowError(appContext.getString(R.string.chat_send_failed)))
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

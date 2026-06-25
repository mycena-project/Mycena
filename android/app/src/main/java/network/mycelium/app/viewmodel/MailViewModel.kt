// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.app.MeshEvent
import network.mycelium.app.meshEvents
import network.mycelium.bindings.MailMessage
import network.mycelium.bindings.mycelium

data class MailContact(
    val peerId: String,
    val displayName: String,
)

class MailViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()

    private val _sent = MutableStateFlow<List<MailMessage>>(emptyList())
    val sent: StateFlow<List<MailMessage>> = _sent.asStateFlow()

    val inbox: StateFlow<List<MailMessage>> =
        flow {
            refreshMail()
            emit(mycelium.mailInbox(100u))
            appContext.meshEvents().collect {
                if (it == MeshEvent.Mail) {
                    refreshMail()
                    emit(mycelium.mailInbox(100u))
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts: StateFlow<List<MailContact>> =
        flow {
            emit(loadContacts())
            appContext.meshEvents().collect {
                when (it) {
                    MeshEvent.Contacts, MeshEvent.PeerUp, MeshEvent.PeerDown ->
                        emit(loadContacts())
                    else -> Unit
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun loadContacts(): List<MailContact> =
        runCatching {
            mycelium.listAcceptedContacts().map { c ->
                MailContact(
                    peerId = c.peerId,
                    displayName = c.displayName.ifBlank { c.peerId.take(16) },
                )
            }
        }.getOrElse { emptyList() }

    private fun refreshMail() {
        _sent.value = runCatching { mycelium.mailSent(100u) }.getOrElse { emptyList() }
    }

    fun sendMail(toPeer: String, subject: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.sendMail(toPeer.trim(), subject.trim(), body.trim())
            refreshMail()
            launch(Dispatchers.Main) { onDone() }
        }
    }

    fun markRead(mailId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.markMailRead(mailId)
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import network.mycelium.app.MeshUiState
import network.mycelium.bindings.MailMessage
import network.mycelium.bindings.mycelium

data class MailContact(
    val peerId: String,
    val displayName: String,
)

class MailViewModel(app: Application) : AndroidViewModel(app) {

    val inbox: StateFlow<List<MailMessage>> = MeshUiState.inbox
    val sent: StateFlow<List<MailMessage>> = MeshUiState.sent

    val contacts: StateFlow<List<MailContact>> =
        flow {
            emit(loadContacts())
            MeshUiState.chatRevision.collect {
                emit(loadContacts())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private fun loadContacts(): List<MailContact> =
        runCatching {
            mycelium.listAcceptedContacts().map { c ->
                MailContact(
                    peerId = c.peerId,
                    displayName = c.displayName.ifBlank { c.peerId.take(16) },
                )
            }
        }.getOrElse { emptyList() }

    fun sendMail(toPeer: String, subject: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            mycelium.sendMail(toPeer.trim(), subject.trim(), body.trim())
            MeshUiState.refreshMail()
            launch(Dispatchers.Main) { onDone() }
        }
    }

    fun markRead(mailId: String) {
        MeshUiState.markMailRead(mailId)
    }
}

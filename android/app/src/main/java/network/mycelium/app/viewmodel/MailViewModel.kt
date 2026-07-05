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

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import network.mycelium.app.R
import network.mycelium.app.util.userVisibleContactName
import uniffi.mycelium.MyceliumException

data class MailContact(
    val peerId: String,
    val displayName: String,
)

class MailViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext = getApplication<Application>()
    private val _sendError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sendError: SharedFlow<String> = _sendError.asSharedFlow()

    val inbox: StateFlow<List<MailMessage>> = MeshUiState.inbox
    val sent: StateFlow<List<MailMessage>> = MeshUiState.sent

    val contacts: StateFlow<List<MailContact>> =
        flow {
            emit(loadContacts())
            MeshUiState.chatRevision.collect {
                emit(loadContacts())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun loadContacts(): List<MailContact> =
        runCatching {
            mycelium.listAcceptedContacts().map { c ->
                MailContact(
                    peerId = c.peerId,
                    displayName =
                        userVisibleContactName(
                            c.displayName,
                            c.peerId,
                            appContext.getString(R.string.contact_unnamed),
                        ),
                )
            }
        }.getOrElse { emptyList() }

    fun sendMail(toPeer: String, subject: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mycelium.sendMail(toPeer.trim(), subject.trim(), body.trim())
                MeshUiState.refreshMail()
                launch(Dispatchers.Main) { onDone() }
            } catch (e: MyceliumException.ChatException) {
                _sendError.emit(formatMailSendError(e.detail))
            } catch (e: Exception) {
                _sendError.emit(formatMailSendError(e.message))
            }
        }
    }

    private fun formatMailSendError(detail: String?): String {
        val d = detail?.trim().orEmpty()
        return when {
            d.contains("channel closed", ignoreCase = true) ||
                d.contains("mesh node stopped", ignoreCase = true) ->
                appContext.getString(R.string.mail_node_stopped)
            d.contains("unknown recipient", ignoreCase = true) ->
                appContext.getString(R.string.mail_unknown_recipient)
            d.contains("12D3Koo", ignoreCase = true) ||
                d.contains("peer", ignoreCase = true) && d.contains("id", ignoreCase = true) ->
                appContext.getString(R.string.mail_send_failed)
            d.isNotBlank() && d.length <= 120 -> d
            else -> appContext.getString(R.string.mail_send_failed)
        }
    }

    fun markRead(mailId: String) {
        MeshUiState.markMailRead(mailId)
    }
}

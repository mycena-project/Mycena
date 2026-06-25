// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import network.mycelium.bindings.MailMessage
import network.mycelium.bindings.mycelium

/**
 * App-scoped mesh UI cache: one broadcast listener while [MeshService] runs.
 * ViewModels observe these flows so mail/chat stay fresh across tab switches
 * without periodic polling.
 */
object MeshUiState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    private val _inbox = MutableStateFlow<List<MailMessage>>(emptyList())
    val inbox: StateFlow<List<MailMessage>> = _inbox.asStateFlow()

    private val _sent = MutableStateFlow<List<MailMessage>>(emptyList())
    val sent: StateFlow<List<MailMessage>> = _sent.asStateFlow()

    /** Incremented on chat/contact mesh events for list screens to reload. */
    private val _chatRevision = MutableStateFlow(0L)
    val chatRevision: StateFlow<Long> = _chatRevision.asStateFlow()

    fun start(context: Context) {
        if (started) return
        started = true
        refreshMail()
        scope.launch {
            context.applicationContext.meshEvents().collect { event ->
                when (event) {
                    MeshEvent.Mail -> refreshMail()
                    MeshEvent.Chat,
                    MeshEvent.Contacts,
                    MeshEvent.PeerUp,
                    MeshEvent.PeerDown,
                    -> _chatRevision.update { it + 1 }
                    else -> Unit
                }
            }
        }
    }

    fun refreshMail() {
        scope.launch(Dispatchers.IO) {
            _inbox.value = runCatching { mycelium.mailInbox(100u) }.getOrElse { emptyList() }
            _sent.value = runCatching { mycelium.mailSent(100u) }.getOrElse { emptyList() }
        }
    }

    fun markMailRead(mailId: String) {
        scope.launch(Dispatchers.IO) {
            mycelium.markMailRead(mailId)
            _inbox.value = runCatching { mycelium.mailInbox(100u) }.getOrElse { emptyList() }
        }
    }
}

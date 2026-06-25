// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import network.mycelium.app.R
import network.mycelium.app.viewmodel.ChatUiEvent
import network.mycelium.app.viewmodel.ChatViewModel

@Composable
fun EncryptionBadge(isEncrypted: Boolean) {
    Surface(
        color =
            if (isEncrypted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (isEncrypted) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint =
                    if (isEncrypted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text =
                    if (isEncrypted) {
                        stringResource(R.string.chat_encrypted)
                    } else {
                        stringResource(R.string.chat_not_encrypted)
                    },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun ChatConversationScreen(peerId: String, vm: ChatViewModel = viewModel()) {
    val messages by vm.chatHistory(peerId).collectAsState(emptyList())
    val hasEncKey by vm.peerHasEncKey(peerId).collectAsState(false)
    val peerReputation by vm.peerReputation(peerId).collectAsState(null)
    val localPeerId = vm.localPeerId
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.uiEvent.collect { event ->
            when (event) {
                is ChatUiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                EncryptionBadge(isEncrypted = hasEncKey)
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.chat_reputation_strikes,
                            peerReputation?.strikes?.toInt() ?: 0,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { vm.blockContact(peerId) }) {
                    Text(stringResource(R.string.chat_block_contact))
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                reverseLayout = false,
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isOwn = msg.fromPeer == localPeerId
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                        horizontalArrangement =
                            if (isOwn) Arrangement.End else Arrangement.Start,
                    ) {
                        ChatMessageBubble(message = msg, isOwn = isOwn)
                    }
                }
            }
            ChatInputRow(
                onSend = { body -> vm.sendChat(peerId, body) },
                shareLocation = { ctx, loading, msg ->
                    vm.sendLocation(ctx, onLoading = loading, onMessage = msg)
                },
            )
        }
    }
}

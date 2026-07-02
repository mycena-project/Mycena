// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import network.mycelium.app.MeshUiState
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
            Icon(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    peerId: String,
    navController: NavController,
    vm: ChatViewModel = viewModel(),
) {
    val messages by vm.chatHistory(peerId).collectAsState()
    val hasEncKey by vm.peerHasEncKey(peerId).collectAsState()
    val accepted by vm.acceptedContacts.collectAsState()
    val pending by vm.pendingContacts.collectAsState()
    val localPeerId = vm.localPeerId
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }

    val contact = accepted.find { it.peerId == peerId } ?: pending.find { it.peerId == peerId }
    val title = contact?.displayName ?: peerId.take(16)
    val subtitle =
        contact?.let {
            if (it.online) stringResource(R.string.chat_online) else stringResource(R.string.chat_offline)
        }

    LaunchedEffect(peerId) {
        // ContactHello may update the remote display name a few seconds after QR scan.
        repeat(8) {
            delay(2_000)
            MeshUiState.bumpChatRevision()
        }
    }

    BackHandler { navController.popBackStack() }

    LaunchedEffect(messages.size, messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(vm) {
        vm.uiEvent.collect { event ->
            when (event) {
                is ChatUiEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_block_contact)) },
                            onClick = {
                                menuOpen = false
                                vm.blockContact(peerId)
                                navController.popBackStack()
                            },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                EncryptionBadge(isEncrypted = hasEncKey)
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                state = listState,
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.chat_no_messages_yet),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(messages, key = { it.id }) { msg ->
                    val isOwn = msg.fromPeer == localPeerId
                    ChatMessageBubble(message = msg, isOwn = isOwn)
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

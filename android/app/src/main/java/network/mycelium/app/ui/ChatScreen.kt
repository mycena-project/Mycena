package network.mycelium.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.viewmodel.ChatListEntry
import network.mycelium.app.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavController, vm: ChatViewModel = viewModel()) {
    val accepted by vm.acceptedContacts.collectAsState()
    val pending by vm.pendingContacts.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                actions = {
                    IconButton(onClick = { navController.navigate("groups") }) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = stringResource(R.string.nav_groups),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pending.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chat_pending_requests),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(pending, key = { "p-${it.peerId}" }) { entry ->
                    PendingContactRow(entry, vm, navController)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.chat_contacts),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (accepted.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chat_no_conversations),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(accepted, key = { "a-${it.peerId}" }) { entry ->
                ContactRow(entry, navController)
            }
        }
    }
}

@Composable
private fun ContactRow(entry: ChatListEntry, navController: NavController) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("chat/${entry.peerId}") }
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text = entry.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            text =
                if (entry.online) {
                    stringResource(
                        R.string.chat_peer_subtitle_online,
                        entry.peerId.take(20) + "…",
                    )
                } else {
                    stringResource(R.string.chat_peer_subtitle_offline, entry.peerId.take(20) + "…")
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PendingContactRow(
    entry: ChatListEntry,
    vm: ChatViewModel,
    navController: NavController,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(text = entry.displayName, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = entry.peerId.take(24) + "…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = {
                vm.acceptContact(entry.peerId)
                navController.navigate("chat/${entry.peerId}")
            }) {
                Text(stringResource(R.string.chat_accept_contact))
            }
            TextButton(onClick = { vm.rejectContact(entry.peerId) }) {
                Text(stringResource(R.string.chat_reject_contact))
            }
        }
    }
}

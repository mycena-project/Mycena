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
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import network.mycelium.app.R
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

@Composable
fun ChatConversationScreen(peerId: String, vm: ChatViewModel = viewModel()) {
    val messages by vm.chatHistory(peerId).collectAsState(emptyList())
    val hasEncKey by vm.peerHasEncKey(peerId).collectAsState(false)
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            EncryptionBadge(isEncrypted = hasEncKey)
        }
        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            items(messages) { msg ->
                Text("${msg.fromDisplayName}: ${msg.body}", modifier = Modifier.padding(8.dp))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
            )
            IconButton(
                onClick = {
                    vm.sendChat(peerId, input)
                    input = ""
                },
            ) {
                Icon(Icons.Default.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

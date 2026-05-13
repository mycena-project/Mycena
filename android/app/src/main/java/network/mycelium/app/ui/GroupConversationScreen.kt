package network.mycelium.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.viewmodel.GroupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupConversationScreen(
    groupId: String,
    navController: NavController,
    vm: GroupViewModel = viewModel(),
) {
    val groups by vm.groups.collectAsState()
    val title = groups.find { it.id == groupId }?.name ?: groupId.take(12)
    val messages by vm.groupChatHistory(groupId).collectAsState(emptyList())
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
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
                EncryptionBadge(isEncrypted = true)
            }
            LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
                items(messages, key = { it.id }) { msg ->
                    Text(
                        "${msg.fromDisplayName}: ${msg.body}",
                        modifier = Modifier.padding(8.dp),
                    )
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
                        if (input.isNotBlank()) {
                            vm.sendGroupMessage(groupId, input)
                            input = ""
                        }
                    },
                ) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(R.string.chat_send))
                }
            }
        }
    }
}

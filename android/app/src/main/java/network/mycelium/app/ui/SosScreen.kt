// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.viewmodel.SosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosScreen(navController: NavController, vm: SosViewModel = viewModel()) {
    val listenEnabled by vm.listenEnabled.collectAsState()
    val posts by vm.posts.collectAsState()
    var geohash by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.sos_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(R.string.sos_disclaimer),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                Text(stringResource(R.string.sos_listen_section), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.sos_listen_label))
                    Switch(checked = listenEnabled, onCheckedChange = { vm.setListenEnabled(it) })
                }
                Text(
                    stringResource(R.string.sos_listen_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            item {
                Text(stringResource(R.string.sos_post_section), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = geohash,
                    onValueChange = { geohash = it.take(12) },
                    label = { Text(stringResource(R.string.sos_geohash_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(280) },
                    label = { Text(stringResource(R.string.sos_message_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Button(
                    onClick = {
                        if (geohash.isNotBlank() && message.isNotBlank()) {
                            vm.postSos(geohash, message)
                            message = ""
                        }
                    },
                    enabled = geohash.isNotBlank() && message.isNotBlank(),
                ) {
                    Text(stringResource(R.string.sos_post_button))
                }
            }
            item {
                Text(
                    stringResource(R.string.sos_nearby_section, posts.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (!listenEnabled) {
                    Text(
                        stringResource(R.string.sos_listen_off_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Start,
                    )
                } else if (posts.isEmpty()) {
                    Text(
                        stringResource(R.string.sos_empty),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(posts) { post ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(post.geohash, style = MaterialTheme.typography.labelMedium)
                    Text(post.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sos_meshaid_section), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.sos_meshaid_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { navController.navigate("miniapp/run/network.mycelium.meshaid") }) {
                    Text(stringResource(R.string.sos_meshaid_button))
                }
            }
        }
    }
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import network.mycelium.app.util.ChatTimeFormatter
import network.mycelium.app.viewmodel.BulletinViewModel
import network.mycelium.bindings.BulletinPost

private val suggestedScopes = listOf("mycelium/general", "local", "community")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulletinScreen(
    navController: NavController,
    vm: BulletinViewModel = viewModel(),
) {
    @Suppress("UNUSED_PARAMETER")
    val _nav = navController
    val subscriptions by vm.subscriptions.collectAsState()
    val activeScope by vm.activeScope.collectAsState()
    val posts by vm.posts.collectAsState()
    var scopeInput by remember(activeScope) { mutableStateOf(activeScope) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.bulletin_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.bulletin_compose_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = scopeInput,
                            onValueChange = { scopeInput = it.lowercase().replace(" ", "/") },
                            label = { Text(stringResource(R.string.bulletin_scope_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestedScopes.forEach { suggestion ->
                                FilterChip(
                                    selected = activeScope == suggestion,
                                    onClick = {
                                        scopeInput = suggestion
                                        vm.setActiveScope(suggestion)
                                    },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                        if (subscriptions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.bulletin_following_scopes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                subscriptions.forEach { scope ->
                                    FilterChip(
                                        selected = activeScope == scope,
                                        onClick = {
                                            scopeInput = scope
                                            vm.setActiveScope(scope)
                                        },
                                        label = { Text(scope) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.bulletin_title_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            label = { Text(stringResource(R.string.bulletin_body_hint)) },
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                vm.addScope(scopeInput)
                                vm.post(title, body)
                                title = ""
                                body = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = scopeInput.isNotBlank() && title.isNotBlank() && body.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.bulletin_post))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bulletin_board_heading, activeScope),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (posts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.bulletin_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    BulletinPostCard(post)
                }
            }
        }
    }
}

@Composable
private fun BulletinPostCard(post: BulletinPost) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                post.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${post.fromDisplayName} · ${post.scope}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                ChatTimeFormatter.format(post.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                post.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

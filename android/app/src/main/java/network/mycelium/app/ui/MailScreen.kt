// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.util.ChatTimeFormatter
import network.mycelium.app.viewmodel.MailViewModel
import network.mycelium.bindings.MailMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(navController: NavController, vm: MailViewModel = viewModel()) {
    val inbox by vm.inbox.collectAsState()
    val sent by vm.sent.collectAsState()
    val contacts by vm.contacts.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var showCompose by remember { mutableStateOf(false) }
    var selectedMail by remember { mutableStateOf<MailMessage?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.sendError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MyceliumBackTopBar(
                title = stringResource(R.string.mail_title),
                onBack = { navController.popBackStack() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCompose = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.mail_compose))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = stringResource(R.string.mail_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.mail_inbox)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.mail_sent)) },
                )
            }
            val messages = if (tab == 0) inbox else sent
            if (messages.isEmpty()) {
                Text(
                    text = stringResource(R.string.mail_empty_inbox),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { it.id }) { mail ->
                        MailListRow(
                            mail = mail,
                            showUnread = tab == 0 && !mail.isRead,
                            onClick = {
                                if (tab == 0 && !mail.isRead) {
                                    vm.markRead(mail.id)
                                }
                                selectedMail = mail
                            },
                        )
                    }
                }
            }
        }
    }

    selectedMail?.let { mail ->
        AlertDialog(
            onDismissRequest = { selectedMail = null },
            title = { Text(mail.subject.ifBlank { stringResource(R.string.mail_title) }) },
            text = {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text =
                            if (tab == 0) {
                                stringResource(R.string.mail_from_format, mail.fromDisplayName)
                            } else {
                                stringResource(R.string.mail_to_format, mail.toPeer)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = ChatTimeFormatter.format(mail.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(text = mail.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMail = null }) {
                    Text(stringResource(R.string.mail_close))
                }
            },
        )
    }

    if (showCompose) {
        MailComposeDialog(
            contacts = contacts,
            onDismiss = { showCompose = false },
            onSend = { toPeer, subject, body ->
                vm.sendMail(toPeer, subject, body) { showCompose = false }
            },
        )
    }
}

@Composable
private fun MailListRow(
    mail: MailMessage,
    showUnread: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = mail.subject.ifBlank { "—" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (showUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (showUnread) {
                    Text(
                        text = stringResource(R.string.mail_unread),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = mail.fromDisplayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = mail.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = ChatTimeFormatter.format(mail.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MailComposeDialog(
    contacts: List<network.mycelium.app.viewmodel.MailContact>,
    onDismiss: () -> Unit,
    onSend: (toPeer: String, subject: String, body: String) -> Unit,
) {
    var toPeer by remember { mutableStateOf("") }
    var selectedLabel by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mail_compose)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (contacts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedLabel.ifBlank { stringResource(R.string.mail_pick_contact) },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.mail_to_hint)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                            modifier =
                                Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            contacts.forEach { contact ->
                                DropdownMenuItem(
                                    text = { Text(contact.displayName) },
                                    onClick = {
                                        toPeer = contact.peerId
                                        selectedLabel = contact.displayName
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.mail_no_contacts_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = toPeer,
                        onValueChange = { toPeer = it },
                        label = { Text(stringResource(R.string.mail_to_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.mail_subject_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(stringResource(R.string.mail_body_hint)) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(toPeer, subject, body) },
                enabled = toPeer.isNotBlank() && subject.isNotBlank() && body.isNotBlank(),
            ) {
                Text(stringResource(R.string.mail_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.mail_cancel))
            }
        },
    )
}

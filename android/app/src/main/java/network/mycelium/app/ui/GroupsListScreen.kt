// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import network.mycelium.app.R
import network.mycelium.app.viewmodel.GroupViewModel
import network.mycelium.bindings.GroupInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsListScreen(navController: NavController, vm: GroupViewModel = viewModel()) {
    val groups by vm.groups.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<GroupInfo?>(null) }
    var shareTarget by remember { mutableStateOf<GroupInfo?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    val scannerLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val raw = result.contents?.trim().orEmpty()
            if (raw.isEmpty()) return@rememberLauncherForActivityResult
            if (!raw.contains("mycelium-group-invite")) {
                scanError = context.getString(R.string.group_import_failed)
                return@rememberLauncherForActivityResult
            }
            scope.launch {
                val ok = vm.importInviteAsync(raw)
                scanError =
                    if (ok) {
                        null
                    } else {
                        context.getString(R.string.group_import_failed)
                    }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_groups)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scannerLauncher.launch(
                                ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(context.getString(R.string.group_invite_scan))
                                    .setBeepEnabled(false)
                                    .setOrientationLocked(false),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.group_invite_scan),
                        )
                    }
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.group_new))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.group_key_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            scanError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(groups, key = { it.id }) { g ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate("groups/${g.id}") }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = g.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.group_members, g.memberCount.toInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { shareTarget = g },
                        ) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.group_invite_share))
                        }
                        IconButton(onClick = { deleteTarget = g }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.group_delete))
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.group_new)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            vm.createGroup(newName.trim())
                            newName = ""
                            showCreate = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    deleteTarget?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.group_delete)) },
            text = { Text(stringResource(R.string.group_delete_confirm, g.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteGroup(g.id)
                        deleteTarget = null
                    },
                ) {
                    Text(stringResource(R.string.group_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    shareTarget?.let { g ->
        val json = remember(g.id) { vm.exportInvite(g.id) }
        val qr = remember(json) { if (json.isNotBlank()) generateQrCode(json, 400) else null }
        AlertDialog(
            onDismissRequest = { shareTarget = null },
            title = { Text(stringResource(R.string.group_invite_share)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.group_key_warning),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    qr?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(200.dp),
                        )
                    }
                    Button(
                        onClick = {
                            val send =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, json)
                                }
                            context.startActivity(
                                Intent.createChooser(send, context.getString(R.string.group_invite_share)),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.group_invite_share))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { shareTarget = null }) {
                    Text(stringResource(R.string.wallet_dismiss))
                }
            },
        )
    }
}

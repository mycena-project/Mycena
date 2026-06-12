// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.mycelium.app.PortraitQrScanActivity
import network.mycelium.app.R
import network.mycelium.app.bootstrap.InviteConnect
import network.mycelium.app.bootstrap.PeerInvite
import network.mycelium.app.viewmodel.PeersViewModel
import network.mycelium.bindings.mycelium

private data class RelayStatus(val online: Boolean, val connections: Long)

@Composable
private fun RelayStatusBadge() {
    var relayStatus by remember { mutableStateOf<RelayStatus?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            relayStatus =
                try {
                    val url = java.net.URL("https://mycelium-relay.fly.dev/")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    if (conn.responseCode == 200) {
                        val json = conn.inputStream.bufferedReader().readText()
                        val connections =
                            Regex("\"connections\"\\s*:\\s*(\\d+)")
                                .find(json)
                                ?.groupValues
                                ?.get(1)
                                ?.toLongOrNull() ?: 0L
                        RelayStatus(online = true, connections = connections)
                    } else {
                        RelayStatus(online = false, connections = 0)
                    }
                } catch (_: Exception) {
                    RelayStatus(online = false, connections = 0)
                }
        }
    }

    relayStatus?.let { status ->
        Surface(
            color =
                if (status.online) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text =
                    if (status.online) {
                        stringResource(R.string.peers_relay_online, status.connections)
                    } else {
                        stringResource(R.string.peers_relay_offline)
                    },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeersScreen(navController: NavController, vm: PeersViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val networkPeers by vm.networkPeers.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val localPeerId = mycelium.localPeerId()
    val invitePayload = remember(localPeerId) { PeerInvite.encode(localPeerId) }
    val qrBitmap = remember(invitePayload) { generateQrCode(invitePayload, 640) }
    var showMyQr by remember { mutableStateOf(false) }
    var pasteInvite by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    fun onInviteApplied(successMessage: String) {
        scope.launch {
            snackbar.showSnackbar(successMessage)
            vm.refresh()
        }
    }

    val scannerLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val raw = result.contents
            if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
            InviteConnect.apply(raw)
                .onSuccess { onInviteApplied(context.getString(R.string.connect_success)) }
                .onFailure { scope.launch { snackbar.showSnackbar(context.getString(R.string.error_invalid_qr)) } }
        }

    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scannerLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(context.getString(R.string.peers_scan_qr_prompt))
                        .setBeepEnabled(false)
                        .setOrientationLocked(true)
                        .setCaptureActivity(PortraitQrScanActivity::class.java),
                )
            } else {
                scope.launch { snackbar.showSnackbar(context.getString(R.string.connect_camera_denied)) }
            }
        }

    fun launchScanner() {
        when (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        ) {
            PackageManager.PERMISSION_GRANTED ->
                scannerLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt(context.getString(R.string.peers_scan_qr_prompt))
                        .setBeepEnabled(false)
                        .setOrientationLocked(true)
                        .setCaptureActivity(PortraitQrScanActivity::class.java),
                )
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (showMyQr) {
        AlertDialog(
            onDismissRequest = { showMyQr = false },
            title = { Text(stringResource(R.string.connect_show_qr_title)) },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.peers_my_qr_cd),
                        modifier = Modifier.size(260.dp),
                    )
                    Text(
                        text = stringResource(R.string.connect_show_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMyQr = false }) {
                    Text(stringResource(R.string.wallet_dismiss))
                }
            },
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.peers_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { RelayStatusBadge() }

            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.connect_how_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.connect_how_body),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text =
                                stringResource(
                                    R.string.peers_connected_count,
                                    networkPeers.size,
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        if (metrics.maxPeers > 0uL) {
                            val utilization =
                                (metrics.connectedPeers.toFloat() / metrics.maxPeers.toFloat() * 100f)
                                    .toInt()
                            LinearProgressIndicator(
                                progress = {
                                    metrics.connectedPeers.toFloat() / metrics.maxPeers.toFloat()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                            )
                            Text(
                                "${metrics.connectedPeers}/${metrics.maxPeers} peers ($utilization%)",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { launchScanner() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connect_scan_button))
                    }
                    OutlinedButton(onClick = { showMyQr = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connect_show_qr_button))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = pasteInvite,
                    onValueChange = { pasteInvite = it },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    label = { Text(stringResource(R.string.connect_paste_label)) },
                    placeholder = { Text(stringResource(R.string.connect_paste_hint)) },
                    minLines = 2,
                )
            }
            item {
                Button(
                    onClick = {
                        val raw = pasteInvite.trim()
                        if (raw.isEmpty()) return@Button
                        InviteConnect.apply(raw)
                            .onSuccess {
                                pasteInvite = ""
                                onInviteApplied(context.getString(R.string.connect_success))
                            }
                            .onFailure {
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.error_invalid_qr))
                                }
                            }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    enabled = pasteInvite.isNotBlank(),
                ) {
                    Text(stringResource(R.string.connect_paste_button))
                }
            }

            item {
                TextButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        if (showAdvanced) {
                            stringResource(R.string.connect_hide_advanced)
                        } else {
                            stringResource(R.string.connect_show_advanced)
                        },
                    )
                }
            }

            if (showAdvanced) {
                item {
                    Text(
                        text = stringResource(R.string.connect_advanced_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.peers_nearby_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (networkPeers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.peers_nearby_empty),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(networkPeers, key = { it }) { peer ->
                    Text(peer, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

internal fun generateQrCode(payload: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    val bitmap = createBitmap(size, size)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(
                x,
                y,
                if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
            )
        }
    }
    return bitmap
}

fun parseScannedPeerInfo(raw: String): String? =
    PeerInvite.parse(raw)?.let { PeerInvite.toDialMultiaddr(it) }

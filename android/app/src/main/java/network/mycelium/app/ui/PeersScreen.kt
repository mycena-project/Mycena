// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.nfc.NfcAdapter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.mycelium.app.MeshUiState
import network.mycelium.app.PortraitQrScanActivity
import network.mycelium.app.R
import network.mycelium.app.bootstrap.InviteConnect
import network.mycelium.app.bootstrap.PeerInvite
import network.mycelium.app.discovery.PeersDiscoveryPhase
import network.mycelium.app.discovery.PeersDiscoveryPresentation
import network.mycelium.app.nfc.NfcInviteReader
import network.mycelium.app.viewmodel.PeersViewModel
import network.mycelium.app.util.sharePlainText
import network.mycelium.app.util.userVisibleContactName
import androidx.compose.material3.Slider
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.Alignment
import network.mycelium.bindings.PeerReputationData
import network.mycelium.bindings.mycelium

private data class RelayStatus(val online: Boolean, val reachable: Long)

@Composable
private fun RelayStatusBadge() {
    val context = LocalContext.current
    val internetEnabled =
        context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            .getBoolean("internet_enabled", true)

    if (!internetEnabled) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.peers_relay_internet_off),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        return
    }

    var relayStatus by remember { mutableStateOf<RelayStatus?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            withContext(Dispatchers.IO) {
                relayStatus =
                    try {
                        val statusUrl = java.net.URL("https://mycelium-relay.fly.dev/")
                        val statusConn = statusUrl.openConnection() as java.net.HttpURLConnection
                        statusConn.connectTimeout = 3000
                        statusConn.readTimeout = 3000
                        if (statusConn.responseCode == 200) {
                            val statusJson =
                                org.json.JSONObject(
                                    statusConn.inputStream.bufferedReader().readText(),
                                )
                            var reachable = 0L
                            try {
                                val rendezvousUrl =
                                    java.net.URL("https://mycelium-relay.fly.dev/rendezvous")
                                val rendezvousConn =
                                    rendezvousUrl.openConnection() as java.net.HttpURLConnection
                                rendezvousConn.connectTimeout = 3000
                                rendezvousConn.readTimeout = 3000
                                if (rendezvousConn.responseCode == 200) {
                                    val rendezvousJson =
                                        org.json.JSONObject(
                                            rendezvousConn.inputStream.bufferedReader().readText(),
                                        )
                                    reachable =
                                        rendezvousJson.optJSONArray("peers")?.length()?.toLong()
                                            ?: 0L
                                }
                            } catch (_: Exception) {
                            }
                            if (reachable == 0L) {
                                reachable =
                                    statusJson.optLong("reachable_peers", 0).takeIf { it > 0 }
                                        ?: statusJson.optLong("unique_peers", 0)
                            }
                            RelayStatus(online = true, reachable = reachable)
                        } else {
                            RelayStatus(online = false, reachable = 0)
                        }
                    } catch (_: Exception) {
                        RelayStatus(online = false, reachable = 0)
                    }
            }
            delay(30_000)
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
                        stringResource(R.string.peers_relay_online, status.reachable)
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
    val discovery by vm.discoveryPresentation.collectAsState()
    val metrics by vm.metrics.collectAsState()
    val localPeerId = mycelium.localPeerId()
    var invitePayload by remember { mutableStateOf(PeerInvite.encode(localPeerId)) }
    LaunchedEffect(localPeerId) {
        invitePayload =
            withContext(Dispatchers.IO) {
                mycelium.getShareableMultiaddrs().firstOrNull()
                    ?: PeerInvite.encode(localPeerId)
            }
        delay(3_000)
        invitePayload =
            withContext(Dispatchers.IO) {
                mycelium.getShareableMultiaddrs().firstOrNull()
                    ?: PeerInvite.encode(localPeerId)
            }
    }
    val qrBitmap = remember(invitePayload) { generateQrCode(invitePayload, 640) }
    var showMyQr by remember { mutableStateOf(false) }
    var pasteInvite by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    val nfcAvailable = remember { NfcAdapter.getDefaultAdapter(context) != null }
    var nfcListening by remember { mutableStateOf(false) }

    fun onInviteApplied(successMessage: String, peerId: String?) {
        scope.launch {
            vm.onInviteConnectFinished()
            snackbar.showSnackbar(successMessage)
            vm.refresh()
            if (!peerId.isNullOrBlank()) {
                launch {
                    repeat(15) {
                        delay(2_000)
                        MeshUiState.bumpChatRevision()
                    }
                }
                navController.navigate("chat") {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
                navController.navigate("chat/$peerId")
            }
        }
    }

    fun applyInvite(raw: String, onSuccess: () -> Unit = {}) {
        scope.launch {
            vm.onInviteConnectStarted()
            InviteConnect.apply(raw)
                .onSuccess { peerId ->
                    onSuccess()
                    onInviteApplied(context.getString(R.string.connect_success), peerId)
                }
                .onFailure {
                    vm.onInviteConnectFinished()
                    snackbar.showSnackbar(context.getString(R.string.error_invalid_qr))
                }
        }
    }

    val nfcReader =
        remember(context) {
            (context as? Activity)?.let { activity ->
                NfcInviteReader(
                    activity = activity,
                    onInvite = { raw ->
                        nfcListening = false
                        applyInvite(raw)
                    },
                    onUnavailable = {
                        nfcListening = false
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.peers_nfc_unavailable))
                        }
                    },
                )
            }
        }

    DisposableEffect(nfcListening, nfcReader) {
        if (nfcListening) {
            nfcReader?.enable()
        } else {
            nfcReader?.disable()
        }
        onDispose { nfcReader?.disable() }
    }

    val scannerLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val raw = result.contents
            if (raw.isNullOrBlank()) return@rememberLauncherForActivityResult
            applyInvite(raw)
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
                    Button(
                        onClick = {
                            sharePlainText(
                                context,
                                invitePayload,
                                context.getString(R.string.peers_share),
                            )
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text(
                            stringResource(R.string.peers_share),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.peers_title)) },
                actions = {
                    IconButton(onClick = { navController.navigate("share_app") }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share_app_title),
                        )
                    }
                },
            )
        },
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
                        Text(
                            text = stringResource(R.string.peers_connected_local_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (metrics.crowdDensityActive) {
                            Text(
                                stringResource(R.string.status_crowd_density_active),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(top = 8.dp),
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
                    if (nfcAvailable) {
                        OutlinedButton(
                            onClick = { nfcListening = !nfcListening },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (nfcListening) {
                                    stringResource(R.string.peers_nfc_tap) + "…"
                                } else {
                                    stringResource(R.string.peers_nfc_tap)
                                },
                            )
                        }
                    }
                    OutlinedButton(onClick = { showMyQr = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.connect_show_qr_button))
                    }
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
                            applyInvite(raw) { pasteInvite = "" }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                        enabled = pasteInvite.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.connect_paste_button))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            sharePlainText(
                                context,
                                invitePayload,
                                context.getString(R.string.peers_share),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.peers_share))
                    }
                }
            }

            item {
                DiscoveryStatusCard(presentation = discovery)
            }

            item {
                Text(
                    text = stringResource(R.string.peers_connected_section_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (networkPeers.isNotEmpty()) {
                items(networkPeers, key = { it }) { peer ->
                    PeerRow(
                        peerId = peer,
                        onEndorsed = {
                            scope.launch {
                                snackbar.showSnackbar(context.getString(R.string.peers_endorse_success))
                            }
                        },
                        onEndorse = { id, strength -> vm.endorsePeer(id, strength) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peerId: String,
    onEndorsed: () -> Unit,
    onEndorse: (String, UByte) -> Unit,
) {
    val context = LocalContext.current
    val chatRevision by MeshUiState.chatRevision.collectAsState()
    var showEndorse by remember { mutableStateOf(false) }
    var strength by remember { mutableStateOf(3f) }
    var reputation by remember { mutableStateOf<PeerReputationData?>(null) }
    var contactName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(peerId, chatRevision) {
        reputation =
            withContext(Dispatchers.IO) {
                mycelium.peerReputation(peerId)
            }
        contactName =
            withContext(Dispatchers.IO) {
                mycelium.listContacts()
                    .find { it.peerId == peerId }
                    ?.let {
                        userVisibleContactName(
                            it.displayName,
                            it.peerId,
                            context.getString(R.string.contact_unnamed),
                        )
                    }
            }
    }

    Card(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contactName ?: stringResource(R.string.peers_nearby_device),
                    style = MaterialTheme.typography.bodyMedium,
                )
                reputation?.let { rep ->
                    Text(
                        stringResource(
                            R.string.peers_reputation_relay,
                            (rep.relaySuccessRate * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { showEndorse = true }) {
                Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.peers_endorse_button), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }

    if (showEndorse) {
        AlertDialog(
            onDismissRequest = { showEndorse = false },
            title = { Text(stringResource(R.string.peers_endorse_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.peers_endorse_body))
                    Text(
                        stringResource(R.string.peers_endorse_strength, strength.toInt()),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        valueRange = 1f..5f,
                        steps = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEndorse(peerId, strength.toInt().toUByte())
                        showEndorse = false
                        onEndorsed()
                    },
                ) {
                    Text(stringResource(R.string.peers_endorse_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndorse = false }) {
                    Text(stringResource(R.string.wallet_dismiss))
                }
            },
        )
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

@Composable
private fun DiscoveryStatusCard(presentation: PeersDiscoveryPresentation) {
    if (presentation.titleResId == 0) return
    Card(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when (presentation.phase) {
                        PeersDiscoveryPhase.NobodyInRange ->
                            MaterialTheme.colorScheme.surfaceVariant
                        PeersDiscoveryPhase.Connecting,
                        PeersDiscoveryPhase.Searching,
                        PeersDiscoveryPhase.DevicesNearby,
                        ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (presentation.showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.size(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(presentation.titleResId),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(presentation.bodyResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

fun parseScannedPeerInfo(raw: String): String? =
    PeerInvite.parse(raw)?.let { PeerInvite.toDialMultiaddr(it) }

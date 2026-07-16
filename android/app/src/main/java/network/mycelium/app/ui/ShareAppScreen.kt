// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import network.mycelium.app.R
import network.mycelium.app.bootstrap.ApkSharePayload
import network.mycelium.app.util.shareApkFile
import network.mycelium.app.util.sharePlainText
import network.mycelium.app.viewmodel.ShareAppMode
import network.mycelium.app.viewmodel.ShareAppSession
import network.mycelium.app.viewmodel.ShareAppViewModel
import network.mycelium.app.viewmodel.SharePhoneTransport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(navController: NavController, vm: ShareAppViewModel = viewModel()) {
    val context = LocalContext.current
    val session by vm.session.collectAsState()
    val isServing by vm.isServing.collectAsState()
    val transferCount by vm.transferCount.collectAsState()
    val error by vm.error.collectAsState()
    val hotspotFailureReason by vm.hotspotFailureReason.collectAsState()

    Scaffold(
        topBar = {
            MyceliumBackTopBar(
                title = stringResource(R.string.share_app_title),
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.share_app_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            if (error != null) {
                Text(
                    text =
                        when (error) {
                            "hotspot_failed" ->
                                stringResource(
                                    R.string.share_app_hotspot_failed,
                                    hotspotFailureReason ?: 0,
                                )
                            "wifi_direct_failed" -> stringResource(R.string.share_app_wifi_direct_failed)
                            else -> stringResource(R.string.share_app_error_generic)
                        },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            OutlinedButton(
                onClick = {
                    shareApkFile(context, context.getString(R.string.share_app_share_apk))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.share_app_share_apk))
            }

            Text(
                stringResource(R.string.share_app_share_apk_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (isServing && session != null) {
                ActiveShareSession(
                    session = session!!,
                    transferCount = transferCount,
                    onShareText = { text, title ->
                        sharePlainText(context, text, title)
                    },
                    onStop = vm::stopSharing,
                )
            } else {
                Button(
                    onClick = vm::startPhoneToPhone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_app_phone_to_phone))
                }

                Text(
                    stringResource(R.string.share_app_phone_to_phone_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                OutlinedButton(
                    onClick = vm::startOnLocalNetwork,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_app_local_network))
                }

                Text(
                    stringResource(R.string.share_app_wifi_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ActiveShareSession(
    session: ShareAppSession,
    transferCount: Int,
    onShareText: (String, String) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current

    if (session.mode == ShareAppMode.PHONE_TO_PHONE && session.hotspot != null) {
        if (session.wifiWasPaused) {
            Text(
                stringResource(R.string.share_app_wifi_paused_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (session.phoneTransport == SharePhoneTransport.WIFI_DIRECT) {
            Text(
                stringResource(R.string.share_app_wifi_direct_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.share_app_step_wifi_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(
                        if (session.phoneTransport == SharePhoneTransport.WIFI_DIRECT) {
                            R.string.share_app_step_wifi_body_direct
                        } else {
                            R.string.share_app_step_wifi_body
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                val wifiQr = remember(session.hotspot) {
                    ApkSharePayload.buildWifiQr(session.hotspot!!)
                }
                val wifiQrBitmap = remember(wifiQr) { generateQrCode(wifiQr, 512) }
                Image(
                    bitmap = wifiQrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_app_wifi_qr_cd),
                    modifier = Modifier.size(220.dp),
                )
                Text(
                    stringResource(
                        R.string.share_app_wifi_credentials,
                        session.hotspot!!.ssid,
                        session.hotspot!!.password,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.share_app_step_install_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.share_app_step_install_body),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            val installQrBitmap = remember(session.installUrl) {
                generateQrCode(session.installUrl, 512)
            }
            Image(
                bitmap = installQrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.share_app_qr_content_description),
                modifier = Modifier.size(220.dp),
            )
            Text(
                session.installUrl,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
        }
    }

    if (transferCount > 0) {
        Text(
            stringResource(R.string.share_app_transfers, transferCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    OutlinedButton(
        onClick = {
            shareApkFile(context, context.getString(R.string.share_app_share_apk))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.share_app_share_apk))
    }

    OutlinedButton(
        onClick = {
            val text =
                if (session.hotspot != null) {
                    ApkSharePayload.buildShareText(session.hotspot!!, session.installUrl)
                } else {
                    session.installUrl
                }
            onShareText(text, context.getString(R.string.share_app_share_link))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.share_app_share_link))
    }

    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.share_app_stop))
    }

    Text(
        stringResource(R.string.share_app_keep_open_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.mycena_project.app.R
import io.github.mycena_project.app.bootstrap.ApkSharePayload
import io.github.mycena_project.app.bootstrap.ShareApkArtifact
import io.github.mycena_project.app.bootstrap.SharePermissions
import io.github.mycena_project.app.bootstrap.ShareTransportPreconditions
import io.github.mycena_project.app.util.shareApkFile
import io.github.mycena_project.app.util.sharePlainText
import io.github.mycena_project.app.viewmodel.ShareAppMode
import io.github.mycena_project.app.viewmodel.ShareAppSession
import io.github.mycena_project.app.viewmodel.ShareAppViewModel
import io.github.mycena_project.app.viewmodel.SharePhoneTransport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(navController: NavController, vm: ShareAppViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val session by vm.session.collectAsState()
    val isServing by vm.isServing.collectAsState()
    val isStarting by vm.isStarting.collectAsState()
    val transferCount by vm.transferCount.collectAsState()
    val error by vm.error.collectAsState()
    val hotspotFailureReason by vm.hotspotFailureReason.collectAsState()
    var permissionNotice by remember { mutableStateOf<String?>(null) }
    var pendingShareStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sheetError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val granted = results.values.all { it } && SharePermissions.hasAll(context)
            if (granted) {
                permissionNotice = null
                pendingShareStart?.invoke()
            } else {
                val missing = SharePermissions.missing(context)
                val permanent =
                    activity != null &&
                        missing.isNotEmpty() &&
                        SharePermissions.permanentlyDenied(activity, missing)
                permissionNotice =
                    if (permanent) "permission_permanent" else "permission_denied"
            }
            pendingShareStart = null
        }

    fun requestSharePermissionsThen(start: () -> Unit) {
        permissionNotice = null
        sheetError = null
        val locationBlock =
            SharePermissions.locationToggleRequired() &&
                !SharePermissions.isLocationEnabled(context)
        if (locationBlock) {
            permissionNotice = ShareTransportPreconditions.ERROR_LOCATION_DISABLED
            return
        }
        val missing = SharePermissions.missing(context)
        if (missing.isEmpty()) {
            start()
            return
        }
        pendingShareStart = start
        permissionLauncher.launch(missing)
    }

    KeepScreenOn(enabled = isServing)

    Scaffold(
        topBar = {
            MycenaBackTopBar(
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

            val shownError = error ?: permissionNotice ?: sheetError
            if (shownError != null) {
                Text(
                    text = shareErrorText(shownError, hotspotFailureReason),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (shownError == "permission_permanent") {
                    OutlinedButton(
                        onClick = { openAppSettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.share_app_open_app_settings))
                    }
                }
                if (shownError == ShareTransportPreconditions.ERROR_LOCATION_DISABLED) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.share_app_open_location_settings))
                    }
                }
                if (shownError == ShareTransportPreconditions.ERROR_WIFI_DISABLED) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.share_app_open_wifi_settings))
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    sheetError = null
                    try {
                        shareApkFile(context, context.getString(R.string.share_app_share_apk))
                    } catch (_: ShareApkArtifact.SplitApkNotSupportedException) {
                        sheetError = "split_apk"
                    } catch (_: ShareApkArtifact.DebugApkNotShareableException) {
                        sheetError = "debug_apk"
                    } catch (_: ShareApkArtifact.NotTechnicalBetaException) {
                        sheetError = "not_technical_beta"
                    } catch (_: ShareApkArtifact.IncompleteApkException) {
                        sheetError = "incomplete_apk"
                    } catch (_: Exception) {
                        sheetError = "share_failed"
                    }
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

            if (isStarting) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.share_app_starting),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            if (isServing && session != null) {
                ActiveShareSession(
                    session = session!!,
                    transferCount = transferCount,
                    onShareText = { text, title ->
                        sharePlainText(context, text, title)
                    },
                    onStop = vm::stopSharing,
                )
            } else if (!isStarting) {
                Button(
                    onClick = {
                        requestSharePermissionsThen(vm::startPhoneToPhone)
                    },
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
private fun shareErrorText(error: String, hotspotFailureReason: Int?): String =
    when (error) {
        "hotspot_failed" ->
            stringResource(
                R.string.share_app_hotspot_failed,
                hotspotFailureReason ?: 0,
            )
        "wifi_direct_failed",
        ShareTransportPreconditions.ERROR_WIFI_DIRECT_UNSUPPORTED,
        -> stringResource(R.string.share_app_wifi_direct_failed)
        "permission_denied",
        ShareTransportPreconditions.ERROR_PERMISSION,
        -> stringResource(R.string.share_app_permission_denied)
        "permission_permanent" -> stringResource(R.string.share_app_permission_permanent)
        ShareTransportPreconditions.ERROR_LOCATION_DISABLED ->
            stringResource(R.string.share_app_location_disabled)
        ShareTransportPreconditions.ERROR_WIFI_DISABLED ->
            stringResource(R.string.share_app_wifi_disabled)
        ShareTransportPreconditions.ERROR_WIFI_HARDWARE ->
            stringResource(R.string.share_app_wifi_unavailable)
        "split_apk" -> stringResource(R.string.share_app_split_apk)
        "debug_apk", "not_technical_beta" -> stringResource(R.string.share_app_debug_apk)
        "incomplete_apk" -> stringResource(R.string.share_app_incomplete_apk)
        "no_reachable_address" -> stringResource(R.string.share_app_no_reachable_address)
        "hotspot_stopped" -> stringResource(R.string.share_app_hotspot_stopped)
        else -> stringResource(R.string.share_app_error_generic)
    }

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    val intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
    context.startActivity(intent)
}

@Composable
private fun ActiveShareSession(
    session: ShareAppSession,
    transferCount: Int,
    onShareText: (String, String) -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current

    Text(
        stringResource(R.string.share_app_technical_beta_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

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
        val wifiQr = remember(session.hotspot) {
            ApkSharePayload.buildWifiQrOrNull(session.hotspot!!)
        }
        if (wifiQr != null) {
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
            session.apkSha256?.let { hash ->
                Text(
                    stringResource(R.string.share_app_apk_sha256, hash),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (!session.sidecarReady) {
        Text(
            stringResource(R.string.share_app_sidecar_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }

    val invite = session.meshInviteUri
    if (!invite.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.share_app_step_join_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.share_app_step_join_body),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
                val joinQr = remember(invite) { generateQrCode(invite, 512) }
                Image(
                    bitmap = joinQr.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_app_join_qr_cd),
                    modifier = Modifier.size(220.dp),
                )
            }
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
            try {
                shareApkFile(context, context.getString(R.string.share_app_share_apk))
            } catch (_: Exception) {
            }
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

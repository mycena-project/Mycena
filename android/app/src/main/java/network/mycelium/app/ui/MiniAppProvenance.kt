// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import network.mycelium.app.R
import network.mycelium.app.miniapp.miniAppTrustLabel
import network.mycelium.bindings.MiniAppInfo

@Composable
fun MiniAppInstallProvenanceDialog(
    previewTrust: String,
    listingSignatureOk: Boolean,
    bundleSignatureOk: Boolean,
    hasInlineScript: Boolean,
    reproducibleAttested: Boolean,
    isDowngrade: Boolean,
    installedVersion: String?,
    manifest: MiniAppInfo,
    onConfirm: (allowDowngrade: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var allowDowngrade by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val trustLabel =
        miniAppTrustLabel(context, previewTrust)
            ?: stringResource(R.string.miniapp_trust_sideload)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.miniapp_install_review_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                Text(manifest.id, style = MaterialTheme.typography.bodySmall)
                Text("v${manifest.version}", style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.miniapp_install_trust, trustLabel),
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (listingSignatureOk) {
                    Text(
                        stringResource(R.string.miniapp_listing_signed),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (bundleSignatureOk) {
                    Text(
                        stringResource(R.string.miniapp_bundle_signed),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (manifest.permissions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.miniapp_permissions_label),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    manifest.permissions.forEach { p ->
                        Text("• $p", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (hasInlineScript) {
                    Text(
                        stringResource(R.string.miniapp_inline_script_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (reproducibleAttested) {
                    Text(
                        stringResource(R.string.miniapp_reproducible_attested),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (isDowngrade && installedVersion != null) {
                    Text(
                        stringResource(
                            R.string.miniapp_downgrade_warning,
                            installedVersion,
                            manifest.version,
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allowDowngrade,
                            onCheckedChange = { allowDowngrade = it },
                        )
                        Text(
                            stringResource(R.string.miniapp_allow_downgrade),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    stringResource(R.string.miniapp_sideload_warning),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(isDowngrade && allowDowngrade) },
                enabled = previewTrust != "hash_mismatch" && (!isDowngrade || allowDowngrade),
            ) {
                Text(stringResource(R.string.miniapp_install_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.miniapp_install_cancel))
            }
        },
    )
}

@Composable
fun MiniAppLaunchProvenanceDialog(
    app: MiniAppInfo,
    installTrust: String?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.miniapp_launch_review_title)) },
        text = {
            Column {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                Text(app.id, style = MaterialTheme.typography.bodySmall)
                miniAppTrustLabel(
                    LocalContext.current,
                    installTrust,
                )?.let { label ->
                    Text(
                        stringResource(R.string.miniapp_install_trust, label),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (app.permissions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.miniapp_permissions_label),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    app.permissions.forEach { p -> Text("• $p", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.miniapp_open))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.miniapp_install_cancel))
            }
        },
    )
}

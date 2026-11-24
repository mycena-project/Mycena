// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mycena_project.app.R
import io.github.mycena_project.app.util.LocationHelper

@Composable
fun ChatInputRow(
    onSend: (String) -> Unit,
    shareLocation: (
        context: Context,
        onLoading: (Boolean) -> Unit,
        onMessage: (String) -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val locationPermission =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            if (permissions.values.any { it }) {
                shareLocation(context, { isLoadingLocation = it }) { body ->
                    onSend(body)
                }
            }
        }

    fun requestLocation() {
        if (LocationHelper.hasLocationPermission(context)) {
            shareLocation(context, { isLoadingLocation = it }) { body ->
                onSend(body)
            }
        } else {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    fun onLocationButtonClick() {
        val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("location_privacy_acknowledged", false)) {
            showPrivacyDialog = true
        } else {
            requestLocation()
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.location_privacy_title)) },
            text = { Text(stringResource(R.string.location_privacy_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.getSharedPreferences("app", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("location_privacy_acknowledged", true)
                            .apply()
                        showPrivacyDialog = false
                        requestLocation()
                    },
                ) {
                    Text(stringResource(R.string.location_share_once))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onLocationButtonClick() },
            enabled = !isLoadingLocation,
        ) {
            if (isLoadingLocation) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = stringResource(R.string.chat_share_location),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_message_hint)) },
        )
        IconButton(
            onClick = {
                if (input.isNotBlank()) {
                    onSend(input)
                    input = ""
                }
            },
            enabled = input.isNotBlank(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send),
            )
        }
    }
}

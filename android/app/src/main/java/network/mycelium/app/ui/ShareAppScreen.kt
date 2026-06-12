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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import network.mycelium.app.R
import network.mycelium.app.viewmodel.ShareAppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareAppScreen(vm: ShareAppViewModel = viewModel()) {
    val shareUrl by vm.shareUrl.collectAsState()
    val isServing by vm.isServing.collectAsState()
    val transferCount by vm.transferCount.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.share_app_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.share_app_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            if (isServing && shareUrl != null) {
                val qrBitmap = remember(shareUrl) { generateQrCode(shareUrl!!, 512) }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.share_app_qr_content_description),
                    modifier = Modifier.size(240.dp),
                )

                Text(
                    shareUrl!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )

                Text(
                    stringResource(R.string.share_app_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )

                if (transferCount > 0) {
                    Text(
                        stringResource(R.string.share_app_transfers, transferCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedButton(onClick = vm::stopSharing) {
                    Text(stringResource(R.string.share_app_stop))
                }
            } else {
                Button(
                    onClick = vm::startSharing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.share_app_start))
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

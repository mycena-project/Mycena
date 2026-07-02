// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import network.mycelium.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.more_title)) })
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.more_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            MoreHubRow(
                icon = Icons.Default.Share,
                title = stringResource(R.string.share_app_title),
                subtitle = stringResource(R.string.more_share_app_sub),
                onClick = { navController.navigate("share_app") },
            )
            Spacer(Modifier.height(10.dp))
            MoreHubRow(
                icon = Icons.Default.Apps,
                title = stringResource(R.string.settings_mini_apps),
                subtitle = stringResource(R.string.more_mini_apps_sub),
                onClick = { navController.navigate("miniapp_store") },
            )
            Spacer(Modifier.height(10.dp))
            MoreHubRow(
                icon = Icons.Default.Wallet,
                title = stringResource(R.string.nav_wallet),
                subtitle = stringResource(R.string.more_wallet_sub),
                onClick = { navController.navigate("wallet") },
            )
            Spacer(Modifier.height(10.dp))
            MoreHubRow(
                icon = Icons.AutoMirrored.Filled.Send,
                title = stringResource(R.string.settings_open_mail),
                subtitle = stringResource(R.string.more_mail_sub),
                onClick = { navController.navigate("mail") },
            )
            Spacer(Modifier.height(10.dp))
            MoreHubRow(
                icon = Icons.Default.Settings,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.more_settings_sub),
                onClick = { navController.navigate("settings") },
            )
        }
    }
}

@Composable
private fun MoreHubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

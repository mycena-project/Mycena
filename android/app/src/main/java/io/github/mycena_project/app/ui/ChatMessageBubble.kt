// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mycena_project.app.R
import io.github.mycena_project.app.util.ChatTimeFormatter
import io.github.mycena_project.app.util.LocationHelper
import io.github.mycena_project.bindings.ChatMessage
import io.github.mycena_project.bindings.CustodyPendingStatus

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isOwn: Boolean,
    custodyStatus: CustodyPendingStatus? = null,
) {
    val context = LocalContext.current
    val isLocationMessage = message.body.startsWith("📍")
    val maxBubbleWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color =
                if (isOwn) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = maxBubbleWidth),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isOwn) {
                    Text(
                        text = message.fromDisplayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = ChatTimeFormatter.format(message.timestampMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (custodyStatus != null) {
                    CustodyDeliveryStatusChip(status = custodyStatus)
                }
                if (isLocationMessage) {
                    val coords = LocationHelper.extractCoordinates(message.body)
                    if (coords != null) {
                        TextButton(
                            onClick = {
                                val uri =
                                    Uri.parse(
                                        "geo:${coords.first},${coords.second}?q=${coords.first},${coords.second}",
                                    )
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.location_open_maps),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

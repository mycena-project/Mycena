// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import network.mycelium.app.R
import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.CustodyPendingStatus
import network.mycelium.bindings.ExpectedDeliveryBasis

internal fun msToDeliveryMinutes(ms: ULong): Int = max(1, (ms.toLong() / 60_000.0).roundToInt())

internal fun custodyStatusForMessage(
    message: ChatMessage,
    pending: List<CustodyPendingStatus>,
    isOwn: Boolean,
    meshOnly: Boolean,
): CustodyPendingStatus? {
    if (!isOwn || !meshOnly) return null
    return pending.firstOrNull { status ->
        status.chatBody == message.body &&
            abs(status.enqueuedAtMs.toLong() - message.timestampMs.toLong()) <= 120_000
    }
}

@Composable
fun CustodyDeliveryStatusChip(status: CustodyPendingStatus) {
    val carriers = status.expected.activeCarrierCount
    val noCarriers =
        carriers == 0u ||
            status.expected.basis == ExpectedDeliveryBasis.NO_CARRIERS_NEARBY
    val minMin = msToDeliveryMinutes(status.expected.etaMinMs)
    val maxMin = msToDeliveryMinutes(status.expected.etaMaxMs)
    val text =
        if (noCarriers) {
            stringResource(R.string.chat_custody_waiting_nearby)
        } else {
            val carriersText =
                when (carriers) {
                    1u -> stringResource(R.string.chat_custody_carriers_one)
                    else -> stringResource(R.string.chat_custody_carriers_many, carriers.toInt())
                }
            stringResource(
                R.string.chat_custody_message_status,
                minMin,
                maxMin,
                carriersText,
            )
        }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

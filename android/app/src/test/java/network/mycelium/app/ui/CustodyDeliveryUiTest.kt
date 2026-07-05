// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.ui

import network.mycelium.bindings.ChatMessage
import network.mycelium.bindings.CustodyPendingStatus
import network.mycelium.bindings.ExpectedDeliveryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import network.mycelium.bindings.ExpectedDeliveryBasis

class CustodyDeliveryUiTest {
    private fun status(body: String, enqueuedMs: ULong = 1_000u) =
        CustodyPendingStatus(
            messageId = "mid",
            destPeer = "peer-z",
            chatBody = body,
            enqueuedAtMs = enqueuedMs,
            spraysReleased = 0u,
            maxSpray = 3u,
            hopCount = 0u,
            expected =
                ExpectedDeliveryData(
                    etaMinMs = 300_000u,
                    etaMaxMs = 1_200_000u,
                    confidence = 0.4,
                    activeCarrierCount = 2u,
                    bestCarrierPeer = null,
                    basis = ExpectedDeliveryBasis.DELIVERY_PROBABILITY,
                ),
        )

    private fun message(body: String, ts: ULong = 1_000u) =
        ChatMessage(
            id = "chat-1",
            fromPeer = "self",
            fromDisplayName = "Me",
            body = body,
            timestampMs = ts,
        )

    @Test
    fun matchesOwnMessageOnMeshOnly() {
        val pending = listOf(status("hello"))
        val match =
            custodyStatusForMessage(
                message("hello"),
                pending,
                isOwn = true,
                meshOnly = true,
            )
        assertEquals("mid", match?.messageId)
    }

    @Test
    fun skipsWhenInternetActive() {
        val pending = listOf(status("hello"))
        assertNull(
            custodyStatusForMessage(
                message("hello"),
                pending,
                isOwn = true,
                meshOnly = false,
            ),
        )
    }

    @Test
    fun skipsIncomingMessages() {
        val pending = listOf(status("hello"))
        assertNull(
            custodyStatusForMessage(
                message("hello"),
                pending,
                isOwn = false,
                meshOnly = true,
            ),
        )
    }

    @Test
    fun minutesNeverBelowOne() {
        assertEquals(1, msToDeliveryMinutes(30_000u))
        assertEquals(5, msToDeliveryMinutes(300_000u))
    }
}

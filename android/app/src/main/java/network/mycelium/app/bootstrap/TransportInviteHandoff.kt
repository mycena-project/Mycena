// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.util.Log
import network.mycelium.bindings.mycelium
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared F1 transport dial from any discovery bearer (BLE GATT, Wi-Fi Aware, NSD).
 */
object TransportInviteHandoff {
    private const val TAG = "TransportInviteHandoff"

    private val accountsHandedOff = ConcurrentHashMap.newKeySet<String>()

    fun resetSession() {
        accountsHandedOff.clear()
    }

    sealed class Result {
        data object Connected : Result()
        data object AlreadyConnected : Result()
        data object SkippedDuplicate : Result()
        data class Failed(val reason: String) : Result()
    }

    fun tryInviteHandoff(invite: String, peerHint: String, rssi: Int = 0): Result {
        val trimmed = invite.trim()
        if (trimmed.isEmpty()) return Result.Failed("empty_invite")
        val accountId =
            PeerInvite.accountIdFrom(trimmed)
                ?: PeerInvite.peerIdFrom(trimmed)
                ?: peerHint
        if (accountsHandedOff.contains(accountId)) {
            return Result.SkippedDuplicate
        }
        val peerId = PeerInvite.peerIdFrom(trimmed)
        if (peerId != null && mycelium.connectedPeers().contains(peerId)) {
            accountsHandedOff.add(accountId)
            return Result.AlreadyConnected
        }
        return runCatching {
            mycelium.connectTransportInvite(trimmed)
        }.fold(
            onSuccess = {
                accountsHandedOff.add(accountId)
                Log.i(TAG, "handoff ok account=$accountId peer=$peerId hint=$peerHint rssi=$rssi")
                Result.Connected
            },
            onFailure = { err ->
                Result.Failed(err.message ?: "connect_failed")
            },
        )
    }
}

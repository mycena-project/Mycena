// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.util.Log
import network.mycelium.bindings.mycelium
import java.util.concurrent.ConcurrentHashMap

/**
 * Attempts BLE GATT invite read → transport dial (F1). Chat contacts still require QR / connectInvite.
 */
object BleMeshHandoff {

    private const val TAG = "BleMeshHandoff"

    private val accountsHandedOff = ConcurrentHashMap.newKeySet<String>()
    private val devicesInFlight = ConcurrentHashMap.newKeySet<String>()

    fun resetSession() {
        accountsHandedOff.clear()
        devicesInFlight.clear()
    }

    sealed class Result {
        data object Connected : Result()
        data object AlreadyConnected : Result()
        data object SkippedDuplicate : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun tryHandoff(context: Context, target: BleBootstrapScanner.HandoffTarget): Result {
        val address = target.device.address ?: return Result.Failed("no_address")
        if (!devicesInFlight.add(address)) {
            return Result.SkippedDuplicate
        }
        try {
            val invite = BleInviteReader.readInvite(context, target.device)
                ?: return Result.Failed("gatt_read_failed")
            val accountId =
                PeerInvite.accountIdFrom(invite)
                    ?: PeerInvite.peerIdFrom(invite)
                    ?: address
            if (accountsHandedOff.contains(accountId)) {
                return Result.SkippedDuplicate
            }
            val peerId = PeerInvite.peerIdFrom(invite)
            if (peerId != null && mycelium.connectedPeers().contains(peerId)) {
                accountsHandedOff.add(accountId)
                return Result.AlreadyConnected
            }
            return runCatching {
                mycelium.connectTransportInvite(invite.trim())
            }.fold(
                onSuccess = {
                    accountsHandedOff.add(accountId)
                    if (peerId != null) {
                        BleMeshLinkRegistry.register(peerId, target.device)
                        BleMeshDataSession.open(context, target.device, peerId)
                        mycelium.registerBleLink(peerId, target.device.address ?: address)
                    }
                    Log.i(TAG, "handoff ok account=$accountId peer=$peerId rssi=${target.rssi}")
                    Result.Connected
                },
                onFailure = { err ->
                    Result.Failed(err.message ?: "connect_failed")
                },
            )
        } finally {
            devicesInFlight.remove(address)
        }
    }
}

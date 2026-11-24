// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.content.Context
import android.util.Log
import io.github.mycena_project.bindings.mycena
import java.util.concurrent.ConcurrentHashMap

/**
 * Attempts BLE GATT invite read → transport dial (F1). Chat contacts still require QR / connectInvite.
 */
object BleMeshHandoff {

    private const val TAG = "BleMeshHandoff"

    private val devicesInFlight = ConcurrentHashMap.newKeySet<String>()

    fun resetSession() {
        TransportInviteHandoff.resetSession()
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
            return when (
                val handoff =
                    TransportInviteHandoff.tryInviteHandoff(
                        invite,
                        target.device.address ?: "ble",
                        target.rssi,
                    )
            ) {
                TransportInviteHandoff.Result.Connected -> {
                    val peerId = PeerInvite.peerIdFrom(invite)
                    if (peerId != null) {
                        BleMeshLinkRegistry.register(peerId, target.device)
                        BleMeshDataSession.open(context, target.device, peerId)
                        mycena.registerBleLink(peerId, target.device.address ?: address)
                    }
                    Log.i(TAG, "handoff ok peer=$peerId rssi=${target.rssi}")
                    Result.Connected
                }
                TransportInviteHandoff.Result.AlreadyConnected -> Result.AlreadyConnected
                TransportInviteHandoff.Result.SkippedDuplicate -> Result.SkippedDuplicate
                is TransportInviteHandoff.Result.Failed ->
                    Result.Failed(handoff.reason)
            }
        } finally {
            devicesInFlight.remove(address)
        }
    }
}

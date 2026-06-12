// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import network.mycelium.bindings.ConnectivityMode

sealed interface MeshEvent {
    data object Chat : MeshEvent
    data object Mail : MeshEvent
    data object Bulletin : MeshEvent
    data object PeerUp : MeshEvent
    data object PeerDown : MeshEvent
    data object AppStore : MeshEvent
    data object Contacts : MeshEvent
    data class Connectivity(val mode: ConnectivityMode) : MeshEvent
}

fun Context.meshEvents(): Flow<MeshEvent> =
    callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context?,
                    intent: Intent?,
                ) {
                    val event =
                        when (intent?.action) {
                            MeshEvents.CHAT -> MeshEvent.Chat
                            MeshEvents.MAIL -> MeshEvent.Mail
                            MeshEvents.BULLETIN -> MeshEvent.Bulletin
                            MeshEvents.PEER_UP -> MeshEvent.PeerUp
                            MeshEvents.PEER_DOWN -> MeshEvent.PeerDown
                            MeshEvents.APPSTORE -> MeshEvent.AppStore
                            MeshEvents.CONTACTS -> MeshEvent.Contacts
                            MeshEvents.CONNECTIVITY -> {
                                val internet = intent.getBooleanExtra(MeshEvents.EXTRA_INTERNET, true)
                                MeshEvent.Connectivity(
                                    if (internet) ConnectivityMode.INTERNET else ConnectivityMode.MESH_ONLY,
                                )
                            }
                            else -> return
                        }
                    trySend(event)
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(MeshEvents.CHAT)
                addAction(MeshEvents.MAIL)
                addAction(MeshEvents.BULLETIN)
                addAction(MeshEvents.PEER_UP)
                addAction(MeshEvents.PEER_DOWN)
                addAction(MeshEvents.APPSTORE)
                addAction(MeshEvents.CONTACTS)
                addAction(MeshEvents.CONNECTIVITY)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        awaitClose { unregisterReceiver(receiver) }
    }

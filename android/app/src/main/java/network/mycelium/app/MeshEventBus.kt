package network.mycelium.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface MeshEvent {
    data object Chat : MeshEvent
    data object Mail : MeshEvent
    data object Bulletin : MeshEvent
    data object PeerUp : MeshEvent
    data object PeerDown : MeshEvent
    data object AppStore : MeshEvent
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
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        awaitClose { unregisterReceiver(receiver) }
    }

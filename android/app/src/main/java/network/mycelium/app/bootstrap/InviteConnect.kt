package network.mycelium.app.bootstrap

import network.mycelium.bindings.mycelium

/** Apply a scanned or pasted invite: add contact (peer-id invites) and dial. */
object InviteConnect {
    fun apply(raw: String): Result<Unit> {
        val target =
            PeerInvite.parse(raw.trim())
                ?: return Result.failure(IllegalArgumentException("invalid_invite"))
        val multiaddr =
            PeerInvite.toDialMultiaddr(target)
                ?: return Result.failure(IllegalArgumentException("invalid_invite"))
        when (target) {
            is PeerInvite.Target.PeerId ->
                mycelium.addContact(target.id, target.id.take(16), true)
            is PeerInvite.Target.Multiaddr -> { /* dial only */ }
        }
        mycelium.addBootstrapPeer(multiaddr)
        return Result.success(Unit)
    }
}

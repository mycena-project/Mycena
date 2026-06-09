package network.mycelium.app.bootstrap

/** Must match `mycelium_core::bootstrap::RELAY_PEER_ID`. */
object RelayBootstrap {
    const val RELAY_PEER_ID: String = "12D3KooWGv6goWd2fHwcigDjqQm5Dfw28UijwEjnMcYwTpPRPZy6"

    private const val RELAY_TCP: String =
        "/dns4/mycelium-relay.fly.dev/tcp/4001/p2p/$RELAY_PEER_ID"

    fun circuitMultiaddr(remotePeerId: String): String? {
        val remote = remotePeerId.trim()
        if (remote.isEmpty()) return null
        return "$RELAY_TCP/p2p-circuit/p2p/$remote"
    }
}

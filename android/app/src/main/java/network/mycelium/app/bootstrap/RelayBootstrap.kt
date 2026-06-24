// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/** Must match `mycelium_core::bootstrap::RELAY_PEER_ID`. */
object RelayBootstrap {
    const val RELAY_PEER_ID: String = "12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj"

    private const val RELAY_TCP: String =
        "/ip4/149.248.194.198/tcp/4001/p2p/$RELAY_PEER_ID"

    fun circuitMultiaddr(remotePeerId: String): String? {
        val remote = remotePeerId.trim()
        if (remote.isEmpty()) return null
        return "$RELAY_TCP/p2p-circuit/p2p/$remote"
    }
}

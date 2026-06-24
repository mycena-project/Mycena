// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerInviteTests {

    @Test
    fun parsesInviteV1() {
        val id = "12D3KooWGv6goWd2fHwcigDjqQm5Dfw28UijwEjnMcYwTpPRPZy6"
        val raw = PeerInvite.encode(id)
        val target = PeerInvite.parse(raw) as PeerInvite.Target.PeerId
        assertEquals(id, target.id)
        assertNotNull(PeerInvite.toDialMultiaddr(target))
    }

    @Test
    fun parsesInviteV2WithEncKey() {
        val id = "12D3KooWGv6goWd2fHwcigDjqQm5Dfw28UijwEjnMcYwTpPRPZy6"
        val enc = "a".repeat(64)
        val raw = PeerInvite.encodeV2(id, enc)
        val target = PeerInvite.parse(raw) as PeerInvite.Target.PeerId
        assertEquals(id, target.id)
        assertNotNull(PeerInvite.toDialMultiaddr(target))
    }

    @Test
    fun parsesIp4CircuitMultiaddr() {
        val addr =
            "/ip4/149.248.194.198/tcp/4001/p2p/12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj/p2p-circuit/p2p/12D3KooWOtherPeerIdHereXXXXXXXX"
        val target = PeerInvite.parse(addr) as PeerInvite.Target.Multiaddr
        assertEquals(addr, target.addr)
    }
}

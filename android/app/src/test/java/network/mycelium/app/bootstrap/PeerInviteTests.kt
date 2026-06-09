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
    fun parsesDns4CircuitMultiaddr() {
        val addr =
            "/dns4/mycelium-relay.fly.dev/tcp/4001/p2p/12D3KooWGv6goWd2fHwcigDjqQm5Dfw28UijwEjnMcYwTpPRPZy6/p2p-circuit/p2p/12D3KooWOtherPeerIdHereXXXXXXXX"
        val target = PeerInvite.parse(addr) as PeerInvite.Target.Multiaddr
        assertEquals(addr, target.addr)
    }
}

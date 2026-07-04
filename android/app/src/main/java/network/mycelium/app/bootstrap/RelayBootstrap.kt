// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import network.mycelium.bindings.mycelium

/**
 * Federated relay circuit bootstrap — mirrors Rust [`RelayPool`] hash selection.
 * Production dialable relays only; full registry lives in `mycelium-core`.
 */
object RelayBootstrap {
    /** Legacy primary relay (embedded production manifest). */
    const val RELAY_PEER_ID: String = "12D3KooWSkVL5ThExB7paWskDKc2pUobKrV9yWVabMpUdw8hE2wj"

    /** TCP multiaddrs (no `/p2p-circuit` suffix). Add new production relays here after curator endorsement. */
    private val RELAY_TCP_BASES: List<String> = listOf(
        "/ip4/149.248.194.198/tcp/4001/p2p/$RELAY_PEER_ID",
    )

    fun circuitMultiaddr(remotePeerId: String): String? {
        val remote = remotePeerId.trim()
        if (remote.isEmpty()) return null
        return try {
            mycelium.relayCircuitMultiaddr(remote)
        } catch (_: Exception) {
            null
        } ?: run {
            val base = selectRelayBase(remote)
            "$base/p2p-circuit/p2p/$remote"
        }
    }

    private fun selectRelayBase(seed: String): String {
        val bases = RELAY_TCP_BASES
        if (bases.size == 1) return bases[0]
        val h = stableHash(seed)
        val idx = (h and 0x7FFF_FFFF_FFFF_FFFFL) % bases.size.toLong()
        return bases[idx.toInt()]
    }

    private fun stableHash(seed: String): Long {
        var h = 0xcbf29ce484222325L
        for (b in seed.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h *= 0x100000001b3L
        }
        return h
    }
}

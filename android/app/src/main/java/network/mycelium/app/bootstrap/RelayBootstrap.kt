// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import network.mycelium.bindings.mycelium

/**
 * Federated relay circuit bootstrap — mirrors Rust [`RelayPool`] hash selection.
 * Equal-weight standard bootstrap relays (no primary / fallback ordering).
 */
object RelayBootstrap {
    /** Operated slot on VPS (`relay.example.invalid`). */
    const val RELAY_PEER_ID: String = "12D3KooWCVC3ZgY5T4tvBz6kujKzbifbCGnU3kDbvtPnRPfHTogn"

    /** Plain HTTP. Port 443 on this host is the demo Caddy. */
    const val RELAY_HTTP_ORIGIN: String = "http://relay.example.invalid:8080"

    /** TCP `/ip4/` bases (no `/p2p-circuit` suffix) — keep in sync with `standard_bootstrap.rs`. */
    private val RELAY_TCP_BASES: List<String> = listOf(
        "/ip4/192.0.2.57/tcp/4001/p2p/$RELAY_PEER_ID",
        "/ip4/243.185.187.10/tcp/4001/p2p/12D3KooWHkNVYzAAYb37LJJVnkx5n6Z2GkArjT48QXbNx9aFCzi5",
        "/ip4/243.186.187.10/tcp/4001/p2p/12D3KooWCxnh9j2SV9i4sCA4Z7Z8gN8piMTmQ3WsJMLTYYPCnVbb",
        "/ip4/243.187.187.10/tcp/4001/p2p/12D3KooWBzTB6FdEpsoCjJXVrGPZWt2Uqpqo6DY5HfksxWDCdmib",
        "/ip4/243.188.187.10/tcp/4001/p2p/12D3KooWMvn86f7CtdSDH6KE2TCLd2EmW2ARS6PTw2j95o9DgQcC",
        "/ip4/243.189.187.10/tcp/4001/p2p/12D3KooWPTo5HXEsJ19oE6i1QeUgX1AWAeRpB3PCFmRREC3AhFAi",
        "/ip4/243.190.187.10/tcp/4001/p2p/12D3KooWN3cqN7NLRHT7aoKSjnyecVHv5XR4DAasdaLhvDXLCsiE",
    )

    private val nativeLibLoaded: Boolean =
        run {
            try {
                System.loadLibrary("uniffi_mycelium")
                true
            } catch (_: Throwable) {
                false
            }
        }

    fun circuitMultiaddr(remotePeerId: String): String? {
        val remote = remotePeerId.trim()
        if (remote.isEmpty()) return null
        if (nativeLibLoaded) {
            try {
                mycelium.relayCircuitMultiaddr(remote)?.let { return it }
            } catch (_: Throwable) {
                // Fall through to embedded relay bases (JVM unit tests, early startup).
            }
        }
        val base = selectRelayBase(remote)
        return "$base/p2p-circuit/p2p/$remote"
    }

    private fun selectRelayBase(seed: String): String {
        val bases = RELAY_TCP_BASES
        if (bases.size == 1) return bases[0]
        val idx = (stableHash(seed) and Int.MAX_VALUE) % bases.size
        return bases[idx]
    }

    private fun stableHash(seed: String): Int {
        var h = 0x811c9dc5.toInt()
        for (b in seed.toByteArray(Charsets.UTF_8)) {
            h = h xor b.toInt()
            h *= 0x01000193
        }
        return h
    }
}

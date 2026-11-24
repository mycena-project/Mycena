// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import io.github.mycena_project.bindings.mycena

/**
 * Federated relay circuit bootstrap — mirrors Rust [`RelayPool`] hash selection.
 * Equal-weight standard bootstrap relays (no primary / fallback ordering).
 */
object RelayBootstrap {
    /** Operated slot on Fly.io (`mycena-relay.fly.dev`). */
    const val RELAY_PEER_ID: String = "12D3KooWEjWpxescd3inApzVuFEDcMfCFJ5NJy9cYvWLxKxgdJpZ"

    /** HTTPS via the Fly proxy. */
    const val RELAY_HTTP_ORIGIN: String = "https://mycena-relay.fly.dev"

    /**
     * TCP `/ip4/` bases (no `/p2p-circuit` suffix) for the JVM fallback path. Only live relays:
     * mirrors `standard_bootstrap.rs` entries with `live: true` (reserved catalog slots are
     * excluded, as in Rust unless MYCENA_INCLUDE_CATALOG_RELAYS=1).
     */
    private val RELAY_TCP_BASES: List<String> = listOf(
        "/ip4/168.220.93.128/tcp/4001/p2p/$RELAY_PEER_ID",
    )

    private val nativeLibLoaded: Boolean =
        run {
            try {
                System.loadLibrary("uniffi_mycena")
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
                mycena.relayCircuitMultiaddr(remote)?.let { return it }
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

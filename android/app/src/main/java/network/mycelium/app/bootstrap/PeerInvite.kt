// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/**
 * Parses QR / deep-link invites (must match `mycelium_core::invite`).
 */
object PeerInvite {
    private const val PREFIX_V1 = "mycelium://invite/v1#"
    private const val PREFIX_V2 = "mycelium://invite/v2#"

    sealed class Target {
        data class PeerId(val id: String) : Target()
        data class Multiaddr(val addr: String) : Target()
    }

    /** Legacy v1 (peer id only). Prefer [encodeV2] for new QR codes. */
    fun encode(peerId: String): String = "$PREFIX_V1${peerId.trim()}"

    /** v2: peer id + E2E public key (+ optional direct multiaddr on same Wi‑Fi). */
    fun encodeV2(
        peerId: String,
        encPubkeyHex: String,
        directMultiaddr: String? = null,
    ): String {
        val id = peerId.trim()
        val enc = encPubkeyHex.trim()
        val direct = directMultiaddr?.trim()?.takeIf { it.isNotEmpty() }
        return if (direct != null) {
            "$PREFIX_V2$id|$enc|$direct"
        } else {
            "$PREFIX_V2$id|$enc"
        }
    }

    fun parse(raw: String): Target? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        if (input.startsWith(PREFIX_V2)) {
            val rest = input.removePrefix(PREFIX_V2)
            val parts = rest.split('|', limit = 3)
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].length == 64) {
                return Target.PeerId(parts[0].trim())
            }
            return null
        }
        if (input.startsWith(PREFIX_V1)) {
            val id = input.removePrefix(PREFIX_V1).trim()
            return if (id.isNotEmpty()) Target.PeerId(id) else null
        }
        if (input.startsWith("/")) {
            return Target.Multiaddr(input)
        }
        if (input.startsWith("12D3Koo") && input.length >= 40) {
            return Target.PeerId(input)
        }
        return legacyHostPort(input)
    }

    fun toDialMultiaddr(target: Target): String? =
        when (target) {
            is Target.Multiaddr -> target.addr
            is Target.PeerId -> RelayBootstrap.circuitMultiaddr(target.id)
        }

    private fun legacyHostPort(input: String): Target? {
        val parts = input.split("@")
        if (parts.size != 2) return null
        val peerId = parts[0].trim()
        val hostPort = parts[1].split(":")
        if (peerId.isEmpty() || hostPort.size != 2) return null
        val port = hostPort[1].toIntOrNull() ?: return null
        return Target.Multiaddr("/ip4/${hostPort[0]}/tcp/$port/p2p/$peerId")
    }
}

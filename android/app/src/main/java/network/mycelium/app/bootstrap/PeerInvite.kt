// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/**
 * Parses QR / deep-link invites (must match `mycelium_core::invite`).
 */
object PeerInvite {
    private const val PREFIX_V1 = "mycelium://invite/v1#"
    private const val PREFIX_V2 = "mycelium://invite/v2#"
    private const val PREFIX_V3 = "mycelium://invite/v3#"

    sealed class Target {
        data class PeerId(val id: String) : Target()
        data class Multiaddr(val addr: String) : Target()
    }

    /** Default v1 QR (peer id only; E2E key via PeerInfo after connect). */
    fun encode(peerId: String): String = "$PREFIX_V1${peerId.trim()}"

    /** Alias for clarity — same as [encode]. */
    fun encodeV1(peerId: String): String = encode(peerId)

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
        if (input.startsWith(PREFIX_V3)) {
            val rest = input.removePrefix(PREFIX_V3)
            val parts = rest.split('|', limit = 6)
            if (parts.size >= 2 && parts[1].isNotBlank()) {
                return Target.PeerId(parts[1].trim())
            }
            return null
        }
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

    fun peerIdFrom(raw: String): String? =
        when (val target = parse(raw)) {
            is Target.PeerId -> target.id
            else -> null
        }

    /** Stable account id from signed v3 invite (for BLE handoff dedupe). */
    fun accountIdFrom(raw: String): String? {
        val input = raw.trim()
        if (!input.startsWith(PREFIX_V3)) return null
        return input.removePrefix(PREFIX_V3)
            .split('|', limit = 2)
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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

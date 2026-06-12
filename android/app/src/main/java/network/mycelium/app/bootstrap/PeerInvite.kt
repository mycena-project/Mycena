// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/**
 * Parses QR / deep-link invites (must match `mycelium_core::invite`).
 */
object PeerInvite {
    private const val PREFIX_V1 = "mycelium://invite/v1#"

    sealed class Target {
        data class PeerId(val id: String) : Target()
        data class Multiaddr(val addr: String) : Target()
    }

    fun encode(peerId: String): String = "$PREFIX_V1${peerId.trim()}"

    fun parse(raw: String): Target? {
        val input = raw.trim()
        if (input.isEmpty()) return null
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

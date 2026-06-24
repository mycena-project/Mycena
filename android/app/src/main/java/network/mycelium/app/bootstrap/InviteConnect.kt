// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import network.mycelium.bindings.mycelium

/** Apply a scanned or pasted invite: contact, E2E key (v2), and dial. */
object InviteConnect {
    fun apply(raw: String): Result<Unit> {
        return try {
            mycelium.connectInvite(raw.trim())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                e as? Exception ?: IllegalArgumentException(e.message ?: "invalid_invite"),
            )
        }
    }
}

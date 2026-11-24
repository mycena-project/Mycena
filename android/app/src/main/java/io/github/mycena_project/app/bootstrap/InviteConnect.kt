// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.mycena_project.app.MeshUiState
import io.github.mycena_project.bindings.mycena

/** Apply a scanned or pasted invite: contact, E2E key (v2/v3), and dial. Returns remote peer id when known. */
object InviteConnect {
    suspend fun apply(raw: String): Result<String?> =
        withContext(Dispatchers.IO) {
            try {
                mycena.connectInvite(raw.trim())
                MeshUiState.bumpChatRevision()
                Result.success(PeerInvite.peerIdFrom(raw))
            } catch (e: Exception) {
                Result.failure(
                    e as? Exception ?: IllegalArgumentException(e.message ?: "invalid_invite"),
                )
            }
        }
}

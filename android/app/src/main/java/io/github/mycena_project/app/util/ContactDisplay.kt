// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.util

/** Hide peer-id fragments and other technical placeholders from user-visible labels. */
fun userVisibleContactName(
    displayName: String,
    peerId: String,
    fallback: String,
): String {
    val trimmed = displayName.trim()
    if (trimmed.isEmpty()) return fallback
    if (looksLikeTechnicalId(trimmed, peerId)) return fallback
    return trimmed
}

private fun looksLikeTechnicalId(name: String, peerId: String): Boolean {
    if (name == peerId) return true
    if (peerId.length >= 12 && name == peerId.take(12)) return true
    if (peerId.length >= 16 && name == peerId.take(16)) return true
    if (name.startsWith("12D3") && name.length <= 20) return true
    return false
}

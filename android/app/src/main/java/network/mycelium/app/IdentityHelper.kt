// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Context
import java.io.File

/**
 * Detects whether a prior Mycelium identity exists on this device (e.g. after reinstall
 * that preserved app data or storage keys).
 */
object IdentityHelper {
    private const val DB_DIR = "mycelium"
    private const val ACCOUNT_SECRET = "account_ed25519.enc"

    fun dbPath(context: Context): String = context.filesDir.absolutePath + "/$DB_DIR"

    fun hasExistingIdentity(context: Context): Boolean {
        val secretsDir = File(dbPath(context), ".secrets")
        if (!secretsDir.isDirectory) return false
        val accountFile = File(secretsDir, ACCOUNT_SECRET)
        if (accountFile.isFile && accountFile.length() > 0L) return true
        return secretsDir.list()?.any { it.endsWith(".enc") } == true
    }
}

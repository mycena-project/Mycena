// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.InputStream

/**
 * Imports mesh bootstrap sidecar saved during phone-to-phone APK install.
 *
 * Listing public Downloads is best-effort only (fails under scoped storage on API 29+).
 * Recipients should scan the sharer's invite QR or pick the `.myc` file via SAF.
 */
object OfflineBootstrap {
    private const val TAG = "OfflineBootstrap"
    private const val PREFS = "offline_bootstrap"
    const val KEY_IMPORTED = "imported_v1"
    const val KEY_DISPLAY_NAME = "display_name"
    const val KEY_BOOTSTRAP_PEERS = "bootstrap_peers"
    const val KEY_INVITE = "invite_uri"

    data class ImportResult(
        val displayName: String,
        val bootstrapPeers: List<String>,
        val inviteUri: String,
    )

    fun parseSidecarJson(text: String): ImportResult? {
        return try {
            val json = JSONObject(text)
            val invite = json.optString("invite_uri").trim()
            val peerId = json.optString("sharer_peer_id").trim()
            if (invite.isEmpty()) return null
            val guestName =
                json.optString("guest_display_name", "").trim().ifEmpty {
                    "guest-" + peerId.takeLast(8).ifEmpty { "mesh" }
                }
            val peers = buildList {
                add(invite)
                PeerInvite.toDialMultiaddr(
                    PeerInvite.parse(invite) ?: return@buildList,
                )?.let { add(it) }
            }.distinct()
            ImportResult(guestName, peers, invite)
        } catch (e: Exception) {
            Log.w(TAG, "sidecar parse failed: ${e.message}")
            null
        }
    }

    fun parseSidecar(bytes: ByteArray): ImportResult? =
        parseSidecarJson(bytes.toString(Charsets.UTF_8))

    fun parseInviteUri(bytes: ByteArray): String? =
        parseSidecar(bytes)?.inviteUri

    fun parseSidecar(input: InputStream): ImportResult? =
        parseSidecarJson(input.bufferedReader().readText())

    fun persistImport(context: Context, imported: ImportResult) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IMPORTED, true)
            .putString(KEY_DISPLAY_NAME, imported.displayName)
            .putString(KEY_BOOTSTRAP_PEERS, imported.bootstrapPeers.joinToString("\n"))
            .putString(KEY_INVITE, imported.inviteUri)
            .apply()
        context
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("display_name", imported.displayName)
            .putString("bootstrap_peers", imported.bootstrapPeers.joinToString("\n"))
            .apply()
    }

    /**
     * Scan Downloads for `mycelium-bootstrap.myc` (or legacy names), apply once, delete file.
     * Unreliable on API 29+ / targetSdk 34.
     */
    fun tryImportFromDownloads(context: Context): ImportResult? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IMPORTED, false)) {
            return null
        }
        val downloads =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.isDirectory) return null
        val candidate =
            downloads
                .listFiles()
                ?.filter { f ->
                    f.isFile &&
                        (
                            f.name.equals("mycelium-bootstrap.myc", ignoreCase = true) ||
                                f.name.startsWith("mycelium-bootstrap", ignoreCase = true)
                            )
                }
                ?.maxByOrNull { it.lastModified() }
                ?: return null

        return try {
            val imported = parseSidecarJson(candidate.readText()) ?: return null
            persistImport(context, imported)
            candidate.delete()
            Log.i(TAG, "imported offline bootstrap from ${candidate.name}")
            imported
        } catch (e: Exception) {
            Log.w(TAG, "bootstrap import failed: ${e.message}")
            null
        }
    }

    fun tryImportFromUri(context: Context, uri: Uri): ImportResult? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { parseSidecar(it) }
        } catch (e: Exception) {
            Log.w(TAG, "uri import failed: ${e.message}")
            null
        }
    }

    /**
     * Apply [imported] after a local wipe so onboarding cannot clobber join data.
     */
    fun applyAfterWipe(context: Context, imported: ImportResult, displayNameOverride: String?) {
        val named =
            if (displayNameOverride.isNullOrBlank()) {
                imported
            } else {
                imported.copy(displayName = displayNameOverride.trim())
            }
        persistImport(context, named)
        Log.i(TAG, "applied sidecar after wipe as ${named.displayName}")
    }

    fun applySideloadAutoOnboarding(context: Context): Boolean {
        val appPrefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
        if (appPrefs.getBoolean("onboarding_complete", false)) {
            return false
        }
        val imported = tryImportFromDownloads(context) ?: return false
        appPrefs.edit().putBoolean("onboarding_complete", true).apply()
        Log.i(
            TAG,
            "auto onboarding for sideload recipient as ${imported.displayName}",
        )
        return true
    }
}

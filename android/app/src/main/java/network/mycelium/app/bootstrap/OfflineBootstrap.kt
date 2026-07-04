// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Imports mesh bootstrap sidecar saved to Downloads during phone-to-phone APK install.
 * Enables automatic identity + mesh join without manual onboarding.
 */
object OfflineBootstrap {
    private const val TAG = "OfflineBootstrap"
    private const val PREFS = "offline_bootstrap"
    private const val KEY_IMPORTED = "imported_v1"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_BOOTSTRAP_PEERS = "bootstrap_peers"
    private const val KEY_INVITE = "invite_uri"

    data class ImportResult(
        val displayName: String,
        val bootstrapPeers: List<String>,
        val inviteUri: String,
    )

    /**
     * Scan Downloads for `mycelium-bootstrap.myc` (or legacy names), apply once, delete file.
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
            val json = JSONObject(candidate.readText())
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
            prefs
                .edit()
                .putBoolean(KEY_IMPORTED, true)
                .putString(KEY_DISPLAY_NAME, guestName)
                .putString(KEY_BOOTSTRAP_PEERS, peers.joinToString("\n"))
                .putString(KEY_INVITE, invite)
                .apply()
            context
                .getSharedPreferences("settings", Context.MODE_PRIVATE)
                .edit()
                .putString("display_name", guestName)
                .putString("bootstrap_peers", peers.joinToString("\n"))
                .apply()
            candidate.delete()
            Log.i(TAG, "imported offline bootstrap from ${candidate.name}")
            ImportResult(guestName, peers, invite)
        } catch (e: Exception) {
            Log.w(TAG, "bootstrap import failed: ${e.message}")
            null
        }
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

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import network.mycelium.bindings.mycelium
import java.io.File

/** Scorched-earth wipe of all Mycelium local data on this device (mirrors desktop `reset_local_data`). */
object LocalDataWiper {
    private const val TAG = "LocalDataWiper"
    private const val PREFS_SETTINGS = "settings"
    private const val PREFS_APP = "app"

    /**
     * Delete [dir] with retries. Returns true if [dir] does not exist afterwards
     * (including if it never existed). Does not claim remote mesh copies were deleted.
     */
    internal fun deleteDirectoryWithRetry(
        dir: File,
        attempts: Int = 5,
        sleepMs: Long = 400L,
        sleeper: (Long) -> Unit = { Thread.sleep(it) },
        deleteOp: (File) -> Unit = { it.deleteRecursively() },
    ): Boolean {
        if (!dir.exists()) return true
        repeat(attempts) { index ->
            try {
                deleteOp(dir)
                if (!dir.exists()) return true
            } catch (_: Exception) {
            }
            if (index < attempts - 1) {
                sleeper(sleepMs)
            }
        }
        return !dir.exists()
    }

    /**
     * Stop mesh node + service, delete DB/keys/contacts/chats/wallet, clear prefs and at-rest key.
     * Safe to call before first node start (onboarding) or after [mycelium.stopNode].
     *
     * @return true if the database directory does not exist after the delete procedure.
     */
    fun wipeAllLocalData(context: Context): Boolean {
        runCatching { mycelium.stopNode() }
            .onFailure { Log.w(TAG, "stopNode during wipe: ${it.message}") }
        context.stopService(Intent(context, MeshService::class.java))

        val dbDir = File(IdentityHelper.dbPath(context))
        val dbGone = deleteDirectoryWithRetry(dbDir)
        if (dbGone) {
            Log.i(TAG, "destroyed local data at ${dbDir.absolutePath}")
        } else {
            Log.e(TAG, "failed to delete ${dbDir.absolutePath}")
        }

        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit().clear().apply()
        StorageKeyHelper.clear(context)
        return dbGone
    }
}

/**
 * Panic wipe this device only. Returns false if the database directory still exists;
 * in that case the app is **not** relaunched.
 */
fun performPanicWipe(context: Context): Boolean {
    val wiped = LocalDataWiper.wipeAllLocalData(context)
    if (!wiped) {
        Log.e("LocalDataWiper", "panic wipe incomplete: database directory still exists")
        return false
    }
    val activity = context as? AppCompatActivity
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    activity?.finishAffinity()
    context.startActivity(intent)
    return true
}

fun performFreshIdentity(context: Context) {
    LocalDataWiper.wipeAllLocalData(context)
    val activity = context as? AppCompatActivity
    activity?.finishAffinity()
    context.startActivity(
        Intent(context, OnboardingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

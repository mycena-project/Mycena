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
     * Stop mesh node + service, delete DB/keys/contacts/chats/wallet, clear prefs and at-rest key.
     * Safe to call before first node start (onboarding) or after [mycelium.stopNode].
     */
    fun wipeAllLocalData(context: Context) {
        runCatching { mycelium.stopNode() }
            .onFailure { Log.w(TAG, "stopNode during wipe: ${it.message}") }
        context.stopService(Intent(context, MeshService::class.java))

        val dbDir = File(IdentityHelper.dbPath(context))
        if (dbDir.exists()) {
            var lastError: Exception? = null
            repeat(5) {
                try {
                    dbDir.deleteRecursively()
                    if (!dbDir.exists()) {
                        lastError = null
                        return@repeat
                    }
                } catch (e: Exception) {
                    lastError = e
                }
                Thread.sleep(400)
            }
            if (dbDir.exists()) {
                Log.e(TAG, "failed to delete ${dbDir.absolutePath}", lastError)
            } else {
                Log.i(TAG, "destroyed local data at ${dbDir.absolutePath}")
            }
        }

        context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences(PREFS_APP, Context.MODE_PRIVATE).edit().clear().apply()
        StorageKeyHelper.clear(context)
    }
}

fun performPanicWipe(context: Context) {
    LocalDataWiper.wipeAllLocalData(context)
    val activity = context as? AppCompatActivity
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
    activity?.finishAffinity()
    context.startActivity(intent)
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

// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.stability

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Safe OEM/crash diagnostics. Never log message bodies, keys, or invite URIs.
 */
object StabilityDiagnostics {
    const val TAG = "MyceliumStability"

    fun record(operation: String, throwable: Throwable, extra: String? = null) {
        val lifecycle = AppLifecycleTracker.stateLabel()
        val extraPart = extra?.let { " extra=${redact(it)}" }.orEmpty()
        Log.e(
            TAG,
            "op=$operation api=${Build.VERSION.SDK_INT} mfr=${Build.MANUFACTURER} " +
                "model=${Build.MODEL} lifecycle=$lifecycle " +
                "subtype=${throwable.javaClass.name}$extraPart",
        )
        Log.e(TAG, redact(Log.getStackTraceString(throwable)))
    }

    fun info(operation: String, detail: String) {
        Log.i(
            TAG,
            "op=$operation api=${Build.VERSION.SDK_INT} mfr=${Build.MANUFACTURER} " +
                "model=${Build.MODEL} lifecycle=${AppLifecycleTracker.stateLabel()} " +
                "detail=${redact(detail)}",
        )
    }

    fun redact(raw: String): String {
        var s = raw
        s = INVITE_URI.replace(s, "mycelium://[redacted]")
        s = LONG_HEX.replace(s, "[hex]")
        s = STORAGE_KEY.replace(s, "storageKey=[redacted]")
        return s.take(MAX_LEN)
    }

    fun installUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record("uncaught.${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    private const val MAX_LEN = 4000
    private val INVITE_URI = Regex("mycelium://\\S+")
    private val LONG_HEX = Regex("\\b[0-9a-fA-F]{32,}\\b")
    private val STORAGE_KEY = Regex("storageKeyHex=[^\\s,)]+")
}

object AppLifecycleTracker : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var started = 0

    @Volatile
    private var lastActivity: WeakReference<Activity>? = null

    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    fun stateLabel(): String =
        when {
            started > 0 -> "fg"
            else -> "bg"
        }

    override fun onActivityStarted(activity: Activity) {
        started++
        lastActivity = WeakReference(activity)
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}

package network.mycelium.app.miniapp

import android.content.Context

/** Runtime grants for sensitive mini-app bridge APIs (Cell C0). */
object MiniAppGrants {
    private const val PREFS = "miniapp_grants"

    const val IDENTITY = "Identity"
    const val CAMERA = "Camera"
    const val MESSAGING_BROADCAST = "MessagingBroadcast"
    const val PAYMENTS = "Payments"

    fun hasGrant(context: Context, appId: String, permission: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(key(appId, permission), false)
    }

    fun setGrant(context: Context, appId: String, permission: String, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(appId, permission), granted)
            .apply()
    }

    fun clearApp(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$appId:") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun recordInstallTrust(context: Context, appId: String, trustLevel: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("install_trust:$appId", trustLevel)
            .apply()
    }

    fun installTrust(context: Context, appId: String): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("install_trust:$appId", null)
    }

    fun hasSeenProvenance(context: Context, appId: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("provenance_seen:$appId", false)
    }

    fun markProvenanceSeen(context: Context, appId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("provenance_seen:$appId", true)
            .apply()
    }

    private fun key(appId: String, permission: String) = "$appId:$permission"
}

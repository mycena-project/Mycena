package network.mycelium.app.miniapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import network.mycelium.bindings.mycelium
import org.json.JSONObject

/**
 * Runs in the main process; serves [IMiniAppBridge] to the isolated mini-app WebView process.
 */
class MiniAppBridgeService : Service() {
    private val binder =
        object : IMiniAppBridge.Stub() {
            override fun bridgeCall(
                appId: String,
                method: String,
                argsJson: String,
            ): String = mycelium.miniappBridgeCall(appId, method, argsJson)

            override fun issueSession(appId: String): String =
                mycelium.miniappIssueBridgeSession(appId)

            override fun issueCapability(
                appId: String,
                permission: String,
                sessionToken: String,
            ): String = mycelium.miniappIssueCapability(appId, permission, sessionToken)

            override fun revokeSession(appId: String) {
                mycelium.miniappRevokeBridgeSession(appId)
            }

            override fun hasGrant(appId: String, permission: String): Boolean =
                MiniAppGrants.hasGrant(applicationContext, appId, permission)

            override fun setGrant(appId: String, permission: String, granted: Boolean) {
                MiniAppGrants.setGrant(applicationContext, appId, permission, granted)
            }

            override fun getPolicyJson(appId: String): String {
                val p = mycelium.miniappGetPolicy(appId)
                return JSONObject()
                    .put("app_id", p.appId)
                    .put("reputation_score", p.reputationScore.toInt())
                    .put("safe_mode_active", p.safeModeActive)
                    .put("safe_mode_forced", p.safeModeForced)
                    .put("safe_mode_suggested", p.safeModeSuggested)
                    .put("user_safe_mode", p.userSafeMode)
                    .put("revoked", p.revoked)
                    .put("strict_csp_eligible", p.strictCspEligible)
                    .toString()
            }

            override fun getAppDisplayName(appId: String): String =
                mycelium.listInstalledApps().find { it.id == appId }?.name ?: appId

            override fun getAppFile(appId: String, path: String): ByteArray? =
                mycelium.getAppFile(appId, path)

            override fun getAppEntry(appId: String): String? =
                mycelium.listInstalledApps().find { it.id == appId }?.entry
        }

    override fun onBind(intent: Intent?): IBinder = binder
}

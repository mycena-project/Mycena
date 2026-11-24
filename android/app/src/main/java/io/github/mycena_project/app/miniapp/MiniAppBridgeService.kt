// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.miniapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.github.mycena_project.bindings.mycena
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
            ): String = mycena.miniappBridgeCall(appId, method, argsJson)

            override fun issueSession(appId: String): String =
                mycena.miniappIssueBridgeSession(appId)

            override fun issueCapability(
                appId: String,
                permission: String,
                sessionToken: String,
            ): String = mycena.miniappIssueCapability(appId, permission, sessionToken)

            override fun revokeSession(appId: String) {
                mycena.miniappRevokeBridgeSession(appId)
            }

            override fun hasGrant(appId: String, permission: String): Boolean =
                MiniAppGrants.hasGrant(applicationContext, appId, permission)

            override fun setGrant(appId: String, permission: String, granted: Boolean) {
                MiniAppGrants.setGrant(applicationContext, appId, permission, granted)
            }

            override fun getPolicyJson(appId: String): String {
                val p = mycena.miniappGetPolicy(appId)
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
                mycena.listInstalledApps().find { it.id == appId }?.name ?: appId

            override fun getAppFile(appId: String, path: String): ByteArray? =
                mycena.getAppFile(appId, path)

            override fun getAppEntry(appId: String): String? {
                val app = mycena.listInstalledApps().find { it.id == appId } ?: return null
                val runtime = mycena.miniappGetRuntime(appId)
                if (runtime == "wasm") {
                    val ui = "ui/index.html"
                    if (app.entry != ui && mycena.getAppFile(appId, ui) != null) {
                        return ui
                    }
                    if (app.entry.endsWith(".wasm")) {
                        return null
                    }
                }
                return app.entry
            }

            override fun getAppRuntime(appId: String): String =
                mycena.miniappGetRuntime(appId)

            override fun invokeWasmRun(appId: String): Int =
                mycena.miniappInvokeWasmRun(appId)

            override fun getAppPaymentAddress(appId: String): String? =
                mycena.listInstalledApps().find { it.id == appId }?.paymentAddress

            override fun getWalletConfirmedMuon(): Long =
                mycena.walletBalance().settledMuon.toLong()

            override fun sendTransaction(toAddress: String, amountMuon: Long, memo: String?) {
                mycena.sendTransaction(toAddress, amountMuon.toULong(), memo?.ifBlank { null })
            }
        }

    override fun onBind(intent: Intent?): IBinder = binder
}

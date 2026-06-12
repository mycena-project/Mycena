// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.miniapp

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.ByteArrayInputStream
import java.util.UUID
import network.mycelium.app.MeshEvents
import network.mycelium.app.R
import org.json.JSONObject

/**
 * Mini-app host in isolated process `:miniapp` (H16). Bridge calls go to [MiniAppBridgeService].
 */
class MiniAppProcessActivity : ComponentActivity() {
    private var bridge: IMiniAppBridge? = null

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bridge = IMiniAppBridge.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bridge = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: run {
            finish()
            return
        }
        bindService(
            Intent(this, MiniAppBridgeService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        setContent {
            MiniAppProcessContent(
                appId = appId,
                bridgeProvider = { bridge },
                onBack = { finish() },
            )
        }
    }

    override fun onDestroy() {
        bridge?.revokeSession(intent.getStringExtra(EXTRA_APP_ID) ?: "")
        unbindService(connection)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_APP_ID = "app_id"

        fun intent(context: Context, appId: String): Intent =
            Intent(context, MiniAppProcessActivity::class.java).putExtra(EXTRA_APP_ID, appId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniAppProcessContent(
    appId: String,
    bridgeProvider: () -> IMiniAppBridge?,
    onBack: () -> Unit,
) {
    val bridge = bridgeProvider()
    var displayName by remember { mutableStateOf(appId) }
    val ctx = LocalContext.current
    var policyBanner by remember { mutableStateOf<String?>(null) }

    DisposableEffect(appId, bridge) {
        if (bridge != null) {
            displayName = bridge.getAppDisplayName(appId)
            val policy = JSONObject(bridge.getPolicyJson(appId))
            policyBanner =
                when {
                    policy.optBoolean("revoked") ->
                        ctx.getString(R.string.miniapp_revoked_banner)
                    policy.optBoolean("safe_mode_forced") ->
                        ctx.getString(R.string.miniapp_safe_mode_banner_forced)
                    policy.optBoolean("safe_mode_suggested") ->
                        ctx.getString(R.string.miniapp_safe_mode_banner_suggested)
                    else -> null
                }
        }
        onDispose { }
    }

    val scanLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            val cb = MiniAppBridgeCoordinator.pendingScanCallback
            MiniAppBridgeCoordinator.pendingScanCallback = null
            cb?.invoke(result.contents)
        }

    DisposableEffect(appId) {
        MiniAppBridgeCoordinator.requestQrScan = { cb ->
            MiniAppBridgeCoordinator.pendingScanCallback = cb
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setPrompt("Scan QR"),
            )
        }
        val bulletinReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    MiniAppBridgeCoordinator.onBulletinRefresh?.invoke()
                }
            }
        val chatReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val fromPeer = intent?.getStringExtra(MeshEvents.EXTRA_FROM_PEER) ?: return
                    val body = intent.getStringExtra(MeshEvents.EXTRA_BODY) ?: return
                    val prefix = "[app/$appId] "
                    if (!body.startsWith(prefix)) return
                    MiniAppBridgeCoordinator.onAppMessage?.invoke(
                        fromPeer,
                        body.removePrefix(prefix),
                    )
                }
            }
        ctx.registerReceiver(bulletinReceiver, IntentFilter(MeshEvents.BULLETIN), Context.RECEIVER_NOT_EXPORTED)
        ctx.registerReceiver(chatReceiver, IntentFilter(MeshEvents.CHAT), Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            MiniAppBridgeCoordinator.requestQrScan = null
            MiniAppBridgeCoordinator.pendingScanCallback = null
            MiniAppBridgeCoordinator.onBulletinRefresh = null
            MiniAppBridgeCoordinator.onAppMessage = null
            ctx.unregisterReceiver(bulletinReceiver)
            ctx.unregisterReceiver(chatReceiver)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.miniapp_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (bridge != null) {
                AndroidView(
                    factory = { ctx ->
                        createIsolatedMiniAppWebView(ctx, appId, bridge, displayName)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            policyBanner?.let { msg ->
                Text(
                    text = msg,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(Color(0xFFB45309))
                            .padding(8.dp),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createIsolatedMiniAppWebView(
    context: Context,
    appId: String,
    bridge: IMiniAppBridge,
    displayName: String,
): WebView {
    val scriptNonce = UUID.randomUUID().toString().replace("-", "")
    val sessionToken = bridge.issueSession(appId)
    val entry = bridge.getAppEntry(appId) ?: "index.html"
    val html =
        buildMiniappHtmlIsolated(
            context,
            appId,
            entry,
            scriptNonce,
            sessionToken,
            bridge,
        ) ?: "<html><body>Unknown app</body></html>"
    return WebView(context).apply {
        layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = false
        addJavascriptInterface(
            IsolatedMiniappJsBridge(Handler(Looper.getMainLooper()), this, appId, displayName, bridge),
            "MyceliumMiniapp",
        )
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    return !(url.startsWith("about:") || url.startsWith("data:") || url.startsWith(MINIAPP_BASE))
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url.toString()
                    if (!url.startsWith(MINIAPP_BASE)) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    val path =
                        Uri.parse(url).path?.trimStart('/')?.let { Uri.decode(it) } ?: ""
                    val data =
                        bridge.getAppFile(appId, path)
                            ?: return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                    val mime = miniappMimeForPath(path)
                    return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(data))
                }
            }
        loadDataWithBaseURL(MINIAPP_BASE, html, "text/html", "UTF-8", null)
        val handler = Handler(Looper.getMainLooper())
        MiniAppBridgeCoordinator.onBulletinRefresh = {
            handler.post {
                evaluateJavascript(
                    "if(window.__mycelium_on_bulletin_refresh){window.__mycelium_on_bulletin_refresh();}",
                    null,
                )
            }
        }
        MiniAppBridgeCoordinator.onAppMessage = { fromPeer, payload ->
            handler.post {
                val msgJson =
                    JSONObject()
                        .put("from_peer", fromPeer)
                        .put("payload", payload)
                        .toString()
                evaluateJavascript(
                    "if(window.__mycelium_dispatch_message){window.__mycelium_dispatch_message(${JSONObject.quote(msgJson)});}",
                    null,
                )
            }
        }
    }
}

private const val MINIAPP_BASE = "https://app.mycelium.local/"

private fun miniappMimeForPath(path: String): String {
    val lower = path.lowercase()
    return when {
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        lower.endsWith(".js") -> "application/javascript"
        lower.endsWith(".css") -> "text/css"
        else -> "application/octet-stream"
    }
}

private fun buildMiniappHtmlIsolated(
    context: Context,
    appId: String,
    entry: String,
    scriptNonce: String,
    sessionToken: String,
    bridge: IMiniAppBridge,
): String? {
    val bytes = bridge.getAppFile(appId, entry) ?: return null
    val bodyHtml = String(bytes, Charsets.UTF_8)
    val bridgeJs =
        try {
            context.assets.open("miniapp/bridge_api.js").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        }

    val networkBlockScript =
        """
        <script nonce="$scriptNonce">
        window.fetch = function() { return Promise.reject(new Error('Network blocked')); };
        window.XMLHttpRequest = function() { throw new Error('Network blocked'); };
        window.WebSocket = function() { throw new Error('WebSocket blocked'); };
        if (navigator.sendBeacon) { navigator.sendBeacon = function() { return false; }; }
        </script>
        """.trimIndent()

    val initScript =
        """
        <script nonce="$scriptNonce">
        window.__mycelium_session = ${JSONObject.quote(sessionToken)};
        window.__mycelium_native_call = function(json) { MyceliumMiniapp.onNativeCall(json); };
        </script>
        """.trimIndent()

    val bridgeScript =
        """
        <script nonce="$scriptNonce">
        $bridgeJs
        </script>
        """.trimIndent()

    val appHtmlWithNonces = MiniAppCsp.addNonceToScripts(bodyHtml, scriptNonce)
    val fullHtml = "$networkBlockScript\n$initScript\n$bridgeScript\n$appHtmlWithNonces"
    return MiniAppCsp.injectIntoHtml(fullHtml, scriptNonce)
}

private class IsolatedMiniappJsBridge(
    private val handler: Handler,
    private val webView: WebView,
    private val appId: String,
    private val appDisplayName: String,
    private val bridge: IMiniAppBridge,
) {
    private fun hostedMessage(body: String): String {
        val notice = webView.context.getString(R.string.miniapp_hosted_dialog_notice)
        return "$notice\n\n$body"
    }

    private fun capabilityPermissionFor(method: String): String? =
        when (method) {
            "identity.get" -> MiniAppGrants.IDENTITY
            "messaging.send" -> "Messaging"
            "messaging.broadcast" -> MiniAppGrants.MESSAGING_BROADCAST
            "payment.request", "payment.create_qr", "payment.get_balance" -> MiniAppGrants.PAYMENTS
            "util.scan_qr" -> MiniAppGrants.CAMERA
            "bulletin.post" -> "BulletinWrite"
            "peers.nearby" -> "PeerDiscovery"
            "proximity.start" -> "PeerDiscovery"
            "proximity.nearby" -> "PeerDiscovery"
            "proximity.connect" -> "PeerDiscovery"
            "proximity.messages" -> "Messaging"
            "proximity.send_message" -> "Messaging"
            else -> null
        }

    private fun withCapability(method: String, args: JSONObject): JSONObject {
        val perm = capabilityPermissionFor(method) ?: return args
        val session = args.optString("_session", null) ?: return args
        return try {
            val cap = bridge.issueCapability(appId, perm, session)
            JSONObject(args.toString()).apply { put("_cap", cap) }
        } catch (_: Exception) {
            args
        }
    }

    @JavascriptInterface
    fun onNativeCall(json: String) {
        handler.post {
            var callId = -1
            try {
                val o = JSONObject(json)
                callId = o.getInt("id")
                val method = o.getString("method")
                val args = o.optJSONObject("args") ?: JSONObject()
                dispatchBridgeCall(callId, method, args)
            } catch (e: Exception) {
                resolveError(callId, e.message ?: "error")
            }
        }
    }

    private fun dispatchBridgeCall(callId: Int, method: String, args: JSONObject) {
        val ctx = webView.context
        when (method) {
            "identity.get" -> {
                if (!bridge.hasGrant(appId, MiniAppGrants.IDENTITY)) {
                    promptGrant(
                        title = ctx.getString(R.string.miniapp_grant_identity_title),
                        message = ctx.getString(R.string.miniapp_grant_identity_message, appId),
                        permission = MiniAppGrants.IDENTITY,
                        onGranted = { dispatchBridgeCall(callId, method, args) },
                        onDenied = { resolveError(callId, "permission denied") },
                    )
                    return
                }
            }
            "util.scan_qr" -> {
                if (!bridge.hasGrant(appId, MiniAppGrants.CAMERA)) {
                    promptGrant(
                        title = ctx.getString(R.string.miniapp_grant_camera_title),
                        message = ctx.getString(R.string.miniapp_grant_camera_message),
                        permission = MiniAppGrants.CAMERA,
                        onGranted = { dispatchBridgeCall(callId, method, args) },
                        onDenied = { resolveError(callId, "permission denied") },
                    )
                    return
                }
                val scanner = MiniAppBridgeCoordinator.requestQrScan
                if (scanner == null) {
                    resolveError(callId, "QR scanner unavailable")
                    return
                }
                scanner { raw ->
                    if (raw == null) resolveError(callId, "scan cancelled")
                    else resolveOk(callId, raw)
                }
                return
            }
            "messaging.broadcast", "payment.request", "payment.create_qr", "payment.get_balance" -> {
                val perm =
                    when (method) {
                        "messaging.broadcast" -> MiniAppGrants.MESSAGING_BROADCAST
                        else -> MiniAppGrants.PAYMENTS
                    }
                if (!bridge.hasGrant(appId, perm)) {
                    val (title, message) =
                        when (perm) {
                            MiniAppGrants.MESSAGING_BROADCAST ->
                                ctx.getString(R.string.miniapp_grant_broadcast_title) to
                                    ctx.getString(R.string.miniapp_grant_broadcast_message)
                            else ->
                                ctx.getString(R.string.miniapp_grant_payments_title) to
                                    ctx.getString(R.string.miniapp_grant_payments_message)
                        }
                    promptGrant(
                        title = title,
                        message = message,
                        permission = perm,
                        onGranted = { dispatchBridgeCall(callId, method, args) },
                        onDenied = { resolveError(callId, "permission denied") },
                    )
                    return
                }
            }
        }
        try {
            val bridgedArgs = withCapability(method, args)
            val resultJson = bridge.bridgeCall(appId, method, bridgedArgs.toString())
            val result = JSONObject(resultJson)
            if (result.has("error")) {
                throw Exception(result.getString("error"))
            }
            when (result.optString("_bridge_action")) {
                "show_alert" -> {
                    AlertDialog.Builder(ctx)
                        .setTitle(appDisplayName)
                        .setMessage(hostedMessage(result.optString("message")))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    resolveOk(callId, "ok")
                }
                "show_confirm" -> {
                    AlertDialog.Builder(ctx)
                        .setTitle(appDisplayName)
                        .setMessage(hostedMessage(result.optString("message")))
                        .setPositiveButton(android.R.string.ok) { _, _ -> resolveOk(callId, true) }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            resolveError(callId, "cancelled")
                        }
                        .show()
                }
                "payment_confirmation_required" -> {
                    showMiniAppPaymentConfirmation(
                        ctx = ctx,
                        appId = appId,
                        appDisplayName = appDisplayName,
                        result = result,
                        host =
                            object : MiniAppPaymentHost {
                                override fun paymentAddressFor(appId: String): String? =
                                    bridge.getAppPaymentAddress(appId)

                                override fun walletConfirmedMuon(): Long =
                                    bridge.getWalletConfirmedMuon()

                                override fun sendPayment(
                                    toAddress: String,
                                    amountMuon: Long,
                                    memo: String?,
                                ) {
                                    bridge.sendTransaction(toAddress, amountMuon, memo)
                                }
                            },
                        resolveOk = { resolveOk(callId, it) },
                        resolveError = { resolveError(callId, it) },
                    )
                }
                "render_qr" -> {
                    val uri = result.optString("uri")
                    AlertDialog.Builder(ctx)
                        .setTitle(ctx.getString(R.string.miniapp_payment_qr_title))
                        .setMessage(uri)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    resolveOk(callId, mapOf("uri" to uri))
                }
                else -> {
                    val expr = "JSON.parse(${JSONObject.quote(resultJson)})"
                    webView.evaluateJavascript(
                        "window.__mycelium_resolve($callId, $expr, null)",
                        null,
                    )
                }
            }
        } catch (e: Exception) {
            resolveError(callId, e.message ?: "error")
        }
    }

    private fun promptGrant(
        title: String,
        message: String,
        permission: String,
        onGranted: () -> Unit,
        onDenied: () -> Unit,
    ) {
        AlertDialog.Builder(webView.context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                bridge.setGrant(appId, permission, true)
                onGranted()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onDenied() }
            .show()
    }

    private fun resolveOk(callId: Int, result: Any) {
        val lit =
            when (result) {
                is String -> JSONObject.quote(result)
                is Boolean -> result.toString()
                is Map<*, *> ->
                    @Suppress("UNCHECKED_CAST")
                    JSONObject(result as Map<String, Any?>).toString()
                else -> JSONObject.quote(result.toString())
            }
        webView.evaluateJavascript("window.__mycelium_resolve($callId, $lit, null)", null)
    }

    private fun resolveError(callId: Int, message: String) {
        if (callId < 0) return
        webView.evaluateJavascript(
            "window.__mycelium_resolve($callId, null, ${JSONObject.quote(message)})",
            null,
        )
    }
}

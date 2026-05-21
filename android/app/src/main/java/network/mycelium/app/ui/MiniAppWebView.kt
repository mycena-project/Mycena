package network.mycelium.app.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import java.io.ByteArrayInputStream
import java.util.UUID
import network.mycelium.app.R
import network.mycelium.app.miniapp.MiniAppBridgeCoordinator
import network.mycelium.app.miniapp.MiniAppGrants
import network.mycelium.bindings.mycelium
import org.json.JSONObject

private const val MINIAPP_BASE = "https://app.mycelium.local/"

private const val NETWORK_BLOCK_JS = """
window.fetch = function() {
    return Promise.reject(new Error(
        'Network access not allowed in Mycelium mini-apps. Use window.mycelium APIs.'
    ));
};
window.XMLHttpRequest = function() {
    throw new Error('Network access not allowed in Mycelium mini-apps.');
};
window.WebSocket = function() {
    throw new Error('WebSocket not allowed in Mycelium mini-apps.');
};
if (navigator.sendBeacon) {
    navigator.sendBeacon = function() { return false; };
};
"""

private fun mimeForPath(path: String): String {
    val lower = path.lowercase()
    return when {
        lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
        lower.endsWith(".js") -> "application/javascript"
        lower.endsWith(".css") -> "text/css"
        lower.endsWith(".json") -> "application/json"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".svg") -> "image/svg+xml"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".woff2") -> "font/woff2"
        lower.endsWith(".woff") -> "font/woff"
        else -> "application/octet-stream"
    }
}

private fun injectCsp(html: String, scriptNonce: String): String {
    val csp =
        """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$scriptNonce' 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; wasm-unsafe-eval 'none';">"""
    return if (html.contains("<head>", ignoreCase = true)) {
        html.replaceFirst("<head>", "<head>\n$csp\n", ignoreCase = true)
    } else {
        "$csp\n$html"
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun buildMiniappHtml(
    context: Context,
    appId: String,
    scriptNonce: String,
    sessionToken: String,
): String? {
    val info = mycelium.listInstalledApps().find { it.id == appId } ?: return null
    val bytes = mycelium.getAppFile(appId, info.entry) ?: return null
    val body = String(bytes, Charsets.UTF_8)
    val bridge =
        try {
            context.assets.open("miniapp/bridge_api.js").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return "<html><body><p>Missing miniapp/bridge_api.js in assets.</p></body></html>"
        }
    val shim =
        """
        <script nonce="$scriptNonce">
        window.__mycelium_session = ${JSONObject.quote(sessionToken)};
        window.__mycelium_native_call = function (json) { MyceliumMiniapp.onNativeCall(json); };
        </script><script nonce="$scriptNonce">
        """.trimIndent()
    return injectCsp("$shim$bridge</script>\n$body", scriptNonce)
}

class MiniappJsBridge(
    private val handler: Handler,
    private val webView: WebView,
    private val appId: String,
    private val appDisplayName: String,
) {
    private fun hostedMessage(body: String): String {
        val ctx = webView.context
        val notice = ctx.getString(R.string.miniapp_hosted_dialog_notice)
        return "$notice\n\n$body"
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
                if (!MiniAppGrants.hasGrant(ctx, appId, MiniAppGrants.IDENTITY)) {
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
                if (!MiniAppGrants.hasGrant(ctx, appId, MiniAppGrants.CAMERA)) {
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
                    if (raw == null) {
                        resolveError(callId, "scan cancelled")
                    } else {
                        resolveOk(callId, raw)
                    }
                }
                return
            }
            "messaging.broadcast" -> {
                if (!MiniAppGrants.hasGrant(ctx, appId, MiniAppGrants.MESSAGING_BROADCAST)) {
                    promptGrant(
                        title = ctx.getString(R.string.miniapp_grant_broadcast_title),
                        message = ctx.getString(R.string.miniapp_grant_broadcast_message),
                        permission = MiniAppGrants.MESSAGING_BROADCAST,
                        onGranted = { dispatchBridgeCall(callId, method, args) },
                        onDenied = { resolveError(callId, "permission denied") },
                    )
                    return
                }
            }
            "payment.request", "payment.create_qr", "payment.get_balance" -> {
                if (!MiniAppGrants.hasGrant(ctx, appId, MiniAppGrants.PAYMENTS)) {
                    promptGrant(
                        title = ctx.getString(R.string.miniapp_grant_payments_title),
                        message = ctx.getString(R.string.miniapp_grant_payments_message),
                        permission = MiniAppGrants.PAYMENTS,
                        onGranted = { dispatchBridgeCall(callId, method, args) },
                        onDenied = { resolveError(callId, "permission denied") },
                    )
                    return
                }
            }
        }

        try {
            val resultJson = mycelium.miniappBridgeCall(appId, method, args.toString())
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
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            resolveOk(callId, true)
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            resolveError(callId, "cancelled")
                        }
                        .show()
                }
                "payment_confirmation_required" -> {
                    val amount = result.optLong("amount_muon")
                    val memo = result.optString("memo", "")
                    AlertDialog.Builder(ctx)
                        .setTitle(ctx.getString(R.string.miniapp_payment_confirm_title))
                        .setMessage(
                            ctx.getString(
                                R.string.miniapp_payment_confirm_message,
                                amount,
                                memo,
                            ),
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            resolveOk(callId, "acknowledged")
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            resolveError(callId, "cancelled")
                        }
                        .show()
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
                "open_qr_scanner" -> {
                    resolveError(callId, "use mycelium.scanQr()")
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
                MiniAppGrants.setGrant(webView.context, appId, permission, true)
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
        webView.evaluateJavascript(
            "window.__mycelium_resolve($callId, $lit, null)",
            null,
        )
    }

    private fun resolveError(callId: Int, message: String) {
        if (callId < 0) return
        val errLit = JSONObject.quote(message)
        webView.evaluateJavascript(
            "window.__mycelium_resolve($callId, null, $errLit)",
            null,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createMiniAppWebView(context: Context, appId: String): WebView {
    val scriptNonce = UUID.randomUUID().toString().replace("-", "")
    val sessionToken = mycelium.miniappIssueBridgeSession(appId)
    val appDisplayName =
        mycelium.listInstalledApps().find { it.id == appId }?.name ?: appId
    return WebView(context).apply {
        layoutParams =
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            databaseEnabled = false
            mediaPlaybackRequiresUserGesture = true
        }
        val handler = Handler(Looper.getMainLooper())
        addJavascriptInterface(
            MiniappJsBridge(handler, this, appId, appDisplayName),
            "MyceliumMiniapp",
        )
        webViewClient =
            object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    evaluateJavascript(NETWORK_BLOCK_JS, null)
                }

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
                    if (
                        !url.startsWith("data:") &&
                        !url.startsWith("about:") &&
                        !url.startsWith(MINIAPP_BASE)
                    ) {
                        return WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream("blocked".toByteArray()),
                        )
                    }
                    if (!url.startsWith(MINIAPP_BASE)) {
                        return super.shouldInterceptRequest(view, request)
                    }
                    val path =
                        Uri.parse(url).path?.trimStart('/')?.let { Uri.decode(it) } ?: ""
                    val data =
                        mycelium.getAppFile(appId, path)
                            ?: return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0)),
                            )
                    val mime = mimeForPath(path)
                    return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(data))
                }
            }
        val html = buildMiniappHtml(context, appId, scriptNonce, sessionToken)
            ?: "<html><body>Unknown app</body></html>"
        loadDataWithBaseURL(MINIAPP_BASE, html, "text/html", "UTF-8", null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppRunnerScreen(appId: String, navController: NavController) {
    val context = LocalContext.current
    var showProvenance by remember {
        mutableStateOf(!MiniAppGrants.hasSeenProvenance(context, appId))
    }
    val appInfo = mycelium.listInstalledApps().find { it.id == appId }
    val installTrust = MiniAppGrants.installTrust(context, appId)

    val scanLauncher =
        rememberLauncherForActivityResult(
            com.journeyapps.barcodescanner.ScanContract(),
        ) { result ->
            val cb = MiniAppBridgeCoordinator.pendingScanCallback
            MiniAppBridgeCoordinator.pendingScanCallback = null
            cb?.invoke(result.contents)
        }

    DisposableEffect(appId) {
        MiniAppBridgeCoordinator.requestQrScan = { cb ->
            MiniAppBridgeCoordinator.pendingScanCallback = cb
            scanLauncher.launch(
                com.journeyapps.barcodescanner.ScanOptions()
                    .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    .setBeepEnabled(false)
                    .setPrompt("Scan QR"),
            )
        }
        onDispose {
            mycelium.miniappRevokeBridgeSession(appId)
            MiniAppBridgeCoordinator.requestQrScan = null
            MiniAppBridgeCoordinator.pendingScanCallback = null
        }
    }

    if (showProvenance && appInfo != null) {
        MiniAppLaunchProvenanceDialog(
            app = appInfo,
            installTrust = installTrust,
            onContinue = {
                MiniAppGrants.markProvenanceSeen(context, appId)
                showProvenance = false
            },
            onDismiss = { navController.popBackStack() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(appInfo?.name ?: stringResource(R.string.miniapp_store_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.miniapp_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (!showProvenance) {
            key(appId) {
                AndroidView(
                    factory = { ctx -> createMiniAppWebView(ctx, appId) },
                    modifier = Modifier.fillMaxSize().padding(padding),
                    update = {},
                )
            }
        }
    }
}

package network.mycelium.app.ui

import android.annotation.SuppressLint
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
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import java.io.ByteArrayInputStream
import network.mycelium.app.R
import network.mycelium.bindings.mycelium
import org.json.JSONObject

private const val MINIAPP_BASE = "https://miniapp.local/"

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

@SuppressLint("SetJavaScriptEnabled")
private fun buildMiniappHtml(context: Context, appId: String): String? {
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
        <script>
        window.__mycelium_native_call = function (json) { MyceliumMiniapp.onNativeCall(json); };
        </script><script>
        """.trimIndent()
    return "$shim$bridge</script>\n$body"
}

class MiniappJsBridge(
    private val handler: Handler,
    private val webView: WebView,
    private val appId: String,
) {
    @JavascriptInterface
    fun onNativeCall(json: String) {
        handler.post {
            var callId = -1
            try {
                val o = JSONObject(json)
                callId = o.getInt("id")
                val method = o.getString("method")
                val args = o.optJSONObject("args") ?: JSONObject()
                val resultJson = mycelium.miniappBridgeCall(appId, method, args.toString())
                val result = JSONObject(resultJson)
                if (result.has("error")) {
                    throw Exception(result.getString("error"))
                }
                when (result.optString("_bridge_action")) {
                    "show_alert" -> {
                        Toast.makeText(
                            webView.context,
                            result.optString("message"),
                            Toast.LENGTH_SHORT,
                        ).show()
                        webView.evaluateJavascript(
                            "window.__mycelium_resolve($callId, ${JSONObject.quote("ok")}, null)",
                            null,
                        )
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
                val msg = e.message ?: "error"
                val errLit = JSONObject.quote(msg)
                if (callId >= 0) {
                    webView.evaluateJavascript(
                        "window.__mycelium_resolve($callId, null, $errLit)",
                        null,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniAppRunnerScreen(appId: String, navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.miniapp_store_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.miniapp_back))
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    val handler = Handler(Looper.getMainLooper())
                    addJavascriptInterface(MiniappJsBridge(handler, this, appId), "MyceliumMiniapp")
                    webViewClient =
                        object : WebViewClient() {
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
                                val data = mycelium.getAppFile(appId, path) ?: return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    ByteArrayInputStream(ByteArray(0)),
                                )
                                val mime = mimeForPath(path)
                                return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(data))
                            }
                        }
                    val html = buildMiniappHtml(ctx, appId) ?: "<html><body>Unknown app</body></html>"
                    loadDataWithBaseURL(MINIAPP_BASE, html, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
            update = {},
        )
    }
}

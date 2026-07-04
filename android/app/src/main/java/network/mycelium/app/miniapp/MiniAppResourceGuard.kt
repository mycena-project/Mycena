// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.miniapp

import android.content.ComponentCallbacks2
import android.webkit.WebView
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Platform resource limits for the isolated mini-app WebView process.
 *
 * Matches Rust constants in `mycelium-miniapp/src/bridge_limits.rs`.
 */
object MiniAppResourceGuard {
    /** Single native bridge call wall timeout (IPC + Rust handler). */
    const val BRIDGE_CALL_TIMEOUT_MS: Long = 5_000

    private val bridgeExecutor = Executors.newCachedThreadPool()

    fun bridgeCallWithTimeout(
        bridge: IMiniAppBridge,
        appId: String,
        method: String,
        argsJson: String,
    ): String {
        val task =
            Callable {
                bridge.bridgeCall(appId, method, argsJson)
            }
        val future = bridgeExecutor.submit(task)
        return try {
            future.get(BRIDGE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            throw BridgeTimeoutException(
                "bridge call timed out (${BRIDGE_CALL_TIMEOUT_MS}ms)",
            )
        }
    }

    /**
     * React to system memory pressure — pause or terminate the mini-app host.
     *
     * Called from [ComponentCallbacks2.onTrimMemory] in the isolated `:miniapp` process.
     */
    fun onTrimMemory(
        level: Int,
        webView: WebView?,
        finishHost: () -> Unit,
    ) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                webView?.destroy()
                finishHost()
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                webView?.onPause()
                webView?.pauseTimers()
                webView?.clearCache(true)
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                webView?.clearCache(true)
            }
        }
    }

    fun onLowMemory(
        webView: WebView?,
        finishHost: () -> Unit,
    ) {
        webView?.destroy()
        finishHost()
    }
}

class BridgeTimeoutException(message: String) : Exception(message)

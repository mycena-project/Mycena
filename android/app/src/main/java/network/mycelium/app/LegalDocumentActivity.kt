// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class LegalDocumentActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val asset = intent.getStringExtra(EXTRA_ASSET) ?: "privacy_policy.html"
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = false
        webView.loadUrl("file:///android_asset/$asset")
    }

    companion object {
        const val EXTRA_ASSET = "asset"
    }
}

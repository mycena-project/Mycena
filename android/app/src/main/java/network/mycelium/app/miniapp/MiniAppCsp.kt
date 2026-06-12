// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.miniapp

/** CSP meta tag for mini-app WebView hosts (H05 / P3). */
object MiniAppCsp {
    fun metaTag(scriptNonce: String): String {
        return """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'nonce-$scriptNonce'; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; wasm-unsafe-eval 'none';">"""
    }

    fun injectIntoHtml(html: String, scriptNonce: String): String {
        val csp = metaTag(scriptNonce)
        return if (html.contains("<head>", ignoreCase = true)) {
            html.replaceFirst("<head>", "<head>\n$csp\n", ignoreCase = true)
        } else {
            "$csp\n$html"
        }
    }

    /**
     * Adds [nonce] to all `<script>` tags that do not already have one.
     */
    fun addNonceToScripts(html: String, nonce: String): String {
        return html.replace(
            Regex("<script(\\s[^>]*)?>", RegexOption.IGNORE_CASE),
        ) { match ->
            val attrs = match.groupValues[1]
            if ("nonce=" in attrs) {
                match.value
            } else {
                "<script nonce=\"$nonce\"$attrs>"
            }
        }
    }
}

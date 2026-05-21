package network.mycelium.app.miniapp

/** CSP meta tag for mini-app WebView hosts (H05 / P3). */
object MiniAppCsp {
    fun metaTag(scriptNonce: String, allowInlineScripts: Boolean): String {
        val scriptSrc =
            if (allowInlineScripts) {
                "'nonce-$scriptNonce' 'unsafe-inline'"
            } else {
                "'nonce-$scriptNonce'"
            }
        return """<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src $scriptSrc; style-src 'unsafe-inline'; img-src data:; connect-src 'none'; form-action 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; wasm-unsafe-eval 'none';">"""
    }

    fun injectIntoHtml(html: String, scriptNonce: String, allowInlineScripts: Boolean): String {
        val csp = metaTag(scriptNonce, allowInlineScripts)
        return if (html.contains("<head>", ignoreCase = true)) {
            html.replaceFirst("<head>", "<head>\n$csp\n", ignoreCase = true)
        } else {
            "$csp\n$html"
        }
    }
}

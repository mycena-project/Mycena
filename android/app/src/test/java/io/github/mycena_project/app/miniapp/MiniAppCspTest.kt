// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.miniapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniAppCspTest {
    @Test
    fun metaTag_doesNotAllowUnsafeInlineScripts() {
        val tag = MiniAppCsp.metaTag("abc123nonce")
        assertTrue(tag.contains("script-src 'nonce-abc123nonce'"))
        assertFalse(Regex("script-src[^;]*'unsafe-inline'").containsMatchIn(tag))
    }

    @Test
    fun addNonceToScripts_tagsScriptsWithoutNonce() {
        val html = "<script>alert(1)</script><script nonce=\"x\">ok</script>"
        val out = MiniAppCsp.addNonceToScripts(html, "n1")
        assertTrue(out.contains("<script nonce=\"n1\">"))
        assertTrue(out.contains("<script nonce=\"x\">"))
    }
}

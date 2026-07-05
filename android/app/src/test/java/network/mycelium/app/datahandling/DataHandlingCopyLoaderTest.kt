// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.datahandling

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DataHandlingCopyLoaderTest {
    @Test
    fun parsesGermanCopy() {
        val raw =
            """
            {
              "locale": "de",
              "title": "Wie Mycelium mit deinen Daten umgeht",
              "intro": "Kurz.",
              "sections": [
                {
                  "heading": "Was auf deinem Gerät bleibt",
                  "bullets": ["Nachrichten"]
                },
                {
                  "heading": "Wer die App betreibt",
                  "paragraphs": ["the maintainer."]
                }
              ],
              "footnote": "Hinweis.",
              "technical_policy_link_label": "Technische Datenschutzerklärung",
              "close_button": "Schließen"
            }
            """.trimIndent()
        val copy = DataHandlingCopyLoader.parseFromJson(raw)
        assertEquals("de", copy.locale)
        assertEquals("the maintainer.", copy.sections[1].paragraphs.first())
        assertEquals(1, copy.sections[0].bullets.size)
    }
}

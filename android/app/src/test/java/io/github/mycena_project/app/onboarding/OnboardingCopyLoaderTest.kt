// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class OnboardingCopyLoaderTest {

    private fun fixture(name: String): String {
        val dir =
            System.getProperty("onboarding.copy.dir")?.let(::File)
                ?: error("missing onboarding.copy.dir system property")
        return dir.resolve(name).readText(StandardCharsets.UTF_8)
    }

    @Test
    fun parseGermanCopyFixture() {
        val copy = OnboardingCopyLoader.parseFromJson(fixture("de.json"))

        assertEquals("de", copy.locale)
        assertTrue(copy.positioningVariants.containsKey("a"))
        assertEquals(3, copy.whyBullets.size)
        assertEquals(4, copy.faqItems.size)
        assertEquals("Verfolgt mich das?", copy.faqItems.first().question)
    }

    @Test
    fun parseEnglishCopyFixture() {
        val copy = OnboardingCopyLoader.parseFromJson(fixture("en.json"))

        assertEquals("en", copy.locale)
        assertEquals("Why this app?", copy.whyTitle)
        assertEquals("a", copy.positioningDefaultVariant)
    }
}

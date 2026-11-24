// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import io.github.mycena_project.app.stability.StabilityDiagnostics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleScanPolicyTest {
    @Test
    fun feature_unsupported_falls_back() {
        assertTrue(BleScanPolicy.shouldFallbackToAllMatches(4))
        assertTrue(
            BleScanPolicy.shouldFallbackToAllMatches(
                android.bluetooth.le.ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED,
            ),
        )
        assertFalse(BleScanPolicy.shouldFallbackToAllMatches(1))
    }

    @Test
    fun portable_type_is_all_matches() {
        assertEquals(
            android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
            BleScanPolicy.portableCallbackType(),
        )
    }
}

class StabilityRedactTest {
    @Test
    fun redacts_invite_and_long_hex_and_storage_key() {
        val raw =
            "invite=mycena://invite/v3#secretstuff extra=aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899 storageKeyHex=deadbeef"
        val out = StabilityDiagnostics.redact(raw)
        assertFalse(out.contains("mycena://invite"))
        assertTrue(out.contains("mycena://[redacted]"))
        assertTrue(out.contains("[hex]"))
        assertFalse(out.contains("deadbeef"))
        assertTrue(out.contains("storageKey=[redacted]"))
    }

    @Test
    fun redacts_storage_key_in_stack_like_text() {
        val raw = "at foo(storageKeyHex=aabbccddeeff0011, other)"
        val out = StabilityDiagnostics.redact(raw)
        assertFalse(out.contains("aabbccddeeff0011"))
        assertTrue(out.contains("storageKey=[redacted]"))
    }

    @Test
    fun does_not_need_message_bodies_in_api() {
        // Callers must pass operation names only — this test locks the redact contract.
        val chat = StabilityDiagnostics.redact("hello from a friend")
        assertEquals("hello from a friend", chat)
    }
}

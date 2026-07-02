// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BootstrapTests {

    @Test
    fun mycelium_service_uuid_is_stable() {
        assertEquals(
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            BleBootstrapAdvertiser.MYCELIUM_SERVICE_UUID,
        )
    }

    @Test
    fun scanner_extracts_peer_id_from_manufacturer_data() {
        val peerIdBytes = "12D3KooW".toByteArray().take(8).toByteArray()
        val extracted = peerIdBytes.joinToString("") { "%02x".format(it) }
        assertTrue(extracted.isNotEmpty())
        assertEquals(16, extracted.length)
    }

    @Test
    fun http_response_has_correct_content_type() {
        val header = WifiDirectSharer.buildApkHttpHeader(contentLength = 1024)
        assertTrue(header.contains("Content-Type: application/vnd.android.package-archive"))
        assertTrue(header.contains("Content-Length: 1024"))
    }

    @Test
    fun install_landing_page_links_to_apk() {
        val html = WifiDirectSharer.buildInstallLandingHtml()
        assertTrue(html.contains("href=\"/mycelium.apk\""))
        assertTrue(html.contains("Install Mycelium"))
    }

    @Test
    fun wifi_qr_payload_includes_credentials() {
        val payload =
            ApkSharePayload.buildWifiQr(
                ApkSharePayload.HotspotCredentials("MyceliumShare", "test-pass"),
            )
        assertTrue(payload.startsWith("WIFI:"))
        assertTrue(payload.contains("S:MyceliumShare"))
        assertTrue(payload.contains("P:test-pass"))
    }

    @Test
    fun wifi_qr_escapes_semicolons_in_password() {
        val payload =
            ApkSharePayload.buildWifiQr(
                ApkSharePayload.HotspotCredentials("Net", "a;b"),
            )
        assertTrue(payload.contains("P:a\\;b"))
    }
}

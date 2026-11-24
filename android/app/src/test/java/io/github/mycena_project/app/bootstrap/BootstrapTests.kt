// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BootstrapTests {

    @Test
    fun mycena_service_uuid_is_stable() {
        assertEquals(
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            BleMeshGatt.SERVICE_UUID,
        )
        assertEquals(
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
            BleMeshGatt.INVITE_CHAR_UUID,
        )
    }

    @Test
    fun peer_invite_account_id_from_v3() {
        val raw =
            "mycena://invite/v3#acc123|12D3KooWpeer|" +
                "a".repeat(64) + "|1|sig"
        assertEquals("acc123", PeerInvite.accountIdFrom(raw))
        assertEquals("12D3KooWpeer", PeerInvite.peerIdFrom(raw))
    }

    @Test
    fun scanner_uses_device_address_not_peer_prefix() {
        val node =
            BleBootstrapScanner.NearbyNode(
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                rssi = -70,
            )
        assertEquals("AA:BB:CC:DD:EE:FF", node.deviceAddress)
    }

    @Test
    fun http_response_has_correct_content_type() {
        val header = WifiDirectSharer.buildApkHttpHeader(contentLength = 1024)
        assertTrue(header.contains("Content-Type: application/vnd.android.package-archive"))
        assertTrue(header.contains("Content-Length: 1024"))
        assertTrue(header.contains("\r\n"))
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(header))
    }

    @Test
    fun install_landing_includes_bootstrap_sidecar_path() {
        val html = WifiDirectSharer.buildInstallLandingHtml()
        assertTrue(html.contains("/mesh/bootstrap.myc"))
        assertTrue(html.contains("unknown apps") || html.contains("Install unknown"))
        assertTrue(html.contains("Samsung"))
    }

    @Test
    fun install_landing_includes_sha256_when_provided() {
        val html =
            WifiDirectSharer.buildInstallLandingHtml(
                sha256 = "abc123",
                sidecarAvailable = true,
            )
        assertTrue(html.contains("abc123"))
        assertTrue(html.contains("technical-beta") || html.contains("not</strong> the Play Store"))
    }

    @Test
    fun wifi_qr_payload_includes_credentials() {
        val payload =
            ApkSharePayload.buildWifiQr(
                ApkSharePayload.HotspotCredentials("MycenaShare", "test-pass"),
            )
        assertTrue(payload.startsWith("WIFI:"))
        assertTrue(payload.contains("S:MycenaShare"))
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

    @Test
    fun wifi_qr_null_when_ssid_blank() {
        assertEquals(
            null,
            ApkSharePayload.buildWifiQrOrNull(
                ApkSharePayload.HotspotCredentials("", "x"),
            ),
        )
    }
}

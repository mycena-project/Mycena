// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareLinkAddressesTest {

    @Test
    fun loopback_hosts_are_rejected() {
        assertTrue(ShareLinkAddresses.isLoopbackHost("localhost"))
        assertTrue(ShareLinkAddresses.isLoopbackHost("127.0.0.1"))
        assertTrue(ShareLinkAddresses.isLoopbackHost("0.0.0.0"))
        assertFalse(ShareLinkAddresses.isUsableIpv4("localhost"))
        assertFalse(ShareLinkAddresses.isUsableIpv4("127.0.0.1"))
    }

    @Test
    fun pick_never_falls_back_to_localhost() {
        val picked =
            ShareLinkAddresses.pickShareHost(
                preferredHost = "localhost",
                hostsBeforeLink = emptySet(),
                interfaces = emptyList(),
            )
        assertNull(picked)
    }

    @Test
    fun preferred_api_address_wins_if_usable() {
        val picked =
            ShareLinkAddresses.pickShareHost(
                preferredHost = "192.168.49.1",
                hostsBeforeLink = emptySet(),
                interfaces =
                    listOf(
                        ShareLinkAddresses.Ipv4Interface("wlan0", "10.0.0.5"),
                    ),
            )
        assertEquals("192.168.49.1", picked)
    }

    @Test
    fun newly_appeared_ap_interface_preferred_over_old_sta() {
        val picked =
            ShareLinkAddresses.pickShareHost(
                preferredHost = null,
                hostsBeforeLink = setOf("10.0.0.5"),
                interfaces =
                    listOf(
                        ShareLinkAddresses.Ipv4Interface("wlan0", "10.0.0.5"),
                        ShareLinkAddresses.Ipv4Interface("ap0", "192.168.75.1"),
                    ),
            )
        assertEquals("192.168.75.1", picked)
    }

    @Test
    fun skipped_cellular_names() {
        assertTrue(ShareLinkAddresses.isSkippedInterfaceName("rmnet0"))
        assertFalse(ShareLinkAddresses.isSkippedInterfaceName("wlan0"))
        assertFalse(ShareLinkAddresses.isSkippedInterfaceName("ap0"))
        assertTrue(ShareLinkAddresses.isPreferredShareInterfaceName("p2p-wlan0-0"))
    }
}

class SharePermissionsSdkTest {

    @Test
    fun nearby_wifi_on_api_33() {
        assertEquals(
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES).toList(),
            SharePermissions.required(33).toList(),
        )
        assertFalse(SharePermissions.locationToggleRequired(33))
    }

    @Test
    fun fine_location_on_api_26() {
        assertEquals(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION).toList(),
            SharePermissions.required(26).toList(),
        )
        assertTrue(SharePermissions.locationToggleRequired(32))
    }
}

class ShareApkArtifactTest {

    @Test
    fun split_install_detected() {
        assertFalse(ShareApkArtifact.isSplitInstall(null))
        assertFalse(ShareApkArtifact.isSplitInstall(emptyArray()))
        assertTrue(ShareApkArtifact.isSplitInstall(arrayOf("/data/app/split_config.arm64_v8a.apk")))
    }

    @Test
    fun share_rejects_debug_split_and_incomplete() {
        assertEquals(
            ShareApkArtifact.RejectReason.DEBUG_BUILD,
            ShareApkArtifact.rejectReason(
                debuggable = true,
                technicalBeta = true,
                splitSourceDirs = null,
                sourceExistsAndNonEmpty = true,
            ),
        )
        assertEquals(
            ShareApkArtifact.RejectReason.NOT_TECHNICAL_BETA,
            ShareApkArtifact.rejectReason(
                debuggable = false,
                technicalBeta = false,
                splitSourceDirs = null,
                sourceExistsAndNonEmpty = true,
            ),
        )
        assertEquals(
            ShareApkArtifact.RejectReason.SPLIT_INSTALL,
            ShareApkArtifact.rejectReason(
                debuggable = false,
                technicalBeta = true,
                splitSourceDirs = arrayOf("/data/app/split_config.arm64_v8a.apk"),
                sourceExistsAndNonEmpty = true,
            ),
        )
        assertEquals(
            ShareApkArtifact.RejectReason.INCOMPLETE,
            ShareApkArtifact.rejectReason(
                debuggable = false,
                technicalBeta = true,
                splitSourceDirs = null,
                sourceExistsAndNonEmpty = false,
            ),
        )
        assertNull(
            ShareApkArtifact.rejectReason(
                debuggable = false,
                technicalBeta = true,
                splitSourceDirs = null,
                sourceExistsAndNonEmpty = true,
            ),
        )
    }

    @Test
    fun sha256_of_known_bytes() {
        val dir = File("build/tmp-share-test").apply { mkdirs() }
        val file = File(dir, "t.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ShareApkArtifact.sha256Hex(file),
        )
    }
}

class ShareHttpTest {

    @Test
    fun apk_header_uses_crlf_not_bare_lf() {
        val header = ShareHttp.apk200(1024)
        assertTrue(header.contains("\r\nContent-Type: application/vnd.android.package-archive\r\n"))
        assertTrue(header.endsWith("\r\n\r\n"))
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(header))
    }
}

class ShareTransportPreconditionsTest {

    @Test
    fun wifi_pause_only_below_q() {
        assertTrue(ShareTransportPreconditions.canPauseStaWifi(28))
        assertFalse(ShareTransportPreconditions.canPauseStaWifi(29))
        assertFalse(ShareTransportPreconditions.canPauseStaWifi(34))
    }
}

class OfflineBootstrapParseTest {

    @Test
    fun parse_invite_from_sidecar_json() {
        val json =
            """{"version":1,"invite_uri":"mycena://invite/v1#12D3KooWpeer","sharer_peer_id":"12D3KooWpeer","install_url":"http://10.0.0.1:8765/","created_at_ms":1}"""
        val parsed = OfflineBootstrap.parseSidecarJson(json)!!
        assertEquals("mycena://invite/v1#12D3KooWpeer", parsed.inviteUri)
        assertTrue(parsed.displayName.startsWith("guest-"))
    }

    @Test
    fun reject_empty_invite() {
        assertNull(OfflineBootstrap.parseSidecarJson("""{"invite_uri":"","sharer_peer_id":"x"}"""))
    }
}

class ApkSharePayloadBlankSsidTest {

    @Test
    fun blank_ssid_has_no_wifi_qr() {
        assertNull(
            ApkSharePayload.buildWifiQrOrNull(
                ApkSharePayload.HotspotCredentials("", "pass"),
            ),
        )
    }
}

class SharePermissionsRequiredMatchesBleWifiSplit {
    @Test
    fun share_does_not_request_bluetooth() {
        val required = SharePermissions.required(Build.VERSION.SDK_INT)
        assertFalse(required.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertFalse(required.contains(Manifest.permission.BLUETOOTH_SCAN))
    }
}

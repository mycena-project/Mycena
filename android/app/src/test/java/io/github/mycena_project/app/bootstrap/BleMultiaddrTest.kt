// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kotlin-side checks mirroring Rust BLE multiaddr format (F3). */
class BleMultiaddrTest {

    @Test
    fun normalizeMacInMultiaddr() {
        val addr = "/ble/aabbccddeeff/gatt/6e400001-b5a3-f393-e0a9-e50e24dcca9e/p2p/12D3KooWExample"
        assertTrue(isBleMultiaddr(addr))
        val parsed = parseBleMultiaddr(addr)
        assertEquals("aabbccddeeff", parsed?.deviceId)
        assertEquals("12D3KooWExample", parsed?.peerId)
    }

    @Test
    fun buildFromMacAndPeer() {
        val addr = buildBleMultiaddr("AA:BB:CC:DD:EE:FF", "12D3KooWTest")
        assertTrue(addr.contains("/ble/aabbccddeeff/"))
        assertTrue(addr.endsWith("/p2p/12D3KooWTest"))
    }

    @Test
    fun rejectsTcpMultiaddr() {
        assertFalse(isBleMultiaddr("/ip4/127.0.0.1/tcp/7761/p2p/abc"))
    }

    private fun isBleMultiaddr(addr: String): Boolean = addr.startsWith("/ble/")

    private fun parseBleMultiaddr(addr: String): Parsed? {
        val parts = addr.trim('/').split('/')
        if (parts.size < 6 || parts[0] != "ble" || parts[2] != "gatt" || parts[4] != "p2p") {
            return null
        }
        return Parsed(
            deviceId = parts[1].replace(":", "").lowercase(),
            peerId = parts[5],
        )
    }

    private fun buildBleMultiaddr(mac: String, peerId: String): String {
        val device = mac.replace(":", "").lowercase()
        return "/ble/$device/gatt/6e400001-b5a3-f393-e0a9-e50e24dcca9e/p2p/$peerId"
    }

    private data class Parsed(val deviceId: String, val peerId: String)
}

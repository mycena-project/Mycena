// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/** QR / share-sheet payloads for offline APK distribution. */
object ApkSharePayload {

    data class HotspotCredentials(
        val ssid: String,
        val password: String,
    )

    /** Standard Wi‑Fi join QR (scannable from the Android camera app). Null if SSID is blank. */
    fun buildWifiQrOrNull(credentials: HotspotCredentials): String? {
        if (credentials.ssid.isBlank()) return null
        return buildWifiQr(credentials)
    }

    /** Standard Wi‑Fi join QR (scannable from the Android camera app). */
    fun buildWifiQr(credentials: HotspotCredentials): String {
        require(credentials.ssid.isNotBlank()) { "blank SSID" }
        val ssid = escapeWifiField(credentials.ssid)
        val password = escapeWifiField(credentials.password)
        return "WIFI:T:WPA;S:$ssid;P:$password;;"
    }

    fun buildShareText(
        credentials: HotspotCredentials,
        installUrl: String,
    ): String =
        """
        Join Wi‑Fi: ${credentials.ssid}
        Password: ${credentials.password}

        Then open in a browser (no app needed):
        $installUrl
        """.trimIndent()

    internal fun escapeWifiField(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")
}

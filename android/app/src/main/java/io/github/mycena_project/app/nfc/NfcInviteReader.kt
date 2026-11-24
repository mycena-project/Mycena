// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.nfc

import android.app.Activity
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import io.github.mycena_project.app.bootstrap.PeerInvite

/** NFC reader mode: parse NDEF URI/text for a Mycena invite (same payload as QR paste). */
class NfcInviteReader(
    private val activity: Activity,
    private val onInvite: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var enabled = false

    private val callback =
        NfcAdapter.ReaderCallback { tag ->
            val raw = parseInviteFromTag(tag) ?: return@ReaderCallback
            activity.runOnUiThread {
                disable()
                onInvite(raw)
            }
        }

    fun enable() {
        val nfc = adapter
        if (nfc == null || !nfc.isEnabled) {
            onUnavailable()
            return
        }
        nfc.enableReaderMode(
            activity,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        enabled = true
    }

    fun disable() {
        if (!enabled) return
        adapter?.disableReaderMode(activity)
        enabled = false
    }

    companion object {
        private val URI_PREFIXES =
            arrayOf(
                "",
                "http://www.",
                "https://www.",
                "http://",
                "https://",
                "tel:",
                "mailto:",
                "ftp://anonymous:anonymous@",
                "ftp://ftp.",
                "ftps://",
                "sftp://",
                "smb://",
                "nfs://",
                "ftp://",
                "dav://",
                "news:",
                "telnet://",
                "imap:",
                "rtsp://",
                "urn:",
                "pop:",
                "sip:",
                "sips:",
                "tftp:",
                "btspp://",
                "btl2cap://",
                "btgoep://",
                "tcpobex://",
                "irdaobex://",
                "file://",
                "urn:epc:id:",
                "urn:epc:tag:",
                "urn:epc:pat:",
                "urn:epc:raw:",
                "urn:epc:",
                "urn:nfc:",
            )

        fun parseInviteFromTag(tag: Tag): String? {
            val ndef = Ndef.get(tag) ?: return null
            return try {
                ndef.connect()
                val message = ndef.ndefMessage ?: return null
                extractInviteFromNdefMessage(message)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    ndef.close()
                } catch (_: Exception) {
                }
            }
        }

        fun extractInviteFromNdefMessage(message: android.nfc.NdefMessage): String? {
            for (record in message.records) {
                val raw =
                    when {
                        record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                            record.type.contentEquals(NdefRecord.RTD_URI) ->
                            parseUriRecord(record)
                        record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                            record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                            parseTextRecord(record)
                        else ->
                            String(record.payload, Charsets.UTF_8).trim()
                    }
                if (PeerInvite.parse(raw) != null) {
                    return raw.trim()
                }
            }
            return null
        }

        private fun parseUriRecord(record: NdefRecord): String {
            val payload = record.payload
            if (payload.isEmpty()) return ""
            val prefixCode = payload[0].toInt() and 0xFF
            val prefix =
                if (prefixCode in URI_PREFIXES.indices) {
                    URI_PREFIXES[prefixCode]
                } else {
                    ""
                }
            return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
        }

        private fun parseTextRecord(record: NdefRecord): String {
            val payload = record.payload
            if (payload.size < 2) return ""
            val languageCodeLength = payload[0].toInt() and 0x3F
            val textStart = 1 + languageCodeLength
            if (textStart >= payload.size) return ""
            return String(payload, textStart, payload.size - textStart, Charsets.UTF_8)
        }
    }
}

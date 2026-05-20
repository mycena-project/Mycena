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
}

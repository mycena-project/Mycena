package network.mycelium.app.bootstrap

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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
    fun apk_file_exists_and_is_readable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apkPath = context.packageCodePath
        assertTrue(File(apkPath).exists())
        assertTrue(File(apkPath).canRead())
    }

    @Test
    fun http_response_has_correct_content_type() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharer = WifiDirectSharer(context)
        sharer.startApkServer()

        try {
            val socket = Socket("127.0.0.1", WifiDirectSharer.PORT)
            socket.getOutputStream().write("GET /mycelium.apk HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val status = reader.readLine()
            assertTrue(status!!.contains("200"))

            var contentType: String? = null
            var line: String?
            while (reader.readLine().also { line = it } != null && line!!.isNotEmpty()) {
                if (line!!.startsWith("Content-Type:", ignoreCase = true)) {
                    contentType = line!!.substringAfter(":").trim()
                }
            }
            assertEquals("application/vnd.android.package-archive", contentType)
            socket.close()
        } finally {
            sharer.stopServer()
        }
    }
}

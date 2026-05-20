package network.mycelium.app.bootstrap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serves the installed Mycelium APK over a minimal HTTP server on the local network.
 * Recipients open the URL in a browser — no Mycelium install required on their side.
 */
class WifiDirectSharer(private val context: Context) {

    companion object {
        const val PORT = 8765
        const val TAG = "WifiDirectSharer"

        /** Minimal HTTP 200 header for APK download (testable without a live server). */
        internal fun buildApkHttpHeader(contentLength: Int): String =
            """
                HTTP/1.1 200 OK
                Content-Type: application/vnd.android.package-archive
                Content-Disposition: attachment; filename="mycelium.apk"
                Content-Length: $contentLength
                Connection: close
                
            """.trimIndent() + "\r\n"
    }

    private var serverSocket: ServerSocket? = null
    private var isServing = false
    private val transferCount = AtomicInteger(0)
    private var onTransfer: (() -> Unit)? = null

    fun setOnTransferListener(listener: (() -> Unit)?) {
        onTransfer = listener
    }

    fun getTransferCount(): Int = transferCount.get()

    suspend fun startApkServer(): String = withContext(Dispatchers.IO) {
        val apkFile = getApkFile()
        serverSocket = ServerSocket(PORT)
        isServing = true
        transferCount.set(0)

        val localIp = getLocalIpAddress()
        Log.i(TAG, "APK server started: http://$localIp:$PORT/mycelium.apk")

        Thread {
            while (isServing) {
                try {
                    val client = serverSocket!!.accept()
                    handleApkRequest(client, apkFile)
                } catch (e: IOException) {
                    if (isServing) Log.e(TAG, "Server error: ${e.message}")
                }
            }
        }.start()

        "http://$localIp:$PORT/mycelium.apk"
    }

    private fun handleApkRequest(client: Socket, apkFile: File) {
        try {
            val output = client.getOutputStream()
            val apkBytes = apkFile.readBytes()

            val header = buildApkHttpHeader(apkBytes.size)

            output.write(header.toByteArray())
            output.write(apkBytes)
            output.flush()

            transferCount.incrementAndGet()
            onTransfer?.invoke()
            Log.i(TAG, "APK sent to ${client.inetAddress.hostAddress} (${apkBytes.size} bytes)")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send APK: ${e.message}")
        } finally {
            client.close()
        }
    }

    fun stopServer() {
        isServing = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
    }

    private fun getApkFile(): File = File(context.packageCodePath)

    private fun getLocalIpAddress(): String {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    return addr.hostAddress ?: continue
                }
            }
        }
        return "localhost"
    }
}

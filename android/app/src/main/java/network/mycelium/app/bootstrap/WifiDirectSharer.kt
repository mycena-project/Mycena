// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serves the installed Mycelium APK over a minimal HTTP server on the local network.
 * Recipients open the URL in a browser — no Mycelium install required on their side.
 *
 * Works over an existing LAN or a [LocalHotspotHelper] phone-to-phone link.
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

        internal fun buildHtmlHttpHeader(contentLength: Int): String =
            """
                HTTP/1.1 200 OK
                Content-Type: text/html; charset=utf-8
                Content-Length: $contentLength
                Connection: close

            """.trimIndent() + "\r\n"

        internal fun buildInstallLandingHtml(installPath: String = "/mycelium.apk"): String =
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>Install Mycelium</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 2rem; text-align: center; }
                a.btn { display: inline-block; margin: 0.75rem; padding: 1rem 1.5rem;
                     background: #2e7d32; color: #fff; text-decoration: none; border-radius: 8px; }
                a.secondary { background: #1565c0; }
                p.hint { color: #555; font-size: 0.95rem; max-width: 28rem; margin: 1rem auto; }
              </style>
            </head>
            <body>
              <h1>Mycelium</h1>
              <p>Offline mesh messaging — shared from a friend nearby.</p>
              <p class="hint">Join Wi‑Fi from the other QR first if prompted. No Play Store or internet needed.</p>
              <a class="btn" href="$installPath">Install Mycelium</a>
              <a class="btn secondary" href="/mesh/bootstrap.myc" download="mycelium-bootstrap.myc">Save mesh join file</a>
              <script>
                fetch("/mesh/bootstrap.myc").then(r => r.blob()).then(b => {
                  const a = document.createElement("a");
                  a.href = URL.createObjectURL(b);
                  a.download = "mycelium-bootstrap.myc";
                  a.click();
                }).catch(() => {});
              </script>
            </body>
            </html>
            """.trimIndent()
    }

    private var serverSocket: ServerSocket? = null
    private var isServing = false
    private val transferCount = AtomicInteger(0)
    private var onTransfer: (() -> Unit)? = null
    private var bootstrapSidecar: ByteArray? = null

    fun setBootstrapSidecar(bytes: ByteArray?) {
        bootstrapSidecar = bytes
    }

    fun setOnTransferListener(listener: (() -> Unit)?) {
        onTransfer = listener
    }

    fun getTransferCount(): Int = transferCount.get()

    suspend fun startApkServer(): String = withContext(Dispatchers.IO) {
        val apkFile = getApkFile()
        serverSocket = ServerSocket(PORT)
        isServing = true
        transferCount.set(0)

        delay(300)

        val localIp = getLocalIpAddress()
        Log.i(TAG, "APK server started: http://$localIp:$PORT/")

        Thread {
            while (isServing) {
                try {
                    val client = serverSocket!!.accept()
                    handleRequest(client, apkFile)
                } catch (e: IOException) {
                    if (isServing) Log.e(TAG, "Server error: ${e.message}")
                }
            }
        }.start()

        "http://$localIp:$PORT/"
    }

    private fun handleRequest(client: Socket, apkFile: File) {
        try {
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine().orEmpty()
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            skipHttpHeaders(reader)

            val output = client.getOutputStream()
            when {
                path == "/" || path.startsWith("/index") -> {
                    val html = buildInstallLandingHtml().toByteArray(Charsets.UTF_8)
                    output.write(buildHtmlHttpHeader(html.size).toByteArray())
                    output.write(html)
                }
                path.startsWith("/mycelium.apk") -> sendApk(output, apkFile)
                path.startsWith("/mesh/bootstrap.myc") || path.startsWith("/mesh/bootstrap.json") -> {
                    sendBootstrapSidecar(output)
                }
                else -> {
                    val html = buildInstallLandingHtml().toByteArray(Charsets.UTF_8)
                    output.write(buildHtmlHttpHeader(html.size).toByteArray())
                    output.write(html)
                }
            }
            output.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to handle request: ${e.message}")
        } finally {
            client.close()
        }
    }

    private fun sendApk(output: java.io.OutputStream, apkFile: File) {
        val apkBytes = apkFile.readBytes()
        output.write(buildApkHttpHeader(apkBytes.size).toByteArray())
        output.write(apkBytes)
        transferCount.incrementAndGet()
        onTransfer?.invoke()
        Log.i(TAG, "APK sent (${apkBytes.size} bytes)")
    }

    private fun sendBootstrapSidecar(output: java.io.OutputStream) {
        val bytes = bootstrapSidecar ?: byteArrayOf()
        val header =
            """
                HTTP/1.1 200 OK
                Content-Type: application/json
                Content-Disposition: attachment; filename="mycelium-bootstrap.myc"
                Content-Length: ${bytes.size}
                Connection: close

            """.trimIndent() + "\r\n"
        output.write(header.toByteArray())
        if (bytes.isNotEmpty()) {
            output.write(bytes)
        }
    }

    private fun skipHttpHeaders(reader: java.io.BufferedReader) {
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
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
        val candidates = mutableListOf<String>()
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            if (!intf.isUp || intf.isLoopback) continue
            for (addr in intf.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                    addr.hostAddress?.let { candidates.add(it) }
                }
            }
        }
        return candidates.firstOrNull { it.startsWith("192.168.43.") }
            ?: candidates.firstOrNull { it.startsWith("192.168.49.") }
            ?: candidates.firstOrNull { it.startsWith("192.168.4.") }
            ?: candidates.firstOrNull()
            ?: "localhost"
    }
}

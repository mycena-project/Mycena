// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

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
 * Serves the installed Mycena APK over a minimal HTTP server on the local network.
 * Recipients open the URL in a browser — no Mycena install required on their side.
 *
 * Works over an existing LAN or a [LocalHotspotHelper] phone-to-phone link.
 */
class WifiDirectSharer {

    companion object {
        const val PORT = 8765
        const val TAG = "WifiDirectSharer"
        private const val IP_WAIT_MS = 8_000L
        private const val IP_POLL_MS = 250L

        /** Minimal HTTP 200 header for APK download (testable without a live server). */
        internal fun buildApkHttpHeader(contentLength: Int): String =
            ShareHttp.apk200(contentLength.toLong())

        internal fun buildApkHttpHeader(contentLength: Long): String =
            ShareHttp.apk200(contentLength)

        internal fun buildHtmlHttpHeader(contentLength: Int): String =
            ShareHttp.html200(contentLength)

        internal fun buildInstallLandingHtml(
            installPath: String = "/mycena.apk",
            sha256: String? = null,
            sidecarAvailable: Boolean = false,
            debugSigned: Boolean = false,
            technicalBetaNotice: Boolean = true,
        ): String {
            val hashBlock =
                if (sha256.isNullOrBlank()) {
                    ""
                } else {
                    """<p class="hint">SHA-256: <code>$sha256</code></p>
              <p class="hint">Compare this with the hash shown on the sharing phone. This page is HTTP (not encrypted).</p>"""
                }
            val debugBlock =
                if (debugSigned) {
                    """<p class="warn">This APK is <strong>debug-signed</strong>. Mycena will not share a debug APK; install the Technical Beta build.</p>"""
                } else if (technicalBetaNotice) {
                    """<p class="warn">This artifact is for <strong>technical-beta / offline testing</strong> and is <strong>not</strong> the Play Store artifact.</p>"""
                } else {
                    ""
                }
            val sidecarBlock =
                if (sidecarAvailable) {
                    """<a class="btn secondary" href="/mesh/bootstrap.myc" download="mycena-bootstrap.myc">Save mesh join file</a>
              <p class="hint">After install: open Mycena → Contacts, then scan the join QR still shown on the other phone. Or pick the saved <code>.myc</code> file on first launch. Do not rely on the Downloads folder being readable.</p>"""
                } else {
                    """<a class="btn secondary" href="/mesh/bootstrap.myc" download="mycena-bootstrap.myc">Save mesh join file</a>
              <p class="warn">The mesh join file may be missing until the sharing phone&apos;s node is ready. After install, scan the other phone&apos;s Contacts / invite QR.</p>"""
                }
            val sidecarScript =
                if (sidecarAvailable) {
                    """
              <script>
                fetch("/mesh/bootstrap.myc").then(r => {
                  if (!r.ok) return;
                  return r.blob();
                }).then(b => {
                  if (!b) return;
                  const a = document.createElement("a");
                  a.href = URL.createObjectURL(b);
                  a.download = "mycena-bootstrap.myc";
                  a.click();
                }).catch(() => {});
              </script>
                    """.trimIndent()
                } else {
                    ""
                }
            return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>Install Mycena</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 2rem; text-align: center; }
                a.btn { display: inline-block; margin: 0.75rem; padding: 1rem 1.5rem;
                     background: #2e7d32; color: #fff; text-decoration: none; border-radius: 8px; }
                a.secondary { background: #1565c0; }
                p.hint { color: #555; font-size: 0.95rem; max-width: 32rem; margin: 1rem auto; text-align: left; }
                p.warn { color: #8a4b08; font-size: 0.95rem; max-width: 32rem; margin: 1rem auto; text-align: left; }
                code { word-break: break-all; }
                ol { max-width: 32rem; margin: 1rem auto; text-align: left; color: #333; }
              </style>
            </head>
            <body>
              <h1>Mycena</h1>
              <p>Offline mesh messaging — shared from a friend nearby.</p>
              <p class="hint">Join Wi‑Fi from the other QR first if prompted. No Play Store or internet needed.</p>
              $debugBlock
              $hashBlock
              <ol>
                <li>Tap <strong>Install Mycena</strong>. Chrome will download an APK.</li>
                <li>Open the download and tap Install. You may need <strong>Settings → Apps → Special app access → Install unknown apps</strong> for Chrome (or your browser).</li>
                <li><strong>Samsung:</strong> if Auto Blocker, unknown-source install, or Play Protect stops the install, open those settings and allow this one install. Mycena cannot bypass those controls.</li>
                <li>If install still fails, you are back in the browser — return to the sharing phone and use Quick Share, or try again after changing the setting.</li>
              </ol>
              <a class="btn" href="$installPath">Install Mycena</a>
              $sidecarBlock
              $sidecarScript
            </body>
            </html>
            """.trimIndent()
        }
    }

    class NoReachableShareAddressException :
        IOException("no_reachable_address")

    private var serverSocket: ServerSocket? = null
    @Volatile
    private var isServing = false
    private var acceptThread: Thread? = null
    private val transferCount = AtomicInteger(0)
    private var onTransfer: (() -> Unit)? = null
    @Volatile
    private var bootstrapSidecar: ByteArray? = null
    @Volatile
    private var apkSha256: String? = null

    fun setBootstrapSidecar(bytes: ByteArray?) {
        bootstrapSidecar = bytes
    }

    fun hasBootstrapSidecar(): Boolean {
        val bytes = bootstrapSidecar
        return bytes != null && bytes.isNotEmpty()
    }

    fun setOnTransferListener(listener: (() -> Unit)?) {
        onTransfer = listener
    }

    fun getTransferCount(): Int = transferCount.get()

    fun apkSha256Hex(): String? = apkSha256

    /**
     * Binds after a reachable IPv4 is known. Never advertises loopback.
     *
     * @param preferredHost Direct group-owner or LAN LinkProperties address, if any
     * @param hostsBeforeLink IPv4s observed before the share link was started
     * @param apkFile Complete Technical Beta APK bytes to stream (cache copy)
     */
    suspend fun startApkServer(
        preferredHost: String?,
        hostsBeforeLink: Set<String>,
        apkFile: File,
    ): String = withContext(Dispatchers.IO) {
        stopServer()
        apkSha256 = ShareApkArtifact.sha256Hex(apkFile)

        val localIp = waitForShareHost(preferredHost, hostsBeforeLink)
        Log.i(TAG, "P2P-MT-04 share_host=$localIp port=$PORT")

        val socket = ServerSocket(PORT)
        serverSocket = socket
        isServing = true
        transferCount.set(0)

        val thread =
            Thread {
                while (isServing) {
                    try {
                        val client = socket.accept()
                        handleRequest(client, apkFile)
                    } catch (e: IOException) {
                        if (isServing) Log.e(TAG, "Server error: ${e.message}")
                    }
                }
            }
        thread.isDaemon = true
        acceptThread = thread
        thread.start()

        "http://$localIp:$PORT/"
    }

    private suspend fun waitForShareHost(
        preferredHost: String?,
        hostsBeforeLink: Set<String>,
    ): String {
        val deadline = System.currentTimeMillis() + IP_WAIT_MS
        var last: String? = null
        while (System.currentTimeMillis() < deadline) {
            val picked =
                ShareLinkAddresses.pickShareHost(
                    preferredHost,
                    hostsBeforeLink,
                    ShareLinkAddresses.collectIpv4Interfaces(),
                )
            if (picked != null) return picked
            last = preferredHost
            delay(IP_POLL_MS)
        }
        val late =
            ShareLinkAddresses.pickShareHost(
                last,
                hostsBeforeLink,
                ShareLinkAddresses.collectIpv4Interfaces(),
            )
        if (late != null) return late
        throw NoReachableShareAddressException()
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
                    val html =
                        buildInstallLandingHtml(
                            sha256 = apkSha256,
                            sidecarAvailable = hasBootstrapSidecar(),
                        ).toByteArray(Charsets.UTF_8)
                    output.write(buildHtmlHttpHeader(html.size).toByteArray())
                    output.write(html)
                }
                path.startsWith("/mycena.apk.sha256") -> {
                    val hex = apkSha256.orEmpty() + "\n"
                    val (header, body) = ShareHttp.text200(hex)
                    output.write(header.toByteArray())
                    output.write(body)
                }
                path.startsWith("/mycena.apk") -> sendApk(output, apkFile)
                path.startsWith("/mesh/bootstrap.myc") || path.startsWith("/mesh/bootstrap.json") -> {
                    sendBootstrapSidecar(output)
                }
                else -> {
                    val html =
                        buildInstallLandingHtml(
                            sha256 = apkSha256,
                            sidecarAvailable = hasBootstrapSidecar(),
                        ).toByteArray(Charsets.UTF_8)
                    output.write(buildHtmlHttpHeader(html.size).toByteArray())
                    output.write(html)
                }
            }
            output.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to handle request: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun sendApk(output: java.io.OutputStream, apkFile: File) {
        val length = apkFile.length()
        output.write(buildApkHttpHeader(length).toByteArray())
        apkFile.inputStream().use { input -> input.copyTo(output) }
        transferCount.incrementAndGet()
        onTransfer?.invoke()
        Log.i(TAG, "APK sent ($length bytes)")
    }

    private fun sendBootstrapSidecar(output: java.io.OutputStream) {
        val bytes = bootstrapSidecar
        if (bytes == null || bytes.isEmpty()) {
            output.write(ShareHttp.notFound().toByteArray())
            return
        }
        output.write(ShareHttp.sidecar200(bytes.size).toByteArray())
        output.write(bytes)
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
        acceptThread = null
        apkSha256 = null
    }
}

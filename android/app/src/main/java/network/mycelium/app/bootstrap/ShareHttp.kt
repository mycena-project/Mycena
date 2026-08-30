// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/** Minimal HTTP/1.1 header blocks with CRLF line endings. */
internal object ShareHttp {

    fun headerBlock(statusLine: String, headers: List<Pair<String, String>>): String {
        val sb = StringBuilder()
        sb.append(statusLine).append("\r\n")
        for ((name, value) in headers) {
            sb.append(name).append(": ").append(value).append("\r\n")
        }
        sb.append("\r\n")
        return sb.toString()
    }

    fun apk200(contentLength: Long): String =
        headerBlock(
            "HTTP/1.1 200 OK",
            listOf(
                "Content-Type" to "application/vnd.android.package-archive",
                "Content-Disposition" to "attachment; filename=\"mycelium.apk\"",
                "Content-Length" to contentLength.toString(),
                "Connection" to "close",
            ),
        )

    fun html200(contentLength: Int): String =
        headerBlock(
            "HTTP/1.1 200 OK",
            listOf(
                "Content-Type" to "text/html; charset=utf-8",
                "Content-Length" to contentLength.toString(),
                "Connection" to "close",
            ),
        )

    fun sidecar200(contentLength: Int): String =
        headerBlock(
            "HTTP/1.1 200 OK",
            listOf(
                "Content-Type" to "application/json",
                "Content-Disposition" to "attachment; filename=\"mycelium-bootstrap.myc\"",
                "Content-Length" to contentLength.toString(),
                "Connection" to "close",
            ),
        )

    fun notFound(): String =
        headerBlock(
            "HTTP/1.1 404 Not Found",
            listOf(
                "Content-Length" to "0",
                "Connection" to "close",
            ),
        )

    fun text200(body: String): Pair<String, ByteArray> {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header =
            headerBlock(
                "HTTP/1.1 200 OK",
                listOf(
                    "Content-Type" to "text/plain; charset=utf-8",
                    "Content-Length" to bytes.size.toString(),
                    "Connection" to "close",
                ),
            )
        return header to bytes
    }
}

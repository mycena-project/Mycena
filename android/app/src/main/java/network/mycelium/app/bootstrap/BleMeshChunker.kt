// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app.bootstrap

/**
 * GATT chunk codec matching [`mycelium_core::ble_mesh`] (MYLC chunks).
 */
object BleMeshChunker {

    private val CHUNK_MAGIC = byteArrayOf('M'.code.toByte(), 'Y'.code.toByte(), 'L'.code.toByte(), 'C'.code.toByte())
    private const val CHUNK_HEADER = 6
    private const val MAX_PAYLOAD = 476

    fun chunkFrame(frame: ByteArray): List<ByteArray> {
        if (frame.isEmpty()) return emptyList()
        val pieces = frame.toList().chunked(MAX_PAYLOAD)
        val total = pieces.size.coerceAtMost(255)
        return pieces.take(total).mapIndexed { idx, piece ->
            ByteArray(CHUNK_HEADER + piece.size).also { out ->
                CHUNK_MAGIC.copyInto(out, 0)
                out[4] = idx.toByte()
                out[5] = total.toByte()
                piece.toByteArray().copyInto(out, CHUNK_HEADER)
            }
        }
    }

    class Assembler {
        private var total: Int = 0
        private val parts = mutableMapOf<Int, ByteArray>()

        fun reset() {
            total = 0
            parts.clear()
        }

        /** Returns full frame when reassembly completes. */
        fun push(chunk: ByteArray): ByteArray? {
            if (chunk.size < CHUNK_HEADER) return null
            if (!chunk.copyOfRange(0, 4).contentEquals(CHUNK_MAGIC)) return null
            val idx = chunk[4].toInt() and 0xFF
            val count = chunk[5].toInt() and 0xFF
            if (count == 0 || idx >= count) return null
            if (total == 0) {
                total = count
            } else if (total != count) {
                reset()
                total = count
            }
            parts[idx] = chunk.copyOfRange(CHUNK_HEADER, chunk.size)
            if (parts.size < total) return null
            val out = ByteArray(parts.values.sumOf { it.size })
            var offset = 0
            for (i in 0 until total) {
                val p = parts[i] ?: return null
                p.copyInto(out, offset)
                offset += p.size
            }
            reset()
            return out
        }
    }
}

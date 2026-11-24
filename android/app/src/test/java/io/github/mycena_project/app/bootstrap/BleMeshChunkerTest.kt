// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycena Project
package io.github.mycena_project.app.bootstrap

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BleMeshChunkerTest {

    @Test
    fun chunkAndReassemble() {
        val frame = ByteArray(900) { it.toByte() }
        val chunks = BleMeshChunker.chunkFrame(frame)
        assert(chunks.size > 1)
        val asm = BleMeshChunker.Assembler()
        var done: ByteArray? = null
        for (c in chunks) {
            asm.push(c)?.let { done = it }
        }
        assertArrayEquals(frame, done)
    }

    @Test
    fun rejectsInvalidMagic() {
        val asm = BleMeshChunker.Assembler()
        assertEquals(null, asm.push(byteArrayOf(0, 1, 2, 3, 0, 1, 4)))
    }
}

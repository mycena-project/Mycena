// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 Mycelium Project
package network.mycelium.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalDataWiperTest {

    @Test
    fun missingDirectoryIsSuccess() {
        val missing = File("/tmp/mycelium-wipe-test-does-not-exist-${System.nanoTime()}")
        assertFalse(missing.exists())
        assertTrue(LocalDataWiper.deleteDirectoryWithRetry(missing, attempts = 2, sleepMs = 0L))
    }

    @Test
    fun populatedDirectoryIsDeleted() {
        val dir = createTempDirectory("mycelium-wipe-ok").toFile()
        File(dir, "node.db").writeText("local")
        assertTrue(dir.exists())
        assertTrue(LocalDataWiper.deleteDirectoryWithRetry(dir, attempts = 2, sleepMs = 0L))
        assertFalse(dir.exists())
    }

    @Test
    fun incompleteDeleteIsFailureAndDoesNotClaimSuccess() {
        val dir = createTempDirectory("mycelium-wipe-fail").toFile()
        File(dir, "stuck.db").writeText("still here")
        var deleteCalls = 0
        var sleeps = 0
        val gone =
            LocalDataWiper.deleteDirectoryWithRetry(
                dir,
                attempts = 3,
                sleepMs = 1L,
                sleeper = { sleeps += 1 },
                deleteOp = { deleteCalls += 1 },
            )
        assertFalse("incomplete wipe must not be reported as success", gone)
        assertTrue(dir.exists())
        assertEquals(3, deleteCalls)
        assertEquals(2, sleeps)
        dir.deleteRecursively()
    }
}

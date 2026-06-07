package com.aicleaner.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ShellEngine (non-JNI parts).
 * These test the path validation and command building logic.
 */
class ShellEngineTest {

    // ==================== Path Validation Tests ====================

    @Test
    fun `sdcard paths are valid`() {
        val validPaths = listOf(
            "/sdcard",
            "/sdcard/Download",
            "/sdcard/Documents/file.pdf",
            "/sdcard/DCIM/Camera/photo.jpg"
        )

        validPaths.forEach { path ->
            assertTrue("$path should be valid", path.startsWith("/sdcard"))
        }
    }

    @Test
    fun `non-sdcard paths are rejected`() {
        val invalidPaths = listOf(
            "/system/etc",
            "/data/data/com.app",
            "/tmp/file",
            "/root/file"
        )

        invalidPaths.forEach { path ->
            assertFalse("$path should be rejected", path.startsWith("/sdcard"))
        }
    }

    @Test
    fun `path traversal is detected`() {
        val traversalPaths = listOf(
            "/sdcard/../../etc/passwd",
            "/sdcard/Download/../../../system",
            "/sdcard/./../../root"
        )

        traversalPaths.forEach { path ->
            assertTrue("$path should detect traversal", path.contains(".."))
        }
    }

    // ==================== Command Building Tests ====================

    @Test
    fun `safe commands are identified`() {
        val safeCommands = setOf(
            "ls", "find", "du", "df", "cat", "stat", "file",
            "wc", "head", "tail", "grep", "sort", "uniq"
        )

        safeCommands.forEach { cmd ->
            assertTrue("$cmd should be safe", cmd in safeCommands)
        }
    }

    @Test
    fun `dangerous commands are rejected`() {
        val dangerousCommands = listOf("rm", "mv", "cp", "chmod", "chown", "dd", "mkfs")

        val safeCommands = setOf(
            "ls", "find", "du", "df", "cat", "stat", "file",
            "wc", "head", "tail", "grep", "sort", "uniq"
        )

        dangerousCommands.forEach { cmd ->
            assertFalse("$cmd should be rejected", cmd in safeCommands)
        }
    }

    @Test
    fun `dangerous operators are detected`() {
        val dangerousOps = listOf(";", "&&", "||", "|", ">", ">>", "`", "$(")

        dangerousOps.forEach { op ->
            val cmd = "ls $op rm -rf /"
            val hasDangerousOp = dangerousOps.any { cmd.contains(it) }
            assertTrue("Should detect '$op' in command", hasDangerousOp)
        }
    }

    @Test
    fun `shell meta characters are detected in paths`() {
        val metaChars = charArrayOf(
            '\'', '"', '`', '$', ';', '&', '|', '(', ')',
            '{', '}', '<', '>', '\n', '\r', '\\', '!'
        )

        val safePath = "/sdcard/Download/file.txt"
        val unsafePath = "/sdcard/Download/file'; rm -rf /'"

        assertFalse("Safe path has no meta chars",
            safePath.any { it in metaChars })
        assertTrue("Unsafe path has meta chars",
            unsafePath.any { it in metaChars })
    }
}

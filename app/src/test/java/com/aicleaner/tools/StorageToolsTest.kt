package com.aicleaner.tools

import com.aicleaner.engine.ShellEngine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for all StorageTools.
 */
class StorageToolsTest {

    private lateinit var mockShell: ShellEngine

    @Before
    fun setup() {
        mockShell = mock(ShellEngine::class.java)
    }

    // ==================== list_directory ====================

    @Test
    fun `list_directory rejects non-sdcard paths`() = runBlocking {
        val tool = ListDirectoryTool(mockShell)
        val args = JSONObject().put("path", "/system/etc")

        val result = tool.execute(args)

        assertFalse(result.success)
        assertTrue(result.error?.contains("Access denied") == true)
    }

    @Test
    fun `list_directory accepts sdcard path`() = runBlocking {
        val tool = ListDirectoryTool(mockShell)
        `when`(mockShell.exec(anyString())).thenReturn("total 100\n-rw-r--r-- 1 root root 1024 file.txt")

        val args = JSONObject().put("path", "/sdcard/Download")
        val result = tool.execute(args)

        assertTrue(result.success)
        assertTrue(result.output.contains("/sdcard/Download"))
    }

    // ==================== find_files ====================

    @Test
    fun `find_files rejects path traversal`() = runBlocking {
        val tool = FindFilesTool(mockShell)
        val args = JSONObject().put("path", "/sdcard/../../etc")

        val result = tool.execute(args)

        assertFalse(result.success)
    }

    @Test
    fun `find_files with pattern filter`() = runBlocking {
        val tool = FindFilesTool(mockShell)
        `when`(mockShell.exec(contains(".apk"))).thenReturn("/sdcard/Download/app.apk")

        val args = JSONObject()
            .put("path", "/sdcard")
            .put("name_pattern", "*.apk")

        val result = tool.execute(args)
        assertTrue(result.success)
    }

    // ==================== delete_file ====================

    @Test
    fun `delete_file rejects root sdcard`() = runBlocking {
        val tool = DeleteFileTool(mockShell)
        val args = JSONObject().put("path", "/sdcard")

        val result = tool.execute(args)

        assertFalse(result.success)
        assertTrue(result.error?.contains("Cannot delete") == true)
    }

    @Test
    fun `delete_file rejects system paths`() = runBlocking {
        val tool = DeleteFileTool(mockShell)
        val args = JSONObject().put("path", "/system/bin/sh")

        val result = tool.execute(args)

        assertFalse(result.success)
        assertTrue(result.error?.contains("Access denied") == true)
    }

    @Test
    fun `delete_file accepts valid sdcard path`() = runBlocking {
        val tool = DeleteFileTool(mockShell)
        `when`(mockShell.exec(contains("du"))).thenReturn("10M\t/sdcard/file.apk")
        `when`(mockShell.exec(contains("rm"))).thenReturn("")

        val args = JSONObject().put("path", "/sdcard/file.apk")
        val result = tool.execute(args)

        assertTrue(result.success)
    }

    // ==================== move_file ====================

    @Test
    fun `move_file rejects non-sdcard source`() = runBlocking {
        val tool = MoveFileTool(mockShell)
        val args = JSONObject()
            .put("source", "/tmp/file.txt")
            .put("destination", "/sdcard/file.txt")

        val result = tool.execute(args)

        assertFalse(result.success)
    }

    @Test
    fun `move_file rejects non-sdcard destination`() = runBlocking {
        val tool = MoveFileTool(mockShell)
        val args = JSONObject()
            .put("source", "/sdcard/file.txt")
            .put("destination", "/tmp/file.txt")

        val result = tool.execute(args)

        assertFalse(result.success)
    }

    @Test
    fun `move_file accepts valid sdcard paths`() = runBlocking {
        val tool = MoveFileTool(mockShell)
        `when`(mockShell.exec(anyString())).thenReturn("")

        val args = JSONObject()
            .put("source", "/sdcard/Download/file.pdf")
            .put("destination", "/sdcard/Documents/file.pdf")

        val result = tool.execute(args)

        assertTrue(result.success)
    }

    // ==================== execute_shell ====================

    @Test
    fun `execute_shell rejects dangerous commands`() = runBlocking {
        val tool = ExecuteShellTool(mockShell)

        val dangerous = listOf(
            "rm -rf /sdcard",
            "ls; rm -rf /",
            "echo hello && rm -rf /",
            "ls | rm -rf /",
            "cat /sdcard > /dev/null"
        )

        dangerous.forEach { cmd ->
            val args = JSONObject().put("command", cmd)
            val result = tool.execute(args)
            assertFalse("Should reject: $cmd", result.success)
        }
    }

    @Test
    fun `execute_shell allows safe commands`() = runBlocking {
        val tool = ExecuteShellTool(mockShell)
        `when`(mockShell.exec(anyString())).thenReturn("file.txt")

        val safe = listOf(
            "ls /sdcard",
            "find /sdcard -name '*.apk'",
            "du -sh /sdcard/*",
            "df -h /sdcard"
        )

        safe.forEach { cmd ->
            val args = JSONObject().put("command", cmd)
            val result = tool.execute(args)
            assertTrue("Should allow: $cmd", result.success)
        }
    }
}

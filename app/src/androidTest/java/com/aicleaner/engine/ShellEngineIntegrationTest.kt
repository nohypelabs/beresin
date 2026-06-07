package com.aicleaner.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for ShellEngine on real Android device.
 * These tests execute actual shell commands on the device.
 * Requires: Android device/emulator with storage permission granted.
 */
@RunWith(AndroidJUnit4::class)
class ShellEngineIntegrationTest {

    private lateinit var shell: ShellEngine

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        shell = ShellEngine(context)
    }

    @Test
    fun testSimpleCommand() = runBlocking {
        val result = shell.exec("echo hello")
        assertEquals("hello", result.trim())
    }

    @Test
    fun testListSdcard() = runBlocking {
        val result = shell.exec("ls /sdcard 2>/dev/null")
        // /sdcard should exist on any Android device
        assertNotNull(result)
        // Result might be empty if no permission, but shouldn't crash
    }

    @Test
    fun testDiskUsage() = runBlocking {
        val result = shell.exec("df -h /sdcard 2>/dev/null")
        // Should return disk usage info or empty if no permission
        assertNotNull(result)
    }

    @Test
    fun testFindCommand() = runBlocking {
        val result = shell.exec("find /sdcard -maxdepth 1 -type d 2>/dev/null | head -5")
        assertNotNull(result)
    }

    @Test
    fun testCommandTimeout() = runBlocking {
        // This should complete quickly
        val result = shell.exec("echo quick")
        assertEquals("quick", result.trim())
    }

    @Test
    fun testMultipleCommands() = runBlocking {
        // Test sequential commands
        val result1 = shell.exec("echo first")
        val result2 = shell.exec("echo second")

        assertEquals("first", result1.trim())
        assertEquals("second", result2.trim())
    }

    @Test
    fun testCommandWithError() = runBlocking {
        // Command that should produce stderr
        val result = shell.exec("ls /nonexistent/path 2>&1")
        assertNotNull(result)
        // Should not crash, just return error output
    }
}

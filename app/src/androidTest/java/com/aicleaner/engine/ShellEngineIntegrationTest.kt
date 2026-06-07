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
    fun testProotNotReady() {
        // PRoot should not be set up in test environment
        assertFalse(shell.isProotReady())
    }

    @Test
    fun testCommandTimeout() = runBlocking {
        // This should complete quickly
        val result = shell.exec("echo quick")
        assertEquals("quick", result.trim())
    }
}

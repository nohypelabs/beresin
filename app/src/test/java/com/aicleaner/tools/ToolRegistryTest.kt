package com.aicleaner.tools

import com.aicleaner.engine.ShellEngine
import com.aicleaner.ai.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for ToolRegistry.
 */
class ToolRegistryTest {

    private lateinit var mockShell: ShellEngine
    private lateinit var registry: ToolRegistry

    @Before
    fun setup() {
        mockShell = mock(ShellEngine::class.java)
        registry = ToolRegistry(mockShell)
    }

    @Test
    fun `registry has all 8 built-in tools`() {
        val tools = registry.getAllTools()
        assertEquals(8, tools.size)
    }

    @Test
    fun `registry contains expected tool names`() {
        val expectedNames = setOf(
            "list_directory",
            "find_files",
            "get_file_info",
            "delete_file",
            "move_file",
            "copy_file",
            "get_storage_summary",
            "execute_shell"
        )

        val actualNames = registry.getAllTools().map { it.name }.toSet()
        assertEquals(expectedNames, actualNames)
    }

    @Test
    fun `getTool returns correct tool`() {
        val tool = registry.getTool("list_directory")
        assertNotNull(tool)
        assertEquals("list_directory", tool?.name)
    }

    @Test
    fun `getTool returns null for unknown tool`() {
        val tool = registry.getTool("nonexistent_tool")
        assertNull(tool)
    }

    @Test
    fun `getToolDefinitions returns valid OpenAI format`() {
        val defs = registry.getToolDefinitions()
        assertEquals(8, defs.size)

        defs.forEach { def ->
            assertTrue("Name should not be empty", def.name.isNotEmpty())
            assertTrue("Description should not be empty", def.description.isNotEmpty())
            assertNotNull("Parameters should not be null", def.parameters)
            assertEquals("Should be object type", "object", def.parameters.optString("type"))
        }
    }

    @Test
    fun `execute queues destructive tools for confirmation`() = runBlocking {
        val result = registry.execute(
            ToolCall(
                id = "call_1",
                name = "delete_file",
                arguments = JSONObject().put("path", "/sdcard/Download/old.apk")
            )
        )

        assertFalse(result.success)
        assertEquals(true, result.metadata["requires_confirmation"])
        assertEquals(1, registry.getPendingActions().size)
    }

    @Test
    fun `cancelPendingAction removes queued destructive action`() = runBlocking {
        val result = registry.execute(
            ToolCall(
                id = "call_1",
                name = "move_file",
                arguments = JSONObject()
                    .put("source", "/sdcard/Download/file.pdf")
                    .put("destination", "/sdcard/Documents/file.pdf")
            )
        )
        val actionId = result.metadata["action_id"] as String

        assertTrue(registry.cancelPendingAction(actionId))
        assertTrue(registry.getPendingActions().isEmpty())
    }
}

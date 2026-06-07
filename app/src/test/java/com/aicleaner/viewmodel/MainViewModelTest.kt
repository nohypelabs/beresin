package com.aicleaner.viewmodel

import com.aicleaner.ai.AgentStep
import com.aicleaner.ai.StepType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for ViewModel data classes and logic.
 * Note: Full ViewModel tests require Android context (Robolectric).
 */
class MainViewModelTest {

    // ==================== ChatMessage Tests ====================

    @Test
    fun `ChatMessage User has correct properties`() {
        val msg = ChatMessage.User(text = "Hello", timestamp = 12345L)

        assertEquals("Hello", msg.text)
        assertEquals(12345L, msg.timestamp)
        assertTrue(msg is ChatMessage)
    }

    @Test
    fun `ChatMessage Agent has correct properties`() {
        val steps = listOf(
            AgentStep(
                iteration = 1,
                type = StepType.TOOL_CALL,
                toolName = "list_directory",
                content = "Listing files"
            )
        )

        val msg = ChatMessage.Agent(
            text = "Found 10 files",
            steps = steps,
            success = true,
            iterations = 2,
            timestamp = 12345L
        )

        assertEquals("Found 10 files", msg.text)
        assertEquals(1, msg.steps.size)
        assertTrue(msg.success)
        assertEquals(2, msg.iterations)
    }

    @Test
    fun `ChatMessage Agent can represent error`() {
        val msg = ChatMessage.Agent(
            text = "Error: API timeout",
            success = false
        )

        assertFalse(msg.success)
        assertTrue(msg.text.contains("Error"))
    }

    @Test
    fun `ChatMessage timestamp defaults to current time`() {
        val before = System.currentTimeMillis()
        val msg = ChatMessage.User(text = "Test")
        val after = System.currentTimeMillis()

        assertTrue(msg.timestamp in before..after)
    }

    // ==================== AgentStep Tests ====================

    @Test
    fun `AgentStep TOOL_CALL has correct type`() {
        val step = AgentStep(
            iteration = 1,
            type = StepType.TOOL_CALL,
            toolName = "find_files",
            toolArgs = """{"path": "/sdcard"}""",
            content = "Finding files..."
        )

        assertEquals(StepType.TOOL_CALL, step.type)
        assertEquals("find_files", step.toolName)
        assertNotNull(step.toolArgs)
    }

    @Test
    fun `AgentStep RESPONSE has correct type`() {
        val step = AgentStep(
            iteration = 1,
            type = StepType.RESPONSE,
            content = "Here are the results..."
        )

        assertEquals(StepType.RESPONSE, step.type)
        assertNull(step.toolName)
    }

    @Test
    fun `AgentStep can be updated with result`() {
        val step = AgentStep(
            iteration = 1,
            type = StepType.TOOL_CALL,
            toolName = "delete_file",
            content = "Deleting..."
        )

        // Simulate update after execution
        step.result = "Deleted successfully"
        step.success = true
        step.content = "✅ delete_file: Deleted successfully"

        assertEquals("Deleted successfully", step.result)
        assertTrue(step.success == true)
        assertTrue(step.content.contains("✅"))
    }

    @Test
    fun `AgentStep can represent failure`() {
        val step = AgentStep(
            iteration = 1,
            type = StepType.TOOL_CALL,
            toolName = "delete_file",
            content = "Deleting..."
        )

        step.result = "Permission denied"
        step.success = false
        step.content = "❌ delete_file: Permission denied"

        assertFalse(step.success == true)
        assertTrue(step.content.contains("❌"))
    }

    // ==================== StepType Tests ====================

    @Test
    fun `StepType has expected values`() {
        val values = StepType.entries

        assertEquals(2, values.size)
        assertTrue(values.contains(StepType.TOOL_CALL))
        assertTrue(values.contains(StepType.RESPONSE))
    }
}

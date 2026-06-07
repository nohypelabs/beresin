package com.aicleaner.ai

import com.aicleaner.ai.provider.*
import com.aicleaner.tools.ToolRegistry
import com.aicleaner.tools.ToolResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for AgentEngine (agentic loop).
 */
class AgentEngineTest {

    private lateinit var mockTools: ToolRegistry
    private lateinit var agent: AgentEngine

    @Before
    fun setup() {
        mockTools = mock(ToolRegistry::class.java)
        agent = AgentEngine(mockTools)

        // Mock tool definitions
        `when`(mockTools.getToolDefinitions()).thenReturn(emptyList())
    }

    @After
    fun tearDown() {
        org.mockito.Mockito.reset(mockTools)
    }

    @Test
    fun `agent returns response when no tool calls`() = runBlocking {
        val mockProvider = MockAIProvider(
            response = ChatResponse(
                text = "Done! Storage is clean.",
                toolCalls = emptyList()
            )
        )

        val result = agent.run(
            provider = mockProvider,
            userRequest = "Clean my storage"
        )

        assertTrue(result.success)
        assertEquals("Done! Storage is clean.", result.finalResponse)
        assertEquals(1, result.iterations)
    }

    @Test
    fun `agent executes tool calls and continues`() = runBlocking {
        // First response: tool call
        // Second response: final text
        val mockProvider = MockAIProvider(
            responses = listOf(
                ChatResponse(
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_1",
                            name = "list_directory",
                            arguments = JSONObject().put("path", "/sdcard")
                        )
                    )
                ),
                ChatResponse(
                    text = "Found 10 files in /sdcard",
                    toolCalls = emptyList()
                )
            )
        )

        // Mock tool execution
        `when`(mockTools.execute(anyNonNull())).thenReturn(
            ToolResult(true, "file1.txt\nfile2.txt")
        )

        val steps = mutableListOf<AgentStep>()
        val result = agent.run(
            provider = mockProvider,
            userRequest = "List files",
            onStep = { steps.add(it) }
        )

        assertTrue(result.success)
        assertEquals(2, result.iterations)
        assertTrue(result.finalResponse.contains("Found 10 files"))

        // Verify steps were recorded
        assertTrue(steps.any { it.type == StepType.TOOL_CALL })
        assertTrue(steps.any { it.type == StepType.RESPONSE })
    }

    @Test
    fun `agent handles tool execution error gracefully`() = runBlocking {
        val mockProvider = MockAIProvider(
            responses = listOf(
                ChatResponse(
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_1",
                            name = "delete_file",
                            arguments = JSONObject().put("path", "/sdcard/file.txt")
                        )
                    )
                ),
                ChatResponse(
                    text = "Failed to delete file",
                    toolCalls = emptyList()
                )
            )
        )

        // Mock tool execution failure
        `when`(mockTools.execute(anyNonNull())).thenReturn(
            ToolResult(false, "", "Permission denied")
        )

        val result = agent.run(
            provider = mockProvider,
            userRequest = "Delete a file"
        )

        assertTrue(result.success) // Agent still completes
        assertEquals(2, result.iterations)
    }

    @Test
    fun `agent stops at max iterations`() = runBlocking {
        // Create a provider that always returns tool calls
        val infiniteProvider = object : AIProvider {
            override val name = "Infinite"
            private var callCount = 0

            override suspend fun chat(request: ChatRequest): ChatResponse {
                callCount++
                return ChatResponse(
                    text = "",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_$callCount",
                            name = "list_directory",
                            arguments = JSONObject().put("path", "/sdcard")
                        )
                    )
                )
            }
        }

        `when`(mockTools.execute(anyNonNull())).thenReturn(
            ToolResult(true, "file.txt")
        )

        val result = agent.run(
            provider = infiniteProvider,
            userRequest = "Do something forever"
        )

        assertFalse(result.success)
        assertTrue(result.error?.contains("Max iterations") == true)
        assertEquals(20, result.iterations) // MAX_ITERATIONS
    }

    @Test
    fun `agent handles provider exception`() = runBlocking {
        val errorProvider = object : AIProvider {
            override val name = "Error"
            override suspend fun chat(request: ChatRequest): ChatResponse {
                throw RuntimeException("API rate limit exceeded")
            }
        }

        val result = agent.run(
            provider = errorProvider,
            userRequest = "Do something"
        )

        assertFalse(result.success)
        assertTrue(result.error?.contains("rate limit") == true)
    }

    @Test
    fun `agent passes system prompt to provider`() = runBlocking {
        var receivedRequest: ChatRequest? = null
        val captureProvider = object : AIProvider {
            override val name = "Capture"
            override suspend fun chat(request: ChatRequest): ChatResponse {
                receivedRequest = request
                return ChatResponse(text = "Done", toolCalls = emptyList())
            }
        }

        agent.run(
            provider = captureProvider,
            userRequest = "Test"
        )

        assertNotNull(receivedRequest)
        assertTrue(receivedRequest!!.systemPrompt?.contains("Beresin") == true)
        assertTrue(receivedRequest!!.systemPrompt?.contains("/sdcard") == true)
    }
}

/**
 * Helper to fix Mockito any() returning null in Kotlin.
 */
private fun <T> anyNonNull(): T {
    org.mockito.ArgumentMatchers.any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}

/**
 * Mock AI provider for testing.
 */
private class MockAIProvider(
    private val response: ChatResponse? = null,
    private val responses: List<ChatResponse>? = null
) : AIProvider {
    override val name = "Mock"
    private var callIndex = 0

    override suspend fun chat(request: ChatRequest): ChatResponse {
        return if (responses != null) {
            responses[callIndex++ % responses.size]
        } else {
            response!!
        }
    }
}

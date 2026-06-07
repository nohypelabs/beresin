package com.aicleaner.ai

import android.util.Log
import com.aicleaner.ai.provider.*
import com.aicleaner.tools.ToolRegistry
import org.json.JSONObject

/**
 * Agentic loop engine.
 * Manages the conversation between user, AI model, and tools.
 *
 * Flow:
 * 1. User sends request
 * 2. AI decides what tools to call
 * 3. We execute tools and return results
 * 4. AI processes results and decides next step
 * 5. Repeat until AI says "done" (no more tool calls)
 */
class AgentEngine(
    private val tools: ToolRegistry
) {
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_ITERATIONS = 20  // Safety limit
    }

    /**
     * Run the agentic loop.
     */
    suspend fun run(
        provider: AIProvider,
        userRequest: String,
        onStep: (AgentStep) -> Unit = {}
    ): AgentResult {
        val messages = mutableListOf<Message>()
        val steps = mutableListOf<AgentStep>()
        var iteration = 0

        // Add user message
        messages.add(Message(role = "user", content = userRequest))

        // Get tool definitions
        val toolDefs = tools.getToolDefinitions()

        // System prompt
        val systemPrompt = getSystemPrompt()

        while (iteration < MAX_ITERATIONS) {
            iteration++
            Log.d(TAG, "Iteration $iteration")

            try {
                // Call AI
                val response = provider.chat(ChatRequest(
                    messages = messages,
                    tools = toolDefs,
                    systemPrompt = systemPrompt,
                    maxTokens = 4096
                ))

                // If no tool calls, we're done
                if (!response.hasToolCalls) {
                    val step = AgentStep(
                        iteration = iteration,
                        type = StepType.RESPONSE,
                        content = response.text
                    )
                    steps.add(step)
                    onStep(step)

                    return AgentResult(
                        success = true,
                        finalResponse = response.text,
                        steps = steps,
                        iterations = iteration,
                        usage = response.usage
                    )
                }

                // Add assistant message with tool calls
                messages.add(Message(
                    role = "assistant",
                    content = response.text.ifEmpty { null },
                    toolCalls = response.toolCalls
                ))

                // Execute each tool call
                for (toolCall in response.toolCalls) {
                    val step = AgentStep(
                        iteration = iteration,
                        type = StepType.TOOL_CALL,
                        toolName = toolCall.name,
                        toolArgs = toolCall.arguments.toString(),
                        content = "Calling ${toolCall.name}..."
                    )
                    steps.add(step)
                    onStep(step)

                    // Execute tool
                    val result = tools.execute(toolCall)
                    Log.d(TAG, "Tool ${toolCall.name}: success=${result.success}, output=${result.output.take(100)}")

                    // Update step with result
                    step.result = result.output
                    step.success = result.success
                    step.content = if (result.success) {
                        "✅ ${toolCall.name}: ${result.output.take(100)}"
                    } else {
                        "❌ ${toolCall.name}: ${result.error}"
                    }
                    onStep(step)

                    // Add tool result to conversation
                    messages.add(Message(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = if (result.success) result.output else "Error: ${result.error}"
                    ))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in iteration $iteration: ${e.message}")
                return AgentResult(
                    success = false,
                    finalResponse = "Error: ${e.message}",
                    steps = steps,
                    iterations = iteration,
                    error = e.message
                )
            }
        }

        // Max iterations reached
        return AgentResult(
            success = false,
            finalResponse = "Reached maximum iterations ($MAX_ITERATIONS). Task may be incomplete.",
            steps = steps,
            iterations = iteration,
            error = "Max iterations exceeded"
        )
    }

    /**
     * System prompt for the AI agent.
     */
    private fun getSystemPrompt(): String {
        return """You are Beresin, an AI storage cleaning assistant for Android phones.

Your job is to help users clean, organize, and manage their phone storage.

AVAILABLE TOOLS:
- list_directory: List files in a directory with sizes
- find_files: Find files by name, size, type
- get_file_info: Get details about a specific file
- delete_file: Delete files or directories
- move_file: Move/rename files
- copy_file: Copy files
- get_storage_summary: Get overall storage overview
- execute_shell: Run read-only shell commands

RULES:
1. ALWAYS explain what you're doing before calling a tool
2. Ask for confirmation before deleting files
3. Only operate under /sdcard (never touch system files)
4. Be thorough but efficient — don't make unnecessary tool calls
5. Report results clearly in a user-friendly format
6. Use Indonesian if the user writes in Indonesian
7. When organizing files, suggest a clear folder structure
8. Track how much space was freed and report at the end

SAFETY:
- Never delete /sdcard root
- Never delete system files
- Always show what will be deleted before deleting
- If unsure, ask the user"""
    }
}

/**
 * Result from the agent loop.
 */
data class AgentResult(
    val success: Boolean,
    val finalResponse: String,
    val steps: List<AgentStep>,
    val iterations: Int,
    val usage: TokenUsage? = null,
    val error: String? = null
)

/**
 * A single step in the agent loop.
 */
data class AgentStep(
    val iteration: Int,
    val type: StepType,
    val toolName: String? = null,
    val toolArgs: String? = null,
    var content: String = "",
    var result: String? = null,
    var success: Boolean? = null
)

enum class StepType {
    TOOL_CALL,
    RESPONSE
}

package com.aicleaner.ai

import android.util.Log
import com.aicleaner.ai.provider.*
import com.aicleaner.tools.ToolRegistry
import org.json.JSONObject

/**
 * Agentic loop engine.
 * Manages the conversation between user, AI model, and tools.
 */
class AgentEngine(
    private val tools: ToolRegistry
) {
    companion object {
        private const val TAG = "AgentEngine"
        private const val MAX_ITERATIONS = 20
    }

    /**
     * Run the agentic loop.
     */
    suspend fun run(
        provider: AIProvider,
        userRequest: String,
        userName: String = "",
        onStep: (AgentStep) -> Unit = {}
    ): AgentResult {
        val messages = mutableListOf<Message>()
        val steps = mutableListOf<AgentStep>()
        var iteration = 0

        messages.add(Message(role = "user", content = userRequest))

        val toolDefs = tools.getToolDefinitions()
        val systemPrompt = getSystemPrompt(userName)

        while (iteration < MAX_ITERATIONS) {
            iteration++
            Log.d(TAG, "Iteration $iteration")

            try {
                val response = provider.chat(ChatRequest(
                    messages = messages,
                    tools = toolDefs,
                    systemPrompt = systemPrompt,
                    maxTokens = 4096
                ))

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

                messages.add(Message(
                    role = "assistant",
                    content = response.text.ifEmpty { null },
                    toolCalls = response.toolCalls
                ))

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

                    val result = tools.execute(toolCall)
                    Log.d(TAG, "Tool ${toolCall.name}: success=${result.success}, output=${result.output.take(100)}")

                    step.result = result.output
                    step.success = result.success
                    step.content = if (result.success) {
                        "✅ ${toolCall.name}: ${result.output.take(100)}"
                    } else {
                        "❌ ${toolCall.name}: ${result.error}"
                    }
                    onStep(step)

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

        return AgentResult(
            success = false,
            finalResponse = "Reached maximum iterations ($MAX_ITERATIONS). Task may be incomplete.",
            steps = steps,
            iterations = iteration,
            error = "Max iterations exceeded"
        )
    }

    /**
     * System prompt — Dora personality.
     */
    private fun getSystemPrompt(userName: String): String {
        val nameContext = if (userName.isNotBlank()) {
            "The user's name is $userName. Use their name occasionally to make it personal, but don't overdo it."
        } else {
            ""
        }

        return """You are Dora, the AI assistant inside Beresin — an Android phone cleaner app.

PERSONALITY:
- You're casual, friendly, and speak like a real person — not a robot
- Use Indonesian slang naturally (lo/gw, dong, sih, nih, ya, bro)
- Be warm and slightly playful, but still helpful and efficient
- Keep responses SHORT and conversational — like texting a friend
- Use emoji sparingly (1-2 per message max)
- Never say "I'm an AI" or break character
- You're the cool friend who happens to be really good at cleaning phones
$nameContext

YOUR JOB:
Help users clean, organize, and manage their phone storage. You have access to tools that can scan, find, and manage files on their Android device.

AVAILABLE TOOLS:
- list_directory: List files in a directory with sizes
- find_files: Find files by name, size, type
- get_file_info: Get details about a specific file
- delete_file: Delete files or directories
- move_file: Move/rename files
- copy_file: Copy files
- get_storage_summary: Get overall storage overview
- execute_shell: Run read-only shell commands

RESPONSE STYLE:
- For scan results: give a quick summary first, then details if asked
- Format file sizes nicely (MB/GB, not bytes)
- When suggesting cleanup, be specific: "Ada 2.3GB file sampah, mau gw bersihin?"
- Always ask before deleting anything
- After cleaning, celebrate briefly: "Done! Storage lo naik 15% 🎉"
- If user says "gas" or "bersihin semua" → proceed with cleanup

RULES:
1. ALWAYS explain what you're doing before calling a tool
2. Ask for confirmation before deleting files
3. Only operate under /sdcard (never touch system files)
4. Be thorough but efficient — don't make unnecessary tool calls
5. Report results clearly in a user-friendly format
6. Always respond in Indonesian (casual/informal)
7. Track how much space was freed and report at the end

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

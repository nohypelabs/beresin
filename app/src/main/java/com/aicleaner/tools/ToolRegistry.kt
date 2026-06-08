package com.aicleaner.tools

import com.aicleaner.ai.provider.ToolDefinition
import com.aicleaner.ai.provider.ToolCall
import com.aicleaner.engine.ShellEngine
import org.json.JSONObject
import java.util.UUID

/**
 * Registry of all available tools for the AI agent.
 * Each tool is a self-contained operation on the Android filesystem.
 */
class ToolRegistry(private val shell: ShellEngine) {

    private val tools = mutableMapOf<String, Tool>()
    private val pendingActions = linkedMapOf<String, PendingToolAction>()

    private val destructiveTools = setOf(
        "delete_file",
        "move_file",
        "copy_file"
    )

    init {
        // Register all built-in tools
        register(ListDirectoryTool(shell))
        register(FindFilesTool(shell))
        register(GetFileInfoTool(shell))
        register(DeleteFileTool(shell))
        register(MoveFileTool(shell))
        register(CopyFileTool(shell))
        register(GetStorageSummaryTool(shell))
        register(ExecuteShellTool(shell))
    }

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun getPendingActions(): List<PendingToolAction> = pendingActions.values.toList()

    fun cancelPendingAction(actionId: String): Boolean {
        return pendingActions.remove(actionId) != null
    }

    suspend fun confirmPendingAction(actionId: String): ToolResult {
        val action = pendingActions.remove(actionId)
            ?: return ToolResult(false, "", "Pending action not found or already handled")

        val confirmedArgs = JSONObject(action.toolCall.arguments.toString()).apply {
            put("__beresin_confirmed", true)
        }

        val confirmedCall = ToolCall(
            id = action.toolCall.id,
            name = action.toolCall.name,
            arguments = confirmedArgs
        )

        return executeDirect(confirmedCall)
    }

    /**
     * Get tool definitions in OpenAI function calling format.
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return tools.values.map { tool ->
            ToolDefinition(
                name = tool.name,
                description = tool.description,
                parameters = buildParametersSchema(tool)
            )
        }
    }

    /**
     * Execute a tool call.
     */
    suspend fun execute(toolCall: ToolCall): ToolResult {
        if (toolCall.name in destructiveTools &&
            !toolCall.arguments.optBoolean("__beresin_confirmed", false)
        ) {
            return queueDestructiveAction(toolCall)
        }

        return executeDirect(toolCall)
    }

    private suspend fun executeDirect(toolCall: ToolCall): ToolResult {
        val tool = tools[toolCall.name]
            ?: return ToolResult(false, "", "Unknown tool: ${toolCall.name}")

        return try {
            tool.execute(toolCall.arguments)
        } catch (e: Exception) {
            ToolResult(false, "", "Tool error: ${e.message}")
        }
    }

    private fun queueDestructiveAction(toolCall: ToolCall): ToolResult {
        val actionId = UUID.randomUUID().toString()
        val action = PendingToolAction(
            id = actionId,
            toolCall = toolCall,
            title = summarizeTitle(toolCall),
            details = summarizeDetails(toolCall)
        )
        pendingActions[actionId] = action

        return ToolResult(
            success = false,
            output = "",
            error = "User confirmation required before ${toolCall.name}",
            metadata = mapOf(
                "requires_confirmation" to true,
                "action_id" to actionId,
                "title" to action.title,
                "details" to action.details
            )
        )
    }

    private fun summarizeTitle(toolCall: ToolCall): String {
        return when (toolCall.name) {
            "delete_file" -> "Hapus file/folder?"
            "move_file" -> "Pindahkan file/folder?"
            "copy_file" -> "Salin file/folder?"
            else -> "Konfirmasi operasi"
        }
    }

    private fun summarizeDetails(toolCall: ToolCall): String {
        val args = toolCall.arguments
        return when (toolCall.name) {
            "delete_file" -> args.optString("path", "(path kosong)")
            "move_file" -> "${args.optString("source", "(source kosong)")} -> ${args.optString("destination", "(destination kosong)")}"
            "copy_file" -> "${args.optString("source", "(source kosong)")} -> ${args.optString("destination", "(destination kosong)")}"
            else -> args.toString()
        }
    }

    /**
     * Build JSON Schema for tool parameters.
     */
    private fun buildParametersSchema(tool: Tool): JSONObject {
        val properties = JSONObject()
        val required = mutableListOf<String>()

        tool.parameters.forEach { (name, param) ->
            properties.put(name, JSONObject().apply {
                put("type", param.type)
                put("description", param.description)
            })
            if (param.required) {
                required.add(name)
            }
        }

        return JSONObject().apply {
            put("type", "object")
            put("properties", properties)
            put("required", required)
        }
    }
}

data class PendingToolAction(
    val id: String,
    val toolCall: ToolCall,
    val title: String,
    val details: String
)

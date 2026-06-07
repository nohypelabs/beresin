package com.aicleaner.tools

import com.aicleaner.ai.provider.ToolDefinition
import com.aicleaner.ai.provider.ToolCall
import com.aicleaner.engine.ShellEngine
import org.json.JSONObject

/**
 * Registry of all available tools for the AI agent.
 * Each tool is a self-contained operation on the Android filesystem.
 */
class ToolRegistry(private val shell: ShellEngine) {

    private val tools = mutableMapOf<String, Tool>()

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
        val tool = tools[toolCall.name]
            ?: return ToolResult(false, "", "Unknown tool: ${toolCall.name}")

        return try {
            tool.execute(toolCall.arguments)
        } catch (e: Exception) {
            ToolResult(false, "", "Tool error: ${e.message}")
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

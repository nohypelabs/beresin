package com.aicleaner.tools

import org.json.JSONObject

/**
 * Base class for all tools available to the AI agent.
 */
abstract class Tool(
    val name: String,
    val description: String,
    val parameters: Map<String, ParamDef>
) {
    abstract suspend fun execute(args: JSONObject): ToolResult
}

data class ParamDef(
    val type: String,
    val description: String,
    val required: Boolean = false
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

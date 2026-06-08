package com.aicleaner.ai.provider

import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified AI Provider interface.
 * All providers (Claude, OpenAI, Gemini, MiMo, etc.) implement this.
 * Tool calls are always returned in OpenAI-compatible format.
 */
interface AIProvider {

    /**
     * Send a message with tools and get a response.
     * Returns a unified response that may contain tool calls.
     */
    suspend fun chat(request: ChatRequest): ChatResponse

    /**
     * Provider name for display.
     */
    val name: String
}

/**
 * Unified chat request — works for all providers.
 */
data class ChatRequest(
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    val systemPrompt: String? = null,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.3
)

/**
 * Message in the conversation.
 */
data class Message(
    val role: String,       // "user", "assistant", "tool"
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null   // For tool result messages
)

/**
 * Tool definition — OpenAI function calling format.
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject   // JSON Schema
)

/**
 * Tool call from the AI model.
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

/**
 * Unified chat response.
 */
data class ChatResponse(
    val text: String,
    val toolCalls: List<ToolCall>,
    val hasToolCalls: Boolean = toolCalls.isNotEmpty(),
    val usage: TokenUsage? = null,
    val quota: QuotaStatus? = null,
    val rawResponse: String? = null
)

/**
 * Token usage stats.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

data class QuotaStatus(
    val remaining: Int,
    val total: Int,
    val isPremium: Boolean,
    val resetAt: String? = null
)

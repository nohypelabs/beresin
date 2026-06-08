package com.aicleaner.ai.provider

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible API provider.
 * Works with:
 * - OpenAI (GPT-4o, GPT-4, etc.)
 * - Xiaomi MiMo (via vLLM/SGLang)
 * - DeepSeek
 * - Any OpenAI-compatible endpoint (Ollama, Together AI, etc.)
 */
class OpenAICompatibleProvider(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    override val name: String = "OpenAI-Compatible"
) : AIProvider {

    companion object {
        private const val TAG = "OpenAIProvider"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)  // Reasoning models can be slow
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        Log.d(TAG, "chat() called with ${request.messages.size} messages")
        val body = buildRequestBody(request)

        Log.d(TAG, "Calling $name API at $baseUrl/chat/completions with model $model")
        Log.d(TAG, "Messages: ${request.messages.size}, Tools: ${request.tools?.size ?: 0}")

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from $name API")

        if (!response.isSuccessful) {
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw Exception("$name API error ${response.code}: ${responseBody.take(200)}")
        }

        return parseResponse(responseBody)
    }

    /**
     * Build OpenAI-format request body with tools.
     */
    private fun buildRequestBody(request: ChatRequest): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)

            // Messages
            put("messages", JSONArray().apply {
                // System prompt
                request.systemPrompt?.let { sys ->
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", sys)
                    })
                }

                // Conversation messages
                request.messages.forEach { msg ->
                    put(encodeMessage(msg))
                }
            })

            // Tools (OpenAI function calling format)
            request.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        tools.forEach { tool ->
                            put(JSONObject().apply {
                                put("type", "function")
                                put("function", JSONObject().apply {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                })
                            })
                        }
                    })
                    put("tool_choice", "auto")
                }
            }
        }
    }

    /**
     * Encode a Message to OpenAI JSON format.
     */
    private fun encodeMessage(msg: Message): JSONObject {
        return JSONObject().apply {
            put("role", msg.role)

            when (msg.role) {
                "tool" -> {
                    // Tool result message
                    put("content", msg.content ?: "")
                    msg.toolCallId?.let { put("tool_call_id", it) }
                }
                "assistant" -> {
                    // Assistant message, may have tool calls
                    msg.content?.let { put("content", it) }
                    msg.toolCalls?.let { calls ->
                        if (calls.isNotEmpty()) {
                            put("tool_calls", JSONArray().apply {
                                calls.forEach { call ->
                                    put(JSONObject().apply {
                                        put("id", call.id)
                                        put("type", "function")
                                        put("function", JSONObject().apply {
                                            put("name", call.name)
                                            put("arguments", call.arguments.toString())
                                        })
                                    })
                                }
                            })
                        }
                    }
                }
                else -> {
                    // User message
                    put("content", msg.content ?: "")
                }
            }
        }
    }

    /**
     * Parse OpenAI-format response.
     */
    private fun parseResponse(responseBody: String): ChatResponse {
        val json = JSONObject(responseBody)

        // Extract usage
        val usage = json.optJSONObject("usage")?.let { u ->
            TokenUsage(
                inputTokens = u.optInt("prompt_tokens", 0),
                outputTokens = u.optInt("completion_tokens", 0),
                totalTokens = u.optInt("total_tokens", 0)
            )
        }

        // Extract choice
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) {
            return ChatResponse(
                text = "No response from model",
                toolCalls = emptyList(),
                usage = usage,
                rawResponse = responseBody
            )
        }

        val choice = choices.getJSONObject(0)
        val message = choice.getJSONObject("message")

        // Extract text content — MiMo uses reasoning_content for thinking, content for response
        val text = message.optString("content", "").ifEmpty {
            message.optString("reasoning_content", "")
        }

        // Extract tool calls
        val toolCalls = mutableListOf<ToolCall>()
        val toolCallsArray = message.optJSONArray("tool_calls")
        if (toolCallsArray != null) {
            for (i in 0 until toolCallsArray.length()) {
                val tc = toolCallsArray.getJSONObject(i)
                val function = tc.getJSONObject("function")

                // Parse arguments (could be string or object)
                val argsStr = function.getString("arguments")
                val args = try {
                    JSONObject(argsStr)
                } catch (e: Exception) {
                    JSONObject()
                }

                toolCalls.add(ToolCall(
                    id = tc.optString("id", "call_${UUID.randomUUID()}"),
                    name = function.getString("name"),
                    arguments = args
                ))
            }
        }

        Log.d(TAG, "Response: text=${text.take(100)}, toolCalls=${toolCalls.size}")

        return ChatResponse(
            text = text,
            toolCalls = toolCalls,
            usage = usage,
            rawResponse = responseBody
        )
    }
}

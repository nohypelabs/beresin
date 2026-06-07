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
 * Anthropic Claude API provider.
 * Uses Claude's native tool_use format.
 */
class ClaudeProvider(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514"
) : AIProvider {

    companion object {
        private const val TAG = "ClaudeProvider"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
    }

    override val name = "Claude"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val body = buildRequestBody(request)

        Log.d(TAG, "Calling Claude API with model $model")

        val httpRequest = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from Claude API")

        if (!response.isSuccessful) {
            Log.e(TAG, "API error ${response.code}: $responseBody")
            throw Exception("Claude API error ${response.code}: ${responseBody.take(200)}")
        }

        return parseResponse(responseBody)
    }

    private fun buildRequestBody(request: ChatRequest): JSONObject {
        return JSONObject().apply {
            put("model", model)
            put("max_tokens", request.maxTokens)

            // System prompt
            request.systemPrompt?.let { put("system", it) }

            // Messages
            put("messages", JSONArray().apply {
                request.messages.forEach { msg ->
                    put(encodeMessage(msg))
                }
            })

            // Tools
            request.tools?.let { tools ->
                if (tools.isNotEmpty()) {
                    put("tools", JSONArray().apply {
                        tools.forEach { tool ->
                            put(JSONObject().apply {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.parameters)
                            })
                        }
                    })
                }
            }
        }
    }

    private fun encodeMessage(msg: Message): JSONObject {
        return JSONObject().apply {
            put("role", msg.role)

            when (msg.role) {
                "user" -> {
                    put("content", msg.content ?: "")
                }
                "assistant" -> {
                    val content = JSONArray()

                    // Text content
                    msg.content?.let {
                        if (it.isNotEmpty()) {
                            content.put(JSONObject().apply {
                                put("type", "text")
                                put("text", it)
                            })
                        }
                    }

                    // Tool use blocks
                    msg.toolCalls?.forEach { call ->
                        content.put(JSONObject().apply {
                            put("type", "tool_use")
                            put("id", call.id)
                            put("name", call.name)
                            put("input", call.arguments)
                        })
                    }

                    put("content", content)
                }
                "tool" -> {
                    // Tool result
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "tool_result")
                            msg.toolCallId?.let { put("tool_use_id", it) }
                            put("content", msg.content ?: "")
                        })
                    })
                }
            }
        }
    }

    private fun parseResponse(responseBody: String): ChatResponse {
        val json = JSONObject(responseBody)

        // Usage
        val usage = json.optJSONObject("usage")?.let { u ->
            TokenUsage(
                inputTokens = u.optInt("input_tokens", 0),
                outputTokens = u.optInt("output_tokens", 0),
                totalTokens = u.optInt("input_tokens", 0) + u.optInt("output_tokens", 0)
            )
        }

        // Content blocks
        val content = json.getJSONArray("content")
        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<ToolCall>()

        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            when (block.getString("type")) {
                "text" -> textParts.add(block.getString("text"))
                "tool_use" -> {
                    toolCalls.add(ToolCall(
                        id = block.getString("id"),
                        name = block.getString("name"),
                        arguments = block.optJSONObject("input") ?: JSONObject()
                    ))
                }
            }
        }

        val text = textParts.joinToString("\n")

        Log.d(TAG, "Response: text=${text.take(100)}, toolCalls=${toolCalls.size}")

        return ChatResponse(
            text = text,
            toolCalls = toolCalls,
            usage = usage,
            rawResponse = responseBody
        )
    }
}

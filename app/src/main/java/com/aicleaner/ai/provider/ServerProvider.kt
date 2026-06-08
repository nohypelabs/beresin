package com.aicleaner.ai.provider

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Server-based AI provider.
 * Proxies all AI requests through our backend server.
 * Users don't need to configure API keys.
 */
class ServerProvider(
    private val serverUrl: String,
    private val provider: String = "openai",
    private val installId: String,
    private val premiumToken: String = "",
    private val turnId: String,
    private val appVersion: String = "1.0.0",
    override val name: String = "Server"
) : AIProvider {

    companion object {
        private const val TAG = "ServerProvider"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    var latestQuota: QuotaStatus? = null
        private set

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val body = buildRequestBody(request)

        Log.d(TAG, "Calling server at $serverUrl/api/chat (provider: $provider)")

        val httpRequest = Request.Builder()
            .url("$serverUrl/api/chat")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Beresin-Install-Id", installId)
            .addHeader("X-Beresin-Turn-Id", turnId)
            .addHeader("X-Beresin-App-Version", appVersion)
            .apply {
                if (premiumToken.isNotBlank()) {
                    addHeader("X-Beresin-Premium-Token", premiumToken)
                }
            }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw Exception("Empty response from server")

        if (!response.isSuccessful) {
            Log.e(TAG, "Server error ${response.code}: $responseBody")
            throw Exception("Server error ${response.code}: ${responseBody.take(200)}")
        }

        return parseResponse(responseBody)
    }

    /**
     * Build request body for server.
     */
    private fun buildRequestBody(request: ChatRequest): JSONObject {
        return JSONObject().apply {
            put("provider", provider)

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
                                put("parameters", tool.parameters)
                            })
                        }
                    })
                }
            }

            // System prompt
            request.systemPrompt?.let { put("systemPrompt", it) }

            // Max tokens
            put("maxTokens", request.maxTokens)
        }
    }

    /**
     * Encode message to JSON.
     */
    private fun encodeMessage(msg: Message): JSONObject {
        return JSONObject().apply {
            put("role", msg.role)

            when (msg.role) {
                "tool" -> {
                    put("content", msg.content ?: "")
                    msg.toolCallId?.let { put("toolCallId", it) }
                }
                "assistant" -> {
                    msg.content?.let { put("content", it) }
                    msg.toolCalls?.let { calls ->
                        if (calls.isNotEmpty()) {
                            put("toolCalls", JSONArray().apply {
                                calls.forEach { call ->
                                    put(JSONObject().apply {
                                        put("id", call.id)
                                        put("name", call.name)
                                        put("arguments", call.arguments)
                                    })
                                }
                            })
                        }
                    }
                }
                else -> {
                    put("content", msg.content ?: "")
                }
            }
        }
    }

    /**
     * Parse server response.
     */
    private fun parseResponse(responseBody: String): ChatResponse {
        val json = JSONObject(responseBody)

        val text = json.optString("text", "")

        val toolCalls = mutableListOf<ToolCall>()
        val toolCallsArray = json.optJSONArray("toolCalls")
        if (toolCallsArray != null) {
            for (i in 0 until toolCallsArray.length()) {
                val tc = toolCallsArray.getJSONObject(i)
                val args = tc.optJSONObject("arguments") ?: JSONObject()

                toolCalls.add(ToolCall(
                    id = tc.optString("id", "call_$i"),
                    name = tc.getString("name"),
                    arguments = args
                ))
            }
        }

        val usage = json.optJSONObject("usage")?.let { u ->
            TokenUsage(
                inputTokens = u.optInt("inputTokens", 0),
                outputTokens = u.optInt("outputTokens", 0),
                totalTokens = u.optInt("totalTokens", 0)
            )
        }

        val quota = json.optJSONObject("quota")?.let { q ->
            QuotaStatus(
                remaining = q.optInt("remaining", 0),
                total = q.optInt("total", 0),
                isPremium = q.optBoolean("isPremium", false),
                resetAt = q.optString("resetAt").ifBlank { null }
            )
        }
        latestQuota = quota

        Log.d(TAG, "Response: text=${text.take(100)}, toolCalls=${toolCalls.size}")

        return ChatResponse(
            text = text,
            toolCalls = toolCalls,
            usage = usage,
            quota = quota,
            rawResponse = responseBody
        )
    }
}

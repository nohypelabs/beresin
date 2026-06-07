package com.aicleaner.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AIEngine — handles AI API calls for storage analysis and cleanup commands.
 *
 * Supports:
 * - Claude API (Anthropic)
 * - OpenAI API (GPT)
 * - Google Gemini API
 */
class AIEngine(private val context: Context) {

    companion object {
        private const val TAG = "AIEngine"
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    }

    enum class Provider { CLAUDE, OPENAI, GEMINI }

    data class AIConfig(
        val provider: Provider = Provider.CLAUDE,
        val apiKey: String = "",
        val model: String = "claude-sonnet-4-20250514"
    )

    data class CleanAction(
        val type: String,      // "delete", "move", "organize"
        val target: String,    // file path or pattern
        val destination: String = "",  // for move operations
        val reason: String = "",
        val sizeBytes: Long = 0
    )

    data class AnalysisResult(
        val summary: String,
        val totalWaste: Long,
        val actions: List<CleanAction>,
        val categories: Map<String, List<String>>
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Analyze storage data and get cleanup recommendations from AI.
     */
    suspend fun analyzeStorage(
        storageInfo: String,
        config: AIConfig
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val prompt = buildAnalysisPrompt(storageInfo)
            val response = callAI(prompt, config)
            parseAnalysisResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Analysis failed: ${e.message}")
            AnalysisResult(
                summary = "Analysis failed: ${e.message}",
                totalWaste = 0,
                actions = emptyList(),
                categories = emptyMap()
            )
        }
    }

    /**
     * Get organized folder structure suggestion from AI.
     */
    suspend fun suggestOrganization(
        fileList: String,
        config: AIConfig
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val prompt = buildOrganizationPrompt(fileList)
            val response = callAI(prompt, config)
            parseAnalysisResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Organization suggestion failed: ${e.message}")
            AnalysisResult(
                summary = "Failed: ${e.message}",
                totalWaste = 0,
                actions = emptyList(),
                categories = emptyMap()
            )
        }
    }

    /**
     * Build the system prompt for AI.
     */
    private fun getSystemPrompt(): String {
        return """You are an Android storage cleanup assistant. You analyze file listings and storage data to recommend cleanup actions.

IMPORTANT: Always respond in valid JSON format with this exact structure:
{
  "summary": "Brief description of findings",
  "total_waste_bytes": 0,
  "categories": {
    "category_name": ["file1", "file2"]
  },
  "actions": [
    {
      "type": "delete",
      "target": "/path/to/file",
      "destination": "",
      "reason": "Why this should be deleted",
      "size_bytes": 0
    },
    {
      "type": "move",
      "target": "/path/to/file",
      "destination": "/path/to/destination/",
      "reason": "Why this should be moved",
      "size_bytes": 0
    },
    {
      "type": "organize",
      "target": "/path/to/folder",
      "destination": "/path/to/new/structure/",
      "reason": "How to organize this",
      "size_bytes": 0
    }
  ]
}

Rules:
- Only recommend deleting obvious junk: temp files, cache, duplicate APKs, empty files
- For organization, create meaningful folder structures
- Always explain your reasoning
- Size estimates should be realistic
- Respond in the same language as the user"""
    }

    /**
     * Build analysis prompt.
     */
    private fun buildAnalysisPrompt(storageInfo: String): String {
        return """Analyze this Android device storage and identify:
1. Junk files that can be safely deleted (temp, cache, duplicates, old APKs)
2. Large files that might be unnecessary
3. Files that should be organized into categories

Storage data:
$storageInfo

Respond with cleanup recommendations in JSON format."""
    }

    /**
     * Build organization prompt.
     */
    private fun buildOrganizationPrompt(fileList: String): String {
        return """Analyze these files and suggest an organized folder structure.
Group files by type and purpose. Create a clean directory structure.

Files:
$fileList

Respond with organization plan in JSON format. For the 'organize' action type,
the 'target' is the source folder and 'destination' is the recommended new location."""
    }

    /**
     * Call AI API based on provider.
     */
    private suspend fun callAI(prompt: String, config: AIConfig): String {
        return when (config.provider) {
            Provider.CLAUDE -> callClaude(prompt, config)
            Provider.OPENAI -> callOpenAI(prompt, config)
            Provider.GEMINI -> callGemini(prompt, config)
        }
    }

    /**
     * Call Claude API.
     */
    private fun callClaude(prompt: String, config: AIConfig): String {
        val body = JSONObject().apply {
            put("model", config.model)
            put("max_tokens", 4096)
            put("system", getSystemPrompt())
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url(CLAUDE_API_URL)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $responseBody")
        }

        // Extract text from Claude response
        val json = JSONObject(responseBody)
        val content = json.getJSONArray("content")
        return content.getJSONObject(0).getString("text")
    }

    /**
     * Call OpenAI API.
     */
    private fun callOpenAI(prompt: String, config: AIConfig): String {
        val body = JSONObject().apply {
            put("model", config.model.ifEmpty { "gpt-4o" })
            put("max_tokens", 4096)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", getSystemPrompt())
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val request = Request.Builder()
            .url(OPENAI_API_URL)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $responseBody")
        }

        val json = JSONObject(responseBody)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    /**
     * Call Gemini API.
     */
    private fun callGemini(prompt: String, config: AIConfig): String {
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "${getSystemPrompt()}\n\n$prompt")
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(GEMINI_API_URL)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", config.apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("API error ${response.code}: $responseBody")
        }

        val json = JSONObject(responseBody)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    /**
     * Parse AI response into AnalysisResult.
     */
    private fun parseAnalysisResponse(response: String): AnalysisResult {
        try {
            // Try to extract JSON from response (AI might wrap it in markdown)
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)

            val actions = mutableListOf<CleanAction>()
            val actionsArray = json.optJSONArray("actions") ?: JSONArray()

            for (i in 0 until actionsArray.length()) {
                val actionJson = actionsArray.getJSONObject(i)
                actions.add(CleanAction(
                    type = actionJson.optString("type", "delete"),
                    target = actionJson.optString("target", ""),
                    destination = actionJson.optString("destination", ""),
                    reason = actionJson.optString("reason", ""),
                    sizeBytes = actionJson.optLong("size_bytes", 0)
                ))
            }

            val categories = mutableMapOf<String, List<String>>()
            val categoriesJson = json.optJSONObject("categories") ?: JSONObject()
            categoriesJson.keys().forEach { key ->
                val files = mutableListOf<String>()
                val arr = categoriesJson.getJSONArray(key)
                for (i in 0 until arr.length()) {
                    files.add(arr.getString(i))
                }
                categories[key] = files
            }

            return AnalysisResult(
                summary = json.optString("summary", "Analysis complete"),
                totalWaste = json.optLong("total_waste_bytes", 0),
                actions = actions,
                categories = categories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse failed, returning raw response: ${e.message}")
            return AnalysisResult(
                summary = response.take(500),
                totalWaste = 0,
                actions = emptyList(),
                categories = emptyMap()
            )
        }
    }

    /**
     * Extract JSON from AI response (handles markdown code blocks).
     */
    private fun extractJson(text: String): String {
        // Try to find JSON in code blocks
        val codeBlockRegex = "```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*\\n?```".toRegex()
        val match = codeBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1]
        }

        // Try to find raw JSON
        val jsonRegex = "\\{[\\s\\S]*\\}".toRegex()
        val jsonMatch = jsonRegex.find(text)
        if (jsonMatch != null) {
            return jsonMatch.value
        }

        // Return as-is
        return text
    }
}

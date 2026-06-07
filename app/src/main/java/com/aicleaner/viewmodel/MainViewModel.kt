package com.aicleaner.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicleaner.ai.AgentEngine
import com.aicleaner.ai.AgentResult
import com.aicleaner.ai.AgentStep
import com.aicleaner.ai.provider.AIProvider
import com.aicleaner.ai.provider.ClaudeProvider
import com.aicleaner.ai.provider.OpenAICompatibleProvider
import com.aicleaner.engine.ShellEngine
import com.aicleaner.tools.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A single message in the chat conversation.
 */
sealed class ChatMessage {
    abstract val timestamp: Long

    /** User's message. */
    data class User(
        val text: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()

    /** Agent's response with steps. */
    data class Agent(
        val text: String,
        val steps: List<AgentStep> = emptyList(),
        val success: Boolean = true,
        val iterations: Int = 0,
        override val timestamp: Long = System.currentTimeMillis()
    ) : ChatMessage()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "beresin_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PROVIDER = "api_provider"
        private const val KEY_MODEL = "api_model"
        private const val KEY_BASE_URL = "api_base_url"
    }

    // Core components
    private val shell = ShellEngine(app)
    private val tools = ToolRegistry(shell)
    private val agent = AgentEngine(tools)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Welcome)
    val uiState: StateFlow<UiState> = _uiState

    // Agent steps (for live progress)
    private val _agentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val agentSteps: StateFlow<List<AgentStep>> = _agentSteps

    // Chat messages history
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    // API Config
    private var apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
    private var providerType = prefs.getString(KEY_PROVIDER, "mimo") ?: "mimo"
    private var model = prefs.getString(KEY_MODEL, "") ?: ""
    private var baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""

    // Setup progress
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress

    private val _setupMessage = MutableStateFlow("")
    val setupMessage: StateFlow<String> = _setupMessage

    private var setupJob: Job? = null
    private var agentJob: Job? = null

    /**
     * Save API configuration.
     */
    fun saveConfig(
        provider: String,
        key: String,
        model: String = "",
        baseUrl: String = ""
    ) {
        this.providerType = provider
        this.apiKey = key
        this.model = model
        this.baseUrl = baseUrl

        prefs.edit()
            .putString(KEY_API_KEY, key)
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_MODEL, model)
            .putString(KEY_BASE_URL, baseUrl)
            .apply()

        Log.i(TAG, "Config saved: provider=$provider, model=$model")
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()
    fun getApiKey(): String = apiKey
    fun getProviderType(): String = providerType
    fun getModel(): String = model
    fun getBaseUrl(): String = baseUrl

    /**
     * Create AI provider based on config.
     */
    private fun createProvider(): AIProvider {
        return when (providerType) {
            "mimo" -> OpenAICompatibleProvider(
                baseUrl = baseUrl.ifBlank { "http://localhost:8000/v1" },
                apiKey = apiKey.ifBlank { "not-needed" },
                model = model.ifBlank { "MiMo-7B-RL" },
                name = "Xiaomi MiMo"
            )
            "openai" -> OpenAICompatibleProvider(
                baseUrl = "https://api.openai.com/v1",
                apiKey = apiKey,
                model = model.ifBlank { "gpt-4o" },
                name = "OpenAI"
            )
            "deepseek" -> OpenAICompatibleProvider(
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = apiKey,
                model = model.ifBlank { "deepseek-chat" },
                name = "DeepSeek"
            )
            "claude" -> ClaudeProvider(
                apiKey = apiKey,
                model = model.ifBlank { "claude-sonnet-4-20250514" }
            )
            else -> OpenAICompatibleProvider(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                name = providerType
            )
        }
    }

    /**
     * Check environment and setup if needed.
     */
    fun checkAndSetup() {
        // No PRoot setup needed anymore — direct Android shell
        _uiState.value = UiState.Ready
    }

    /**
     * Run AI agent with user request.
     */
    fun runAgent(userRequest: String) {
        if (apiKey.isBlank() && providerType != "mimo") {
            // Add error as chat message instead of separate screen
            val currentMessages = _chatMessages.value.toMutableList()
            currentMessages.add(ChatMessage.User(userRequest))
            currentMessages.add(ChatMessage.Agent(
                text = "⚠️ API key belum di-set. Tap ⚙️ untuk konfigurasi.",
                success = false
            ))
            _chatMessages.value = currentMessages
            return
        }

        // Add user message to chat
        val currentMessages = _chatMessages.value.toMutableList()
        currentMessages.add(ChatMessage.User(userRequest))
        _chatMessages.value = currentMessages

        agentJob = viewModelScope.launch {
            _uiState.value = UiState.AgentRunning("Starting...", emptyList())
            _agentSteps.value = emptyList()

            try {
                val provider = createProvider()

                val result = agent.run(
                    provider = provider,
                    userRequest = userRequest,
                    onStep = { step ->
                        // Update live progress
                        val currentSteps = _agentSteps.value.toMutableList()
                        val existingIndex = currentSteps.indexOfFirst {
                            it.iteration == step.iteration &&
                            it.toolName == step.toolName &&
                            it.type == step.type
                        }
                        if (existingIndex >= 0) {
                            currentSteps[existingIndex] = step
                        } else {
                            currentSteps.add(step)
                        }
                        _agentSteps.value = currentSteps
                        _uiState.value = UiState.AgentRunning(
                            step.content,
                            currentSteps
                        )
                    }
                )

                // Add agent response to chat
                val updatedMessages = _chatMessages.value.toMutableList()
                updatedMessages.add(ChatMessage.Agent(
                    text = result.finalResponse,
                    steps = result.steps,
                    success = result.success,
                    iterations = result.iterations
                ))
                _chatMessages.value = updatedMessages

                // Done — go back to Ready (chat stays visible)
                _uiState.value = UiState.Ready

            } catch (e: Exception) {
                Log.e(TAG, "Agent failed: ${e.message}")

                // Add error to chat as inline message
                val updatedMessages = _chatMessages.value.toMutableList()
                updatedMessages.add(ChatMessage.Agent(
                    text = "❌ Error: ${e.message}",
                    success = false
                ))
                _chatMessages.value = updatedMessages

                // Stay in Ready state (error shown inline in chat)
                _uiState.value = UiState.Ready
            }
        }
    }

    /**
     * Quick actions (pre-built prompts).
     */
    fun scanStorage() {
        runAgent("Scan my /sdcard storage. Show me a summary of what's taking space, " +
                "identify junk files (old APKs, temp files, cache, duplicates), " +
                "and suggest what can be cleaned up. Ask me before deleting anything.")
    }

    fun organizeDownloads() {
        runAgent("Analyze all files in /sdcard/Download/ and organize them into categories. " +
                "Create folders under /sdcard/Documents/ for documents, /sdcard/Images/ for images, etc. " +
                "Show me the plan before moving anything.")
    }

    fun findDuplicates() {
        runAgent("Find duplicate files in /sdcard/ (same name, same size). " +
                "Group them and suggest which copies to keep and which to delete.")
    }

    fun cleanJunk() {
        runAgent("Find and list all junk files in /sdcard/: .tmp, .bak, .cache, .log, " +
                "old APK files, Thumbs.db, .thumbcache files. " +
                "Show me the list and total size, then ask me before deleting.")
    }

    /**
     * Cancel running agent.
     */
    fun cancelAgent() {
        agentJob?.cancel()
        agentJob = null
        _uiState.value = UiState.Ready
    }

    /**
     * Go back to ready state.
     */
    fun resetToReady() {
        _agentSteps.value = emptyList()
        _uiState.value = UiState.Ready
    }

    /**
     * Clear chat history.
     */
    fun clearChat() {
        _chatMessages.value = emptyList()
        _agentSteps.value = emptyList()
        _uiState.value = UiState.Ready
    }

    // UI State definitions
    sealed class UiState {
        data object Welcome : UiState()
        data object Ready : UiState()
        data class AgentRunning(val message: String, val steps: List<AgentStep>) : UiState()
        data class AgentResult(val result: com.aicleaner.ai.AgentResult) : UiState()
        data class Error(val message: String) : UiState()
    }
}

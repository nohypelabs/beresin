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
import com.aicleaner.ai.provider.QuotaStatus
import com.aicleaner.ai.provider.ServerProvider
import com.aicleaner.engine.ShellEngine
import com.aicleaner.tools.PendingToolAction
import com.aicleaner.tools.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * A single message in the chat conversation.
 */
sealed class ChatMessage {
    abstract val timestamp: Long

    /** User's message. */
    data class User(
        val text: String,
        override val timestamp: Long = java.util.Date().time
    ) : ChatMessage()

    /** Agent's response with steps. */
    data class Agent(
        val text: String,
        val steps: List<AgentStep> = emptyList(),
        val success: Boolean = true,
        val iterations: Int = 0,
        override val timestamp: Long = java.util.Date().time
    ) : ChatMessage()

    /** System/info message (small, centered). */
    data class System(
        val text: String,
        override val timestamp: Long = java.util.Date().time
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
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_INSTALL_ID = "install_id"
        private const val KEY_PREMIUM_TOKEN = "premium_token"
        private const val KEY_IS_PREMIUM = "is_premium"

        private const val KEY_QUOTA_REMAINING = "quota_remaining"
        private const val KEY_QUOTA_TOTAL = "quota_total"
        private const val KEY_QUOTA_RESET_AT = "quota_reset_at"
        private const val FREE_DAILY_QUOTA = 20
        private const val SERVER_BASE_URL = "http://192.168.100.140:3000"
        private const val APP_VERSION = "1.0.0"
    }

    // Core components
    private val shell = ShellEngine(app)
    private val tools = ToolRegistry(shell)
    private val agent = AgentEngine(tools)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // Agent steps (for live progress)
    private val _agentSteps = MutableStateFlow<List<AgentStep>>(emptyList())
    val agentSteps: StateFlow<List<AgentStep>> = _agentSteps

    // Chat messages history
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    // Suggestion chips
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions

    // API Config — server-hosted MiMo by default, no user API key needed
    private var apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
    private var providerType = prefs.getString(KEY_PROVIDER, "mimo") ?: "mimo"
    private var model = prefs.getString(KEY_MODEL, "") ?: ""
    private var baseUrl = prefs.getString(KEY_BASE_URL, SERVER_BASE_URL).let {
        normalizeServerUrl(if (it.isNullOrBlank()) SERVER_BASE_URL else it)
    }
    private val installId = getOrCreateInstallId()
    private var premiumToken = prefs.getString(KEY_PREMIUM_TOKEN, "") ?: ""

    // User info
    private var userName = prefs.getString(KEY_USER_NAME, "") ?: ""
    private var onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    private val _quotaRemaining = MutableStateFlow(
        prefs.getInt(KEY_QUOTA_REMAINING, FREE_DAILY_QUOTA)
    )
    val quotaRemaining: StateFlow<Int> = _quotaRemaining

    private val _quotaTotal = MutableStateFlow(
        prefs.getInt(KEY_QUOTA_TOTAL, FREE_DAILY_QUOTA)
    )
    val quotaTotal: StateFlow<Int> = _quotaTotal

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _pendingActions = MutableStateFlow<List<PendingToolAction>>(emptyList())
    val pendingActions: StateFlow<List<PendingToolAction>> = _pendingActions

    // Setup progress
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress

    private val _setupMessage = MutableStateFlow("")
    val setupMessage: StateFlow<String> = _setupMessage

    private var setupJob: Job? = null
    private var agentJob: Job? = null

    init {
        // Auto-start greeting on launch
        viewModelScope.launch {
            delay(500) // Small delay for UI to settle
            startGreeting()
        }
    }

    /**
     * Start the greeting flow. If first time, ask for name. Otherwise, say hi.
     */
    private suspend fun startGreeting() {
        if (!onboardingDone || userName.isBlank()) {
            // First time — ask for name
            _uiState.value = UiState.Onboarding
            addAgentMessageWithDelay(
                "Halo! 👋 Gw Dora, AI assistant yang bakal bantuin HP lo tetep enteng dan rapi."
            )
            addAgentMessageWithDelay(
                "Panggilannya siapa nih? Biar gw bisa nyapa lo nanti 😊"
            )
        } else {
            // Returning user — greet with name
            _uiState.value = UiState.Ready
            addAgentMessageWithDelay(
                "Halo ${userName}! 👋 Gw Dora, siap beresin HP lo hari ini."
            )
            showDefaultSuggestions()
        }
    }

    /**
     * Handle user introducing their name.
     */
    fun setUserName(name: String) {
        userName = name.trim()
        prefs.edit()
            .putString(KEY_USER_NAME, userName)
            .putBoolean(KEY_ONBOARDING_DONE, true)
            .apply()
        onboardingDone = true

        // Add user message
        addUserMessage(userName)

        // Respond with name
        viewModelScope.launch {
            delay(800)
            addAgentMessage("Asik, kenalan sama $userName! 🎉")
            delay(600)
            addAgentMessage(
                "Oke $userName, gw siap bantuin lo. HP lo ada yang mau dibersihin? " +
                "Atau mau gw scan dulu aja?"
            )
            _uiState.value = UiState.Ready
            showDefaultSuggestions()
        }
    }

    /**
     * Show default suggestion chips.
     */
    private fun showDefaultSuggestions() {
        _suggestions.value = listOf(
            "Scan HP gw",
            "Cari file sampah",
            "Cari foto duplikat",
            "Rapihin folder Download"
        )
    }

    /**
     * Show suggestions after scan results.
     */
    private fun showPostScanSuggestions() {
        _suggestions.value = listOf(
            "Bersihin semua",
            "Cuma bersihin yang gede dulu",
            "Cari duplikat juga",
            "Lihat detail"
        )
    }

    /**
     * Clear suggestions (while agent is running).
     */
    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }

    /**
     * Handle suggestion chip tap.
     */
    fun onSuggestionTap(suggestion: String) {
        clearSuggestions()
        runAgent(suggestion)
    }

    /**
     * Add agent message with typing delay effect.
     */
    private suspend fun addAgentMessageWithDelay(text: String) {
        delay(600) // Simulate typing
        addAgentMessage(text)
    }

    /**
     * Add agent message to chat.
     */
    private fun addAgentMessage(text: String) {
        val current = _chatMessages.value.toMutableList()
        current.add(ChatMessage.Agent(text = text))
        _chatMessages.value = current
    }

    /**
     * Add user message to chat.
     */
    private fun addUserMessage(text: String) {
        val current = _chatMessages.value.toMutableList()
        current.add(ChatMessage.User(text = text))
        _chatMessages.value = current
    }

    /**
     * Save API configuration.
     */
    fun saveConfig(
        provider: String,
        key: String,
        model: String = "",
        baseUrl: String = ""
    ) {
        val normalizedBaseUrl = if (provider == "mimo") {
            normalizeServerUrl(baseUrl.ifBlank { SERVER_BASE_URL })
        } else {
            baseUrl
        }

        this.providerType = provider
        this.apiKey = key
        this.model = model
        this.baseUrl = normalizedBaseUrl

        prefs.edit()
            .putString(KEY_API_KEY, key)
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_MODEL, model)
            .putString(KEY_BASE_URL, normalizedBaseUrl)
            .apply()

        Log.i(TAG, "Config saved: provider=$provider, model=$model")
    }

    fun hasApiKey(): Boolean = providerType == "mimo" || apiKey.isNotBlank()
    fun getApiKey(): String = apiKey
    fun getProviderType(): String = providerType
    fun getModel(): String = model
    fun getBaseUrl(): String = baseUrl
    fun getUserName(): String = userName
    fun getInstallId(): String = installId

    fun savePremiumToken(token: String) {
        premiumToken = token.trim()
        prefs.edit()
            .putString(KEY_PREMIUM_TOKEN, premiumToken)
            .apply()
        addAgentMessage("Premium token disimpan. Dora bakal validasi ke server pas chat berikutnya.")
    }

    /**
     * Create AI provider based on config.
     */
    private fun createProvider(turnId: String): AIProvider {
        return when (providerType) {
            "mimo" -> ServerProvider(
                serverUrl = baseUrl.ifBlank { SERVER_BASE_URL },
                provider = "openai",
                installId = installId,
                premiumToken = premiumToken,
                turnId = turnId,
                appVersion = APP_VERSION,
                name = "Beresin MiMo"
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
        _uiState.value = UiState.Ready
    }

    /**
     * Run AI agent with user request.
     */
    fun runAgent(userRequest: String) {
        Log.d(TAG, "runAgent called: $userRequest, provider=$providerType, baseUrl=$baseUrl, hasApiKey=${hasApiKey()}")

        if (apiKey.isBlank() && providerType != "mimo") {
            Log.w(TAG, "No API key for provider: $providerType")
            addUserMessage(userRequest)
            addAgentMessage("⚠️ API key belum di-set. Tap ⚙️ di pojok kanan atas buat konfigurasi ya.")
            return
        }

        // Add user message
        addUserMessage(userRequest)

        // Clear suggestions while running
        clearSuggestions()

        agentJob = viewModelScope.launch {
            Log.d(TAG, "Agent coroutine started")
            _uiState.value = UiState.AgentRunning("Starting...", emptyList())
            _agentSteps.value = emptyList()

            try {
                Log.d(TAG, "Creating provider...")
                val turnId = UUID.randomUUID().toString()
                val provider = createProvider(turnId)
                Log.d(TAG, "Provider created: ${provider.name}")

                Log.d(TAG, "Calling agent.run()...")
                val result = agent.run(
                    provider = provider,
                    userRequest = userRequest,
                    userName = userName,
                    onStep = { step ->
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

                if (provider is ServerProvider) {
                    provider.latestQuota?.let { updateQuotaSnapshot(it) }
                }

                Log.d(TAG, "Agent result: ${result.finalResponse.take(100)}")
                _pendingActions.value = tools.getPendingActions()

                // Add agent response
                addAgentMessage(result.finalResponse)
                if (_pendingActions.value.isNotEmpty()) {
                    addAgentMessage("Ada ${_pendingActions.value.size} aksi file yang butuh konfirmasi lo dulu.")
                }

                // Show contextual suggestions after response
                delay(300)
                if (userRequest.lowercase().let {
                    it.contains("scan") || it.contains("bersih") || it.contains("clean")
                }) {
                    showPostScanSuggestions()
                } else {
                    showDefaultSuggestions()
                }

                _uiState.value = UiState.Ready

            } catch (e: Exception) {
                Log.e(TAG, "Agent failed: ${e.message}", e)
                addAgentMessage(formatUserFacingError(e.message ?: e.javaClass.simpleName))
                _uiState.value = UiState.Ready
                showDefaultSuggestions()
            }
        }
    }

    fun confirmPendingAction(actionId: String) {
        viewModelScope.launch {
            val result = tools.confirmPendingAction(actionId)
            _pendingActions.value = tools.getPendingActions()
            addAgentMessage(
                if (result.success) {
                    "✅ ${result.output}"
                } else {
                    "❌ Gagal jalanin aksi: ${result.error ?: result.output}"
                }
            )
        }
    }

    fun cancelPendingAction(actionId: String) {
        tools.cancelPendingAction(actionId)
        _pendingActions.value = tools.getPendingActions()
        addAgentMessage("Oke, aksi file itu gue batalin.")
    }

    /**
     * Quick actions (pre-built prompts).
     */
    fun scanStorage() {
        runAgent("Scan HP gw, kasih tau apa aja yang makan tempat dan bisa dibersihin.")
    }

    fun organizeDownloads() {
        runAgent("Rapihin folder Download gw, kategorisasi file-nya.")
    }

    fun findDuplicates() {
        runAgent("Cari file duplikat di HP gw.")
    }

    fun cleanJunk() {
        runAgent("Bersihin file sampah di HP gw — cache, tmp, log, APK lama.")
    }

    /**
     * Cancel running agent.
     */
    fun cancelAgent() {
        agentJob?.cancel()
        agentJob = null
        _uiState.value = UiState.Ready
        showDefaultSuggestions()
    }

    /**
     * Go back to ready state.
     */
    fun resetToReady() {
        _agentSteps.value = emptyList()
        _uiState.value = UiState.Ready
    }

    /**
     * Clear chat history and restart greeting.
     */
    fun clearChat() {
        _chatMessages.value = emptyList()
        _agentSteps.value = emptyList()
        _suggestions.value = emptyList()
        viewModelScope.launch {
            startGreeting()
        }
    }

    // UI State definitions
    sealed class UiState {
        data object Loading : UiState()
        data object Onboarding : UiState()
        data object Ready : UiState()
        data class AgentRunning(val message: String, val steps: List<AgentStep>) : UiState()
        data class AgentResult(val result: com.aicleaner.ai.AgentResult) : UiState()
        data class Error(val message: String) : UiState()
    }

    private fun getOrCreateInstallId(): String {
        val existing = prefs.getString(KEY_INSTALL_ID, "") ?: ""
        if (existing.isNotBlank()) return existing

        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, created).apply()
        return created
    }

    private fun normalizeServerUrl(url: String): String {
        return url.trim().removeSuffix("/v1").removeSuffix("/")
    }

    private fun updateQuotaSnapshot(quota: QuotaStatus) {
        _quotaRemaining.value = quota.remaining
        _quotaTotal.value = quota.total
        _isPremium.value = quota.isPremium
        prefs.edit()
            .putInt(KEY_QUOTA_REMAINING, quota.remaining)
            .putInt(KEY_QUOTA_TOTAL, quota.total)
            .putBoolean(KEY_IS_PREMIUM, quota.isPremium)
            .putString(KEY_QUOTA_RESET_AT, quota.resetAt ?: "")
            .apply()
    }

    private fun formatUserFacingError(message: String): String {
        return when {
            message.contains("402") || message.contains("quota_exceeded", ignoreCase = true) ->
                "⚠️ Kuota gratis hari ini habis. Upgrade premium buat lanjut unlimited."
            message.contains("429") || message.contains("rate_limited", ignoreCase = true) ->
                "⚠️ Kebanyakan request bentar ini. Tunggu sebentar, terus coba lagi."
            message.contains("401") || message.contains("install id", ignoreCase = true) ->
                "⚠️ Sesi app belum valid. Tutup-buka app, lalu coba lagi."
            else -> "❌ Error: $message"
        }
    }
}

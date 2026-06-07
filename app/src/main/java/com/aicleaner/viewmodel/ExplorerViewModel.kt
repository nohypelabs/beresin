package com.aicleaner.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicleaner.ai.AgentEngine
import com.aicleaner.ai.provider.AIProvider
import com.aicleaner.ai.provider.ClaudeProvider
import com.aicleaner.ai.provider.OpenAICompatibleProvider
import com.aicleaner.ai.provider.ServerProvider
import com.aicleaner.engine.ShellEngine
import com.aicleaner.scanner.*
import com.aicleaner.tools.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the AI Storage Explorer.
 * Manages scanning, categorization, and AI-powered suggestions.
 */
class ExplorerViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "ExplorerViewModel"
        private const val PREFS_NAME = "beresin_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PROVIDER = "api_provider"
        private const val KEY_MODEL = "api_model"
        private const val KEY_BASE_URL = "api_base_url"
    }

    // Core components
    private val shell = ShellEngine(app)
    private val scanner = StorageScanner(shell)
    private val tools = ToolRegistry(shell)
    private val agent = AgentEngine(tools)
    private val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    // UI State
    private val _uiState = MutableStateFlow<ExplorerUiState>(ExplorerUiState.Welcome)
    val uiState: StateFlow<ExplorerUiState> = _uiState

    // Scan result
    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult

    // Selected category
    private val _selectedCategory = MutableStateFlow<FileCategory?>(null)
    val selectedCategory: StateFlow<FileCategory?> = _selectedCategory

    // AI processing state
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState: StateFlow<AiState> = _aiState

    // API Config
    private var apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
    private var providerType = prefs.getString(KEY_PROVIDER, "server") ?: "server"
    private var model = prefs.getString(KEY_MODEL, "") ?: ""
    private var baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""

    private var scanJob: Job? = null
    private var aiJob: Job? = null

    /**
     * Check if API key is configured.
     */
    fun hasApiKey(): Boolean = apiKey.isNotBlank() || providerType == "mimo"

    /**
     * Save API configuration.
     */
    fun saveConfig(provider: String, key: String, model: String = "", baseUrl: String = "") {
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

        Log.i(TAG, "Config saved: provider=$provider")
    }

    fun getProviderType(): String = providerType
    fun getApiKey(): String = apiKey
    fun getModel(): String = model
    fun getBaseUrl(): String = baseUrl

    /**
     * Start scanning storage.
     */
    fun startScan() {
        scanJob = viewModelScope.launch {
            _uiState.value = ExplorerUiState.Scanning("Starting scan...", 0f)

            try {
                val result = scanner.scan { message, progress ->
                    _uiState.value = ExplorerUiState.Scanning(message, progress)
                }

                _scanResult.value = result
                _uiState.value = ExplorerUiState.Home(result)

            } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}")
                _uiState.value = ExplorerUiState.Error("Scan failed: ${e.message}")
            }
        }
    }

    /**
     * Select a category to view details.
     */
    fun selectCategory(category: FileCategory) {
        _selectedCategory.value = category
        val result = _scanResult.value ?: return
        val categoryResult = result.categories[category] ?: return
        _uiState.value = ExplorerUiState.CategoryDetail(category, categoryResult)
    }

    /**
     * Go back to home.
     */
    fun goHome() {
        _selectedCategory.value = null
        val result = _scanResult.value
        if (result != null) {
            _uiState.value = ExplorerUiState.Home(result)
        } else {
            _uiState.value = ExplorerUiState.Welcome
        }
    }

    /**
     * View AI suggestions.
     */
    fun viewSuggestions() {
        val result = _scanResult.value ?: return
        _uiState.value = ExplorerUiState.Suggestions(result.suggestions)
    }

    /**
     * Generate AI preview for a suggestion.
     */
    fun previewSuggestion(suggestion: Suggestion) {
        aiJob = viewModelScope.launch {
            _aiState.value = AiState.Processing("Analyzing ${suggestion.title}...")

            try {
                // Use AI to generate detailed preview
                val preview = generatePreview(suggestion)
                _aiState.value = AiState.PreviewReady(preview)
                _uiState.value = ExplorerUiState.Preview(suggestion, preview)

            } catch (e: Exception) {
                Log.e(TAG, "Preview failed: ${e.message}")
                _aiState.value = AiState.Error("Failed to generate preview")
            }
        }
    }

    /**
     * Apply changes from a suggestion.
     */
    fun applySuggestion(suggestion: Suggestion) {
        aiJob = viewModelScope.launch {
            _aiState.value = AiState.Processing("Applying changes...")

            try {
                val result = executeSuggestion(suggestion)
                _aiState.value = AiState.Complete(result)

                // Re-scan to update results
                startScan()

            } catch (e: Exception) {
                Log.e(TAG, "Apply failed: ${e.message}")
                _aiState.value = AiState.Error("Failed to apply changes")
            }
        }
    }

    /**
     * Generate preview using AI.
     */
    private suspend fun generatePreview(suggestion: Suggestion): ChangePreview {
        // For MVP, generate preview without AI (fast)
        // TODO: Use AI for smarter previews in v2
        return when (suggestion.action) {
            SuggestionAction.CLEAN -> {
                ChangePreview(
                    title = suggestion.title,
                    description = suggestion.description,
                    before = listOf(PreviewItem("Current state", "Files present")),
                    after = listOf(PreviewItem("After cleaning", "Files removed")),
                    estimatedSpaceFreed = suggestion.estimatedSpace,
                    filesAffected = suggestion.fileCount
                )
            }
            SuggestionAction.ORGANIZE -> {
                ChangePreview(
                    title = suggestion.title,
                    description = suggestion.description,
                    before = listOf(PreviewItem("Downloads/", "Mixed files")),
                    after = listOf(
                        PreviewItem("Downloads/Images/", "Organized", "move"),
                        PreviewItem("Downloads/Documents/", "Organized", "move"),
                        PreviewItem("Downloads/Videos/", "Organized", "move")
                    ),
                    estimatedSpaceFreed = 0,
                    filesAffected = suggestion.fileCount
                )
            }
            SuggestionAction.REVIEW -> {
                ChangePreview(
                    title = suggestion.title,
                    description = suggestion.description,
                    before = listOf(PreviewItem("${suggestion.fileCount} files", "Need review")),
                    after = listOf(PreviewItem("Reviewed", "Unnecessary files removed")),
                    estimatedSpaceFreed = suggestion.estimatedSpace,
                    filesAffected = suggestion.fileCount
                )
            }
            SuggestionAction.MOVE -> {
                ChangePreview(
                    title = suggestion.title,
                    description = suggestion.description,
                    before = listOf(PreviewItem("Current location", "Files scattered")),
                    after = listOf(PreviewItem("New location", "Files organized")),
                    estimatedSpaceFreed = 0,
                    filesAffected = suggestion.fileCount
                )
            }
        }
    }

    /**
     * Execute a suggestion (actually make changes).
     */
    private suspend fun executeSuggestion(suggestion: Suggestion): String {
        return when (suggestion.action) {
            SuggestionAction.CLEAN -> {
                // Use agent to clean files
                val result = agent.run(
                    provider = createProvider(),
                    userRequest = "Clean the following files: ${suggestion.title}. " +
                        "Files: ${suggestion.fileCount}. " +
                        "Use delete_file tool to remove them safely."
                )
                result.finalResponse
            }
            SuggestionAction.ORGANIZE -> {
                val result = agent.run(
                    provider = createProvider(),
                    userRequest = "Organize files: ${suggestion.title}. " +
                        "Files: ${suggestion.fileCount}. " +
                        "Use move_file tool to organize them."
                )
                result.finalResponse
            }
            SuggestionAction.REVIEW -> {
                "Please review the files manually"
            }
            SuggestionAction.MOVE -> {
                val result = agent.run(
                    provider = createProvider(),
                    userRequest = "Move files: ${suggestion.title}. " +
                        "Files: ${suggestion.fileCount}."
                )
                result.finalResponse
            }
        }
    }

    /**
     * Create AI provider based on config.
     * Default: Server provider (no API key needed for user)
     */
    private fun createProvider(): AIProvider {
        return when (providerType) {
            "server" -> ServerProvider(
                serverUrl = baseUrl.ifBlank { "http://10.0.2.2:3000" }, // Android emulator localhost
                provider = model.ifBlank { "openai" },
                name = "Beresin Server"
            )
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
     * Cancel current operation.
     */
    fun cancel() {
        scanJob?.cancel()
        aiJob?.cancel()
        _uiState.value = ExplorerUiState.Welcome
        _aiState.value = AiState.Idle
    }
}

/**
 * UI States for the explorer.
 */
sealed class ExplorerUiState {
    data object Welcome : ExplorerUiState()
    data class Scanning(val message: String, val progress: Float) : ExplorerUiState()
    data class Home(val scanResult: ScanResult) : ExplorerUiState()
    data class CategoryDetail(val category: FileCategory, val result: CategoryResult) : ExplorerUiState()
    data class Suggestions(val suggestions: List<Suggestion>) : ExplorerUiState()
    data class Preview(val suggestion: Suggestion, val preview: ChangePreview) : ExplorerUiState()
    data class Error(val message: String) : ExplorerUiState()
}

/**
 * AI processing states.
 */
sealed class AiState {
    data object Idle : AiState()
    data class Processing(val message: String) : AiState()
    data class PreviewReady(val preview: ChangePreview) : AiState()
    data class Complete(val result: String) : AiState()
    data class Error(val message: String) : AiState()
}

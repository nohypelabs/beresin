package com.aicleaner.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicleaner.ai.AIEngine
import com.aicleaner.engine.ShellEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "beresin_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PROVIDER = "api_provider"
        private const val KEY_MODEL = "api_model"

        // Characters that are dangerous in shell commands
        private val SHELL_META_CHARS = charArrayOf(
            '\'', '"', '`', '$', ';', '&', '|', '(', ')',
            '{', '}', '<', '>', '\n', '\r', '\\', '!'
        )
    }

    private val shell = ShellEngine(app)
    private val ai = AIEngine(app)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Welcome)
    val uiState: StateFlow<UiState> = _uiState

    // AI Config (loaded from persistence on init)
    private var aiConfig: AIEngine.AIConfig = loadSavedConfig()

    // Setup progress
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress

    private val _setupMessage = MutableStateFlow("")
    val setupMessage: StateFlow<String> = _setupMessage

    // Track setup job for cancellation
    private var setupJob: Job? = null

    init {
        // Restore API key from shared prefs
        val savedKey = prefs.getString(KEY_API_KEY, "") ?: ""
        if (savedKey.isNotBlank()) {
            Log.i(TAG, "Restored API key from preferences")
        }
    }

    /**
     * Load saved config from SharedPreferences.
     */
    private fun loadSavedConfig(): AIEngine.AIConfig {
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        val providerName = prefs.getString(KEY_PROVIDER, AIEngine.Provider.CLAUDE.name)
            ?: AIEngine.Provider.CLAUDE.name
        val model = prefs.getString(KEY_MODEL, "") ?: ""

        val provider = try {
            AIEngine.Provider.valueOf(providerName)
        } catch (e: Exception) {
            AIEngine.Provider.CLAUDE
        }

        return AIEngine.AIConfig(
            provider = provider,
            apiKey = key,
            model = model.ifEmpty {
                when (provider) {
                    AIEngine.Provider.CLAUDE -> "claude-sonnet-4-20250514"
                    AIEngine.Provider.OPENAI -> "gpt-4o"
                    AIEngine.Provider.GEMINI -> "gemini-pro"
                }
            }
        )
    }

    /**
     * Save AI API configuration.
     */
    fun saveAIConfig(provider: AIEngine.Provider, apiKey: String, model: String = "") {
        val finalModel = model.ifEmpty {
            when (provider) {
                AIEngine.Provider.CLAUDE -> "claude-sonnet-4-20250514"
                AIEngine.Provider.OPENAI -> "gpt-4o"
                AIEngine.Provider.GEMINI -> "gemini-pro"
            }
        }

        aiConfig = AIEngine.AIConfig(
            provider = provider,
            apiKey = apiKey,
            model = finalModel
        )

        // Persist to SharedPreferences
        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_MODEL, finalModel)
            .apply()

        Log.i(TAG, "API config saved for provider: $provider")
    }

    /**
     * Check if API key is configured.
     */
    fun hasApiKey(): Boolean = aiConfig.apiKey.isNotBlank()

    /**
     * Get current API key (for UI display).
     */
    fun getApiKey(): String = aiConfig.apiKey

    /**
     * Get current provider.
     */
    fun getProvider(): AIEngine.Provider = aiConfig.provider

    /**
     * Check if environment is ready, if not start setup.
     */
    fun checkAndSetup() {
        if (shell.isProotReady()) {
            _uiState.value = UiState.Ready
            return
        }
        startSetup()
    }

    /**
     * Start PRoot environment setup.
     */
    fun startSetup() {
        setupJob = viewModelScope.launch {
            _uiState.value = UiState.SettingUp

            val success = shell.setupEnvironment { message, progress ->
                _setupMessage.value = message
                _setupProgress.value = progress
            }

            if (success) {
                _uiState.value = UiState.Ready
            } else {
                _uiState.value = UiState.Error("Setup failed. Check your internet connection and try again.")
            }
        }
    }

    /**
     * Cancel ongoing setup.
     */
    fun cancelSetup() {
        setupJob?.cancel()
        setupJob = null
        _uiState.value = UiState.Ready
    }

    /**
     * Scan storage and analyze with AI.
     */
    fun scanStorage() {
        // Guard: API key must be set
        if (aiConfig.apiKey.isBlank()) {
            _uiState.value = UiState.Error("Please configure your API key first (tap the 🔑 icon)")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Scanning("Scanning storage...")

            try {
                // Get storage overview
                val duOutput = shell.smartExec("du -sh /sdcard/* 2>/dev/null | sort -rh | head -20")

                // Get file type breakdown
                val findOutput = shell.smartExec("""
                    find /sdcard -maxdepth 3 -type f 2>/dev/null |
                    sed 's/.*\.//' | sort | uniq -c | sort -rn | head -20
                """.trimIndent())

                // Get large files
                val largeFiles = shell.smartExec(
                    "find /sdcard -maxdepth 3 -type f -size +10M 2>/dev/null | head -30"
                )

                // Get potential junk
                val junkFiles = shell.smartExec("""
                    find /sdcard -maxdepth 3 -type f \( \
                        -name "*.apk" -o -name "*.xapk" -o \
                        -name "*.tmp" -o -name "*.bak" -o \
                        -name "*.log" -o -name "*.cache" -o \
                        -name ".thumbcache*" -o -name "Thumbs.db" \
                    \) 2>/dev/null
                """.trimIndent())

                _uiState.value = UiState.Scanning("Analyzing with AI...")

                // Combine all data for AI analysis
                val storageData = """
                    === Storage Overview ===
                    $duOutput

                    === File Types ===
                    $findOutput

                    === Large Files (>10MB) ===
                    $largeFiles

                    === Potential Junk Files ===
                    $junkFiles
                """.trimIndent()

                // Call AI for analysis
                val result = ai.analyzeStorage(storageData, aiConfig)

                // Check for empty results
                if (result.actions.isEmpty() && result.summary.isBlank()) {
                    _uiState.value = UiState.Error(
                        "AI could not identify any cleanup actions. Try again or adjust your scan scope."
                    )
                } else {
                    _uiState.value = UiState.Result(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}")
                _uiState.value = UiState.Error("Scan failed: ${e.message}")
            }
        }
    }

    /**
     * Organize Download folder.
     */
    fun organizeDownloads() {
        // Guard: API key must be set
        if (aiConfig.apiKey.isBlank()) {
            _uiState.value = UiState.Error("Please configure your API key first (tap the 🔑 icon)")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Scanning("Analyzing Download folder...")

            try {
                val downloadFiles = shell.smartExec("ls -la /sdcard/Download/ 2>/dev/null")
                val duOutput = shell.smartExec("du -sh /sdcard/Download/* 2>/dev/null | sort -rh")

                _uiState.value = UiState.Scanning("Planning organization...")

                val fileList = """
                    === Download Folder Contents ===
                    $downloadFiles

                    === File Sizes ===
                    $duOutput
                """.trimIndent()

                val result = ai.suggestOrganization(fileList, aiConfig)

                if (result.actions.isEmpty() && result.summary.isBlank()) {
                    _uiState.value = UiState.Error(
                        "AI could not suggest any organization. The Download folder might already be clean!"
                    )
                } else {
                    _uiState.value = UiState.OrganizationPlan(result)
                }

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed: ${e.message}")
            }
        }
    }

    /**
     * Validate that a path is safe for shell execution.
     * Rejects paths containing shell metacharacters.
     */
    private fun isPathSafe(path: String): Boolean {
        if (path.isBlank()) return false
        if (path.any { it in SHELL_META_CHARS }) return false
        // Must be an absolute path under /sdcard
        if (!path.startsWith("/sdcard/")) return false
        // No path traversal
        if (path.contains("..")) return false
        return true
    }

    /**
     * Execute cleanup actions with proper sanitization.
     */
    fun executeActions(actions: List<AIEngine.CleanAction>) {
        if (actions.isEmpty()) {
            _uiState.value = UiState.Error("No actions to execute")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Executing("Executing cleanup...", 0f)

            val results = mutableListOf<String>()
            var completed = 0
            var failed = 0

            try {
                actions.forEach { action ->
                    try {
                        // Validate paths before execution
                        val targetSafe = isPathSafe(action.target)
                        val destSafe = action.destination.isBlank() || isPathSafe(action.destination)

                        if (!targetSafe) {
                            results.add("❌ Skipped unsafe path: ${action.target}")
                            failed++
                        } else if (!destSafe) {
                            results.add("❌ Skipped unsafe destination: ${action.destination}")
                            failed++
                        } else {
                            val result = when (action.type) {
                                "delete" -> {
                                    shell.exec("rm -rf '${action.target}'")
                                    "Deleted: ${action.target}"
                                }
                                "move", "organize" -> {
                                    if (action.destination.isBlank()) {
                                        results.add("❌ No destination for: ${action.target}")
                                        failed++
                                        continue
                                    }
                                    shell.exec("mkdir -p '${action.destination}' && mv '${action.target}' '${action.destination}'")
                                    "Moved: ${action.target} → ${action.destination}"
                                }
                                else -> {
                                    results.add("❓ Unknown action: ${action.type}")
                                    failed++
                                    continue
                                }
                            }
                            results.add("✅ $result")
                        }
                    } catch (e: Exception) {
                        results.add("❌ Failed: ${action.target} — ${e.message}")
                        failed++
                    }

                    completed++
                    val progress = completed.toFloat() / actions.size
                    _uiState.value = UiState.Executing(
                        "Processing $completed/${actions.size}...",
                        progress
                    )
                }

                _uiState.value = UiState.CleanupResult(results)

            } catch (e: Exception) {
                // If coroutine was cancelled mid-execution, report partial results
                results.add("⚠️ Execution interrupted: ${e.message}")
                _uiState.value = UiState.CleanupResult(results)
            }
        }
    }

    /**
     * Go back to ready state.
     */
    fun resetToReady() {
        _uiState.value = UiState.Ready
    }

    // UI State definitions
    sealed class UiState {
        data object Welcome : UiState()
        data object SettingUp : UiState()
        data object Ready : UiState()
        data class Scanning(val message: String) : UiState()
        data class Result(val analysis: AIEngine.AnalysisResult) : UiState()
        data class OrganizationPlan(val plan: AIEngine.AnalysisResult) : UiState()
        data class Executing(val message: String, val progress: Float) : UiState()
        data class CleanupResult(val results: List<String>) : UiState()
        data class Error(val message: String) : UiState()
    }
}

package com.aicleaner.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aicleaner.ai.AIEngine
import com.aicleaner.engine.ShellEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val shell = ShellEngine(app)
    private val ai = AIEngine(app)

    // UI State
    private val _uiState = MutableStateFlow<UiState>(UiState.Welcome)
    val uiState: StateFlow<UiState> = _uiState

    // AI Config
    private var aiConfig = AIEngine.AIConfig()

    // Setup progress
    private val _setupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = _setupProgress

    private val _setupMessage = MutableStateFlow("")
    val setupMessage: StateFlow<String> = _setupMessage

    /**
     * Save AI API configuration.
     */
    fun saveAIConfig(provider: AIEngine.Provider, apiKey: String, model: String = "") {
        aiConfig = AIEngine.AIConfig(
            provider = provider,
            apiKey = apiKey,
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
        viewModelScope.launch {
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
     * Scan storage and analyze with AI.
     */
    fun scanStorage() {
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

                _uiState.value = UiState.Result(result)

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
                _uiState.value = UiState.OrganizationPlan(result)

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed: ${e.message}")
            }
        }
    }

    /**
     * Execute cleanup actions.
     */
    fun executeActions(actions: List<AIEngine.CleanAction>) {
        viewModelScope.launch {
            _uiState.value = UiState.Executing("Executing cleanup...", 0f)

            val results = mutableListOf<String>()
            var completed = 0

            actions.forEach { action ->
                try {
                    val result = when (action.type) {
                        "delete" -> {
                            shell.smartExec("rm -rf '${action.target}'")
                            "Deleted: ${action.target}"
                        }
                        "move" -> {
                            shell.smartExec("mkdir -p '${action.destination}' && mv '${action.target}' '${action.destination}'")
                            "Moved: ${action.target} → ${action.destination}"
                        }
                        "organize" -> {
                            shell.smartExec("mkdir -p '${action.destination}' && mv '${action.target}' '${action.destination}'")
                            "Organized: ${action.target} → ${action.destination}"
                        }
                        else -> "Unknown action: ${action.type}"
                    }
                    results.add("✅ $result")
                } catch (e: Exception) {
                    results.add("❌ Failed: ${action.target} — ${e.message}")
                }

                completed++
                val progress = completed.toFloat() / actions.size
                _uiState.value = UiState.Executing(
                    "Processing $completed/${actions.size}...",
                    progress
                )
            }

            _uiState.value = UiState.CleanupResult(results)
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

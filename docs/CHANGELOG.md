# 📋 Changelog

All notable changes to **Beresin** will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

---

## [1.2.0] - 2026-06-07

### ✨ Added
- **Chat UI** — WhatsApp-style chat interface with message history
- **Copy to clipboard** — Tap message to copy content
- **New Chat button** — Clear chat history and start fresh
- **Quick action buttons** — One-tap scan, organize, find duplicates, clean junk
- **Agent running indicator** — Shows typing animation while AI is working
- **ChatMessage data class** — Structured message model (user/assistant/tool)

### 🔄 Changed
- Ready screen replaced with Chat screen
- AgentRunning state now shows in chat context
- AgentResult state now shows in chat context
- Error state now shows in chat context

---

## [1.1.0] - 2026-06-07

### ✨ Added
- **AI Agent Engine** — Multi-step agentic loop (plan → tool call → observe)
- **8 Storage Tools** — list_dir, find, delete, move, copy, get_info, storage_summary, exec
- **Multi-provider support** — Xiaomi MiMo, OpenAI, Claude, DeepSeek, Custom
- **OpenAI-compatible provider** — Works with MiMo via vLLM/SGLang
- **Claude provider** — Anthropic API with native tool_use format
- **Tool Registry** — Centralized tool management and definitions
- **Live agent steps** — Real-time progress during agent execution
- **Path security validation** — All tools validate paths under /sdcard
- **Command whitelist** — execute_shell tool only allows read-only commands

### 🔄 Changed
- Architecture: Single API call → Agentic loop
- Removed PRoot dependency (no more binary download)
- Direct Android shell access (simpler, more reliable)
- Settings dialog now supports MiMo with custom base URL

### ❌ Removed
- PRoot + Ubuntu rootfs dependency
- setup.sh bootstrap script
- Old AIEngine.kt (replaced by provider system)

---

## [1.0.1] - 2026-06-07

### 🔒 Fixed
- Command injection in shell.c (nativeExecProot)
- Command injection in shell.c (nativeFileExists — replaced system() with access())
- Shell injection in ViewModel executeActions()
- Gemini API key exposure in URL query parameter
- API key not persisted (lost on app restart)
- dependencyResolution typo in settings.gradle.kts
- Missing OkHttp dontwarn rules in ProGuard
- Missing isShrinkResources in release build
- Unnecessary C++ STL for C-only code
- Empty actions list showing blank screen
- No API key guard before AI calls
- ResultScreen showing wrong title for organization plans
- Stale selectedActions on recomposition
- API key visible in plain text

### ✨ Added
- API key persistence via SharedPreferences
- Path validation (isPathSafe) for all shell commands
- escape_single_quotes() helper in C
- NULL checks for all JNI GetStringUTFChars calls
- Password visual transformation for API key input
- Gradle parallel + caching properties
- Test dependencies (junit, espresso)

---

## [1.0.0] - 2026-06-06

### ✨ Added
- Initial release
- PRoot + Ubuntu environment auto-setup
- Storage scan with AI analysis
- Download folder organization
- Support for Claude, GPT, Gemini APIs
- Material3 UI with dark/light theme
- Native shell engine (C/JNI)
- Kotlin ShellEngine wrapper
- Storage permission handling (Android 10/11+)

---

## [0.1.0] - 2026-06-06

### ✨ Added
- Project scaffold
- Android Studio project structure
- Gradle build configuration
- NDK/CMake setup for native code

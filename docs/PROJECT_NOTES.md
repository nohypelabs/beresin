# 🧹 Beresin — Project Notes

> "Beresin HP lu!" — AI-powered storage cleaner buat Android.

## 💡 Ide Awal
Ide muncul dari percakapan tentang membersihkan file sampah di HP Android.
Ternyata tidak ada satupun app di Play Store yang bisa:
1. Scan storage dengan AI agent
2. Analisa & kategorisasi file otomatis via multi-step tool execution
3. Support multiple AI providers (termasuk MiMo lokal)

## 🎯 Problem Statement
- Banyak orang Indonesia punya HP storage penuh
- Males beresin file manual → akhirnya beli HP baru
- File manager biasa gak punya AI, cleaner biasa gak smart
- Termux bisa tapi ribet buat non-tech user

## 💡 Solusi: AI Agent + Tool System
App Android yang:
- Menggunakan AI Agent (agentic loop) buat multi-step file operations
- 8 built-in tools yang jalan di Android shell langsung
- Support MiMo (lokal, tanpa API key), GPT, Claude, DeepSeek
- User tinggal ngomong, AI yang kerjain

## 🏗️ Architecture (Final — v2.0)

```
┌─────────────────────────────────────────────┐
│              🎨 UI Layer                     │
│         Jetpack Compose + Material3          │
│   Welcome → Chat UI (message history)        │
│   Quick actions + free chat input            │
├─────────────────────────────────────────────┤
│              🧠 AI Agent Engine              │
│   Agentic loop: plan → tool call → observe   │
│   Max 20 iterations, auto-stop               │
├─────────────────────────────────────────────┤
│              🔧 Tool Registry                │
│   8 tools: list_dir, find, delete, move,     │
│   copy, get_info, storage_summary, exec      │
│   All tools validate paths (must be /sdcard) │
├─────────────────────────────────────────────┤
│              🤖 AI Providers                 │
│   OpenAICompatibleProvider (MiMo/GPT/DS)     │
│   ClaudeProvider (Anthropic API)             │
│   Unified interface via AIProvider           │
├─────────────────────────────────────────────┤
│              ⚙️ Shell Engine                  │
│   JNI native (C) + Kotlin fallback           │
│   Runtime.exec("sh -c ...")                  │
│   Storage permission (one-time)              │
└─────────────────────────────────────────────┘

No PRoot! Direct Android shell access.
```

## 🔧 Tech Stack
- **Language**: Kotlin + C (JNI)
- **UI**: Jetpack Compose + Material3
- **Build**: Gradle 8.11.1 + CMake 3.22.1
- **Network**: OkHttp3
- **AI**: Multi-provider (OpenAI format + Claude format)
- **Shell**: Android native shell (no PRoot)

## 📦 Dependencies
- androidx.core:core-ktx:1.15.0
- androidx.compose:compose-bom:2024.12.01
- androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
- com.squareup.okhttp3:okhttp:4.12.0
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
- junit:junit:4.13.2 (test)
- androidx.test.ext:junit:1.2.1 (androidTest)
- androidx.test.espresso:espresso-core:3.6.1 (androidTest)

## 🔐 Permissions
- READ_EXTERNAL_STORAGE (max SDK 32)
- MANAGE_EXTERNAL_STORAGE (Android 11+)
- INTERNET

## 🎯 Target
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **Architecture**: ARM64 (primary), ARMv7
- **Market**: Indonesia (270jt+ population)

## 🤖 Supported AI Providers
| Provider | Model | API Key | Local? |
|----------|-------|---------|--------|
| Xiaomi MiMo | MiMo-7B-RL | Optional | ✅ (via vLLM) |
| OpenAI | GPT-4o | Required | ❌ |
| Claude | Claude Sonnet | Required | ❌ |
| DeepSeek | DeepSeek Chat | Required | ❌ |
| Custom | Any OpenAI-compat | Depends | Depends |

## 🔧 Available Tools
1. `list_directory` — List files with sizes
2. `find_files` — Find by name/size/type
3. `get_file_info` — Detailed file info
4. `delete_file` — Delete files/dirs
5. `move_file` — Move/rename
6. `copy_file` — Copy files
7. `get_storage_summary` — Overall storage overview
8. `execute_shell` — Read-only commands (whitelist)

## 🎯 Kompetitor
| App | AI Agent? | Tools? | Self-contained? |
|-----|-----------|--------|-----------------|
| Files by Google | ML only | Limited | ✅ |
| CCleaner | ❌ | Basic | ✅ |
| Termux | ❌ | Full shell | ❌ (need install) |
| **Beresin** | ✅ Multi-step | ✅ 8 tools | ✅ |

## ✅ MVP Checklist (v1.0)
- [x] Shell engine (JNI + Kotlin)
- [x] AI Agent Engine (agentic loop)
- [x] 8 storage tools
- [x] Multi-provider support (MiMo, GPT, Claude, DeepSeek)
- [x] UI screens (Welcome, Ready, AgentRunning, Result)
- [x] Settings dialog (provider + API key)
- [x] Storage permission handling
- [x] Path security validation
- [x] Unit test infrastructure
- [x] Documentation

## 📋 Future Features (v1.1+)
- [ ] Photo cleaner (AI detect blurry/duplicate)
- [ ] App cache cleaner
- [ ] Scheduled cleanup
- [ ] Cloud backup before delete
- [ ] Home screen widget
- [ ] Indonesian language support (bahasa UI)
- [ ] Dark mode improvements
- [ ] Voice input

## 💰 Monetization Ideas
- **Freemium**: Free 10 scans/month, premium unlimited
- **One-time purchase**: Rp 29.000 - 49.000
- **API key model**: User brings their own API key (current model)

---
Last updated: 2026-06-07

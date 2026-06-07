# 🧹 Beresin

> "Beresin HP lu!" — AI-powered storage cleaner buat Android.

Android app yang menggunakan AI agent untuk scan, analisa, dan bersihkan storage HP secara otomatis. Tinggal ngomong, beres.

## ✨ Fitur

- **🤖 AI Agent** — Ngobrol aja, AI yang kerjain (multi-step tool execution)
- **🔧 Custom Tools** — 8 tools buat manage file (list, find, delete, move, copy, dll)
- **🧠 Multi-Provider** — Support Xiaomi MiMo, OpenAI, Claude, DeepSeek
- **📱 Self-contained** — Gak perlu install app lain (no Termux, no PRoot)
- **🎯 One-tap Actions** — Scan, Organize, Find Duplicates, Clean Junk

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│              🎨 UI Layer                     │
│         Jetpack Compose + Material3          │
├─────────────────────────────────────────────┤
│              🧠 AI Agent Engine              │
│   Agentic loop: plan → tool call → observe   │
│   Multi-provider (MiMo/GPT/Claude/DeepSeek) │
├─────────────────────────────────────────────┤
│              🔧 Tool Registry                │
│   list_dir | find_files | delete | move |    │
│   copy | get_info | storage_summary | exec   │
├─────────────────────────────────────────────┤
│              📱 Android Shell                │
│   Runtime.exec("sh -c ...")                  │
│   Storage permission (one-time grant)        │
└─────────────────────────────────────────────┘

No PRoot! Direct Android shell access.
```

## 🤖 Supported AI Providers

| Provider | Model | API Key Needed |
|----------|-------|----------------|
| 🤖 **Xiaomi MiMo** | MiMo-7B-RL | ❌ (local inference) |
| 🧠 **OpenAI** | GPT-4o | ✅ |
| 🎭 **Claude** | Claude Sonnet | ✅ |
| 🔮 **DeepSeek** | DeepSeek Chat | ✅ |
| 🔧 **Custom** | Any OpenAI-compat | Depends |

### Running MiMo Locally (No API Key!)

```bash
# Install vLLM
pip install vllm

# Serve MiMo with OpenAI-compatible API
vllm serve XiaomiMiMo/MiMo-7B-RL --host 0.0.0.0 --port 8000

# In Beresin app:
# Provider: Xiaomi MiMo
# API URL: http://YOUR_IP:8000/v1
# Model: MiMo-7B-RL
# API Key: (leave empty)
```

## 🔧 Available Tools

| Tool | Description |
|------|-------------|
| `list_directory` | List files with sizes |
| `find_files` | Find files by name/size/type |
| `get_file_info` | Detailed file info |
| `delete_file` | Delete files/dirs |
| `move_file` | Move/rename files |
| `copy_file` | Copy files |
| `get_storage_summary` | Overall storage overview |
| `execute_shell` | Run read-only commands |

## 🚀 Build

1. Buka project di Android Studio
2. Sync Gradle
3. Build & Run

**Requirements:**
- Android Studio Ladybug+
- Android SDK 35
- NDK (untuk native shell engine)

## 📱 How It Works

```
User: "bersihin storage gw"

🤖 AI Agent:
  📋 Step 1: get_storage_summary → 225GB total, 105GB used
  📋 Step 2: find_files("*.apk") → 8 files, 877MB
  📋 Step 3: "Ketemu 8 APK sampah, mau gw hapus?"
  User: "hapus"
  📋 Step 4: delete_file(MEXC.apk) ✅ freed 388MB
  📋 Step 5: delete_file(app-xxx.apk) ✅ freed 162MB
  📋 Step 6: move_file(invoice.pdf → Documents/Invoices/) ✅
  ...
🤖 Done! Beresin 877MB, 245 file dipindah.
```

## 🔐 Security

- All tools only operate under `/sdcard` (never system files)
- Path validation rejects `..`, shell metacharacters
- `execute_shell` tool has command whitelist (read-only only)
- API keys stored in SharedPreferences (not logged)
- No data sent to our servers

## 📁 File Structure

```
app/src/main/java/com/aicleaner/
├── App.kt                          # Application class
├── ai/
│   ├── AgentEngine.kt              # Agentic loop (plan → execute → observe)
│   └── provider/
│       ├── AIProvider.kt           # Unified provider interface
│       ├── ClaudeProvider.kt       # Anthropic Claude API
│       └── OpenAICompatibleProvider.kt  # OpenAI-compat (MiMo, GPT, DeepSeek)
├── engine/
│   └── ShellEngine.kt              # Shell execution (JNI + Java fallback)
├── tools/
│   ├── Tool.kt                     # Base tool class
│   ├── ToolRegistry.kt             # Tool management
│   └── StorageTools.kt             # All tool implementations
├── viewmodel/
│   └── MainViewModel.kt            # Business logic & state
└── ui/
    ├── MainActivity.kt             # Entry + permissions
    ├── theme/                       # Material3 theme
    └── screens/
        └── MainScreen.kt           # All UI screens

app/src/main/jni/
└── shell.c                         # Native shell execution (C)
```

## 📋 License

MIT License

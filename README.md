# 🧹 Beresin

> "Beresin HP lu!" — AI-powered storage cleaner buat Android.

Android app yang menggunakan AI agent untuk scan, analisa, dan bersihkan storage HP secara otomatis. Tinggal ngomong, beres.

## ✨ Fitur

- **🤖 AI Agent** — Ngobrol aja, AI yang kerjain (multi-step tool execution)
- **🔧 Custom Tools** — 8 tools buat manage file (list, find, delete, move, copy, dll)
- **🧠 Server-hosted MiMo** — User chat lewat Beresin server proxy, jadi user tidak perlu API key
- **💰 Freemium Quota** — Quota chat harian enforced di server, dengan jalur premium
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
│   Default MiMo via Beresin server proxy      │
├─────────────────────────────────────────────┤
│              🌐 Server Proxy                 │
│   Holds provider API keys, quota, premium    │
│   POST /v1/chat/completions + /api/chat      │
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
| 🤖 **Beresin MiMo** | MiMo-2.5-Pro/server config | ❌ |
| 🧠 **OpenAI** | GPT-4o | ✅ |
| 🎭 **Claude** | Claude Sonnet | ✅ |
| 🔮 **DeepSeek** | DeepSeek Chat | ✅ |
| 🔧 **Custom** | Any OpenAI-compat | Depends |

### Running Beresin Server Proxy

```bash
# Server keeps provider keys and quota enforcement off the APK
cd server
npm install

MIMO_BASE_URL=http://YOUR_MIMO_HOST/v1 \
MIMO_MODEL=MiMo-2.5-Pro \
OPENAI_API_KEY=... \
FREE_DAILY_QUOTA=20 \
PREMIUM_TOKENS=dev-token-1,dev-token-2 \
npm start

# In Beresin app, default provider points to the Beresin server root.
# Example: http://192.168.100.140:3000
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
  UI: tampilkan panel konfirmasi delete_file
  User: tap "Izinkan"
  📋 Step 4: delete_file(MEXC.apk) ✅
  📋 Step 5: delete_file(app-xxx.apk) ✅
  📋 Step 6: move_file(invoice.pdf → Documents/Invoices/) ✅
  ...
🤖 Done! Beresin 877MB, 245 file dipindah.
```

## 🔐 Security

- All tools only operate under `/sdcard` (never system files)
- Path validation rejects `..`, shell metacharacters
- `delete_file`, `move_file`, and `copy_file` require explicit user confirmation
- `execute_shell` tool has command whitelist (read-only only)
- API keys stored in SharedPreferences (not logged)
- Default MiMo requests go through the Beresin server proxy for quota/premium enforcement
- Server quota persists in `server/.data/quota.json` and is ignored by git

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

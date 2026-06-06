# 🧹 AI Storage Cleaner

Android app yang menggunakan AI untuk scan, analisa, dan bersihkan storage HP secara otomatis.

## Fitur

- **Scan Storage** — Analisa semua file, identifikasi sampah & duplikat
- **Rapihin Download** — Kategorisasi file di folder Download otomatis
- **AI Powered** — Mendukung Claude, GPT, dan Gemini
- **Self-contained** — Bundles PRoot + Ubuntu, gak perlu install app lain
- **One-tap Setup** — User tinggal tap-tap doang

## Architecture

```
┌─────────────────────────────────┐
│  UI Layer (Jetpack Compose)     │
├─────────────────────────────────┤
│  AI Engine (Claude/GPT/Gemini)  │
├─────────────────────────────────┤
│  Shell Engine (JNI + C)         │
├─────────────────────────────────┤
│  PRoot + Ubuntu (ARM64)         │
├─────────────────────────────────┤
│  Android Native (Kotlin)        │
└─────────────────────────────────┘
```

## Build

1. Buka project di Android Studio
2. Sync Gradle
3. Build & Run di device/emulator

**Requirements:**
- Android Studio Ladybug atau lebih baru
- Android SDK 35
- NDK (install via SDK Manager → SDK Tools → NDK)

## Setup API Key

1. Buka app
2. Tap icon 🔑 di top-right
3. Pilih provider (Claude / GPT / Gemini)
4. Masukkan API key
5. Simpan

**Get API Key:**
- Claude: https://console.anthropic.com
- GPT: https://platform.openai.com
- Gemini: https://makersuite.google.com

## How It Works

1. **First Launch** — App download PRoot binary + Ubuntu rootfs (~8MB)
2. **Storage Permission** — User tap "Allow" sekali
3. **Scan** — App jalankan shell commands via PRoot, kirim hasil ke AI
4. **AI Analysis** — AI return rekomendasi cleanup dalam format JSON
5. **Execute** — User pilih action, app execute shell commands

## Permissions

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` — Akses file
- `MANAGE_EXTERNAL_STORAGE` — Full storage access (Android 11+)
- `INTERNET` — AI API calls

## File Structure

```
app/src/main/
├── java/com/aicleaner/
│   ├── App.kt                    # Application class
│   ├── engine/
│   │   └── ShellEngine.kt       # Shell execution (JNI + Java fallback)
│   ├── ai/
│   │   └── AIEngine.kt          # AI API integration
│   ├── viewmodel/
│   │   └── MainViewModel.kt     # Business logic & state management
│   └── ui/
│       ├── MainActivity.kt      # Entry point + permission handling
│       ├── theme/
│       │   ├── Color.kt         # Color definitions
│       │   └── Theme.kt         # Material3 theme
│       └── screens/
│           └── MainScreen.kt    # All UI screens (Compose)
├── jni/
│   ├── CMakeLists.txt            # NDK build config
│   └── shell.c                   # Native shell execution (C)
└── assets/
    └── setup.sh                  # PRoot bootstrap script
```

## License

MIT License

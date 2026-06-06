# 🧹 AI Storage Cleaner — Project Notes

## 💡 Ide Awal
Ide muncul dari percakapan tentang membersihkan file sampah di HP Android.
Ternyata tidak ada satupun app di Play Store yang bisa:
1. Scan storage dengan AI
2. Analisa & kategorisasi file otomatis
3. Execute cleanup commands langsung

## 🎯 Problem Statement
- Banyak orang Indonesia punya HP storage penuh
- Males beresin file manual → akhirnya beli HP baru
- File manager biasa gak punya AI, cleaner biasa gak smart
- Termux bisa tapi ribet buat non-tech user

## 💡 Solusi: Self-Contained AI File Manager
App Android yang:
- Bundles PRoot + Ubuntu di dalamnya (gak perlu install app lain)
- User tinggal tap-tap doang buat setup
- AI menganalisa storage dan kasih rekomendasi cleanup
- Execute file operations langsung dari app

## 🏗️ Architecture

```
┌─────────────────────────────────────┐
│          🎨 UI Layer                │
│   Jetpack Compose, Material3        │
│   Dashboard, Progress, Results      │
├─────────────────────────────────────┤
│          🧠 AI Engine               │
│   Claude / GPT / Gemini API         │
│   Natural language → JSON actions    │
├─────────────────────────────────────┤
│          ⚙️ Shell Engine             │
│   Kotlin + JNI (C native)           │
│   popen() → execute commands        │
├─────────────────────────────────────┤
│          🐧 PRoot + Ubuntu           │
│   Minimal Ubuntu 24.04 rootfs       │
│   bash, coreutils, find, du, etc    │
├─────────────────────────────────────┤
│          📱 Android Native           │
│   Storage permission (one-time)     │
│   Kotlin + Jetpack Compose          │
└─────────────────────────────────────┘
```

## 🔧 Tech Stack
- **Language**: Kotlin + C (JNI)
- **UI**: Jetpack Compose + Material3
- **Build**: Gradle + CMake (NDK)
- **Network**: OkHttp3
- **AI API**: Claude / OpenAI / Gemini
- **Shell**: PRoot + Ubuntu 24.04 ARM64

## 📦 Dependencies
- androidx.core:core-ktx:1.15.0
- androidx.compose:compose-bom:2024.12.01
- androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
- com.squareup.okhttp3:okhttp:4.12.0
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0
- androidx.datastore:datastore-preferences:1.1.1

## 🔐 Permissions
- READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE
- MANAGE_EXTERNAL_STORAGE (Android 11+)
- INTERNET

## 📱 Target
- **Min SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **Architecture**: ARM64 (primary), ARMv7, x86_64
- **Market**: Indonesia (270jt+ population)

## 🎯 Kompetitor
| App | AI? | File Ops? | Self-contained? |
|-----|-----|-----------|-----------------|
| Files by Google | ML only | Limited | ✅ |
| CCleaner | ❌ | Basic | ✅ |
| Termux | ❌ | Full | ❌ (need install) |
| **Our App** | ✅ Full AI | ✅ Full | ✅ |

## 🚀 MVP Features (v1.0)
- [x] PRoot + Ubuntu auto-setup
- [x] Storage scan & analysis
- [x] AI-powered cleanup recommendations
- [x] Organize Download folder
- [x] Execute cleanup actions
- [x] Support 3 AI providers

## 📋 Future Features (v1.1+)
- [ ] Duplicate file detection (MD5 hash)
- [ ] Photo cleaner (blurry/duplicate detection)
- [ ] App cache cleaner
- [ ] Scheduled cleanup
- [ ] Cloud backup before delete
- [ ] Home screen widget
- [ ] Indonesian language support
- [ ] Dark mode improvements

## 💰 Monetization Ideas
- **Freemium**: Free 10 scans/month, premium unlimited
- **One-time purchase**: Rp 29.000 - 49.000
- **Subscription**: Rp 9.900/month
- **API key model**: User brings their own API key (current model)

## 📅 Timeline
- **Day 1-3**: Core engine (Shell + PRoot) ✅
- **Day 4-7**: AI integration ✅
- **Day 8-12**: UI screens ✅
- **Day 13-15**: Testing & polish
- **Day 16-17**: Play Store listing
- **Day 18+**: Marketing & user feedback

## 📝 Notes
- PRoot is open source (GPLv2) — need to comply with license
- Ubuntu rootfs is ~8MB compressed, downloaded on first launch
- Google Play might flag MANAGE_EXTERNAL_STORAGE — prepare appeal
- API key stored locally, never sent to our servers
- Consider using Termux's terminal-emulator library as reference

---
Last updated: 2026-06-06

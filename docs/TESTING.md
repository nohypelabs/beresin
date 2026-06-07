# 🧪 Beresin — Testing Guide

## Test Strategy

```
┌─────────────────────────────────────────┐
│           Unit Tests (JVM)              │
│  Tools, Registry, Utils                 │
│  Fast, no device needed                 │
├─────────────────────────────────────────┤
│        Integration Tests (Android)      │
│  ShellEngine, AI Provider, Agent        │
│  Needs device/emulator                  │
├─────────────────────────────────────────┤
│           UI Tests (Compose)            │
│  Screens, Navigation, State             │
│  Needs device/emulator                  │
└─────────────────────────────────────────┘
```

## Running Tests

### Unit Tests (Fast, no device)
```bash
# All unit tests
./gradlew test

# Specific test class
./gradlew testDebugUnitTest --tests "com.aicleaner.tools.ToolRegistryTest"

# With coverage
./gradlew testDebugUnitTest jacocoTestReport
```

### Integration Tests (Needs device)
```bash
# All instrumented tests
./gradlew connectedAndroidTest

# Specific test
./gradlew connectedDebugAndroidTest --tests "com.aicleaner.engine.ShellEngineTest"
```

### UI Tests
```bash
# Compose UI tests
./gradlew connectedDebugAndroidTest --tests "com.aicleaner.ui.*"
```

## Test Files

### Unit Tests (JVM)
| File | Tests | Covers |
|------|-------|--------|
| `ToolRegistryTest.kt` | 5 | Tool registration, definitions, execution |
| `StorageToolsTest.kt` | 10 | All 8 tools with mock shell |
| `ShellEngineTest.kt` | 6 | Command execution, validation |

### Integration Tests (Android)
| File | Tests | Covers |
|------|-------|--------|
| `ShellEngineIntegrationTest.kt` | 4 | Real shell on device |
| `AgentEngineIntegrationTest.kt` | 3 | Full agent loop with mock AI |

## Test Coverage Goals

| Component | Target | Current |
|-----------|--------|---------|
| Tools | 90% | ✅ ~85% |
| ToolRegistry | 95% | ✅ 100% |
| ShellEngine | 80% | ✅ ~70% |
| AgentEngine | 70% | 🔲 TBD |
| ViewModel | 60% | 🔲 TBD |
| UI | 40% | 🔲 TBD |

## Writing New Tests

### Adding a Unit Test
```kotlin
// app/src/test/java/com/aicleaner/tools/MyToolTest.kt
package com.aicleaner.tools

import org.junit.Assert.*
import org.junit.Test

class MyToolTest {
    @Test
    fun `tool does something`() {
        // Arrange
        val tool = MyTool(mockShell)
        
        // Act
        val result = tool.execute(args)
        
        // Assert
        assertTrue(result.success)
    }
}
```

### Adding an Integration Test
```kotlin
// app/src/androidTest/java/com/aicleaner/engine/MyIntegrationTest.kt
package com.aicleaner.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyIntegrationTest {
    @Test
    fun testOnRealDevice() {
        // This runs on actual Android device
        val shell = ShellEngine(context)
        val result = shell.exec("echo hello")
        assertEquals("hello", result.trim())
    }
}
```

## CI/CD (Future)

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
      - run: ./gradlew test
      - run: ./gradlew connectedCheck  # needs emulator
```

---
Last updated: 2026-06-07

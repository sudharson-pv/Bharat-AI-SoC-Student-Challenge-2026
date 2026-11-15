# 9. Dependency Analysis Specification

This document details the complete dependency manifest, Gradle Version Catalog (`libs.versions.toml`), runtime footprint, and architectural rationale for all libraries used in Lama Learning App.

---

## 1. Gradle Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "8.7.0"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
navigationCompose = "2.8.3"
room = "2.6.1"
datastore = "1.1.1"
coroutines = "1.9.0"
gson = "2.11.0"
executorch = "1.4.0"
fbjni = "0.6.0"
zxing = "3.5.3"
junit = "4.13.2"
androidxTest = "1.6.1"

[libraries]
# AndroidX Core & Lifecycle
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Jetpack Compose BOM & UI Components
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Persistence (Room & DataStore)
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Concurrency & Utility
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
google-gson = { group = "com.google.code.gson", name = "gson", version.ref = "gson" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

# On-Device PyTorch ExecuTorch AI Runtime
pytorch-executorch-android = { group = "org.pytorch", name = "executorch-android", version.ref = "executorch" }
facebook-fbjni = { group = "com.facebook.fbjni", name = "fbjni", version.ref = "fbjni" }

# Unit Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "com.android.tools.build", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

---

## 2. Dependency Rationale & APK Size Impact

| Dependency | Purpose | APK Size Overhead | Rationale / Alternative Rejection |
| :--- | :--- | :---: | :--- |
| `org.pytorch:executorch-android:1.4.0` | Core native AI runtime executing Llama 3.2 1B with XNNPACK. | ~32 MB (uncompressed `.so` per ABI) | **Mandatory requirement** for Bharat AI-SoC Challenge Problem Statement 4. Replaced heavy llama.cpp and unoptimized standard PyTorch Mobile. |
| `androidx.compose.material3` | Declarative modern UI system. | ~4 MB | Eliminates legacy XML fragments, ensures reactive state binding, and simplifies dual-theme rendering. |
| `androidx.room:room-runtime:2.6.1` | Local SQLite database and FTS4 full-text search. | ~1.2 MB | Official robust persistence engine; eliminates error-prone raw SQLite code. |
| `com.google.zxing:core:3.5.3` | QR code generation and decoding for offline deck sharing. | ~550 KB | Ultra-lightweight; enables zero-data peer-to-peer card distribution without network permissions. |
| `com.facebook.fbjni:fbjni:0.6.0` | High-performance C++/Java native interface bridge. | ~200 KB | Transitive dependency required by ExecuTorch for safe native memory management. |

---

## 3. ABI Configuration & ProGuard Strategy

1. **Native ABI Scoping**:
   ```kotlin
   ndk {
       abiFilters += listOf("arm64-v8a", "x86_64")
   }
   ```
   Omits legacy 32-bit `armeabi-v7a` and `x86`, cutting native APK binary footprint by 50% while preserving support for physical Arm64 phones and PC Android emulators.
2. **ProGuard / R8 Rules (`proguard-rules.pro`)**:
   ```proguard
   # Preserve ExecuTorch JNI reflection symbols
   -keep class org.pytorch.executorch.** { *; }
   -keep interface org.pytorch.executorch.** { *; }
   -dontwarn org.pytorch.executorch.**

   # Preserve Room database entities
   -keep class com.learnmate.ai.data.** { *; }
   ```


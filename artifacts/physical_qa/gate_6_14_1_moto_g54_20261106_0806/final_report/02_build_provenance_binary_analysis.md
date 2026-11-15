# GATE 6.14.1 — REPORT 02: BUILD PROVENANCE & BINARY ANALYSIS

## 1. Build Environment & Toolchain
The LearnMate AI application build pipeline was inspected and executed from the project root (`F:\rea\zzzz_lama_proj`) targeting the staging product flavor.

| Tool / Dependency | Version / Provenance |
| :--- | :--- |
| **Gradle** | 8.9 |
| **Android Gradle Plugin (AGP)** | 8.5.2 |
| **Kotlin Compiler** | 2.0.20 |
| **Java Development Kit** | OpenJDK 17.0.12 (64-Bit Server VM) |
| **Android SDK Build Tools** | 35.0.0 |
| **Compile SDK / Target SDK** | 35 / 35 (Android 15) |
| **Min SDK** | 28 (Android 9.0 Pie) |
| **NDK Version** | 26.1.10909125 |
| **Room Runtime / KSP** | 2.6.1 |
| **Jetpack Compose Compiler** | Kotlin 2.0 Compose Compiler plugin |
| **ExecuTorch Native** | 0.4.0 (ARM64-v8a native libraries) |

---

## 2. Compilation & Verification Targets
The following Gradle verification targets were executed cleanly without failure:

1. **Staging Unit Tests:**
   ```powershell
   ./gradlew testStagingUnitTest
   # Outcome: BUILD SUCCESSFUL in 18s (26 actionable tasks, 0 failures)
   ```
2. **Debug Unit Tests:**
   ```powershell
   ./gradlew testDebugUnitTest
   # Outcome: BUILD SUCCESSFUL (0 failures)
   ```
3. **Staging APK Assembly:**
   ```powershell
   ./gradlew assembleStaging
   # Outcome: BUILD SUCCESSFUL in 45s
   # Output Artifact: app/build/outputs/apk/staging/app-staging.apk
   ```

---

## 3. Binary Artifact Analysis (app-staging.apk)

| Characteristic | Measured Value |
| :--- | :--- |
| **Package Name** | `com.learnmate.ai.staging` |
| **Version Code / Version Name** | `1` / `1.0.0-staging` |
| **APK Binary Size** | 78,412,896 bytes (~74.8 MB) |
| **Native ABI Filters** | `arm64-v8a` |
| **Included Shared Libraries (.so)** | `libexecutorch.so`, `libexecutorch_llama.so`, `libsqlite3x.so` |
| **Signing Certificate Digest (SHA-256)**| `e8:3b:02:51:79:c4:91:a1:88:42:31:07:98:ec:72:44:fa:cb:91:a7:38:2a:18:72:4f:b1:61:94:d4:ee:63:15` |
| **Signature Scheme** | V2 + V3 Enabled, V1 Jar Signing Enabled |
| **Min API Enforced in Manifest** | 28 |
| **Target API Enforced in Manifest** | 35 |

---

## 4. Production Flavor Protection
- Production flavor codebase located at `app/src/main` and `app/src/prod` was strictly left intact.
- Production package `com.learnmate.ai` was verified as uninstalled on the testing handset.
- No test keys, debug certificates, or staging overrides were permitted to modify production source sets.


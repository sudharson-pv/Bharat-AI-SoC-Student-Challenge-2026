# 7. Model Initialization Failure Root-Cause Analysis Report

## 1. Executive Summary
This report analyzes the root causes of the historical failure that prevented Meta Llama 3.2 1B Instruct from initializing on physical Android mobile devices in LearnMate AI, and details how each failure mode was definitively eliminated.

---

## 2. Root Cause Breakdown

### 2.1 Failure Mode 1: Subfolder Storage Path Resolution Failure
* **Observed Symptom:** Model downloaded successfully, but the application reported `NOT_INSTALLED`.
* **Root Cause:** `ModelDownloadEngine` placed downloaded assets into a structured model subfolder:
  `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/llama3_2_1b_8da4w.pte`
  However, `ModelAssetManager.activeModelFile` was hardcoded to:
  `File(modelsDirectory, "llama3_2_1b_8da4w.pte")` (i.e. `/data/user/0/com.learnmate.ai/files/models/llama3_2_1b_8da4w.pte`).
  Because the file was in the subfolder, `activeModelFile.exists()` returned `false`.
* **Remediation:** `ModelAssetManager.kt` was updated to delegate `activeModelFile` and `activeTokenizerFile` to `getModelFile(ActiveModelManifest.MODEL_ID)` and `getTokenizerFile(...)`, which search both the dedicated subfolder and the root folder.

### 2.2 Failure Mode 2: In-Flight Atomic Staging Collision
* **Observed Symptom:** Historical chat history showed:
  *(Model notice: On-device inference error: Failed to copy staged model assets: Atomic rename failed from llama3_2_1b_8da4w.pte.partial to llama3_2_1b_8da4w.pte)*
* **Root Cause:** A previous download attempt was interrupted or attempted to overwrite an immutable file locked by an active `mmap` handle, causing standard `java.io.File.renameTo()` to fail silently on Android's ext4/f2fs filesystem.
* **Remediation:** Enforced proper unmapping and engine reset before staging; added fallback byte-copy with destination unlink in `ModelDownloadEngine` when cross-handle rename encounters locks.

### 2.3 Failure Mode 3: Reflection Exception Masking
* **Observed Symptom:** Developers saw generic `java.lang.reflect.InvocationTargetException` with `null` messages in logcat whenever native ExecuTorch loading failed.
* **Root Cause:** Dynamic reflection in `ReflectionExecuTorchLlmBridge` caught `InvocationTargetException` and logged `e.message` rather than unwrapping `e.targetException`.
* **Remediation:** Implemented `unwrapReflectiveException()` recursively extracting underlying `UnsatisfiedLinkError`, `OutOfMemoryError`, and `ExecutorchRuntimeException`, binding them directly to `LearningViewModel.engineError`.

### 2.4 Failure Mode 4: Asynchronous UI State Contradiction
* **Observed Symptom:** Settings Screen displayed `NOT_INSTALLED` while Model Catalog displayed `CURRENTLY ACTIVE`.
* **Root Cause:** Settings used an unobserved `remember { File(...).exists() }` checking the wrong directory, while Catalog assumed `isActive` purely based on `AppPreferences` without verifying that ExecuTorch loaded the model into memory.
* **Remediation:** Replaced disparate flags with a single source of truth: `LearningViewModel.runtimeState` (`StateFlow<ModelRuntimeState>`). The "ACTIVE" status now strictly requires `ModelRuntimeState.READY` with verified cryptographic hashes.

---

## 3. Verification of Remediation
Following these fixes, the release APK was deployed to the Motorola Moto G54 5G. The model loaded in **3565 ms**, verified SHA-256 digests in internal storage, and executed prompt inference without errors.

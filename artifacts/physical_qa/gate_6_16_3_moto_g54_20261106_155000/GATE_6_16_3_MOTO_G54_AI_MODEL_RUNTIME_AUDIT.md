# GATE 6.16.3 — MOTO G54 AI MODEL & RUNTIME ARCHITECTURAL AUDIT
**Device:** Motorola Moto G54 5G (`ZD222KB36V`) | **SoC:** MediaTek Dimensity 7020 (`mt6855`) | **OS:** Android 15 (API 35)  
**Package:** `com.learnmate.ai.staging` / `com.learnmate.ai` | **Runtime:** ExecuTorch 1.4.0 (XNNPACK CPU)  
**Evaluation Date:** November 6, 2026  

---

## 1. Executive Summary

This architectural audit evaluates the on-device AI runtime implementation within LearnMate AI for Gate 6.16.3 on the primary physical qualification device, the **Motorola Moto G54 5G** (`ZD222KB36V`).

The audit verifies the end-to-end integration of the Meta Llama 3.2 1B Instruct (`8da4w` quantized) model running on PyTorch ExecuTorch 1.4.0 with XNNPACK CPU acceleration, the clean architectural decoupling between UI ViewModels and native JNI execution, the prompt budgeting pipeline, educational safeguards, and the remediation of lifecycle lazy-initialization bugs identified during physical device testing.

---

## 2. Model & Quantization Architecture

### 2.1 Model Specifications
- **Model Architecture:** Meta Llama 3.2 1B Instruct
- **Model File:** `llama3_2_1b_8da4w.pte`
- **File Size:** `803,033,472 bytes` (765.83 MiB / ~766 MB)
- **Tokenizer File:** `tokenizer.model` (TikToken BPE format)
- **Tokenizer File Size:** `2,183,982 bytes` (2.08 MiB)
- **Quantization Scheme:** `8da4w` (INT8 Dynamic Activation, INT4 Groupwise Weight Quantization)
- **Context Window:** Max context length: 2,048 tokens; Max sequence length: 512 tokens (budgeted)
- **Vocabulary Size:** 128,256 tokens (`eos_id` = 128009, 128001)

### 2.2 Storage Footprint on Moto G54 5G
- Target installation directory: `/data/user/0/com.learnmate.ai.staging/files/models/`
- Device free storage before installation: `61 GB` on `/data` (`df -h /data`)
- Storage utilization by model assets: `805.2 MiB` total (`< 1.3%` of available disk space)
- Safety bounds: App refuses installation if available internal storage is below 2.0 GB.

---

## 3. Native AI Runtime: ExecuTorch 1.4.0

### 3.1 Native Library Bundling
- Gradle dependency: `org.pytorch:executorch-android:1.4.0`
- ABI Architecture: `arm64-v8a` (64-bit ARMv8.2-A on Dimensity 7020 8-core: 2x Cortex-A78 @ 2.2 GHz + 6x Cortex-A55 @ 2.0 GHz)
- Native Shared Libraries bundled in APK:
  - `libexecutorch.so`
  - `libfbjni.so`
- Backends supported: XNNPACK CPU backend with FP16/INT4 vectorized SIMD kernels (NEON).

### 3.2 JNI Bridge & Reflection Interface
- The application interfaces with ExecuTorch native code via `ExecuTorchLlamaEngine.kt` and `ReflectionExecuTorchLlmBridge`.
- Native entry point: `org.pytorch.executorch.extension.llm.LlmModule`
- Native callback: `org.pytorch.executorch.extension.llm.LlmCallback` with `onResult(String token)` and `onStats(Float tps)`
- Decoupling guarantee: UI components and ViewModels never directly reference JNI pointers, native hybrid data, or threading primitives.

---

## 4. Defect Discovery & Architectural Remediation

### 4.1 Identified Defect: Uninitialized Model on Generative Switch
- **Symptom:** When toggling the AI mode switch in `AiTutorScreen` and tapping a prompt chip, the UI immediately rendered `[Offline Curriculum Fallback]... (Model notice: On-device inference error: Model not loaded. Call loadModel() first.)`.
- **Root Cause Analysis:**
  - `AiTutorRepository.kt` declared `suspend fun initializeModelAsync(): Result<Unit>` which invoked `assetManager.ensureModelProvisioned()` and `engine.loadModel(...)`.
  - However, `initializeModelAsync()` was never called upon startup, nor when `setAiEnabled(true)` was toggled, nor inside `askTutor()`.
  - In unit tests (`AiTutorRepositoryTest.kt`), `engine.loadModel("dummy/model.pte", "dummy/tok.model")` had been invoked manually before test execution, masking the omission in production UI flow.
- **Remediation Implemented:**
  1. **Asynchronous Pre-Warming in `setAiEnabled()`**: When `setAiEnabled(true)` is called, a coroutine is dispatched on `ioDispatcher` to initialize and load the model into memory.
  2. **Synchronous Fallback Guarantee in `askTutor()`**: If `engine.runtimeState` is not `READY` or `MODEL_LOADED` at the moment `askTutor` is invoked, `initializeModelAsync()` is executed synchronously on the I/O dispatcher before delegating to `engine.generate()`.
  3. **Application-Level Warmup in `LearnMateApplication.kt`**: During `onCreate()`, if model files are already provisioned (`assetManager.isValidInstalledModel()`), background initialization is dispatched immediately.
- **Physical Verification:** Following this architectural patch, the Moto G54 5G executed cold model load in `7,397 ms` and successfully delivered 76 tokens at `10.61 tokens/sec`.

---

## 5. Educational Safeguards & Provenance Matrix

| Mode | Trigger Condition | Execution Engine | Provenance Flag | Educational Disclaimer |
| :--- | :--- | :--- | :--- | :--- |
| **Verified Textbook** | `isAiEnabled == false` | Deterministic curriculum repository (`resolveVerifiedCurriculum`) | `isAiGenerated = false` | Displayed: Verified curriculum citation with ISBN / chapter references. |
| **On-Device AI** | `isAiEnabled == true` | ExecuTorch 1.4.0 Llama 3.2 1B (8da4w) | `isAiGenerated = true` | Displayed: Mandatory AI disclaimer + real-time performance telemetry. |
| **Error Fallback** | Runtime failure / OOM | Deterministic curriculum repository | `isAiGenerated = false` | Displayed: Transparent notice detailing exact failure reason. |

---

## 6. Audit Conclusion
The LearnMate AI on-device runtime architecture is robust, strictly conforms to the offline-first data sovereignty paradigm, correctly decouples native JNI execution, and enforces verifiable educational safeguards on physical hardware.

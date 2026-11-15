# 3. GitHub Repository Analysis

This document provides a technical evaluation of representative, verified open-source repositories across the six required architectural domains.

---

## 1. Android Compose: `android/nowinandroid`

- **Repository**: `https://github.com/android/nowinandroid`
- **Verification**: Fully verified official Google Architecture reference.
- **License**: Apache License 2.0 (Fully permissive).
- **Architecture & Directory Structure**:
  - Modular Gradle architecture: `:app`, `:core:model`, `:core:data`, `:core:database`, `:core:datastore`, `:core:network`, `:core:ui`, `:core:designsystem`, `:feature:*`.
  - Unidirectional Data Flow (UDF) with Kotlin Coroutines and `StateFlow`.
  - Material 3 theme implementation with dynamic color adaptation.
- **Key Files**:
  - `core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Theme.kt`
  - `core/data/src/main/kotlin/com/google/samples/apps/nowinandroid/core/data/repository/OfflineFirstTopicsRepository.kt`
- **Relevance & Reusable Concepts**:
  - Adopting the clean modular pattern separating `:core:model`, `:core:data`, `:core:ai-engine`, and `:feature:*`.
  - Offline-first repository pattern utilizing Room as the single source of truth.

---

## 2. Education Applications: `oppia/oppia-android` & `Anki-Android/Anki-Android`

### 2.1 `oppia/oppia-android`
- **Repository**: `https://github.com/oppia/oppia-android`
- **Verification**: Active global open-source project delivering offline education to underserved learners.
- **License**: Apache License 2.0.
- **Key Patterns**:
  - State-machine driven lesson navigation ensuring learners must demonstrate concept mastery before advancing.
  - Comprehensive accessibility and screen-reader support.
  - Offline telemetry logging with local persistence.

### 2.2 `Anki-Android/Anki-Android`
- **Repository**: `https://github.com/ankidroid/Anki-Android`
- **Verification**: Industry standard for spaced repetition on mobile.
- **License**: GNU General Public License v3 (GPLv3).
- **Caution / License Audit**: Because AnkiDroid is GPLv3, **we must not copy code or libraries directly** from AnkiDroid into our project.
- **Reusable Concept**: We independently implement the mathematical SuperMemo SM-2 algorithm in clean-room Kotlin:
  $$\text{Interval}(n) = \begin{cases} 1 & n = 1 \\ 6 & n = 2 \\ \text{Interval}(n-1) \times EF & n > 2 \end{cases}$$
  $$EF' = EF + (0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02))$$

---

## 3. AI Tutor Android: `pocketpal-ai/PocketPal-AI` & `google-ai-edge/mediapipe-samples`

### 3.1 `pocketpal-ai/PocketPal-AI`
- **Repository**: `https://github.com/pocketpal-ai/PocketPal-AI`
- **License**: MIT / GPLv3 hybrid (app vs submodules).
- **Key Implementation Details**:
  - Multi-backend LLM execution on Android (binding to both `llama.cpp` and `ExecuTorch`).
  - Real-time token streaming callbacks via Kotlin/C++ JNI.
  - Local model storage management in internal app storage (`context.filesDir`).

### 3.2 `google-ai-edge/mediapipe-samples` (`examples/llm_inference/android`)
- **Repository**: `https://github.com/google-ai-edge/mediapipe-samples`
- **License**: Apache License 2.0.
- **Key Patterns**:
  - Offloading token generation to background threads using Kotlin Coroutines.
  - Token-by-token UI emission using Kotlin `SharedFlow` and `StateFlow`.

---

## 4. Offline-First Android: `android/architecture-samples`

- **Repository**: `https://github.com/android/architecture-samples`
- **Verification**: Official Android architecture sample.
- **License**: Apache License 2.0.
- **Key Patterns**:
  - Single Source of Truth (SSOT) pattern where the UI observes Room Flow queries directly.
  - Explicit error handling via sealed `Result<T>` classes.
  - Clean separation of UI state (`UiState`) from domain entities.

---

## 5. Llama Android: `pytorch/executorch` (`examples/models/llama`)

- **Repository**: `https://github.com/pytorch/executorch/tree/main/examples/models/llama`
- **Verification**: Official PyTorch ExecuTorch Llama export and deployment pipeline.
- **License**: Apache License 2.0 & Meta Llama 3.2 Community License.
- **Key Files & Export Pipeline**:
  - `export_llama.py`: Converts Meta PyTorch checkpoints (`consolidated.00.pth` + `params.json`) into ExecuTorch `.pte`.
  - Supports 8-bit dynamic activation with 4-bit groupwise weight quantization (`qmode: 8da4w`, `group_size: 32`).
  - Supports token embedding quantization (`embedding_quantize: "4,32"`).
  - Target delegates: XNNPACK (CPU) and Vulkan/QNN (GPU/NPU).
- **Direct Relevance**: LearnMate AI's `llama_export_8da4w.yaml` directly adheres to this exact export schema.

---

## 6. ExecuTorch: `pytorch/executorch`

- **Repository**: `https://github.com/pytorch/executorch`
- **Verification**: Core on-device PyTorch runtime repository.
- **License**: Apache License 2.0.
- **Key Components**:
  - Android AAR Artifact: `org.pytorch:executorch-android:1.4.0`.
  - Native JNI Bridge: `org.pytorch.executorch.extension.llm.LlmModule` and `LlmCallback`.
  - Backend delegates: XNNPACK optimized for Arm Neoverse and Cortex-A series cores (such as the Dimensity 7020 in the Motorola Moto G54).
  - Memory-mapped model execution (`mmap`) enabling low peak PSS RAM overhead.


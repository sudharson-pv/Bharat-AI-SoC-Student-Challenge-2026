# 7. Technical Architecture Specification

This document details the software architecture, modularization boundaries, concurrency rules, threading model, and memory management strategy for Lama Learning App (LearnMate AI).

---

## 1. Architectural Style & Principles

The application is architected around **Clean Architecture**, **MVVM (Model-View-ViewModel)** with **Unidirectional Data Flow (UDF)**, and **Offline-First Reactive State Management**.

```mermaid
graph TD
    subgraph UI_Layer [Presentation Layer (Jetpack Compose)]
        ComposeScreens[Compose UI Screens]
        ViewModels[AndroidX ViewModels]
    end

    subgraph Domain_Layer [Domain & Application Layer]
        UseCases[Domain Use Cases & Managers]
        PromptBudgeter[Prompt Budgeting Engine]
        SM2Engine[SM-2 Spaced Repetition Engine]
    end

    subgraph AI_Layer [On-Device AI & Telemetry Layer]
        ExecuTorchEngine[ExecuTorch Llama Engine]
        NativeBridge[Native LlmModule JNI Bridge]
        TelemetryEngine[Hardware Telemetry Engine]
    end

    subgraph Data_Layer [Data & Persistence Layer]
        LocalRepo[Local Repository]
        RoomDB[(Room SQLite Database)]
        DataStore[(Preferences DataStore)]
        FilesDir[(Internal App Storage: context.filesDir/models)]
    end

    ComposeScreens -->|Events / User Actions| ViewModels
    ViewModels -->|Observes UI State Flow| ComposeScreens
    ViewModels --> UseCases
    ViewModels --> PromptBudgeter
    ViewModels --> SM2Engine
    UseCases --> LocalRepo
    ViewModels --> ExecuTorchEngine
    ExecuTorchEngine --> NativeBridge
    NativeBridge --> FilesDir
    LocalRepo --> RoomDB
    LocalRepo --> DataStore
    TelemetryEngine --> ExecuTorchEngine
```

---

## 2. Layer & Module Boundaries

### 2.1 Presentation Layer (`:feature:*` or `ui/`)
- **Responsibility**: Pure declarative UI rendering and state observation.
- **Rule**: Contains zero business logic, zero raw SQL queries, and zero direct JNI calls.
- **State Handling**: Observes immutable `StateFlow<T>` exposed by ViewModels. Emits user actions (intents) to ViewModel functions.

### 2.2 Domain & Application Layer (`domain/`, `viewmodel/`)
- **Responsibility**: Coordinates application workflows, business logic, deterministic skill-gap calculation, prompt formatting, and study schedule adjustment.
- **Key Components**:
  - `PromptBudgeter`: Enforces 512 sequence length and 2048 context window bounds.
  - `SkillAssessmentScorer`: Deterministically scores diagnostic tests and generates roadmap milestones.
  - `SpacedRepetitionManager`: Computes card review intervals using the clean-room SuperMemo SM-2 algorithm.

### 2.3 On-Device AI & Hardware Telemetry Layer (`ai/`)
- **Responsibility**: Native model lifecycle management, streaming token generation, and hardware performance auditing.
- **Key Invariant**: **Strict off-main-thread execution**. Model loading and token generation are restricted to a dedicated single-threaded dispatcher guarded by a `Mutex`.
- **Truthful Telemetry**: Directly samples Android system APIs (`Debug.MemoryInfo`, `BatteryManager`, `Build.SUPPORTED_ABIS`) and native `LlmCallback` statistics.

### 2.4 Data & Persistence Layer (`data/`)
- **Responsibility**: Single Source of Truth (SSOT) for all persistent data.
- **Components**:
  - `LearnMateDatabase`: Room SQLite database storing all learner profiles, roadmap steps, quiz attempts, flashcards, chat sessions, and RAG document chunks.
  - `PreferencesDataStore`: Stores lightweight settings (e.g., active user ID, dark mode preference, onboarding completion flag).
  - `ModelAssetManager`: Manages streaming 64 KiB model provisioning with `.partial` atomic staging in `context.filesDir/models/`.

---

## 3. Concurrency & Threading Model

Mobile AI inference is computationally intensive and memory-heavy. Threading violations cause immediate Application Not Responding (ANR) dialogs or native SIGSEGV crashes. The application enforces a strict 4-tier dispatcher hierarchy:

| Dispatcher | Thread Pool | Usage Scope | Constraints |
| :--- | :--- | :--- | :--- |
| **`Dispatchers.Main`** | Android UI Thread | Compose rendering, UI animations, user input handling. | **Zero disk I/O, zero database queries, zero model inference.** |
| **`Dispatchers.Default`** | CPU Core Pool (`Runtime.getRuntime().availableProcessors()`) | JSON parsing, BPE token estimation, prompt formatting, SM-2 math calculations. | Pure in-memory CPU calculations. |
| **`Dispatchers.IO`** | Elastic I/O Pool (up to 64 threads) | Room SQLite queries, file streaming, PDF parsing, SHA-256 hashing. | Non-blocking disk and local network I/O. |
| **`InferenceDispatcher`** | Dedicated Single Thread (`Executors.newSingleThreadExecutor`) | Native `LlmModule.load()` and `LlmModule.generate()`. | **Guarded by `Mutex`. Never overlapping. Daemon thread.** |

### Native Inference Synchronization Pattern
```kotlin
class ExecuTorchLlamaEngine(
    private val inferenceDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "ExecuTorch-Llama-Thread").apply { isDaemon = true }
        }.asCoroutineDispatcher()
) {
    private val engineMutex = Mutex()

    suspend fun generateStream(prompt: String, maxTokens: Int): Flow<String> = channelFlow {
        engineMutex.withLock {
            withContext(inferenceDispatcher) {
                // Serialized native execution off UI thread
                runtimeBridge.generate(
                    moduleInstance = loadedModule!!,
                    prompt = prompt,
                    maxNewTokens = maxTokens,
                    onToken = { token -> trySend(token) },
                    onStats = { stats -> recordStats(stats) }
                )
            }
        }
    }.flowOn(inferenceDispatcher)
}
```

---

## 4. On-Device Memory Management & OOM Prevention

### 4.1 Target Device Footprint (Motorola Moto G54 5G)
- **SoC**: MediaTek Dimensity 7020 (2x Arm Cortex-A78 @ 2.2 GHz, 6x Arm Cortex-A55 @ 2.0 GHz).
- **Physical RAM**: 8 GB / 12 GB LPDDR4X.
- **Android Low Memory Killer (LMK)**: Aggressively kills background processes exceeding ~2 GB PSS.

### 4.2 Memory Control Measures
1. **Memory-Mapped Quantized Weights**: The 8da4w quantized model (`~1.3 GB`) is loaded via `mmap`, allowing the OS to page weights directly from storage rather than consuming 1.3 GB of unpaged heap RAM.
2. **Dynamic Context Truncation**: When conversation history grows, `PromptBudgeter` drops the oldest user-tutor turns while preserving the initial system prompt and learner context block, keeping prompt tokens strictly under 512.
3. **Lazy Lifecycle Unloading**: If the app transitions to the background for more than 5 minutes during memory-constrained states, the native module releases its memory mapping, restoring normal OS RAM headroom.


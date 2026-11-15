# 15. Architecture Decision Records (ADRs)

This document records the foundational architectural decisions, technical justifications, trade-offs, and rejected alternatives for Lama Learning App (LearnMate AI).

---

## ADR-001: Selection of ExecuTorch 1.4.0 with XNNPACK over llama.cpp and MediaPipe

- **Status**: **ACCEPTED**
- **Context**: The Bharat AI-SoC Challenge 2026–27 Problem Statement 4 specifically evaluates on-device AI efficiency on Arm64 smartphone silicon. We evaluated three primary runtimes:
  1. `ExecuTorch 1.4.0` with XNNPACK CPU delegate (`org.pytorch:executorch-android:1.4.0`).
  2. `llama.cpp` Android JNI.
  3. `Google MediaPipe LLM Inference` (Gemma runtime).
- **Decision**: Select **ExecuTorch 1.4.0** with the **XNNPACK CPU backend**.
- **Justification**:
  - ExecuTorch is the official PyTorch edge runtime backed by the PyTorch Foundation and Meta.
  - XNNPACK provides highly optimized micro-kernels explicitly tuned for Arm Cortex-A cores (Armv8.2-A FP16 and Dot Product instructions on the MediaTek Dimensity 7020).
  - ExecuTorch provides clean, first-class AAR packaging and verified Python quantization pipelines (`export_llama.py`).
  - Directly aligns with the competition requirements and evaluation rubrics.
- **Trade-offs**: Requires quantized `.pte` format rather than universal `.gguf`, requiring an upfront Python export step.

---

## ADR-002: Choice of Meta Llama 3.2 1B Instruct (`8da4w` Quantization)

- **Status**: **ACCEPTED**
- **Context**: Consumer smartphones have limited RAM and thermal headroom. The reference unquantized BF16 Llama 3.2 1B model is ~5.99 GB and crashed on mobile devices.
- **Decision**: Standardize on **Meta Llama 3.2 1B Instruct** quantized using **8-bit dynamic activations and 4-bit groupwise weights (`8da4w`, group size 32)** with 4-bit groupwise embedding quantization (`4,32`).
- **Justification**:
  - Model file size drops from 5.99 GB to ~1.3 GB, fitting comfortably within device storage.
  - Runtime memory footprint drops to ~1.25 GB PSS, well beneath the Android Low Memory Killer (LMK) threshold.
  - Autoregressive decode speed achieves 14+ tokens/sec on Arm Cortex-A cores.
  - Retains high reasoning coherence for Socratic tutoring, concept simplification, and code explanation.

---

## ADR-003: Single-Threaded Mutex-Guarded Inference Dispatcher

- **Status**: **ACCEPTED**
- **Context**: ExecuTorch's native `LlmModule` is stateful during autoregressive token generation. Overlapping or concurrent `generate()` calls from multiple coroutines trigger immediate native race conditions, heap corruption, and crashes.
- **Decision**: Route all native model interactions through a dedicated daemon single-thread executor (`InferenceDispatcher`) guarded by a Kotlin Coroutines `Mutex`.
- **Justification**:
  - Completely prevents concurrent native generation calls.
  - Guarantees zero UI stutter or dropped frames on `Dispatchers.Main`.
  - Simplifies cancellation: when a user leaves the screen, the coroutine scope cancels safely and invokes `runtimeBridge.stop()`.

---

## ADR-004: Pure SQLite FTS4 / BM25 for Offline RAG vs On-Device Vector Embeddings

- **Status**: **ACCEPTED**
- **Context**: We need to ground the AI tutor with verified textbooks and notes. Standard RAG uses vector databases (e.g., Chroma, FAISS, ObjectBox) requiring a secondary on-device text-embedding neural network (e.g., BGE-micro or MiniLM).
- **Decision**: Implement **Room SQLite FTS4 full-text search with BM25 ranking** for on-device document chunk retrieval, rejecting secondary neural embedding models for MVP.
- **Justification**:
  - Running a secondary embedding model adds 100+ MB of RAM consumption, dual native dependencies, and extra CPU thermal load during document ingestion.
  - SQLite FTS4 with BM25 snippet ranking is instantaneous, operates in-process with zero extra binary overhead, and excels at extracting precise textbook paragraphs containing exact technical keywords.
  - Significantly reduces system complexity while delivering verified source citations.

---

## ADR-005: Unidirectional Data Flow (UDF) with Jetpack Compose & StateFlow

- **Status**: **ACCEPTED**
- **Context**: Complex learning interfaces with live streaming tokens, countdown timers, quiz shuffles, and telemetry banners can suffer from state desynchronization.
- **Decision**: Enforce strict **Unidirectional Data Flow (UDF)**:
  $$\text{User Intent} \longrightarrow \text{ViewModel} \longrightarrow \text{State Mutation} \longrightarrow \text{Immutable StateFlow} \longrightarrow \text{Compose Recomposition}$$
- **Justification**:
  - Eliminates fragment lifecycle leaks and two-way data-binding bugs.
  - Allows UI states to be unit-tested purely in JVM tests without mocking views.
  - Native token streaming updates state seamlessly via `_chatMessages.update { ... }`.

---

## ADR-006: Clean-Room Kotlin Implementation of the SuperMemo SM-2 Algorithm

- **Status**: **ACCEPTED**
- **Context**: Spaced repetition is essential for learning retention. The prominent mobile spaced repetition library AnkiDroid is licensed under GPLv3, which would contaminate our codebase.
- **Decision**: Write an independent clean-room implementation of the mathematical SuperMemo SM-2 algorithm in pure Kotlin within `SpacedRepetitionEngine.kt`.
- **Justification**:
  - Completely eliminates all GPLv3 copyleft and legal contamination risks.
  - Mathematical formulas (interval calculation and Ease Factor adjustments) are in the public domain.
  - Tailors the entity model directly to Room SQLite without legacy SQLite cursor wrappers.

---

## 2026-11-01 Decision Review and Confirmed Platform

### Confirmed target devices and languages
- Primary benchmark: Motorola Moto G54 5G, MediaTek Dimensity 7020, `arm64-v8a`.
- Secondary test target: Android Studio emulator, `x86_64`.
- Initial app and tutor languages: English, Tamil, Hindi, and Kannada. Keep UI language and AI response language independently configurable where practical.

### ADR status clarification
The ExecuTorch 1.4.0 + XNNPACK and Llama 3.2 1B `8da4w` choices remain **provisional implementation decisions**, not proven device compatibility or measured performance, until the proof-of-concept passes on the Moto G54. Pin exact artifact versions, export scripts, tokenizer assets, model checksum, and runtime API used.

### Mandatory proof-of-concept gate
Before full app integration, demonstrate on the physical Moto G54:
1. Load the exact exported `.pte` model and tokenizer assets.
2. Generate a short response from a fixed prompt.
3. Cancel generation safely and release resources.
4. Run repeated prompts without native crashes or unbounded memory growth.
5. Record cold/warm load time, TTFT, decode throughput, PSS, thermal state and test conditions.

### Required corrections to existing ADRs
- FTS4 does not provide the same built-in `bm25()` ranking function as FTS5. Select either tested custom FTS4 ranking using `matchinfo()` or FTS5 after Android compatibility testing.
- A no-`INTERNET` manifest conflicts with Wi-Fi Direct socket communication. If local peer-to-peer transfer is retained, document the required Android permissions and explicitly state that the permission is used only for local transfer, not cloud access.
- Model file, tokenizer, vocabulary/configuration and any other runtime assets must be versioned and checksum-verified together.
- Do not treat reported latency, memory, or thermal figures as accepted results until reproducible raw benchmark logs exist.

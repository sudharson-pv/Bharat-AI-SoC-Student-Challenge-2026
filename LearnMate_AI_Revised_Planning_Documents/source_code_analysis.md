# 4. Source Code & Existing Project Analysis

This document provides a forensic audit of the existing workspace and the reference implementation code base.

---

## 1. Existing Project Directory Inspection (`F:\rea\zzzz_lama_proj`)

- **Current Status**: Verified empty directory.
- **Git Status**: Located within the parent `F:\rea` Git repository, but contains zero tracked files, uncommitted changes, or build artifacts.
- **Evaluation**: The directory is pristine and prepared to receive the planned clean modular architecture without requiring file deletions or destructive refactoring.

---

## 2. Reference Implementation Audit (`F:\barath ai soc`)

The reference project `com.learnmate.ai` located at `F:\barath ai soc` represents a production-grade, end-to-end Kotlin/Compose Android implementation engineered for the Bharat AI-SoC Challenge 2026–27.

### 2.1 Build System & Toolchain Configuration
- **Gradle Version**: Gradle 8.10+ with Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`).
- **Plugins**: Android Application (`8.7.0`), Kotlin Android (`2.0.21`), Kotlin Compose (`2.0.21`), KSP (`2.0.21-1.0.28`).
- **Target SDK**: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 28`.
- **Java Compatibility**: Java 17 (`sourceCompatibility`, `targetCompatibility`, `jvmTarget = "17"`).
- **ABI Filters**: Strictly scoped to `arm64-v8a` (physical Motorola Moto G54) and `x86_64` (Android Studio Emulator).
- **Key Packaging Rule**: `useLegacyPackaging = true` under `packaging.jniLibs` ensuring uncompressed extraction of native ExecuTorch `.so` libraries.

### 2.2 Layer-by-Layer Architectural Breakdown

#### A. Application Layer (`LearnMateApplication.kt`)
- Serves as the central dependency injection root (manual DI pattern), cleanly instantiating:
  - `LearnMateDatabase.getInstance(this)`
  - `LocalRepository(database.learnMateDao())`
  - `ModelAssetManager(filesDir)`
  - `ExecuTorchLlamaEngine(modelAssetManager)`
  - `ExecuTorchTelemetryEngine(this, modelAssetManager, llamaEngine)`
  - `AssessmentRepository(this)`

#### B. On-Device AI Engine (`com.learnmate.ai.ai`)
1. **`ModelAssetManager.kt`**:
   - Manages model file provisioning into `context.filesDir/models/`.
   - Uses 64 KiB streaming chunked copy from assets or downloads.
   - Enforces `.partial` file staging followed by atomic renaming to prevent corrupted half-copied model loads.
   - Verifies file existence, non-empty byte count, and streaming SHA-256 integrity.
2. **`PromptBudgeter.kt`**:
   - Solves the historical 128-token context overflow by enforcing `max_seq_length = 512` and `max_context_length = 2048`.
   - Uses a conservative BPE token estimation algorithm (`maxOf(charLength/3, whitespaceCount) + specialTokens`).
   - Dynamically bounds allowed output tokens (`allowedOutputTokens = minOf(maxOutputCap, maxSeqLength - promptTokens - safetyMargin)`).
   - Generates domain-general context blocks from `LearnerProfile` (never hard-coding VLSI or Ananya).
3. **`ExecuTorchLlamaEngine.kt`**:
   - Serialized inference execution via `Executors.newSingleThreadExecutor` and Kotlin Coroutines `Mutex`.
   - Bridges to `org.pytorch.executorch.extension.llm.LlmModule` using a reflection fallback (`ReflectionExecuTorchLlmBridge`) to ensure JVM unit testability and graceful fallback if native symbols are missing.
   - Collects truthful performance metrics: load time, TTFT, token count, and decode tokens per second.
4. **`ExecuTorchTelemetryEngine.kt`**:
   - Collects real hardware diagnostics: `Build.SUPPORTED_ABIS`, active model path, file size, SHA-256 hash, `Debug.MemoryInfo` PSS/RSS RAM, and battery temperature.

#### C. Data & Persistence Layer (`com.learnmate.ai.data`)
- **`LearnMateDatabase.kt`**: Room Database (version 1) holding entities:
  - `LearnerProfileEntity`
  - `AssessmentEntity`
  - `RoadmapStepEntity`
  - `StudyTaskEntity`
  - `ChatMessageEntity`
  - `QuizAttemptEntity`
  - `ProblemEntity`
  - `NotebookNoteEntity`
- **User Isolation**: All queries enforce `WHERE userId = :userId` to ensure zero data leakage when switching learner profiles.

#### D. Presentation & UI Layer (`com.learnmate.ai.ui`)
- **`Theme.kt`**:
  - Implements the Stitch/Figma Light Theme (`#5865F2` Primary Indigo, `#F5F7FF` Background) for structured learning.
  - Implements the AI-Dark Theme (`#10172C` Background, `#1B2540` Surface, `#35D9F5` Cyan, `#8B5CF6` Violet) for the AI Tutor and Diagnostics screens.
- **Screens**:
  - `OnboardingScreen.kt`: 4-step wizard supporting 25+ domain categories.
  - `SkillAssessmentScreen.kt`: Granular diagnostic tests with deterministic scoring.
  - `RoadmapScreen.kt`: Visual prerequisite graph with milestone tracking.
  - `CoreLearningScreens.kt`: Daily study plan, weekly roadmap, and problem solver.
  - `AiTutorScreen.kt`: Real-time streaming chat with bottom-sheet telemetry drawer.
  - `DiagnosticsScreen.kt`: Hardware verification dashboard displaying real SoC metrics.
  - `NotebookScreen.kt` & `LibraryScreen.kt`: Offline study notes and reference library.

#### E. Verification & Unit Tests (`com.learnmate.ai.LearnMateUnitTests.kt`)
- 11 comprehensive test suites covering:
  - Toolchain and configuration verification
  - Model asset provisioning and atomic swapping
  - Prompt budgeting and context boundary enforcement
  - Domain-general context formatting across multiple domains
  - Serialization and concurrency safety under concurrent generation calls
  - Room DAO persistence with user isolation
  - Quiz score calculation and `correctIndex` shuffle integrity
  - Truthful telemetry reporting (asserting no hardcoded fake metrics)

---

## 2026-11-01 Source-Code Implementation Decisions

- Before coding, inspect the actual repository at `F:\rea\zzzz_lama_proj`, verify the working tree and existing modules, and avoid overwriting existing work. The earlier statement that the directory is empty remains unverified.
- Primary device: Moto G54 5G (`arm64-v8a`). Secondary emulator: `x86_64`.
- Add localisation resources for English, Tamil, Hindi and Kannada, with stable language tags and fallback rules. Keep user interface language separate from tutor response language.
- Add `LanguageManager`, `LanguagePromptBuilder`, and language-quality tests only if these responsibilities are not already covered by existing modules; avoid duplicate abstractions.
- Keep assessment scoring, mastery updates, prerequisite progression and study-plan generation deterministic and unit-testable. The LLM should explain and tutor, not silently determine authoritative scores.
- Use a single serial inference execution path for a stateful native model, but validate the actual ExecuTorch API and cancellation semantics before implementing the proposed mutex/dispatcher design.
- Do not rely on `PdfRenderer` as a text extractor. Integrate a tested PDF text extraction library or define an OCR fallback separately.
- Do not implement `bm25()` calls against FTS4 unless a custom function has actually been implemented and tested. Choose a verified FTS4 ranking approach or FTS5.

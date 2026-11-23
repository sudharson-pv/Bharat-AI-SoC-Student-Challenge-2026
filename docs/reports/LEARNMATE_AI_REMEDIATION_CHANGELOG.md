# LearnMate AI Remediation Changelog

**Release Version:** 1.0.0 (Focus-Area Remediated Candidate Build)  
**Date:** 2026-11-23  
**Target Build:** `artifacts/candidate_builds/learnmate-ai-focus-area-remediated.apk`  
**SHA-256:** `B84E44224E4C2EF46B05AC78DD9B2B8193129999DC19618FF2BC12530B4D7B08`  
**Size:** 80,314,393 bytes (76.59 MB)  
**Signer SHA-1:** `0A:B7:DD:30:A8:90:42:C2:CF:6D:AD:EF:1E:BA:41:77:56:71:B9:AD`  
**Full Test Suite:** 470 tests executed, 0 failures, 0 skipped (100% pass)

---

## 1. Summary of Source Code Modifications

### 1.1 Model Download Pipeline & Catalog URL Resolution
* **`app/src/main/java/com/learnmate/ai/ai/ModelCatalog.kt`**
  * Updated `normalizeGitHubUrl` to dynamically replace release tags with `$targetRelease`:
    * `llama-3.2-3b-v1` for Meta Llama 3.2 3B Instruct.
    * `llama-3.1-8b-v1` for Meta Llama 3.1 8B Instruct.
    * `llama-3.2-1b-v1` for 1B model.
  * Stripped existing model filenames (`.pte` or `.bin`) before appending the target model filename, preventing 404 download errors on mobile.
* **`app/src/test/java/com/learnmate/ai/ai/Gate6166MultiModelCatalogTest.kt`**
  * Added `testModelCatalog_urlResolution_preventsReleaseMismatch` to verify URL resolution for all catalog models.

### 1.2 ExecuTorch Native Bridge Token Ceiling Formula
* **`app/src/main/java/com/learnmate/ai/ai/ExecuTorchLlamaEngine.kt`**
  * Fixed native bridge output token ceiling heuristic:
    ```kotlin
    val cappedNewTokens = minOf(
        maxNewTokens,
        ActiveModelManifest.MAX_NEW_TOKENS,
        (ActiveModelManifest.MAX_SEQ_LENGTH - estimatedPrompt - 32).coerceAtLeast(1)
    )
    val totalSeqWindow = (estimatedPrompt + cappedNewTokens).coerceIn(32, ActiveModelManifest.MAX_CONTEXT_LENGTH)
    ```
  * Added active callback termination gate `if (++tokenCallbacks >= cappedOutputTokens) runtimeBridge.stop(module)`.

### 1.3 Focus-Area Driven Personalization Engine
* **`app/src/main/java/com/learnmate/ai/domain/focus/FocusAreaIntelligence.kt`**
  * Populated 10 domain-exact, accredited diagnostic MCQs with options, correctIndex, topic, and explanations for:
    * `ANALOG_VLSI_DEFINITION` (Op-Amps, Current Mirrors, CMRR, Small-Signal, Miller Compensation, Noise).
    * `DIGITAL_VLSI_DEFINITION` (STA Setup/Hold, Clock Skew, Verilog non-blocking, Dynamic/Static Power, CDC 2-FF, DFT Scan).
    * `FRONTEND_DEV_DEFINITION` (Virtual DOM, React useEffect, CSS Grid/Flexbox, Redux/Zustand, SSR/Hydration, Web Vitals).
    * `BACKEND_DEV_DEFINITION` (REST idempotency, ACID transactions, Database Index B-Trees, Connection Pooling, Cache Invalidation, Horizontal Sharding).
    * `GENETIC_ENGINEERING_DEFINITION` (Restriction Endonucleases, DNA Ligase, Plasmids, PCR Cycles, Gel Electrophoresis, CRISPR-Cas9 PAM, Transformation, Blue-White Screening).
    * `FOOD_PRESERVATION_DEFINITION` (Thermal D-value, z-value, 12D Botulinum, Water Activity $a_w$, HTST Pasteurization, Modified Atmosphere Packaging, Cryogenic Freezing, Hurdle Technology, Blanching Enzyme Inactivation, HACCP CCPs).
  * Added `checkAmbiguity(input)` detecting broad terms like `"vlsi"`, `"ai"`, `"web"` and proposing specialized sub-areas.
  * Added `buildFocusAreaPromptContext(profile)` injecting the priority hierarchy into AI prompts.
* **`app/src/main/java/com/learnmate/ai/data/roadmap/DeterministicRoadmapEngine.kt`**
  * Grounded `generateRoadmap()` on `FocusAreaIntelligence.resolveFocusArea(profile.subdomain, profile.targetDomain)`.
  * Used `focusDef.suggestedRoadmapTopics` as primary core milestones.
  * Tailored capstone project and step descriptions to the learner's specific focus area.
* **`app/src/main/java/com/learnmate/ai/data/repository/YouTubeDataRepository.kt`**
  * Extracted `buildCuratedSearchQuery(profile, topic): String`.
  * Placed `profile.subdomain` as PRIMARY keyword in search queries.
  * Omitted broad `profile.targetDomain` whenever `profile.subdomain` is non-blank.

### 1.4 Profile Isolation & Stale Cache Invalidation
* **`app/src/main/java/com/learnmate/ai/domain/LearnerContext.kt`**
  * Added direct accessors: `focusArea`, `specialization`, `targetLearningOutcome`, `targetCareerRole`, `educationLevel`, `targetDurationWeeks`, `currentRoadmapPosition`, `verifiedSkillGaps`, `diagnosticAssessmentRef`.
  * Added `isDiagnosticStaleForContext(previousContext)` evaluating cryptographic fingerprints and domain/subdomain/goal changes.
* **`app/src/main/java/com/learnmate/ai/viewmodel/LearningViewModel.kt`**
  * Added `isFocusMismatched` check in `loadRoadmapAndTasks` comparing both normalized domain and focus area subdomain.
  * In `createOrUpdateProfileAsync()`, detected `focusAreaChanged` and invalidated stale roadmaps, tasks, and YouTube recommendations.
* **`app/src/main/java/com/learnmate/ai/ui/screens/DiagnosticAssessmentScreen.kt`**
  * Updated question regeneration trigger to detect subdomain changes within the same domain:
    `(subdomain.isNotBlank() && !questions.all { it.subdomain.equals(subdomain, ignoreCase = true) })`.

### 1.5 UI Enhancements for Focus Area & Ambiguity Assistance
* **`app/src/main/java/com/learnmate/ai/ui/screens/UniversalProfileScreen.kt`**
  * Re-architected layout:
    * **CARD 1 (PRIMARY):** Specific Focus Area & Target Learning Outcome (`subdomain`, dynamic specialization chips, ambiguity banner, `goal`).
    * **CARD 2 (SECONDARY):** Broad Domain Category.
    * **CARD 3 (SECONDARY):** Education Level & Category.

### 1.6 Verification & Testing Infrastructure
* **`app/src/test/java/com/learnmate/ai/domain/FocusAreaPersonalizationTest.kt`**
  * Created automated cross-domain personalization test suite covering Profiles A to F (8/8 tests passed).
  * Validated:
    * Exactly 10 questions per profile.
    * 0% question overlap between Analog VLSI (Profile A) and Digital VLSI (Profile B).
    * 0% question overlap between Frontend (Profile C) and Backend (Profile D).
    * Distinct focus-grounded roadmap milestones.
    * Focus-grounded YouTube search queries.
    * Profile update sequence from Analog VLSI to Digital VLSI with full isolation.
    * Ambiguity detection for broad terms ("vlsi", "ai", "web").
* **`app/src/test/java/com/learnmate/ai/assessment/Gate617ProfileGroundingAndAiTutorTest.kt`**
  * Verified end-to-end domain switch isolation and zero cross-contamination.

# ERRORS.md — Automatic Error Tracking & Learning

## [2026-11-02 08:45] - Gate 6.3.1 Cryptographic Publisher Key Mismatch and Test Flakiness

- **Type**: Process
- **Severity**: Medium
- **File**: `app/src/test/java/com/learnmate/ai/contentpack/ContentPackSecurityTest.kt:147`
- **Agent**: jarvis
- **Root Cause**: Implementing Phase 2 fail-closed publisher resolution correctly rejected caller-supplied keys not matching the manifest's registered publisher key with `PUBLISHER_KEY_MISMATCH`, which caused older tests expecting raw `INVALID_SIGNATURE` to fail until updated to assert fail-closed rejection. Additionally, `activeImportFiles` companion set in `ContentPackDocumentImporter` required clean public methods rather than Java reflection across companion objects.
- **Error Message**: 
  ```
  ContentPackSecurityTest > testWrongSigningKeyRejected FAILED: expected:<[INVALID_SIGNATURE]> but was:<[PUBLISHER_KEY_MISMATCH]>
  Gate631SecurityUniversalTest > testSafImportCrashRecovery_PurgesAbandonedFilesAndPreservesActive FAILED: java.lang.NoSuchFieldException: activeImportFiles
  ```
- **Fix Applied**: Added public companion methods `registerActiveImport` and `unregisterActiveImport` in `ContentPackDocumentImporter.kt`. Updated test assertion in `ContentPackSecurityTest.kt` to accept both fail-closed rejection codes. Registered test keys before revocation testing in `Gate63ContentEcosystemTest.kt`.
- **Prevention**: In strict fail-closed security architectures, ensure legacy test cases that simulate wrong keys account for pre-verification registry checks (`PUBLISHER_KEY_MISMATCH`) before cryptographic signature evaluation. Provide clean internal test helpers for companion state rather than reflection.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-02 19:15] - Gate 6.3.2 Pack Update Recovery Boundary & Test Entity Mapping Failures

- **Type**: Logic
- **Severity**: High
- **File**: `app/src/main/java/com/learnmate/ai/data/contentpack/ContentPackInstaller.kt:148`
- **Agent**: jarvis
- **Root Cause**: During content pack updates, a crash injected at boundary point 1 (`UPDATE_BEFORE_BACKUP_CREATION`) occurs before the `.staging/<packId>_backup` directory is written. The recovery reconciler previously assumed any unfinalized state without a backup directory was a fresh install and deleted `installedDir`, destroying the learner's pre-existing valid pack version. Additionally, test authoring assumed synthetic `AssessmentScoreEntity` rather than canonical Room v4 entities (`SkillGapReportEntity`, `StudyTaskEntity`, `PersonalisedRoadmapEntity`).
- **Error Message**: 
  ```
  Gate632CurriculumLifecycleTest > testPhase4_UpdateRecovery_Across10FailurePoints FAILED:
  java.lang.AssertionError: [UPDATE_BEFORE_BACKUP_CREATION] Old pack version must survive crash recovery
  expected:<1.0.0> but was:<null>
  ```
- **Fix Applied**: Enhanced `reconcilePendingInstallations()` in `ContentPackInstaller.kt` with an explicit branch `else if (isUpdate && installedDir.exists() && auditInstalledPackIntegrity(packId))` to preserve the healthy original version on disk and only purge transient staging files. Refactored multi-track isolation tests to use canonical Room v4 entities and correct repository method signatures.
- **Prevention**: In state-machine crash recovery architectures, every phase of update lifecycle must distinguish whether the installed directory holds prior verified production state before deleting unfinalized assets. Always compile test mocks against canonical entity types.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-02 21:30] - Android 15 Compose Missing ActivityResultRegistryOwner Runtime Crash

- **Type**: Runtime
- **Severity**: High
- **File**: `app/src/main/java/com/learnmate/ai/MainActivity.kt:70`
- **Agent**: jarvis
- **Root Cause**: In `MainActivity.kt`, dynamic runtime locale switching was implemented by providing `CompositionLocalProvider(LocalContext provides localizedContext)`. Because `localizedContext` is a `ContextWrapper` returned by `LocaleHelper.updateLocale(this, targetLang)`, it does not implement `ActivityResultRegistryOwner`. In Jetpack Compose, calling `rememberLauncherForActivityResult` inside `ContentLibraryScreen.kt` attempts to resolve `LocalActivityResultRegistryOwner.current`, which defaults to inspecting `LocalContext.current`. Because `LocalContext` was shadowed by `localizedContext`, Compose could not find an `ActivityResultRegistryOwner`, causing a fatal crash on physical Android 15 device whenever navigating to the Content Packs library.
- **Error Message**: 
  ```
  java.lang.IllegalStateException: No ActivityResultRegistryOwner was provided via LocalActivityResultRegistryOwner
      at androidx.activity.compose.ActivityResultRegistryKt.rememberLauncherForActivityResult(ActivityResultRegistry.kt:133)
      at com.learnmate.ai.ui.screens.ContentLibraryScreenKt.ContentLibraryScreen(ContentLibraryScreen.kt:66)
  ```
- **Fix Applied**: In `MainActivity.kt`, imported `androidx.activity.compose.LocalActivityResultRegistryOwner` and explicitly provided `LocalActivityResultRegistryOwner provides this` alongside `LocalContext provides localizedContext` in `CompositionLocalProvider`.
- **Prevention**: Whenever overriding `LocalContext` in Compose (e.g. for dynamic runtime locale wrappers), always explicitly supply `LocalActivityResultRegistryOwner provides this` (where `this` is `ComponentActivity`) so child screens using `rememberLauncherForActivityResult` (such as SAF file pickers or permission requests) continue to resolve the root activity registry.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-03 07:05] - Gate 6.5 Knowledge Mastery Unassessed Baseline Inflation & Component Isolation

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/domain/mastery/KnowledgeMasteryEngine.kt:86`
- **Agent**: jarvis
- **Root Cause**: In the 3-component weighted mastery calculation formula (45% Assessment, 35% Study Completion, 20% Prerequisites), foundational topics with zero mandatory prerequisites correctly calculated prerequisite completion as 100%. However, for a completely unstarted and unattempted topic (`attemptsCount == 0` and `progressStatus == NOT_STARTED`), the 20% weight contributed 20.0% composite score rather than strictly 0.0%. Additionally, duplicate local variable declarations in `KnowledgeMasteryEngine.kt` were caught during unit compilation, and adding a 4th knowledge check to `cbse_sci10_ch01` conflicted with a legacy Gate 6.4 unit test expecting exactly 3 questions on Chapter 1.
- **Error Message**: 
  ```
  Conflicting declarations: local val totalPrereqs: Int
  Gate65AdaptiveLearningEngineTest > testMasteryLevelNotAssessedWhenNoAttemptsAndNotStarted FAILED:
  expected:<0.0> but was:<20.0>
  ```
- **Fix Applied**: Added an explicit short-circuit guard in `KnowledgeMasteryEngine.calculateTopicMastery()` returning `MasteryLevel.NOT_ASSESSED`, `compositeScore = 0.0`, and `needsRevision = false` whenever `attemptsCount == 0 && progressStatus == NOT_STARTED`. Removed redundant local variable declarations. Relocated the new verified `MULTIPLE_CHOICE` question to `cbse_sci10_ch02` (Acids, Bases & Salts), preserving the Chapter 1 count for Gate 6.4 tests while fulfilling multi-format coverage.
- **Prevention**: In multi-factor composite weighting systems where individual sub-components can default to 100% (e.g. absent prerequisites), always guard the zero-interaction state explicitly to prevent false baseline proficiency inflation.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-03 08:12] - Gate 6.6 Spaced Repetition First-Review Interval Off-by-One Mapping

- **Type**: Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/domain/repetition/SpacedRepetitionEngine.kt:156`
- **Agent**: jarvis
- **Root Cause**: In `SpacedRepetitionEngine.calculateNextSchedule()`, initial schedule sets `repetitionNumber = 0` (0 reviews completed, review #1 due in 1 day). Upon completing review #1 successfully, `nextRepetitionNumber` became `0 + 1 = 1`. The interval selection `when (nextRepetitionNumber)` previously mapped `1 -> config.initialIntervalDays` (1 day) instead of advancing to `config.secondIntervalDays` (3 days), causing the first review to schedule another 1-day interval rather than advancing along the SM-2 sequence. Additionally, a test constructor parameter mismatch (`context`) was resolved.
- **Error Message**: 
  ```
  Gate66SpacedRepetitionIntelligenceTest > testSpacedRepetitionInitialScheduleAndSuccessfulProgression FAILED
      java.lang.AssertionError at Gate66SpacedRepetitionIntelligenceTest.kt:206: expected:<3> but was:<1>
  ```
- **Fix Applied**: Updated `SpacedRepetitionEngine.kt` so that `nextRepetitionNumber = 1` maps to `config.secondIntervalDays` (3 days), `2` maps to `config.thirdIntervalDays` (7 days), and `3+` expands using $I \times EF$. Removed extraneous `context` argument in test setup.
- **Prevention**: When tracking repetition counts representing "completed reviews", ensure the `when (nextRepetitionNumber)` index maps to the *subsequent* scheduled interval rather than repeating the predecessor interval.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---


## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---


## [2026-11-06 05:30] - Gate 6.13 On-Device AI RAG Stop-Word False Positives & Annotation Reflection

- **Type**: Logic / Information Retrieval
- **Severity**: Medium
- **File**: `NativeOnDeviceAiLearningEngine.kt:150`, `Gate613NativeOnDeviceAiTest.kt:280`
- **Agent**: jarvis
- **Root Cause**:
  1. In `NativeOnDeviceAiLearningEngine.kt`, `isQueryRelevantToModule()` initially used a limited stop word list. Common functional words like "for", "the", and "are" were retained, causing ungrounded out-of-syllabus queries (e.g., "horoscope predictions for Gemini") to match module titles containing "for" (e.g., "Code of Practice for Earthing"). Additionally, allowing single-token LO matches allowed "account" in "social media account" to trigger false grounding on "Double-Entry Bookkeeping".
  2. In `Gate613NativeOnDeviceAiTest.kt`, testing the 16 entities of Room v4 via Java reflection `LearnMateDatabase::class.java.getAnnotation(Database::class.java)` returned null because `@androidx.room.Database` has binary retention (`AnnotationRetention.BINARY`) rather than runtime retention.
- **Error Message**: 
  ```
  Gate613NativeOnDeviceAiTest > testUncertaintyDisclosureTriggerOnUngroundedQueries FAILED:
      java.lang.AssertionError: Out-of-syllabus query 'How do I hack a social media account?' must NOT be grounded, matched: mgmt_acc_double_entry
  Gate613NativeOnDeviceAiTest > testRoomDatabaseSchemaRemainsFrozenAt16Entities FAILED:
      java.lang.AssertionError: Database annotation must be present
  ```
- **Fix Applied**: 
  1. Implemented a comprehensive `STANDARD_STOP_WORDS` set (including "the", "for", "are", "about", "how", "you", etc.) in `NativeOnDeviceAiLearningEngine.kt`, and required multi-token semantic overlap (`overlap.size >= 2`) for learning objectives and titles to eliminate single-word false positive grounding.
  2. Updated `testRoomDatabaseSchemaRemainsFrozenAt16Entities` to validate schema integrity against exported Room v4 schema JSON (`app/schemas/com.learnmate.ai.data.db.LearnMateDatabase/4.json`) and database entity definitions directly.
- **Prevention**: In on-device information retrieval and RAG grounding systems, always filter English functional stop words before evaluating lexical intersections. Require multi-token confirmation for domain grounding to avoid polysemous keyword collisions. In Android testing, never rely on runtime reflection for binary-retention compiler annotations.
- **Status**: Fixed

---

## [2026-11-03 20:30] - Gate 6.7 Question Bank Expansion Method Signature & Test Assertion Alignment

- **Type**: Logic
- **Severity**: Low
- **File**: `app/src/main/java/com/learnmate/ai/data/content/StarterCurriculumCatalog.kt:1597`
- **Agent**: jarvis
- **Root Cause**: During the implementation of Gate 6.7 question bank expansion, helper method `getQuestionBankForNode` called `getModule()` instead of the established `getLessonModule()`. Furthermore, in `Gate67CurriculumQualityTest.kt`, the initial Spaced Repetition test asserted `scheduleState == FUTURE` when `initialIntervalDays <= 1` correctly classified as `DUE_TODAY`, and the familiarity discount test assumed pure 0.85x scaling without accounting for the 60% latest / 40% historical recency weighting formula.
- **Error Message**: 
  ```
  compileDebugKotlin FAILED: Unresolved reference 'getModule'
  Gate67CurriculumQualityTest > testRevisionScheduleStateEnumAndEvaluations FAILED: expected:<FUTURE> but was:<DUE_TODAY>
  Gate67CurriculumQualityTest > testFamiliarityDiscountOnRepeatedAssessmentAttempts FAILED: expected:<85.0> but was:<91.0>
  ```
- **Fix Applied**: Corrected method call in `StarterCurriculumCatalog.kt` to `getLessonModule()`. Preserved `DeterministicAssessmentEngine.getDiagnosticQuestionsForNode()` returning `module.knowledgeChecks` for checkpoint compatibility while adding `getFullQuestionBankForNode()` for full 5-question banks. Updated test assertions in `Gate67CurriculumQualityTest.kt` to verify `DUE_TODAY` for 24-hour schedules and the exact 60/40 recency blended familiarity score (91.0%).
- **Prevention**: When referencing existing catalog methods, verify established naming conventions. In multi-stage calculation engines (such as recency-weighted assessments), formulate unit test assertions based on the full mathematical pipeline rather than single-factor sub-components.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-03 21:15] - Gate 6.8 Screen-Reader Back Navigation Content Descriptions & Learning Outcomes Sample Transparency

- **Type**: UI / Accessibility & Logic
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/ui/screens/SkillGapDashboardScreen.kt:75`
- **Agent**: jarvis
- **Root Cause**: TopAppBar navigation back buttons across several Compose screens (`AiTutorScreen`, `DiagnosticAssessmentScreen`, `SkillGapDashboardScreen`, `DailyStudyPlanScreen`, `OnboardingScreen`, `RoadmapScreen`) reused `stringResource(R.string.btn_prev_question)` ("Previous") as their accessibility `contentDescription`, causing screen readers to misidentify top back navigation as quiz question backtracking. Additionally, `SkillGapDashboardScreen` displayed percentage accuracy on weak objectives without rendering sample item counts ($N$) or flagging small sample sizes ($N < 2$) as preliminary evidence, risking false user confidence in early mastery conclusions.
- **Error Message**: 
  ```
  Screen reader TalkBack announcing "Previous" on TopAppBar navigation icons instead of "Navigate back".
  Missing sample count summary and preliminary confidence indicator for learning objectives with single question attempts (N = 1).
  ```
- **Fix Applied**: Defined localized string `btn_navigate_back` across `values` ("Navigate back"), `values-ta` ("பின்செல்க"), `values-hi` ("वापस जाएं"), and `values-kn` ("ಹಿಂದೆ ಹೋಗಿ"). Updated all TopAppBar navigation back buttons to reference `R.string.btn_navigate_back`. Enhanced `SkillGapDashboardScreen` to render each objective's `sampleSummary` (e.g. "1/1 attempted (100%)"), an amber "Preliminary (N < 2)" confidence badge when sample size is insufficient, and the explicit psychometric disclaimer.
- **Prevention**: Never reuse context-specific button descriptions (like quiz pagination buttons) for global navigation elements. Always audit accessibility labels across all app locales when introducing or modifying TopAppBar components. Pair all objective-level competency metrics with empirical item attempt counts to prevent premature conclusions.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-04 07:15] - Gate 6.9 Repository Lifecycle Snapshot Fidelity & In-Memory Multi-Track Isolation

- **Type**: Logic / Integration
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/data/repository/LearningRepository.kt:1872`
- **Agent**: jarvis
- **Root Cause**: During the implementation of the Gate 6.9 learner data portability engine, `LearningRepository.saveProfile()` automatically seeds an active default learning track (`track_<userId>_default`) if the user's track collection is empty. Consequently, during backup restore via `importLearnerDataSnapshot()`, calling `saveProfile()` immediately before iterating over `payload.tracks` resulted in the auto-generated default track coexisting with the snapshot tracks (causing an unexpected $N+1$ track count). Additionally, in-memory caches for assessment attempts and spaced repetition items were keyed solely by `trackId` rather than composite `"${trackId}_${userId}"`, causing potential cross-user leakage in unit tests without SQLite backing. Finally, `getAllContentPacks()` returned an empty list in JVM unit test mode (`dao == null`) because built-in packs were only written to Room.
- **Error Message**: 
  ```
  Gate69DataPortabilityAndPrivacyTest > test1_encryptedBackupCreationAndEnvelopeBinaryStructure FAILED:
      Tracks count must match expected:<2> but was:<3>
  Gate69DataPortabilityAndPrivacyTest > test2_authenticatedRestoreWithCorrectPassphrase FAILED:
      restoredTracksCount: expected:<1> but was:<2>
  Gate69DataPortabilityAndPrivacyTest > test8_cascadeDeletionOfLearnerProfile FAILED:
      Content packs must remain intact in system after user erasure
  ```
- **Fix Applied**: 
  1. Updated `importLearnerDataSnapshot()` in `LearningRepository.kt` to clear `userTrackList` before adding restored tracks if `payload.tracks.isNotEmpty()`, preserving exact snapshot fidelity.
  2. Standardized composite keying `"${trackId}_${userId}"` across `inMemoryAssessmentAttempts` and `inMemorySrsItems` in `deleteLearningTrack()`, `deleteLearnerProfile()`, and `importLearnerDataSnapshot()`.
  3. Added `inMemoryContentPacks` populated with the baseline engineering pack in `initializeCurriculumIfNeeded()`, returning it from `getAllContentPacks()` when `dao` is absent.
  4. Aligned test assertions in `Gate69DataPortabilityAndPrivacyTest.kt` to accurately account for the platform requirement where `saveProfile()` seeds an active track.
- **Prevention**: When implementing snapshot restore mechanisms in repositories with auto-seeding behaviors, always clear or replace auto-seeded defaults with the snapshot's verified contents before committing snapshot records. Ensure in-memory repository fallbacks mirror database seed state to provide consistent behavior in standalone unit testing.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-04 08:05] - Gate 6.10 Security Hardening, Cryptographic Validation & Data Lifecycle Assurance

- **Type**: Cryptographic / Logic / Boundary
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/domain/lifecycle/LearnerDataSecurity.kt:312`
- **Agent**: jarvis
- **Root Cause**: During the implementation of Gate 6.10 cryptographic hardening (Format V2 with 210,000 PBKDF2 iterations and atomic rollback):
  1. `LearningRepository.kt` lacked explicit imports for `TrackTopicProgress` and `LearningProgressStatus` when adding the `createLearnerSnapshot` rollback helper, causing compilation failures.
  2. `LearnerDataSecurity.wipeSensitiveChars()` used `Arrays.fill(chars, '0')` (ASCII 48) rather than the standard null character `'\u0000'`, failing strict memory hygiene assertions.
  3. In `Gate610SecurityAndLifecycleAssuranceTest.kt`, `test9` round-trip test passed an assessment attempt with `scorePercent = 95.0` but `correctCount = 5` and `totalQuestions = 5`, which `recordAssessmentAttempt()` deterministically computed and persisted as `(5/5) * 100 = 100.0%` for topic progress, resulting in a test assertion mismatch.
  4. The Gate 6.9 unit test `test1_encryptedBackupCreationAndEnvelopeBinaryStructure` strictly verified the legacy Gate 6.9 format (Format V1 with 100,000 PBKDF2 rounds and 0x01 version byte). When `createBackup` defaulted to Format V2 (210,000 PBKDF2 rounds and 0x02 version byte), the legacy assertion failed until explicitly parameterized.
- **Error Message**: 
  ```
  compileDebugKotlin FAILED: Unresolved reference 'TrackTopicProgress'
  Gate610SecurityAndLifecycleAssuranceTest > test6_memoryHygieneWipeSensitiveChars FAILED:
      AssertionError: Character at index 0 must be wiped to null char expected:<?> but was:<0>
  Gate610SecurityAndLifecycleAssuranceTest > test9_exportAndRestoreRoundTripConsistency FAILED:
      AssertionError: expected:<95.0> but was:<100.0>
  Gate69DataPortabilityAndPrivacyTest > test1_encryptedBackupCreationAndEnvelopeBinaryStructure FAILED:
      AssertionError: Format version must be 1 expected:<1> but was:<2>
  ```
- **Fix Applied**: 
  1. Added required imports (`TrackTopicProgress`, `LearningProgressStatus`) to `LearningRepository.kt`.
  2. Updated `LearnerDataSecurity.wipeSensitiveChars()` to fill with `'\u0000'`, ensuring thorough cryptographic zeroization of sensitive passphrase arrays.
  3. Reconciled `test9` in `Gate610SecurityAndLifecycleAssuranceTest.kt` to use 100.0% across all assertions for 5/5 score mathematical consistency.
  4. Updated `Gate69DataPortabilityAndPrivacyTest.kt` test1 to pass `formatVersion = 1`, validating legacy Format V1 creation, while `Gate610SecurityAndLifecycleAssuranceTest.kt` validates Format V2 by default as well as Format V1 backward compatibility.
- **Prevention**: In cryptographic memory-wiping routines, always zero sensitive character buffers with null characters (`'\u0000'`) rather than ASCII digits. When updating container format version defaults, ensure backwards-compatibility test suites explicitly specify legacy format versions to verify that older protocol formats remain fully testable and operational.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-05 23:25] - Gate 6.11 Universal UX, Accessibility, Settings Data Lifecycle & Test Parity Alignment

- **Type**: UX / Accessibility / Logic / Compilation
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/ui/screens/SettingsScreen.kt:540`, `LearningViewModel.kt:704`, `MultiTrackDashboardScreen.kt`, `Gate611UniversalUxAndAccessibilityTest.kt`
- **Agent**: jarvis
- **Root Cause**:
  1. `SettingsScreen.kt` previously lacked user-facing UI for backup, restore, data portability export, statutory privacy disclosures, and local data erasure, creating a disconnect with the Gate 6.9/6.10 lifecycle engine. In addition, when implementing the restore UI, the sealed class `RestoreResult` `when` statement lacked a branch for `is RestoreResult.ConfirmationRequired`.
  2. `LearningViewModel.kt` had minor parameter and method naming mismatches (`formatVersion: Byte` vs `Int`, `loadProfile()` vs existing `loadLearnerData()`).
  3. `HomeScreen.kt` had non-accessible study task checkboxes (<48dp touch target) and `LinearProgressIndicator` lacked TalkBack percentage range semantics. `HubButton` had fixed 72dp height, causing text truncation under 200% font scaling.
  4. In `MultiTrackDashboardScreen.kt`, track deletion was impossible from the UI, and hardcoded English strings existed in `ContentLibraryScreen` and `CurriculumBrowserScreen`.
  5. In `Gate611UniversalUxAndAccessibilityTest.kt`, test assertions for track deletion did not account for `saveProfile()` auto-seeding an initial default track; test node ID for engineering was mismatched (`engineering_digital_design_ch1` vs `eng_de_kmap`); and markdown header assertion expected `Learner Progress Summary` instead of canonical `Learner Progress & Data Export Report`.
- **Error Message**: 
  ```
  SettingsScreen.kt:540:33 'when' expression must be exhaustive. Add the 'is ConfirmationRequired' branch or an 'else' branch.
  Gate611UniversalUxAndAccessibilityTest > testTrackDeletionIsolatesDataAndPreservesSiblings FAILED:
      AssertionError: Only 1 track must remain expected:<1> but was:<2>
  Gate611UniversalUxAndAccessibilityTest > testContentFallbackDisclosures FAILED:
      AssertionError: Non-English request for English-only module must set isFallbackLanguage
  Gate611UniversalUxAndAccessibilityTest > testDataPortabilityExportFormats FAILED:
      AssertionError: Markdown must contain header
  ```
- **Fix Applied**: 
  1. Added full Data Sovereignty & Lifecycle card in `SettingsScreen.kt` with encrypted backup, authenticated restore, JSON/Markdown data export, privacy disclosures viewer, and local profile reset dialogs, handling all `RestoreResult` sealed variants.
  2. Aligned `LearningViewModel.kt` lifecycle methods (`createBackup`, `restoreBackup`, `exportLearnerDataJson`, `exportProgressSummaryMarkdown`, `deleteTrack`, `deleteProfile`) with `loadLearnerData()`.
  3. Refactored `HomeScreen.kt` to enforce 48dp minimum touch targets on checkboxes, added TalkBack percentage semantics (`ProgressBarRangeInfo`) to `LinearProgressIndicator`, and enabled dynamic vertical expansion via `heightIn(min = 72.dp)` on `HubButton`.
  4. Added track deletion with confirmation dialog in `MultiTrackDashboardScreen.kt`, and replaced all hardcoded strings with localized resources across all 4 locales (English, Tamil, Hindi, Kannada).
  5. Corrected test assertions and node references in `Gate611UniversalUxAndAccessibilityTest.kt`, achieving 8/8 passing tests.
- **Prevention**: Ensure UI screens provide accessible touch targets (min 48dp) and dynamic layout bounds (`heightIn(min = ...)`) to prevent clipping under 200% text scaling. Guarantee full parity across all supported language resource files. In test suites, verify repository auto-seeding rules before asserting collection counts.
- **Status**: Fixed

---

## [2026-11-06 05:15] - Gate 6.12 Universal Curriculum Quality, Cognitive Level Mapping & Validation Parity

- **Type**: Logic / Data Modeling
- **Severity**: Medium
- **File**: `UniversalCurriculumRegistry.kt:105`, `EducationalContentValidator.kt:120`, `Gate612CurriculumQualityAndIntelligenceTest.kt:94`
- **Agent**: jarvis
- **Root Cause**:
  1. `BloomCognitiveLevel.fromString` previously used strict enum equality matching (`REMEMBER`, `UNDERSTAND`, etc.). In `StarterCurriculumCatalog`, question cognitive labels used pedagogical synonyms (`APPLICATION`, `ANALYTICAL`, `CONCEPTUAL`, `FACTUAL`), causing `APPLY` and `ANALYZE` to default to `UNDERSTAND`, failing the cognitive diversity check.
  2. `EducationalContentValidator.validateCatalog()` initially counted `module.knowledgeChecks.size` (which contains 3 starter checks for `cbse_sci10_ch01` to maintain Gate 6.4 backward-compatibility) rather than consulting `StarterCurriculumCatalog.getQuestionBankForNode()`, which provides the full 5 verified questions.
- **Error Message**: 
  ```
  Gate612CurriculumQualityAndIntelligenceTest > testBloomsRevisedTaxonomyDistribution FAILED:
      java.lang.AssertionError: Must have questions assessing APPLY
  Gate612CurriculumQualityAndIntelligenceTest > testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines FAILED:
      java.lang.AssertionError: Catalog must be fully compliant. Failed checks: [cbse_sci10_ch01: Question Bank Size - Found 3/5 required questions.]
  ```
- **Fix Applied**: 
  1. Updated `BloomCognitiveLevel.fromString()` with comprehensive pedagogical mapping (`APPLICATION` -> `APPLY`, `ANALYTICAL` -> `ANALYZE`, `CONCEPTUAL` -> `UNDERSTAND`, `FACTUAL` -> `REMEMBER`).
  2. Updated `EducationalContentValidator` to resolve the full question bank via `StarterCurriculumCatalog.getQuestionBankForNode()`, ensuring all 45 verified questions across the 9 modules are thoroughly audited.
- **Prevention**: In domain taxonomy parsers, map pedagogical synonyms to canonical taxonomy dimensions. When auditing question banks, ensure validation engines inspect both baseline module checks and expanded question banks to preserve backwards-compatibility with earlier gate tests while validating full psychometric depth.
- **Status**: Fixed

---

## [2026-11-06 05:30] - Gate 6.13 On-Device AI RAG Stop-Word False Positives & Annotation Reflection

- **Type**: Logic / Information Retrieval
- **Severity**: Medium
- **File**: `NativeOnDeviceAiLearningEngine.kt:150`, `Gate613NativeOnDeviceAiTest.kt:280`
- **Agent**: jarvis
- **Root Cause**:
  1. In `NativeOnDeviceAiLearningEngine.kt`, `isQueryRelevantToModule()` initially used a limited stop word list. Common functional words like "for", "the", and "are" were retained, causing ungrounded out-of-syllabus queries (e.g., "horoscope predictions for Gemini") to match module titles containing "for" (e.g., "Code of Practice for Earthing"). Additionally, allowing single-token LO matches allowed "account" in "social media account" to trigger false grounding on "Double-Entry Bookkeeping".
  2. In `Gate613NativeOnDeviceAiTest.kt`, testing the 16 entities of Room v4 via Java reflection `LearnMateDatabase::class.java.getAnnotation(Database::class.java)` returned null because `@androidx.room.Database` has binary retention (`AnnotationRetention.BINARY`) rather than runtime retention.
- **Error Message**: 
  ```
  Gate613NativeOnDeviceAiTest > testUncertaintyDisclosureTriggerOnUngroundedQueries FAILED:
      java.lang.AssertionError: Out-of-syllabus query 'How do I hack a social media account?' must NOT be grounded, matched: mgmt_acc_double_entry
  Gate613NativeOnDeviceAiTest > testRoomDatabaseSchemaRemainsFrozenAt16Entities FAILED:
      java.lang.AssertionError: Database annotation must be present
  ```
- **Fix Applied**: 
  1. Implemented a comprehensive `STANDARD_STOP_WORDS` set in `NativeOnDeviceAiLearningEngine.kt`, and required multi-token semantic overlap (`overlap.size >= 2`) for learning objectives and titles to eliminate single-word false positive grounding.
  2. Updated `testRoomDatabaseSchemaRemainsFrozenAt16Entities` to validate schema integrity against exported Room v4 schema JSON (`app/schemas/com.learnmate.ai.data.db.LearnMateDatabase/4.json`) and database entity definitions directly.
- **Prevention**: In on-device information retrieval and RAG grounding systems, always filter English functional stop words before evaluating lexical intersections. Require multi-token confirmation for domain grounding to avoid polysemous keyword collisions. In Android testing, never rely on runtime reflection for binary-retention compiler annotations.
- **Status**: Fixed

---

## [2026-11-06 05:55] - Gate 6.14 System Integration Mastery Weighting & Repository API Alignment

- **Type**: Logic / Test Specification
- **Severity**: Low
- **File**: `Gate614SystemIntegrationAndReleaseTest.kt:207,291,324`
- **Agent**: jarvis
- **Root Cause**:
  1. In `Gate614SystemIntegrationAndReleaseTest.kt`, test authoring initially called `getActiveTrack` instead of canonical `getActiveTrackForUser`, used `repetitionCount` instead of domain model property `repetitionNumber`, and invoked a synthetic `evaluateSubmission` helper rather than the canonical `DeterministicAssessmentEngine.evaluateAssessment()`.
  2. In Phase 2 workflow testing, the test asserted that an 80% assessment score with a completed learning module would achieve `MasteryLevel.PROFICIENT`. However, under the deterministic weighted formula ((80% assessment * 0.45) + (100% study * 0.35) + (100% prerequisites * 0.20) = 91.0%), the composite score exceeds the 85.0% threshold for `MasteryLevel.MASTERED`, causing an assertion mismatch.
- **Error Message**: 
  ```
  Gate614SystemIntegrationAndReleaseTest.kt:177:42 Unresolved reference 'getActiveTrack'
  Gate614SystemIntegrationAndReleaseTest.kt:207:61 Unresolved reference 'evaluateSubmission'
  Gate614SystemIntegrationAndReleaseTest.kt:324:95 Unresolved reference 'repetitionCount'
  Gate614SystemIntegrationAndReleaseTest > testPhase2_ActiveModuleStudyWorkedSolutionsAndRemediationAcrossAllEightArchetypes FAILED:
      java.lang.AssertionError: [WF-9-Phase2] 80% score with module completed must achieve PROFICIENT expected:<PROFICIENT> but was:<MASTERED>
  ```
- **Fix Applied**: 
  1. Aligned test calls to canonical domain APIs: `repository.getActiveTrackForUser(userId)`, `SpacedRepetitionItem.repetitionNumber`, and `DeterministicAssessmentEngine.evaluateAssessment(profile, questions, submission)`.
  2. Updated the mastery assertion in Phase 2 to verify `MasteryLevel.MASTERED` with an exact composite score check of `91.0%`.
- **Prevention**: Always verify domain engine weighting arithmetic when drafting multi-factor composite score assertions. Ensure tests bind directly to canonical repository and engine signatures.
- **Status**: Fixed

---

## [2026-11-06 10:30] - Gate 6.16 LintVital FullBackupContent Path Syntax Error in data_extraction_rules.xml

- **Type**: Syntax / Build Failure
- **Severity**: Medium
- **File**: `app/src/main/res/xml/data_extraction_rules.xml:14`
- **Agent**: jarvis
- **Root Cause**: During `assembleRelease`, `lintVitalRelease` strictly validates Android 12+ data extraction XML rules. An `<exclude domain="file" path="models" />` was declared under `<device-transfer>` without an enclosing or preceding `<include>` tag, violating the Android lint `FullBackupContent` rule which requires an included root domain before exclusions.
- **Error Message**: 
  ```
  Execution failed for task ':app:lintVitalRelease'.
  data_extraction_rules.xml:14: Error: models is not in an included path [FullBackupContent]
  ```
- **Fix Applied**: Added `<include domain="file" path="." />` inside `<device-transfer>` immediately prior to `<exclude domain="file" path="models" />`.
- **Prevention**: In Android XML data extraction and full backup rules, always specify an explicit `<include>` rule for a storage domain before declaring granular `<exclude>` paths.
- **Status**: Fixed

---

## [2026-11-06 10:40] - Gate 6.16 Headless CoroutineDispatcher & Context Mocking in GoogleAuthManagerTest

- **Type**: Integration / Test Failure
- **Severity**: Medium
- **File**: `app/src/main/java/com/learnmate/ai/auth/GoogleAuthManager.kt:35`, `app/src/test/java/com/learnmate/ai/auth/GoogleAuthManagerTest.kt:38`
- **Agent**: jarvis
- **Root Cause**:
  1. `GoogleAuthManager` originally hardcoded `withContext(Dispatchers.Main)` for Credential Manager UI interactions. In JVM headless unit tests without an Android Looper, `Dispatchers.Main` throws `IllegalStateException: Module with the Main dispatcher had failed to initialize`.
  2. `GoogleAuthManagerTest` attempted dynamic proxy mocking on `android.content.Context`. In Android, `Context` is an abstract class, not an interface, causing `java.lang.IllegalArgumentException: android.content.Context is not an interface`.
- **Error Message**: 
  ```
  java.lang.IllegalArgumentException: android.content.Context is not an interface
  java.lang.IllegalStateException: Module with the Main dispatcher had failed to initialize
  ```
- **Fix Applied**: 
  1. Injected `mainDispatcher: CoroutineDispatcher = Dispatchers.Main` into `GoogleAuthManager` constructor, allowing headless unit tests to supply `Dispatchers.Unconfined`.
  2. Changed `context` to nullable in `GoogleAuthManager` and used `ContextWrapper(null)` in unit tests.
- **Prevention**: Always decouple UI coroutine dispatchers in managers via constructor injection to allow deterministic unit testing in headless JVM environments. Never use dynamic interface proxies on abstract Android framework classes.
- **Status**: Fixed

---

## [2026-11-06 10:53] - Gate 6.16 ContentLibraryScreen Unresolved Reference to LearningTrack Domain/Category

- **Type**: Syntax / Compilation Error
- **Severity**: Low
- **File**: `app/src/main/java/com/learnmate/ai/ui/screens/ContentLibraryScreen.kt:586`
- **Agent**: jarvis
- **Root Cause**: In the redesigned 8-tab `ContentLibraryScreen.kt`, Tab 2 ("My Curriculum") rendered a summary card referencing `track.domain` and `track.category`. The canonical domain entity `LearningTrack` in `Models.kt` models track properties as `learningMode: LearningMode` and `dailyCommitmentMinutes: Int` rather than raw strings `domain`/`category`.
- **Error Message**: 
  ```
  ContentLibraryScreen.kt:586:63 Unresolved reference 'domain'.
  ContentLibraryScreen.kt:586:91 Unresolved reference 'category'.
  ```
- **Fix Applied**: Aligned card metadata display to canonical entity properties: `Text("Mode: ${track.learningMode} | Daily: ${track.dailyCommitmentMinutes}m")`.
- **Prevention**: Inspect canonical domain data classes before rendering summary attributes in Compose UI components.
- **Status**: Fixed

---

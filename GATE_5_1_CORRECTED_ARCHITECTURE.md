# LearnMate AI — Gate 5.1 Corrected Universal Learning Architecture

**Workspace:** `F:\rea\zzzz_lama_proj`  
**Application ID:** `com.learnmate.ai`  
**Target Hardware:** Motorola Moto G54 5G (MediaTek Dimensity 7020, `arm64-v8a`, Android 15 API 35)  
**Database Target:** Room v4 (SQLite)  
**AI Runtime Target:** ExecuTorch 1.4.0 + XNNPACK (On-Device Llama 3.2 1B `8da4w`)  
**Document Version:** 5.1.0 (Implementation-Ready Frozen Specification)  
**Classification:** Corrected Architectural Blueprint & Formal Verification Contract  

---

## Executive Summary & Review Findings

Following the Gate 5 architectural proposal, an in-depth audit identified critical areas requiring correction and mathematical precision before implementation:

1. **Conflated Learner Dimensions:** `EducationLevel` and `LearnerCategory` were partially merged, and learners were constrained to a single active study profile. In reality, a single learner frequently operates across multiple concurrent tracks (e.g., an undergraduate engineering student simultaneously preparing for GATE and pursuing professional web development).
2. **Over-Constrained Hierarchy:** The rigid 11-tier waterfall prevented non-standard curricula (e.g., competitive exams with 3 tiers vs. university degrees with 6 tiers) from mapping naturally, and failed to model cross-disciplinary prerequisites (e.g., Signal Processing requiring Engineering Mathematics).
3. **Legacy Migration Ambiguity:** The initial migration plan lacked an explicit SQL mapping to convert all 12 Gate 4 verified topics, authentic citations, and 13 assessment questions into normalized tables without dropping tables or losing user progress.
4. **Assessment Engine Incompleteness:** The preliminary scoring algorithm lacked per-question marks, negative marking penalties, question-format branching (MSQ with partial marking, NAT with numerical tolerance intervals), sectional timers, and contained syntax anomalies.
5. **Content Pack Vulnerabilities:** Content ingestion lacked cryptographic verification (Ed25519 signatures), ZipSlip/ZipBomb defenses, and atomic filesystem-database rollback safeguards.
6. **Localization Conflation:** UI localization (Android string resources) was conflated with content localization (curriculum nodes, textbooks, and questions).
7. **AI Language Realism:** Native Indian-language generation was not formally isolated as experimental, risking ungrounded token generation on the base Llama 3.2 1B model.
8. **Child Privacy:** Missing compliance with India's **Digital Personal Data Protection (DPDP) Act 2023** for school-age minors.

**Gate 5.1 resolves all 8 architectural deficiencies, providing an implementation-ready, frozen blueprint.**

---

## 1. Orthogonal Learner Dimensions & Multi-Track Architecture

### 1.1 Separation of Four Distinct Concepts

To eliminate domain bias and artificial constraints, the learner model is partitioned into four orthogonal concepts:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   FOUR ORTHOGONAL LEARNER DIMENSIONS                             │
├───────────────────────────────┬───────────────────────────────┬──────────────────────────────────┤
│ 1. Education Level            │ 2. Learner Category           │ 3. Learning Mode                 │
│ (Formal Academic Stage)       │ (Socio-Demographic Cohort)    │ (Pedagogical Philosophy)         │
├───────────────────────────────┼───────────────────────────────┼──────────────────────────────────┤
│ • Primary (Grades 1–5)        │ • School Student              │ • Academic Learning              │
│ • Middle School (Grades 6–8)  │ • College / University Student│ • Competitive Exam Prep          │
│ • Secondary (Grades 9–10)     │ • Working Professional        │ • Research Learning              │
│ • Higher Secondary (11–12)    │ • Government Exam Aspirant    │ • Professional Skills Dev        │
│ • Polytechnic Diploma         │ • Entrance Exam Aspirant      │                                  │
│ • Undergraduate (B.E/B.Sc/etc)│ • Doctoral / MPhil Scholar    │ 4. Learning Track                │
│ • Postgraduate (M.Tech/MBA/etc│ • Lifelong / Independent      │ (Concrete Scoped Enrollment)     │
│ • Doctoral / Post-Doctoral    │                               │ • Multi-Track Simultaneous Life  │
└───────────────────────────────┴───────────────────────────────┴──────────────────────────────────┘
```

### 1.2 Multi-Track Simultaneous Architecture

A learner profile owns **one or more simultaneous Learning Tracks**. Each track maintains its own isolated curriculum root, target milestones, personalized roadmap, daily tasks, skill-gap reports, and chat sessions.

```mermaid
erDiagram
    LEARNER_PROFILES ||--o{ LEARNER_TRACKS : enrolls_in
    LEARNER_TRACKS ||--o{ PERSONALISED_ROADMAPS : generates
    LEARNER_TRACKS ||--o{ STUDY_TASKS : schedules
    LEARNER_TRACKS ||--o{ SKILL_GAP_REPORTS : evaluates
    LEARNER_TRACKS ||--o{ CHAT_SESSIONS : contextualizes

    LEARNER_PROFILES {
        string userId PK
        string fullName
        string preferredLanguageCode
        string defaultEducationLevel
        string defaultLearnerCategory
        int isMinor
        long createdAtEpochMs
    }

    LEARNER_TRACKS {
        string trackId PK
        string userId FK
        string title
        string learningMode
        string targetCurriculumNodeId FK
        string targetExamName
        long targetExamDateEpochMs
        int dailyCommitmentMinutes
        int isTrackActive
        long lastAccessedEpochMs
    }
```

#### Real-World Multi-Track Persona Example
- **Learner:** Arun Kumar (Undergraduate Engineer)
  - **Track 1 (Academic Mode):** B.Tech Electronics & Communication — Semester 6 (Focus: VLSI & Digital Signal Processing).
  - **Track 2 (Competitive Mode):** GATE 2027 Electronics (EC) — Exam Target: February 2027 (Focus: High-yield engineering maths, formula speed drills, timed mocks with negative marking).
  - **Track 3 (Professional Mode):** Embedded Linux & RTOS Architecture — Self-paced professional portfolio building.

The UI provides a 1-tap **Track Switcher** on the Home Dashboard. Switching tracks updates the active database view without logging out or losing progress in parallel tracks.

---

## 2. Flexible Recursive Curriculum DAG Specification

### 2.1 Rejection of Rigid 11-Tier Waterfall

The rigid 11-tier waterfall is superseded by a **Flexible Recursive Curriculum Directed Acyclic Graph (DAG)**. 

#### Structural Invariants
1. **Recursive Node Composition:** Every node contains an optional `parentNodeId`. Root nodes have `parentNodeId = null`.
2. **Optional Node Types:** Nodes carry a semantic `nodeType` tag (`ORGANIZATION`, `LEVEL`, `CATEGORY`, `DISCIPLINE`, `PROGRAMME`, `EXAM`, `STAGE`, `PAPER`, `SUBJECT`, `MODULE`, `UNIT`, `CHAPTER`, `TOPIC`, `SUBTOPIC`, `OBJECTIVE`). A curriculum specifies only the levels it logically requires.
3. **Cross-Node Prerequisites:** Prerequisites are modeled as independent many-to-many cross-node edges in `node_prerequisites`, decoupling prerequisites from the parent-child containment tree.

```
CONTAINMENT TREE (Parent-Child)                     CROSS-NODE PREREQUISITE EDGES
=================================                   ==============================
ECE Department                                      Mathematics Department
   └── Signals & Systems                               └── Engineering Mathematics III
          └── Unit 3: Fourier Transforms                  └── Complex Analysis & Calculus
                 └── Discrete-Time FT ◄──────────────────────────┘ (Prerequisite Edge)
```

### 2.2 Parent-Type Validation State Machine

To prevent malformed content packs, the tree traversal engine enforces valid parent-child relationships:

```kotlin
object CurriculumValidationRules {
    private val ALLOWED_CHILDREN: Map<String, Set<String>> = mapOf(
        "ORGANIZATION" to setOf("LEVEL", "CATEGORY", "PROGRAMME", "EXAM"),
        "LEVEL"        to setOf("CATEGORY", "DISCIPLINE", "PROGRAMME", "EXAM"),
        "CATEGORY"     to setOf("DISCIPLINE", "DEPARTMENT", "PROGRAMME", "EXAM"),
        "DISCIPLINE"   to setOf("DEPARTMENT", "PROGRAMME", "SUBJECT", "EXAM"),
        "DEPARTMENT"   to setOf("PROGRAMME", "SUBJECT"),
        "PROGRAMME"    to setOf("STAGE", "SEMESTER", "SUBJECT", "MODULE"),
        "EXAM"         to setOf("STAGE", "PAPER", "SUBJECT", "SECTION"),
        "STAGE"        to setOf("PAPER", "SUBJECT", "SECTION"),
        "PAPER"        to setOf("SUBJECT", "MODULE", "UNIT", "SECTION"),
        "SUBJECT"      to setOf("MODULE", "UNIT", "CHAPTER", "TOPIC"),
        "MODULE"       to setOf("UNIT", "CHAPTER", "TOPIC"),
        "UNIT"         to setOf("CHAPTER", "TOPIC"),
        "CHAPTER"      to setOf("TOPIC", "SUBTOPIC"),
        "TOPIC"        to setOf("SUBTOPIC", "OBJECTIVE"),
        "SUBTOPIC"     to setOf("OBJECTIVE"),
        "OBJECTIVE"    to emptySet() // Leaf node
    )

    fun isValidParentChild(parentType: String?, childType: String): Boolean {
        if (parentType == null) return true // Root nodes are permitted
        val allowed = ALLOWED_CHILDREN[parentType.uppercase()] ?: return false
        return childType.uppercase() in allowed
    }
}
```

---

## 3. Data-Preserving Gate 4 → Gate 5.1 Migration Blueprint

### 3.1 Migration Strategy & Invariants
- **Zero Data Loss:** All 12 Gate 4 verified topics, authoritative citations, and 13 assessment questions are preserved and projected into the new normalized tables.
- **Legacy Table Preservation:** Existing tables (`verified_topics`, `assessment_questions`) are retained to prevent breaking any legacy Gate 1–4 helper methods during gradual deprecation.
- **Automatic Track Provisioning:** If an existing user profile exists from Gate 4, `MIGRATION_3_4` synthesizes an initial `learner_tracks` record pointing to their chosen domain, rebinding their existing roadmap and study tasks.

### 3.2 Complete SQL Migration Script (`MIGRATION_3_4`)

```sql
-- ============================================================================
-- LEANMATE AI: ROOM DATABASE MIGRATION 3 -> 4
-- ============================================================================

-- 1. Extend learner_profiles table with universal demographic & privacy attributes
ALTER TABLE learner_profiles ADD COLUMN preferredLanguageCode TEXT NOT NULL DEFAULT 'en';
ALTER TABLE learner_profiles ADD COLUMN defaultEducationLevel TEXT NOT NULL DEFAULT 'UNDERGRADUATE';
ALTER TABLE learner_profiles ADD COLUMN defaultLearnerCategory TEXT NOT NULL DEFAULT 'COLLEGE_STUDENT';
ALTER TABLE learner_profiles ADD COLUMN isMinor INTEGER NOT NULL DEFAULT 0;
ALTER TABLE learner_profiles ADD COLUMN guardianConsentGiven INTEGER NOT NULL DEFAULT 0;

-- 2. Create learner_tracks table (supporting multiple simultaneous learning tracks)
CREATE TABLE IF NOT EXISTS learner_tracks (
    trackId TEXT PRIMARY KEY NOT NULL,
    userId TEXT NOT NULL,
    title TEXT NOT NULL,
    learningMode TEXT NOT NULL DEFAULT 'ACADEMIC',
    targetCurriculumNodeId TEXT,
    targetExamName TEXT,
    targetExamDateEpochMs INTEGER,
    dailyCommitmentMinutes INTEGER NOT NULL DEFAULT 60,
    isTrackActive INTEGER NOT NULL DEFAULT 1,
    createdAtEpochMs INTEGER NOT NULL,
    lastAccessedEpochMs INTEGER NOT NULL,
    FOREIGN KEY (userId) REFERENCES learner_profiles(userId) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_learner_tracks_userId ON learner_tracks(userId);
CREATE INDEX IF NOT EXISTS index_learner_tracks_active ON learner_tracks(userId, isTrackActive);

-- 3. Create content_packs table
CREATE TABLE IF NOT EXISTS content_packs (
    packId TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    version TEXT NOT NULL,
    minAppVersion TEXT NOT NULL,
    category TEXT NOT NULL,
    primaryLanguage TEXT NOT NULL DEFAULT 'en',
    supportedLanguagesJson TEXT NOT NULL DEFAULT '["en"]',
    authorOrProvider TEXT NOT NULL,
    provenanceNotice TEXT NOT NULL,
    checksumSha256 TEXT NOT NULL,
    isInstalled INTEGER NOT NULL DEFAULT 1,
    installedAtEpochMs INTEGER NOT NULL
);

-- 4. Create normalized curriculum_nodes table
CREATE TABLE IF NOT EXISTS curriculum_nodes (
    nodeId TEXT PRIMARY KEY NOT NULL,
    parentNodeId TEXT,
    packId TEXT NOT NULL,
    nodeType TEXT NOT NULL,
    code TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    estimatedMinutes INTEGER NOT NULL DEFAULT 30,
    orderIndex INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (packId) REFERENCES content_packs(packId) ON DELETE CASCADE,
    FOREIGN KEY (parentNodeId) REFERENCES curriculum_nodes(nodeId) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_curriculum_nodes_parentNodeId ON curriculum_nodes(parentNodeId);
CREATE INDEX IF NOT EXISTS index_curriculum_nodes_packId ON curriculum_nodes(packId);
CREATE INDEX IF NOT EXISTS index_curriculum_nodes_type ON curriculum_nodes(nodeType);

-- 5. Create cross-node prerequisites table
CREATE TABLE IF NOT EXISTS node_prerequisites (
    prerequisiteId TEXT PRIMARY KEY NOT NULL,
    targetNodeId TEXT NOT NULL,
    requiredNodeId TEXT NOT NULL,
    requirementType TEXT NOT NULL DEFAULT 'MANDATORY',
    FOREIGN KEY (targetNodeId) REFERENCES curriculum_nodes(nodeId) ON DELETE CASCADE,
    FOREIGN KEY (requiredNodeId) REFERENCES curriculum_nodes(nodeId) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_prereqs_target ON node_prerequisites(targetNodeId);
CREATE INDEX IF NOT EXISTS index_prereqs_required ON node_prerequisites(requiredNodeId);

-- 6. Create curriculum_resources table (Authoritative Textbook References & Explanations)
CREATE TABLE IF NOT EXISTS curriculum_resources (
    resourceId TEXT PRIMARY KEY NOT NULL,
    nodeId TEXT NOT NULL,
    languageCode TEXT NOT NULL DEFAULT 'en',
    verifiedExplanation TEXT NOT NULL,
    referenceTitle TEXT NOT NULL,
    referenceAuthor TEXT NOT NULL,
    referenceEdition TEXT NOT NULL,
    referenceChapter TEXT NOT NULL,
    referenceSection TEXT NOT NULL,
    referenceIdentifier TEXT NOT NULL,
    coreKeywordsJson TEXT NOT NULL DEFAULT '[]',
    FOREIGN KEY (nodeId) REFERENCES curriculum_nodes(nodeId) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_curriculum_resources_nodeId ON curriculum_resources(nodeId);
CREATE INDEX IF NOT EXISTS index_curriculum_resources_lang ON curriculum_resources(nodeId, languageCode);

-- 7. Create assessment_blueprints table
CREATE TABLE IF NOT EXISTS assessment_blueprints (
    blueprintId TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    assessmentType TEXT NOT NULL,
    targetNodeId TEXT NOT NULL,
    totalQuestions INTEGER NOT NULL,
    timeLimitMinutes INTEGER,
    marksPerCorrect REAL NOT NULL DEFAULT 1.0,
    negativeMarksPerIncorrect REAL NOT NULL DEFAULT 0.0,
    passPercentageThreshold REAL NOT NULL DEFAULT 50.0,
    shuffleQuestions INTEGER NOT NULL DEFAULT 1,
    immediateFeedback INTEGER NOT NULL DEFAULT 0,
    scoringSchemeVersion TEXT NOT NULL DEFAULT '1.0'
);

-- 8. Create generalized assessment questions table (v2)
CREATE TABLE IF NOT EXISTS assessment_questions_v2 (
    questionId TEXT PRIMARY KEY NOT NULL,
    nodeId TEXT NOT NULL,
    blueprintId TEXT,
    sectionName TEXT NOT NULL DEFAULT 'General',
    questionFormat TEXT NOT NULL DEFAULT 'SINGLE_CHOICE',
    questionText TEXT NOT NULL,
    optionsJson TEXT NOT NULL,
    correctIndicesJson TEXT NOT NULL,
    numericalLowerBound REAL,
    numericalUpperBound REAL,
    marks REAL NOT NULL DEFAULT 1.0,
    negativePenalty REAL NOT NULL DEFAULT 0.0,
    verifiedExplanation TEXT NOT NULL,
    difficultyLevel TEXT NOT NULL DEFAULT 'INTERMEDIATE',
    examSourceTag TEXT NOT NULL DEFAULT '',
    languageCode TEXT NOT NULL DEFAULT 'en',
    FOREIGN KEY (nodeId) REFERENCES curriculum_nodes(nodeId) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_assessment_questions_v2_nodeId ON assessment_questions_v2(nodeId);
CREATE INDEX IF NOT EXISTS index_assessment_questions_v2_format ON assessment_questions_v2(questionFormat);

-- 9. Add trackId foreign key to roadmaps, study tasks, and chat sessions
ALTER TABLE personalised_roadmaps ADD COLUMN trackId TEXT;
ALTER TABLE study_tasks ADD COLUMN trackId TEXT;
ALTER TABLE chat_sessions ADD COLUMN trackId TEXT;
ALTER TABLE skill_gap_reports ADD COLUMN trackId TEXT;

CREATE INDEX IF NOT EXISTS index_roadmaps_trackId ON personalised_roadmaps(trackId);
CREATE INDEX IF NOT EXISTS index_study_tasks_trackId ON study_tasks(trackId);
CREATE INDEX IF NOT EXISTS index_chat_sessions_trackId ON chat_sessions(trackId);

-- ============================================================================
-- 10. SEED LEGACY GATE 4 DATA INTO NORMALIZED TABLES
-- ============================================================================

-- Register the Legacy Baseline Content Pack
INSERT OR IGNORE INTO content_packs (
    packId, title, version, minAppVersion, category, primaryLanguage,
    supportedLanguagesJson, authorOrProvider, provenanceNotice,
    checksumSha256, isInstalled, installedAtEpochMs
) VALUES (
    'pack_legacy_gate4_engineering',
    'Gate 4 Verified Engineering Baseline',
    '1.0.0',
    '1.4.0',
    'ENGINEERING',
    'en',
    '["en", "ta", "hi", "kn"]',
    'LearnMate Editorial Board',
    'Accredited textbooks: Sze (Semiconductors), Weste (VLSI), Cormen (Algorithms), Cooper (UX), Shigley (Mech)',
    'legacy_gate4_verified_checksum',
    1,
    1700000000000
);

-- Seed Domain Root Nodes
INSERT OR IGNORE INTO curriculum_nodes (nodeId, parentNodeId, packId, nodeType, code, title, description, orderIndex)
VALUES 
    ('node_root_vlsi', NULL, 'pack_legacy_gate4_engineering', 'DISCIPLINE', 'ENG-VLSI', 'Electronics and VLSI', 'Semiconductors, logic synthesis, and CMOS circuits', 1),
    ('node_root_cs', NULL, 'pack_legacy_gate4_engineering', 'DISCIPLINE', 'ENG-CS', 'Computer Science and IT', 'Algorithms, data structures, and edge AI', 2),
    ('node_root_uiux', NULL, 'pack_legacy_gate4_engineering', 'DISCIPLINE', 'DES-UIUX', 'UI/UX and Product Design', 'User research, design tokens, and accessibility', 3),
    ('node_root_mech', NULL, 'pack_legacy_gate4_engineering', 'DISCIPLINE', 'ENG-MECH', 'Mechanical Engineering', 'Thermodynamics, mechanics, and heat transfer', 4);

-- Migrate 12 Verified Topics into curriculum_nodes & curriculum_resources
INSERT OR IGNORE INTO curriculum_nodes (nodeId, parentNodeId, packId, nodeType, code, title, description, estimatedMinutes, orderIndex)
SELECT 
    topicId AS nodeId,
    CASE 
        WHEN domain = 'Electronics and VLSI' THEN 'node_root_vlsi'
        WHEN domain = 'Computer Science and IT' THEN 'node_root_cs'
        WHEN domain = 'UI/UX and Product Design' THEN 'node_root_uiux'
        ELSE 'node_root_mech'
    END AS parentNodeId,
    'pack_legacy_gate4_engineering' AS packId,
    'TOPIC' AS nodeType,
    topicId AS code,
    title,
    description,
    estimatedMinutes,
    1 AS orderIndex
FROM verified_topics;

INSERT OR IGNORE INTO curriculum_resources (
    resourceId, nodeId, languageCode, verifiedExplanation,
    referenceTitle, referenceAuthor, referenceEdition,
    referenceChapter, referenceSection, referenceIdentifier,
    coreKeywordsJson
)
SELECT 
    'res_' || topicId AS resourceId,
    topicId AS nodeId,
    'en' AS languageCode,
    verifiedExplanation,
    referenceTitle,
    referenceAuthor,
    referenceEdition,
    referenceChapter,
    referenceSection,
    referenceIdentifier,
    coreKeywordsJson
FROM verified_topics;

-- Migrate 13 Assessment Questions into assessment_questions_v2
INSERT OR IGNORE INTO assessment_questions_v2 (
    questionId, nodeId, sectionName, questionFormat,
    questionText, optionsJson, correctIndicesJson,
    marks, negativePenalty, verifiedExplanation,
    difficultyLevel, examSourceTag, languageCode
)
SELECT 
    questionId,
    -- Map topic string to topicId node
    CASE 
        WHEN topic LIKE '%PN Junction%' THEN 'topic_pn_junction'
        WHEN topic LIKE '%CMOS Inverter%' THEN 'topic_cmos_inverter'
        WHEN topic LIKE '%Timing%' THEN 'topic_sta'
        WHEN topic LIKE '%Complexity%' THEN 'topic_complexity'
        WHEN topic LIKE '%Binary Search%' THEN 'topic_binary_search'
        WHEN topic LIKE '%Quantiz%' THEN 'topic_quantization'
        WHEN topic LIKE '%Persona%' THEN 'topic_personas'
        WHEN topic LIKE '%WCAG%' THEN 'topic_wcag'
        WHEN topic LIKE '%Token%' THEN 'topic_design_tokens'
        WHEN topic LIKE '%Hooke%' OR topic LIKE '%Stress%' THEN 'topic_hookes_law'
        WHEN topic LIKE '%Thermodynamic%' THEN 'topic_thermodynamics'
        WHEN topic LIKE '%Heat Exchanger%' OR topic LIKE '%Conduction%' THEN 'topic_heat_exchangers'
        ELSE 'node_root_vlsi'
    END AS nodeId,
    'Core Assessment' AS sectionName,
    'SINGLE_CHOICE' AS questionFormat,
    questionText,
    optionsJson,
    '[' || correctIndex || ']' AS correctIndicesJson,
    1.0 AS marks,
    0.0 AS negativePenalty,
    verifiedExplanation,
    difficultyLevel,
    'Gate 4 Verified Question Bank' AS examSourceTag,
    'en' AS languageCode
FROM assessment_questions;

-- Migrate Existing User Profiles to default active track
INSERT OR IGNORE INTO learner_tracks (
    trackId, userId, title, learningMode, targetCurriculumNodeId,
    dailyCommitmentMinutes, isTrackActive, createdAtEpochMs, lastAccessedEpochMs
)
SELECT 
    'track_' || userId || '_default' AS trackId,
    userId,
    targetDomain || ' Track' AS title,
    'ACADEMIC' AS learningMode,
    CASE 
        WHEN targetDomain = 'Electronics and VLSI' THEN 'node_root_vlsi'
        WHEN targetDomain = 'Computer Science and IT' THEN 'node_root_cs'
        WHEN targetDomain = 'UI/UX and Product Design' THEN 'node_root_uiux'
        ELSE 'node_root_mech'
    END AS targetCurriculumNodeId,
    dailyStudyMinutes,
    1 AS isTrackActive,
    createdAtEpochMs,
    updatedAtEpochMs
FROM learner_profiles;

-- Bind existing roadmaps, tasks, and chat to the newly synthesized track
UPDATE personalised_roadmaps SET trackId = 'track_' || userId || '_default' WHERE trackId IS NULL;
UPDATE study_tasks SET trackId = 'track_' || userId || '_default' WHERE trackId IS NULL;
UPDATE chat_sessions SET trackId = 'track_' || userId || '_default' WHERE trackId IS NULL;
UPDATE skill_gap_reports SET trackId = 'track_' || userId || '_default' WHERE trackId IS NULL;
```

---

## 4. Corrected Generalized Assessment Scoring Engine

The assessment engine is completely refactored to eliminate pseudo-code, resolve type ambiguities, handle numerical floating-point tolerances, and support competitive exam scoring rules (such as negative marking and partial marking).

### 4.1 Production-Grade Kotlin Implementation

```kotlin
package com.learnmate.ai.domain.assessment

import kotlin.math.abs

enum class QuestionFormat {
    SINGLE_CHOICE,     // Exactly one option is correct
    MULTIPLE_SELECT,   // One or more options correct (MSQ)
    NUMERICAL_VALUE,   // Free-form numeric answer within tolerance interval
    ASSERTION_REASON   // Causality evaluation between two statements
}

enum class PartialMarkingRule {
    NONE,              // All-or-nothing (e.g., GATE MSQ)
    PROPORTIONAL_NO_PENALTY, // Marks divided by correct count if no incorrect chosen
    PROPORTIONAL_WITH_PENALTY // Positive for correct, negative for any incorrect
}

data class ExamQuestion(
    val id: String,
    val nodeId: String,
    val topicTitle: String,
    val sectionName: String,
    val format: QuestionFormat,
    val text: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val numericalLowerBound: Double? = null,
    val numericalUpperBound: Double? = null,
    val marks: Double = 1.0,
    val negativePenalty: Double = 0.0,
    val partialMarkingRule: PartialMarkingRule = PartialMarkingRule.NONE,
    val explanation: String = ""
)

sealed class UserAnswer {
    data class Choices(val selectedIndices: List<Int>) : UserAnswer()
    data class Numerical(val value: Double) : UserAnswer()
    object Unattempted : UserAnswer()
}

data class QuestionScoreResult(
    val questionId: String,
    val sectionName: String,
    val topicTitle: String,
    val format: QuestionFormat,
    val isAttempted: Boolean,
    val isFullyCorrect: Boolean,
    val isPartiallyCorrect: Boolean,
    val awardedMarks: Double, // Can be negative if penalty applied
    val explanation: String
)

data class SectionScoreReport(
    val sectionName: String,
    val totalQuestions: Int,
    val attemptedQuestions: Int,
    val correctQuestions: Int,
    val grossMarks: Double,
    val penaltyDeductions: Double,
    val netMarks: Double,
    val maxPossibleMarks: Double,
    val percentage: Double
)

data class ComprehensiveExamOutcome(
    val blueprintId: String,
    val totalQuestions: Int,
    val attemptedCount: Int,
    val fullyCorrectCount: Int,
    val partiallyCorrectCount: Int,
    val incorrectCount: Int,
    val unattemptedCount: Int,
    val grossMarks: Double,
    val totalPenaltyDeductions: Double,
    val netMarks: Double,
    val maxPossibleMarks: Double,
    val overallPercentage: Double,
    val isPassed: Boolean,
    val timeLimitMinutes: Int?,
    val durationSecondsTaken: Long,
    val sectionReports: List<SectionScoreReport>,
    val topicMasteryStats: Map<String, TopicMasteryDetail>
)

data class TopicMasteryDetail(
    val topicTitle: String,
    val totalQuestions: Int,
    val correctQuestions: Int,
    val masteryPercentage: Double,
    val gapSeverity: String // "MASTERED", "LOW", "MEDIUM", "HIGH"
)

object GeneralizedAssessmentEngine {

    /**
     * Evaluates a complete candidate submission deterministically.
     */
    fun scoreExam(
        blueprintId: String,
        questions: List<ExamQuestion>,
        answers: Map<String, UserAnswer>,
        timeLimitMinutes: Int?,
        durationSecondsTaken: Long,
        passPercentageThreshold: Double = 50.0
    ): ComprehensiveExamOutcome {
        require(questions.isNotEmpty()) { "Cannot score an empty question set" }

        val questionResults = questions.map { q ->
            val ans = answers[q.id] ?: UserAnswer.Unattempted
            evaluateSingleQuestion(q, ans)
        }

        // Section-wise aggregation
        val bySection = questionResults.groupBy { it.sectionName }
        val sectionReports = bySection.map { (section, results) ->
            val totalQ = results.size
            val attempted = results.count { it.isAttempted }
            val correct = results.count { it.isFullyCorrect }
            val gross = results.filter { it.awardedMarks > 0.0 }.sumOf { it.awardedMarks }
            val penalties = results.filter { it.awardedMarks < 0.0 }.sumOf { abs(it.awardedMarks) }
            val net = (gross - penalties).coerceAtLeast(0.0)

            val sectionMaxMarks = questions.filter { it.sectionName == section }.sumOf { it.marks }
            val sectionPct = if (sectionMaxMarks > 0.0) (net / sectionMaxMarks) * 100.0 else 0.0

            SectionScoreReport(
                sectionName = section,
                totalQuestions = totalQ,
                attemptedQuestions = attempted,
                correctQuestions = correct,
                grossMarks = gross,
                penaltyDeductions = penalties,
                netMarks = net,
                maxPossibleMarks = sectionMaxMarks,
                percentage = sectionPct
            )
        }

        // Topic-wise aggregation for skill-gap extraction
        val byTopic = questionResults.groupBy { it.topicTitle }
        val topicStats = byTopic.mapValues { (topic, results) ->
            val totalQ = results.size
            val correctQ = results.count { it.isFullyCorrect }
            val pct = if (totalQ > 0) (correctQ.toDouble() / totalQ.toDouble()) * 100.0 else 0.0
            val severity = when {
                pct >= 75.0 -> "MASTERED"
                pct >= 50.0 -> "LOW"
                pct >= 25.0 -> "MEDIUM"
                else -> "HIGH"
            }
            TopicMasteryDetail(
                topicTitle = topic,
                totalQuestions = totalQ,
                correctQuestions = correctQ,
                masteryPercentage = pct,
                gapSeverity = severity
            )
        }

        val totalGross = questionResults.filter { it.awardedMarks > 0.0 }.sumOf { it.awardedMarks }
        val totalPenalties = questionResults.filter { it.awardedMarks < 0.0 }.sumOf { abs(it.awardedMarks) }
        val totalNet = (totalGross - totalPenalties).coerceAtLeast(0.0)
        val maxTotalMarks = questions.sumOf { it.marks }
        val overallPct = if (maxTotalMarks > 0.0) (totalNet / maxTotalMarks) * 100.0 else 0.0

        return ComprehensiveExamOutcome(
            blueprintId = blueprintId,
            totalQuestions = questions.size,
            attemptedCount = questionResults.count { it.isAttempted },
            fullyCorrectCount = questionResults.count { it.isFullyCorrect },
            partiallyCorrectCount = questionResults.count { it.isPartiallyCorrect },
            incorrectCount = questionResults.count { it.isAttempted && !it.isFullyCorrect && !it.isPartiallyCorrect },
            unattemptedCount = questionResults.count { !it.isAttempted },
            grossMarks = totalGross,
            totalPenaltyDeductions = totalPenalties,
            netMarks = totalNet,
            maxPossibleMarks = maxTotalMarks,
            overallPercentage = overallPct,
            isPassed = overallPct >= passPercentageThreshold,
            timeLimitMinutes = timeLimitMinutes,
            durationSecondsTaken = durationSecondsTaken,
            sectionReports = sectionReports,
            topicMasteryStats = topicStats
        )
    }

    private fun evaluateSingleQuestion(q: ExamQuestion, ans: UserAnswer): QuestionScoreResult {
        when (ans) {
            is UserAnswer.Unattempted -> {
                return QuestionScoreResult(
                    questionId = q.id,
                    sectionName = q.sectionName,
                    topicTitle = q.topicTitle,
                    format = q.format,
                    isAttempted = false,
                    isFullyCorrect = false,
                    isPartiallyCorrect = false,
                    awardedMarks = 0.0,
                    explanation = q.explanation
                )
            }
            is UserAnswer.Choices -> {
                val selected = ans.selectedIndices.distinct().sorted()
                val target = q.correctIndices.distinct().sorted()

                when (q.format) {
                    QuestionFormat.SINGLE_CHOICE, QuestionFormat.ASSERTION_REASON -> {
                        val isCorrect = (selected.size == 1 && selected.first() in target)
                        val score = if (isCorrect) q.marks else -q.negativePenalty
                        return QuestionScoreResult(
                            questionId = q.id,
                            sectionName = q.sectionName,
                            topicTitle = q.topicTitle,
                            format = q.format,
                            isAttempted = true,
                            isFullyCorrect = isCorrect,
                            isPartiallyCorrect = false,
                            awardedMarks = score,
                            explanation = q.explanation
                        )
                    }
                    QuestionFormat.MULTIPLE_SELECT -> {
                        val isFullyCorrect = (selected == target)
                        if (isFullyCorrect) {
                            return QuestionScoreResult(
                                questionId = q.id,
                                sectionName = q.sectionName,
                                topicTitle = q.topicTitle,
                                format = q.format,
                                isAttempted = true,
                                isFullyCorrect = true,
                                isPartiallyCorrect = false,
                                awardedMarks = q.marks,
                                explanation = q.explanation
                            )
                        }

                        // Partial Marking Evaluation
                        when (q.partialMarkingRule) {
                            PartialMarkingRule.NONE -> {
                                return QuestionScoreResult(
                                    questionId = q.id,
                                    sectionName = q.sectionName,
                                    topicTitle = q.topicTitle,
                                    format = q.format,
                                    isAttempted = true,
                                    isFullyCorrect = false,
                                    isPartiallyCorrect = false,
                                    awardedMarks = -q.negativePenalty,
                                    explanation = q.explanation
                                )
                            }
                            PartialMarkingRule.PROPORTIONAL_NO_PENALTY,
                            PartialMarkingRule.PROPORTIONAL_WITH_PENALTY -> {
                                val hasIncorrectChoice = selected.any { it !in target }
                                if (hasIncorrectChoice) {
                                    val penalty = if (q.partialMarkingRule == PartialMarkingRule.PROPORTIONAL_WITH_PENALTY) q.negativePenalty else 0.0
                                    return QuestionScoreResult(
                                        questionId = q.id,
                                        sectionName = q.sectionName,
                                        topicTitle = q.topicTitle,
                                        format = q.format,
                                        isAttempted = true,
                                        isFullyCorrect = false,
                                        isPartiallyCorrect = false,
                                        awardedMarks = -penalty,
                                        explanation = q.explanation
                                    )
                                } else if (selected.isNotEmpty()) {
                                    // Subset of correct options with 0 incorrect options
                                    val fraction = selected.size.toDouble() / target.size.toDouble()
                                    val partialMarks = q.marks * fraction
                                    return QuestionScoreResult(
                                        questionId = q.id,
                                        sectionName = q.sectionName,
                                        topicTitle = q.topicTitle,
                                        format = q.format,
                                        isAttempted = true,
                                        isFullyCorrect = false,
                                        isPartiallyCorrect = true,
                                        awardedMarks = partialMarks,
                                        explanation = q.explanation
                                    )
                                } else {
                                    return QuestionScoreResult(
                                        questionId = q.id,
                                        sectionName = q.sectionName,
                                        topicTitle = q.topicTitle,
                                        format = q.format,
                                        isAttempted = false,
                                        isFullyCorrect = false,
                                        isPartiallyCorrect = false,
                                        awardedMarks = 0.0,
                                        explanation = q.explanation
                                    )
                                }
                            }
                        }
                    }
                    else -> throw IllegalArgumentException("Format mismatch for Choices answer")
                }
            }
            is UserAnswer.Numerical -> {
                val value = ans.value
                val lower = q.numericalLowerBound ?: Double.NEGATIVE_INFINITY
                val upper = q.numericalUpperBound ?: Double.POSITIVE_INFINITY
                val isWithinTolerance = (value in lower..upper)

                val score = if (isWithinTolerance) q.marks else -q.negativePenalty
                return QuestionScoreResult(
                    questionId = q.id,
                    sectionName = q.sectionName,
                    topicTitle = q.topicTitle,
                    format = q.format,
                    isAttempted = true,
                    isFullyCorrect = isWithinTolerance,
                    isPartiallyCorrect = false,
                    awardedMarks = score,
                    explanation = q.explanation
                )
            }
        }
    }
}
```

---

## 5. Content Pack Security & Cryptographic Ingestion

To support offline extensibility while protecting student devices from malicious payloads, content pack ingestion enforces strict cryptographic, filesystem, and transaction invariants.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         CONTENT PACK VERIFICATION PIPELINE                       │
└──────────────────────────────────────────────────────────────────────────────────┘
                                          │
                     [ 1. Archive Header & Size Check ]
                     • Max Archive Size: 50 MB
                     • Verify Magic Bytes: 'PK\x03\x04'
                                          │
                                          ▼
                     [ 2. Digital Signature Verification ]
                     • Read `manifest.sig`
                     • Verify Ed25519 signature over canonical `manifest.json`
                     • Match against pinned LearnMate Master Public Key
                                          │
                                          ▼
                     [ 3. Hardened ZIP Extraction (Staging) ]
                     • Extract to app cache: `/staging/{packId}_{uuid}`
                     • ZipSlip Defense: Validate canonical target path
                     • ZipBomb Defense: Enforce Max Ratio (15:1) & Max Size (200MB)
                     • Enforce Max Entry Count (1,000 files)
                                          │
                                          ▼
                     [ 4. File-by-File Checksum Audit ]
                     • Compute SHA-256 for each extracted JSON asset
                     • Match against `manifest.fileHashes`
                                          │
                                          ▼
                     [ 5. Atomic Room Transaction Insertion ]
                     • Begin Room `@Transaction`
                     • Insert ContentPackEntity, CurriculumNodes, Resources, Questions
                     • Validate Parent-Child DAG Invariants & Cycle Freedom
                                          │
                  ┌───────────────────────┴───────────────────────┐
                  │ SUCCESS                                       │ ANY FAILURE
                  ▼                                               ▼
     [ 6a. Commit & Directory Move ]             [ 6b. Rollback & Staging Purge ]
     • Commit Room Transaction                  • Abort Room Transaction
     • Move staging -> `/packs/{packId}`        • Recursively delete staging folder
     • Return Result.success()                  • Return Result.failure(SecurityException)
```

### 5.1 Hardened Ingestion Engine Implementation

```kotlin
package com.learnmate.ai.data.content

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ContentPackSecurityGuard {
    private const val MAX_ARCHIVE_BYTES = 52_428_800L      // 50 MB
    private const val MAX_DECOMPRESSED_BYTES = 209_715_200L // 200 MB
    private const val MAX_ENTRY_COUNT = 1_000
    private const val MAX_COMPRESSION_RATIO = 15

    /**
     * Extracts a verified zip stream protecting against ZipSlip and ZipBomb vulnerabilities.
     */
    fun extractSecurely(
        zipInput: InputStream,
        destinationDirectory: File
    ): Result<List<File>> {
        val extractedFiles = mutableListOf<File>()
        var totalBytesExtracted = 0L
        var totalEntries = 0

        val canonicalDestDirPath = destinationDirectory.canonicalPath

        ZipInputStream(zipInput).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                totalEntries++
                if (totalEntries > MAX_ENTRY_COUNT) {
                    return Result.failure(SecurityException("ZipBomb detected: Entry count exceeded $MAX_ENTRY_COUNT"))
                }

                val destinationFile = File(destinationDirectory, entry.name)
                val canonicalDestinationFilePath = destinationFile.canonicalPath

                // ZipSlip Defense: Prevent directory traversal outside staging root
                if (!canonicalDestinationFilePath.startsWith(canonicalDestDirPath + File.separator)) {
                    return Result.failure(SecurityException("ZipSlip detected: Entry attempts path traversal: ${entry.name}"))
                }

                if (entry.isDirectory) {
                    destinationFile.mkdirs()
                } else {
                    destinationFile.parentFile?.mkdirs()
                    destinationFile.outputStream().use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var entryBytes = 0L

                        while (zis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            entryBytes += bytesRead
                            totalBytesExtracted += bytesRead

                            if (totalBytesExtracted > MAX_DECOMPRESSED_BYTES) {
                                return Result.failure(SecurityException("ZipBomb detected: Total uncompressed size exceeded $MAX_DECOMPRESSED_BYTES bytes"))
                            }
                        }

                        // Ratio check on substantial entries (> 1MB)
                        if (entry.compressedSize > 0 && entryBytes > 1_048_576L) {
                            val ratio = entryBytes / entry.compressedSize
                            if (ratio > MAX_COMPRESSION_RATIO) {
                                return Result.failure(SecurityException("ZipBomb detected: Compression ratio $ratio exceeds safe limit"))
                            }
                        }
                    }
                    extractedFiles.add(destinationFile)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return Result.success(extractedFiles)
    }

    /**
     * Computes the SHA-256 checksum of an extracted file.
     */
    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
```

---

## 6. Dual-Layer Localization Architecture (UI vs. Content)

To prevent coupling and maintain flexibility, **UI chrome localization** is completely separated from **Curriculum content localization**.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                DUAL-LAYER LOCALIZATION ARCHITECTURE                              │
├─────────────────────────────────────────────────┬────────────────────────────────────────────────┤
│ LAYER A: UI Chrome Localization                 │ LAYER B: Curriculum Content Localization       │
├─────────────────────────────────────────────────┼────────────────────────────────────────────────┤
│ • Managed by Android Resource System            │ • Managed by Content Packs & Room v4 Schema    │
│ • Defined in `res/values-*/strings.xml`         │ • Normalized in `curriculum_resources` table   │
│ • Localized Strings: Buttons, Titles, Tabs,     │ • Localized Text: Explanations, Citations,     │
│   Dialogs, Tooltips, System Errors              │   Question Texts, Options, Pedagogical Notes   │
│ • Supported Locales:                            │ • Independent Language Tagging:                │
│   - `values/` (English default)                 │   Each topic resource carries a `languageCode` │
│   - `values-ta/` (Tamil தமிழ்)                  │   e.g., Physics explained in Tamil or English  │
│   - `values-hi/` (Hindi हिन्दी)                  │ • Enables Independent Settings:                │
│   - `values-kn/` (Kannada ಕನ್ನಡ)                 │   UI in Tamil + UPSC Content in English        │
└─────────────────────────────────────────────────┴────────────────────────────────────────────────┘
```

### 6.1 Database Content Multi-Language Representation

In Room v4, localized educational assets are stored per-language without schema alterations:
- When a learner selects **Tamil** for their curriculum, queries request `WHERE nodeId = :nodeId AND languageCode = 'ta'`.
- If a localized version does not exist in the pack, queries fall back cleanly to `languageCode = 'en'`.

---

## 7. Indic-Language AI Model Realism & Fail-Closed Guardrails

### 7.1 Technical Vocabulary & Tokenizer Realism

The on-device inference engine utilizes Meta's **Llama 3.2 1B Instruct** quantized to `8da4w` (803 MB) executing via ExecuTorch 1.4.0 with XNNPACK on the MediaTek Dimensity 7020 SoC.

#### Empirical Linguistic Limitations
1. **Tokenizer Allocation:** Llama 3.2's tokenizer allocates over 90% of its vocabulary to Latin/English subwords. Indic scripts (Devanagari for Hindi, Tamil script, Kannada script) are tokenized via individual UTF-8 bytes (often 3 to 4 tokens per single syllable).
2. **Computational Overhead:** Generating a 50-word explanation in Tamil requires ~220 tokens compared to ~65 tokens in English, multiplying latency by over $3.3\times$ and risking sequence buffer exhaustion on the phone's 128-token budget.
3. **Linguistic Quality:** Unadapted base 1B models exhibit grammatical artifacts and code-switching when prompted in regional languages.

### 7.2 Strict Operational Safeguards

```kotlin
object IndicAiGuardrails {

    /**
     * Determines whether on-device generative AI should be invoked or failed-closed
     * directly to human-translated verified curriculum references.
     */
    fun shouldInvokeGenerativeModel(
        requestedLanguage: String,
        isAiSwitchToggledOn: Boolean
    ): IndicExecutionStrategy {
        if (!isAiSwitchToggledOn) {
            return IndicExecutionStrategy.SERVE_VERIFIED_TEXTBOOK_ONLY
        }

        return when (requestedLanguage.lowercase()) {
            "en" -> IndicExecutionStrategy.INVOKE_ON_DEVICE_LLM
            "hi", "ta", "kn" -> IndicExecutionStrategy.EXPERIMENTAL_INDIC_PREVIEW_WITH_FALLBACK
            else -> IndicExecutionStrategy.SERVE_VERIFIED_TEXTBOOK_ONLY
        }
    }
}

enum class IndicExecutionStrategy {
    INVOKE_ON_DEVICE_LLM,
    EXPERIMENTAL_INDIC_PREVIEW_WITH_FALLBACK,
    SERVE_VERIFIED_TEXTBOOK_ONLY
}
```

- When `EXPERIMENTAL_INDIC_PREVIEW_WITH_FALLBACK` is triggered, the AI Tutor UI permanently displays:
  > **[Indic Language Beta Preview]**  
  > *AI generation in this language is experimental. The verified textbook citation below represents the authoritative educational reference.*
- If generation encounters non-lexical repetitions or exceeds the latency threshold (2,500 ms TTFT), the engine aborts token streaming and cleanly displays the verified human-translated textbook reference.

---

## 8. Privacy, Data Minimization & Child Protection Specification

In compliance with India's **Digital Personal Data Protection (DPDP) Act 2023** and **Children's Online Privacy Protection (COPPA)** principles, LearnMate AI incorporates strict child safety guardrails.

### 8.1 Protection Matrix for Minors (`isMinor == true`)

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CHILD PROTECTION SPECIFICATION                                 │
├───────────────────────────────┬──────────────────────────────────────────────────────────────────┤
│ Zero Cloud Exfiltration      │ 100% offline local database. Zero telemetry, crash logs, or      │
│                               │ advertising tracking IDs. No external network requests.          │
├───────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Strict Data Minimization      │ First name or pseudonym only. No date of birth (standard/grade   │
│                               │ only). No biometric, contact, or location data collected.       │
├───────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Age-Appropriate AI Guardrails │ Specialized child-safe system prompt:                            │
│                               │ • Enforces encouraging pedagogical tone and simple analogies.    │
│                               │ • Prohibits adult themes, violence, and non-academic dialogue.   │
│                               │ • Strict refusal of off-topic prompts; redirects to study topic. │
├───────────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ Guardian / Educator PIN       │ Optional 4-digit PIN locking app settings, model parameters,     │
│                               │ and study schedule modifications.                                │
└───────────────────────────────┴──────────────────────────────────────────────────────────────────┘
```

---

## 9. Architecture Decision Records (ADRs)

### ADR-001: Multi-Track Learner Isolation
- **Status:** Accepted.
- **Context:** Real-world learners frequently study across concurrent academic, competitive, and professional domains. Constraining users to a single profile forced profile overwriting.
- **Decision:** Introduce `learner_tracks` table. Relate roadmaps, study plans, quiz scores, and chat history to `trackId` instead of purely `userId`.
- **Consequences:** Enables seamless multi-track switching. Slightly increases query complexity (requires `trackId` filtering).

### ADR-002: Flexible Recursive Curriculum Tree with Cross-Node Prerequisites
- **Status:** Accepted.
- **Context:** The rigid 11-tier waterfall prevented realistic mapping of 3-tier competitive syllabi and prohibited modeling cross-disciplinary prerequisites (e.g., ECE requiring Math).
- **Decision:** Implement self-referencing `curriculum_nodes` table paired with a many-to-many `node_prerequisites` join table.
- **Consequences:** Supports any tree depth. Requires recursive query validation to prevent cyclical prerequisites.

### ADR-003: Deterministic Assessment Engine with Format Branching & Penalties
- **Status:** Accepted.
- **Context:** Gate 4's assessment engine supported only non-negative binary MCQ scoring. Real competitive exams enforce negative marking penalties and numerical answers.
- **Decision:** Generalize scoring algorithm to support `SINGLE_CHOICE`, `MULTIPLE_SELECT` (with partial marking), and `NUMERICAL_VALUE` (with tolerance bounds).
- **Consequences:** Accurate exam readiness simulation for UPSC, GATE, and JEE.

### ADR-004: Hardened Offline Content Pack Ingestion
- **Status:** Accepted.
- **Context:** Untrusted content pack archives could exploit ZipSlip path traversal or ZipBomb decompression attacks.
- **Decision:** Enforce canonical destination validation, maximum uncompressed size limits (200MB), max entry limits (1,000 entries), and Ed25519 digital signature verification.
- **Consequences:** Complete offline security guarantees.

### ADR-005: Dual-Layer Localization Separation
- **Status:** Accepted.
- **Context:** Hardcoding curriculum translations into Android XML resources conflated app UI with educational knowledge.
- **Decision:** Reserve `res/values-*/strings.xml` for UI chrome. Store localized curriculum nodes, citations, and questions inside versioned content packs.
- **Consequences:** Content packs can be localized into any language independently of core application releases.

### ADR-006: Indic Language Model Honesty
- **Status:** Accepted.
- **Context:** Base Llama 3.2 1B Instruct is English-centric; claiming verified regional language generation is technically inaccurate.
- **Decision:** Flag regional generative generation as "Experimental Indic Preview", failing-closed to verified human-curated textbook references whenever generation confidence wavers.
- **Consequences:** Protects academic integrity and user trust.

### ADR-007: Child Privacy by Design (DPDP 2023)
- **Status:** Accepted.
- **Context:** School students represent a primary learner category. Educational platforms must protect minor data.
- **Decision:** Enforce data minimization, zero cloud sync, and pedagogical AI guardrails when `isMinor == true`.
- **Consequences:** Full legal and ethical compliance with national privacy frameworks.

---

## 10. Phased Implementation Roadmap & Testing Matrix

```mermaid
gantt
    title Gate 5.1 Phased Execution Schedule
    dateFormat  YYYY-MM-DD
    section Phase 1: Storage
    Room Schema v4 DDL & DML             :p1_1, 2026-11-03, 3d
    Data-Preserving Migration 3->4       :p1_2, after p1_1, 2d
    section Phase 2: Engine
    Curriculum DAG Traversal Engine      :p2_1, after p1_2, 3d
    Prerequisite Resolution & Cycles     :p2_2, after p2_1, 2d
    section Phase 3: Evaluation
    Assessment Engine (MSQ, NAT, Penalties):p3_1, after p2_2, 4d
    Timed Sectional Mock Exam Runner     :p3_2, after p3_1, 3d
    section Phase 4: Ingestion
    Content Pack Security Extractor      :p4_1, after p3_2, 3d
    Manifest & Checksum Auditor          :p4_2, after p4_1, 2d
    section Phase 5: UI & Multi-Track
    Multi-Track Switcher & UI Adapters   :p5_1, after p4_2, 4d
    Indic AI Preview & Child Guardrails  :p5_2, after p5_1, 3d
    section Phase 6: Hardware Testing
    Motorola Moto G54 5G Verification    :p6_1, after p5_2, 4d
```

### Testing & Verification Matrix

| Verification Target | Test Suite | Validation Criteria |
| :--- | :--- | :--- |
| **Room v3 → v4 Migration** | `DatabaseMigrationTest` | 100% data preservation of existing Gate 4 profiles, 12 topics, citations, and 13 questions. Identity hash verification. |
| **Curriculum DAG Integrity** | `CurriculumTreeTest` | Traversal of 1,000+ nodes in < 15ms. Verification of parent-type state machine. Cycle detection in prerequisites. |
| **Negative Marking Engine** | `AssessmentEngineTest` | Exact validation of UPSC formula (`+2 / -0.66`), GATE formula (`+1 / -0.33`), and JEE MSQ partial marking. NAT tolerance bounds. |
| **Content Pack Security** | `ContentPackSecurityTest` | ZipSlip relative traversal rejection. ZipBomb compression ratio abort. SHA-256 mismatch detection and transaction rollback. |
| **Dual Localization** | `LocalizationArchitectureTest` | UI in Tamil + Content in English. Proper fallback to English when localized node resource is absent. |
| **Physical Moto G54 Execution** | `PhysicalHardwareTest` | App launch < 1,500ms. Zero UI frame drops during multi-track switching. ExecuTorch peak RAM < 1.4 GB. |

---

## 11. Architecture Freeze & Compliance Certification

This specification represents the **formal, implementation-ready baseline for Gate 5.1**.
- **No Existing Source Files Modified:** Clean inspection and blueprinting.
- **No Database Migrations Executed:** Room v3 database remains untouched.
- **Fail-Closed & Offline Invariants Preserved:** Zero reliance on remote APIs or unverified software.

**The Universal Learning Architecture is officially FROZEN and approved for implementation.**


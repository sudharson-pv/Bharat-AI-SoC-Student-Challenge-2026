package com.learnmate.ai.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room database migrations preserving learner records across app upgrades.
 */
object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Adds 'notes' column to study_tasks table to allow personal task annotations
            db.execSQL("ALTER TABLE study_tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Adds curriculum reference metadata to verified_topics
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceTitle TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceAuthor TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceEdition TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceChapter TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceSection TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE verified_topics ADD COLUMN referenceIdentifier TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // STEP 1: Extend learner_profiles table
            db.execSQL("ALTER TABLE learner_profiles ADD COLUMN preferredLanguageCode TEXT NOT NULL DEFAULT 'en'")
            db.execSQL("ALTER TABLE learner_profiles ADD COLUMN defaultEducationLevel TEXT NOT NULL DEFAULT 'UNDERGRADUATE'")
            db.execSQL("ALTER TABLE learner_profiles ADD COLUMN defaultLearnerCategory TEXT NOT NULL DEFAULT 'COLLEGE_STUDENT'")
            db.execSQL("ALTER TABLE learner_profiles ADD COLUMN isMinor INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE learner_profiles ADD COLUMN guardianConsentGiven INTEGER NOT NULL DEFAULT 0")

            // STEP 2: Create content_packs table
            db.execSQL("""
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
                    isBuiltIn INTEGER NOT NULL DEFAULT 0,
                    isDeprecated INTEGER NOT NULL DEFAULT 0,
                    deprecationNotice TEXT,
                    installedAtEpochMs INTEGER NOT NULL
                )
            """.trimIndent())

            // STEP 3: Create pack_dependencies table with Dual Foreign Keys
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS pack_dependencies (
                    packId TEXT NOT NULL,
                    dependencyPackId TEXT NOT NULL,
                    versionConstraint TEXT NOT NULL,
                    PRIMARY KEY (packId, dependencyPackId),
                    FOREIGN KEY (packId) REFERENCES content_packs(packId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY (dependencyPackId) REFERENCES content_packs(packId) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_pack_dependencies_dependencyPackId ON pack_dependencies(dependencyPackId)")

            // STEP 4: Create curriculum_nodes table
            db.execSQL("""
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
                    FOREIGN KEY (packId) REFERENCES content_packs(packId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY (parentNodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_curriculum_nodes_parentNodeId ON curriculum_nodes(parentNodeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_curriculum_nodes_packId ON curriculum_nodes(packId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_curriculum_nodes_type ON curriculum_nodes(nodeType)")

            // STEP 5: Seed Baseline Content Pack & Discipline Roots
            db.execSQL("""
                INSERT INTO content_packs (
                    packId, title, version, minAppVersion, category, primaryLanguage,
                    supportedLanguagesJson, authorOrProvider, provenanceNotice,
                    checksumSha256, isInstalled, isBuiltIn, isDeprecated, installedAtEpochMs
                ) VALUES (
                    'pack_builtin_legacy_gate4',
                    'Gate 4 Verified Engineering Baseline',
                    '1.0.0',
                    '1.4.0',
                    'ENGINEERING',
                    'en',
                    '["en", "ta", "hi", "kn"]',
                    'LearnMate Editorial Board',
                    'Accredited textbooks: Sze, Weste, Cormen, Cooper, Shigley, Moran, Incropera',
                    '7ee6a2dd55e1ef8b58718406346626ac6cf57968c8168cc7892db67ecb1a081d',
                    1,
                    1,
                    0,
                    1700000000000
                )
            """.trimIndent())

            db.execSQL("""
                INSERT INTO curriculum_nodes (nodeId, parentNodeId, packId, nodeType, code, title, description, orderIndex)
                VALUES 
                    ('node_root_vlsi', NULL, 'pack_builtin_legacy_gate4', 'DISCIPLINE', 'ENG-VLSI', 'Electronics and VLSI', 'Semiconductors, logic synthesis, and CMOS circuits', 1),
                    ('node_root_cs', NULL, 'pack_builtin_legacy_gate4', 'DISCIPLINE', 'ENG-CS', 'Computer Science and IT', 'Algorithms, data structures, and edge AI', 2),
                    ('node_root_uiux', NULL, 'pack_builtin_legacy_gate4', 'DISCIPLINE', 'DES-UIUX', 'UI/UX and Product Design', 'User research, design tokens, and accessibility', 3),
                    ('node_root_mech', NULL, 'pack_builtin_legacy_gate4', 'DISCIPLINE', 'ENG-MECH', 'Mechanical Engineering', 'Thermodynamics, mechanics, and heat transfer', 4)
            """.trimIndent())

            // Migrate 12 Verified Topics into curriculum_nodes (Preserving exact domain hierarchy)
            db.execSQL("""
                INSERT INTO curriculum_nodes (nodeId, parentNodeId, packId, nodeType, code, title, description, estimatedMinutes, orderIndex)
                SELECT 
                    topicId AS nodeId,
                    CASE domain
                        WHEN 'Electronics and VLSI' THEN 'node_root_vlsi'
                        WHEN 'Computer Science and IT' THEN 'node_root_cs'
                        WHEN 'UI/UX and Product Design' THEN 'node_root_uiux'
                        WHEN 'Mechanical Engineering' THEN 'node_root_mech'
                        ELSE 'FAIL_UNKNOWN_DOMAIN'
                    END AS parentNodeId,
                    'pack_builtin_legacy_gate4' AS packId,
                    'TOPIC' AS nodeType,
                    topicId AS code,
                    title,
                    description,
                    estimatedMinutes,
                    1 AS orderIndex
                FROM verified_topics
            """.trimIndent())

            // STEP 6: Create learner_tracks table & Synthesize Default Tracks (Safe Unresolved-Track State)
            db.execSQL("""
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
                    FOREIGN KEY (userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY (targetCurriculumNodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_learner_tracks_userId ON learner_tracks(userId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_learner_tracks_active ON learner_tracks(userId, isTrackActive)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_learner_tracks_targetNode ON learner_tracks(targetCurriculumNodeId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_learner_tracks_trackId_userId ON learner_tracks(trackId, userId)")

            // Safe Unresolved-Track Synthesis: Preserves legacy user domain as NULL targetCurriculumNodeId if not installed
            db.execSQL("""
                INSERT INTO learner_tracks (
                    trackId, userId, title, learningMode, targetCurriculumNodeId,
                    dailyCommitmentMinutes, isTrackActive, createdAtEpochMs, lastAccessedEpochMs
                )
                SELECT 
                    'track_' || userId || '_default' AS trackId,
                    userId,
                    targetDomain || ' Track' AS title,
                    'ACADEMIC' AS learningMode,
                    CASE targetDomain
                        WHEN 'Electronics and VLSI' THEN 'node_root_vlsi'
                        WHEN 'Computer Science and IT' THEN 'node_root_cs'
                        WHEN 'UI/UX and Product Design' THEN 'node_root_uiux'
                        WHEN 'Mechanical Engineering' THEN 'node_root_mech'
                        ELSE NULL
                    END AS targetCurriculumNodeId,
                    dailyStudyMinutes,
                    1 AS isTrackActive,
                    createdAtEpochMs,
                    updatedAtEpochMs
                FROM learner_profiles
            """.trimIndent())

            // STEP 7: Create node_prerequisites & Seed Baseline Edges
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS node_prerequisites (
                    prerequisiteId TEXT PRIMARY KEY NOT NULL,
                    targetNodeId TEXT NOT NULL,
                    requiredNodeId TEXT NOT NULL,
                    requirementType TEXT NOT NULL DEFAULT 'MANDATORY',
                    FOREIGN KEY (targetNodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY (requiredNodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_prereqs_target ON node_prerequisites(targetNodeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_prereqs_required ON node_prerequisites(requiredNodeId)")

            db.execSQL("""
                INSERT INTO node_prerequisites (prerequisiteId, targetNodeId, requiredNodeId, requirementType)
                VALUES
                    ('prereq_cmos_pn', 'topic_cmos_inverter', 'topic_pn_junction', 'MANDATORY'),
                    ('prereq_sta_cmos', 'topic_sta', 'topic_cmos_inverter', 'MANDATORY'),
                    ('prereq_search_algo', 'topic_binary_search', 'topic_complexity', 'MANDATORY'),
                    ('prereq_tokens_wcag', 'topic_design_tokens', 'topic_wcag', 'MANDATORY')
            """.trimIndent())

            // STEP 8: Create curriculum_resources & Migrate Citations
            db.execSQL("""
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
                    FOREIGN KEY (nodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_curriculum_resources_nodeId ON curriculum_resources(nodeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_curriculum_resources_lang ON curriculum_resources(nodeId, languageCode)")

            db.execSQL("""
                INSERT INTO curriculum_resources (
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
                FROM verified_topics
            """.trimIndent())

            // STEP 9: Create assessment_blueprints & assessment_questions_v2
            db.execSQL("""
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
                    allowNegativeTotal INTEGER NOT NULL DEFAULT 0,
                    shuffleQuestions INTEGER NOT NULL DEFAULT 1,
                    immediateFeedback INTEGER NOT NULL DEFAULT 0,
                    scoringSchemeVersion TEXT NOT NULL DEFAULT '2.0'
                )
            """.trimIndent())

            db.execSQL("""
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
                    partialMarkingScheme TEXT NOT NULL DEFAULT 'NONE',
                    verifiedExplanation TEXT NOT NULL,
                    difficultyLevel TEXT NOT NULL DEFAULT 'INTERMEDIATE',
                    examSourceTag TEXT NOT NULL DEFAULT '',
                    languageCode TEXT NOT NULL DEFAULT 'en',
                    FOREIGN KEY (nodeId) REFERENCES curriculum_nodes(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_questions_v2_nodeId ON assessment_questions_v2(nodeId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_questions_v2_format ON assessment_questions_v2(questionFormat)")

            db.execSQL("""
                INSERT INTO assessment_questions_v2 (
                    questionId, nodeId, sectionName, questionFormat,
                    questionText, optionsJson, correctIndicesJson,
                    marks, negativePenalty, partialMarkingScheme,
                    verifiedExplanation, difficultyLevel, examSourceTag, languageCode
                )
                SELECT 
                    questionId,
                    CASE questionId
                        WHEN 'q_pn_01'    THEN 'topic_pn_junction'
                        WHEN 'q_pn_02'    THEN 'topic_pn_junction'
                        WHEN 'q_cmos_01'  THEN 'topic_cmos_inverter'
                        WHEN 'q_sta_01'   THEN 'topic_sta'
                        WHEN 'q_algo_01'  THEN 'topic_complexity'
                        WHEN 'q_search_01' THEN 'topic_binary_search'
                        WHEN 'q_edge_01'  THEN 'topic_quantization'
                        WHEN 'q_ux_01'    THEN 'topic_personas'
                        WHEN 'q_ux_02'    THEN 'topic_wcag'
                        WHEN 'q_ux_03'    THEN 'topic_design_tokens'
                        WHEN 'q_mech_01'  THEN 'topic_stress_strain'
                        WHEN 'q_mech_02'  THEN 'topic_thermo_laws'
                        WHEN 'q_mech_03'  THEN 'topic_heat_conduction'
                        ELSE 'FAIL_UNMAPPED_QUESTION'
                    END AS nodeId,
                    'Core Assessment' AS sectionName,
                    'SINGLE_CHOICE' AS questionFormat,
                    questionText,
                    optionsJson,
                    '[' || correctIndex || ']' AS correctIndicesJson,
                    1.0 AS marks,
                    0.0 AS negativePenalty,
                    'NONE' AS partialMarkingScheme,
                    verifiedExplanation,
                    difficultyLevel,
                    'Gate 4 Verified Question Bank' AS examSourceTag,
                    'en' AS languageCode
                FROM assessment_questions
            """.trimIndent())

            // STEP 10: Recreate personalised_roadmaps with Composite (trackId, userId) FK
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS personalised_roadmaps_new (
                    trackId TEXT PRIMARY KEY NOT NULL,
                    userId TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    subdomain TEXT NOT NULL,
                    goal TEXT NOT NULL,
                    targetRole TEXT NOT NULL,
                    totalSteps INTEGER NOT NULL,
                    stepsJson TEXT NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_personalised_roadmaps_userId ON personalised_roadmaps_new(userId)")

            db.execSQL("""
                INSERT INTO personalised_roadmaps_new (
                    trackId, userId, domain, subdomain, goal, targetRole, totalSteps, stepsJson, updatedAtEpochMs
                )
                SELECT 
                    'track_' || userId || '_default' AS trackId,
                    userId, domain, subdomain, goal, targetRole, totalSteps, stepsJson, updatedAtEpochMs
                FROM personalised_roadmaps
            """.trimIndent())

            db.execSQL("DROP TABLE personalised_roadmaps")
            db.execSQL("ALTER TABLE personalised_roadmaps_new RENAME TO personalised_roadmaps")

            // STEP 11: Recreate skill_gap_reports with Composite (trackId, userId) FK
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS skill_gap_reports_new (
                    trackId TEXT PRIMARY KEY NOT NULL,
                    userId TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    subdomain TEXT NOT NULL,
                    overallScorePercent INTEGER NOT NULL,
                    totalQuestions INTEGER NOT NULL,
                    correctCount INTEGER NOT NULL,
                    strengthsJson TEXT NOT NULL,
                    weaknessesJson TEXT NOT NULL,
                    skillGapsJson TEXT NOT NULL,
                    generatedAtEpochMs INTEGER NOT NULL,
                    FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_gap_reports_userId ON skill_gap_reports_new(userId)")

            db.execSQL("""
                INSERT INTO skill_gap_reports_new (
                    trackId, userId, domain, subdomain, overallScorePercent, totalQuestions,
                    correctCount, strengthsJson, weaknessesJson, skillGapsJson, generatedAtEpochMs
                )
                SELECT 
                    'track_' || userId || '_default' AS trackId,
                    userId, domain, subdomain, overallScorePercent, totalQuestions,
                    correctCount, strengthsJson, weaknessesJson, skillGapsJson, generatedAtEpochMs
                FROM skill_gap_reports
            """.trimIndent())

            db.execSQL("DROP TABLE skill_gap_reports")
            db.execSQL("ALTER TABLE skill_gap_reports_new RENAME TO skill_gap_reports")

            // STEP 12: Recreate study_tasks with Composite (trackId, userId) FK
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS study_tasks_new (
                    taskId TEXT PRIMARY KEY NOT NULL,
                    trackId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    roadmapStepId TEXT NOT NULL,
                    dayIndex INTEGER NOT NULL,
                    weekIndex INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    topic TEXT NOT NULL,
                    durationMinutes INTEGER NOT NULL,
                    isCompleted INTEGER NOT NULL DEFAULT 0,
                    completedAtEpochMs INTEGER,
                    notes TEXT NOT NULL DEFAULT '',
                    FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_trackId ON study_tasks_new(trackId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_dayIndex ON study_tasks_new(userId, dayIndex)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_isCompleted ON study_tasks_new(userId, isCompleted)")

            db.execSQL("""
                INSERT INTO study_tasks_new (
                    taskId, trackId, userId, roadmapStepId, dayIndex, weekIndex,
                    title, topic, durationMinutes, isCompleted, completedAtEpochMs, notes
                )
                SELECT 
                    taskId,
                    'track_' || userId || '_default' AS trackId,
                    userId, roadmapStepId, dayIndex, weekIndex,
                    title, topic, durationMinutes, isCompleted, completedAtEpochMs, notes
                FROM study_tasks
            """.trimIndent())

            db.execSQL("DROP TABLE study_tasks")
            db.execSQL("ALTER TABLE study_tasks_new RENAME TO study_tasks")

            // STEP 13: Stage chat_messages, Recreate chat_sessions and Recreate chat_messages with Lossless Recovery Policy
            // Capture rowid as backup_rowid to guarantee unique ordering and deterministic duplicate identification
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages_backup AS SELECT rowid AS backup_rowid, * FROM chat_messages")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_sessions_new (
                    sessionId TEXT PRIMARY KEY NOT NULL,
                    trackId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    domain TEXT NOT NULL,
                    title TEXT NOT NULL,
                    isAiEnabled INTEGER NOT NULL DEFAULT 1,
                    createdAtEpochMs INTEGER NOT NULL,
                    updatedAtEpochMs INTEGER NOT NULL,
                    FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_trackId ON chat_sessions_new(trackId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_userId_updatedAtEpochMs ON chat_sessions_new(userId, updatedAtEpochMs)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_chat_sessions_sessionId_userId ON chat_sessions_new(sessionId, userId)")

            db.execSQL("""
                INSERT INTO chat_sessions_new (
                    sessionId, trackId, userId, domain, title, isAiEnabled, createdAtEpochMs, updatedAtEpochMs
                )
                SELECT 
                    sessionId,
                    'track_' || userId || '_default' AS trackId,
                    userId, domain, title, isAiEnabled, createdAtEpochMs, updatedAtEpochMs
                FROM chat_sessions
            """.trimIndent())

            // Child table chat_messages MUST be dropped BEFORE parent table chat_sessions
            db.execSQL("DROP TABLE chat_messages")
            db.execSQL("DROP TABLE chat_sessions")
            db.execSQL("ALTER TABLE chat_sessions_new RENAME TO chat_sessions")

            // Synthesize recovery chat sessions for orphaned messages belonging to known users
            // Using INSERT OR IGNORE to handle collisions if a recovery session already exists
            db.execSQL("""
                INSERT OR IGNORE INTO chat_sessions (
                    sessionId, trackId, userId, domain, title, isAiEnabled, createdAtEpochMs, updatedAtEpochMs
                )
                SELECT 
                    'session_recovered_' || b.userId AS sessionId,
                    'track_' || b.userId || '_default' AS trackId,
                    b.userId,
                    'Recovered Messages' AS domain,
                    'Recovered Chat Session' AS title,
                    0 AS isAiEnabled,
                    MIN(b.timestampEpochMs) AS createdAtEpochMs,
                    MAX(b.timestampEpochMs) AS updatedAtEpochMs
                FROM chat_messages_backup b
                LEFT JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                JOIN learner_profiles lp ON b.userId = lp.userId
                WHERE cs.sessionId IS NULL AND b.userId IS NOT NULL AND b.userId != ''
                GROUP BY b.userId
            """.trimIndent())

            // Create target Room v4 chat_messages table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    messageId TEXT PRIMARY KEY NOT NULL,
                    sessionId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    senderRole TEXT NOT NULL,
                    text TEXT NOT NULL,
                    isAiGenerated INTEGER NOT NULL,
                    promptTokensEstimate INTEGER NOT NULL DEFAULT 0,
                    generatedTokens INTEGER NOT NULL DEFAULT 0,
                    ttftMs INTEGER,
                    tokensPerSec REAL,
                    errorText TEXT,
                    timestampEpochMs INTEGER NOT NULL,
                    FOREIGN KEY (sessionId, userId) REFERENCES chat_sessions(sessionId, userId) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId_timestampEpochMs ON chat_messages(sessionId, timestampEpochMs)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_userId ON chat_messages(userId)")

            // Create audit quarantine archive table for unmatchable or colliding messages
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages_unmatched_archive (
                    messageId TEXT PRIMARY KEY NOT NULL,
                    originalSessionId TEXT NOT NULL,
                    originalUserId TEXT NOT NULL,
                    senderRole TEXT NOT NULL,
                    text TEXT NOT NULL,
                    isAiGenerated INTEGER NOT NULL DEFAULT 0,
                    promptTokensEstimate INTEGER NOT NULL DEFAULT 0,
                    generatedTokens INTEGER NOT NULL DEFAULT 0,
                    ttftMs INTEGER,
                    tokensPerSec REAL,
                    errorText TEXT,
                    timestampEpochMs INTEGER NOT NULL,
                    quarantineReason TEXT NOT NULL,
                    archivedAtEpochMs INTEGER NOT NULL
                )
            """.trimIndent())

            // 1. Process candidate primary messages (first occurrence of each messageId by backup_rowid)
            // 1A. Valid message linked to active session where userId matches session userId OR was blank
            db.execSQL("""
                INSERT INTO chat_messages (
                    messageId, sessionId, userId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs
                )
                SELECT 
                    b.messageId, b.sessionId, cs.userId, b.senderRole, b.text, b.isAiGenerated,
                    b.promptTokensEstimate, b.generatedTokens, b.ttftMs, b.tokensPerSec, b.errorText, b.timestampEpochMs
                FROM chat_messages_backup b
                JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                WHERE b.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                  AND (b.userId = cs.userId OR b.userId IS NULL OR b.userId = '')
            """.trimIndent())

            // 1B. Conflicting session/user ID: message is in session but message.userId != '' and message.userId != session.userId
            // Never silently reassign to an arbitrary learner -> quarantine with audit trail
            db.execSQL("""
                INSERT INTO chat_messages_unmatched_archive (
                    messageId, originalSessionId, originalUserId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs,
                    quarantineReason, archivedAtEpochMs
                )
                SELECT 
                    b.messageId, b.sessionId, b.userId, b.senderRole, b.text, b.isAiGenerated,
                    b.promptTokensEstimate, b.generatedTokens, b.ttftMs, b.tokensPerSec, b.errorText, b.timestampEpochMs,
                    'CONFLICTING_SESSION_USER_ID',
                    strftime('%s', 'now') * 1000
                FROM chat_messages_backup b
                JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                WHERE b.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                  AND b.userId IS NOT NULL AND b.userId != '' AND b.userId != cs.userId
            """.trimIndent())

            // 1C. Orphaned session, but user is known: link to synthesized recovery session
            db.execSQL("""
                INSERT INTO chat_messages (
                    messageId, sessionId, userId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs
                )
                SELECT 
                    b.messageId,
                    'session_recovered_' || b.userId AS sessionId,
                    b.userId,
                    b.senderRole,
                    b.text,
                    b.isAiGenerated,
                    b.promptTokensEstimate,
                    b.generatedTokens,
                    b.ttftMs,
                    b.tokensPerSec,
                    b.errorText,
                    b.timestampEpochMs
                FROM chat_messages_backup b
                LEFT JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                JOIN learner_profiles lp ON b.userId = lp.userId
                WHERE b.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                  AND cs.sessionId IS NULL
                  AND b.userId IS NOT NULL AND b.userId != ''
            """.trimIndent())

            // 1D. Orphaned session with unknown user (user not in learner_profiles, but userId not blank)
            db.execSQL("""
                INSERT INTO chat_messages_unmatched_archive (
                    messageId, originalSessionId, originalUserId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs,
                    quarantineReason, archivedAtEpochMs
                )
                SELECT 
                    b.messageId, b.sessionId, b.userId, b.senderRole, b.text, b.isAiGenerated,
                    b.promptTokensEstimate, b.generatedTokens, b.ttftMs, b.tokensPerSec, b.errorText, b.timestampEpochMs,
                    'ORPHANED_UNKNOWN_USER',
                    strftime('%s', 'now') * 1000
                FROM chat_messages_backup b
                LEFT JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                LEFT JOIN learner_profiles lp ON b.userId = lp.userId
                WHERE b.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                  AND cs.sessionId IS NULL
                  AND lp.userId IS NULL
                  AND b.userId IS NOT NULL AND b.userId != ''
            """.trimIndent())

            // 1E. Blank user ID with missing session (neither session nor learner identity exists)
            db.execSQL("""
                INSERT INTO chat_messages_unmatched_archive (
                    messageId, originalSessionId, originalUserId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs,
                    quarantineReason, archivedAtEpochMs
                )
                SELECT 
                    b.messageId, b.sessionId, '', b.senderRole, b.text, b.isAiGenerated,
                    b.promptTokensEstimate, b.generatedTokens, b.ttftMs, b.tokensPerSec, b.errorText, b.timestampEpochMs,
                    'BLANK_USER_AND_MISSING_SESSION',
                    strftime('%s', 'now') * 1000
                FROM chat_messages_backup b
                LEFT JOIN chat_sessions cs ON b.sessionId = cs.sessionId
                WHERE b.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                  AND cs.sessionId IS NULL
                  AND (b.userId IS NULL OR b.userId = '')
            """.trimIndent())

            // 2. Duplicate Message IDs (backup_rowid > MIN(backup_rowid) for that messageId)
            // Differentiate identical duplicate vs colliding duplicate with different content
            // Every duplicate gets a unique audit identity to prevent PK collisions in the archive
            db.execSQL("""
                INSERT INTO chat_messages_unmatched_archive (
                    messageId, originalSessionId, originalUserId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs,
                    quarantineReason, archivedAtEpochMs
                )
                SELECT 
                    b.messageId || '_dup_' || b.backup_rowid AS messageId,
                    b.sessionId,
                    COALESCE(b.userId, ''),
                    b.senderRole,
                    b.text,
                    b.isAiGenerated,
                    b.promptTokensEstimate,
                    b.generatedTokens,
                    b.ttftMs,
                    b.tokensPerSec,
                    b.errorText,
                    b.timestampEpochMs,
                    CASE 
                        WHEN b.text = first_msg.text THEN 'DUPLICATE_MESSAGE_ID_IDENTICAL'
                        ELSE 'DUPLICATE_MESSAGE_ID_COLLISION'
                    END AS quarantineReason,
                    strftime('%s', 'now') * 1000 AS archivedAtEpochMs
                FROM chat_messages_backup b
                JOIN chat_messages_backup first_msg 
                  ON first_msg.backup_rowid = (SELECT MIN(b2.backup_rowid) FROM chat_messages_backup b2 WHERE b2.messageId = b.messageId)
                WHERE b.backup_rowid > first_msg.backup_rowid
            """.trimIndent())

            db.execSQL("DROP TABLE chat_messages_backup")
        }
    }
}

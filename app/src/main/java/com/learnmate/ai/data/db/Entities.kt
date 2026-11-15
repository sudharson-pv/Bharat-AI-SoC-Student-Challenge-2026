package com.learnmate.ai.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 1. Universal Learner Profile Entity
 */
@Entity(tableName = "learner_profiles")
data class LearnerProfileEntity(
    @PrimaryKey
    val userId: String,
    val name: String,
    val targetDomain: String,
    val subdomain: String,
    val goal: String,
    val targetRole: String,
    val previousExperience: String,
    val currentLevel: String, // "Beginner", "Intermediate", "Advanced"
    val audienceLevel: String, // "Foundational", "Academic", "Industry Specialist"
    val dailyStudyMinutes: Int = 60,
    val weeklyStudyDays: Int = 5,
    val currentTopic: String = "",
    val onboardingCompleted: Boolean = false,
    val assessmentCompleted: Boolean = false,
    val isDemoAccount: Boolean = false,
    @ColumnInfo(defaultValue = "'en'")
    val preferredLanguageCode: String = "en",
    @ColumnInfo(defaultValue = "'UNDERGRADUATE'")
    val defaultEducationLevel: String = "UNDERGRADUATE",
    @ColumnInfo(defaultValue = "'COLLEGE_STUDENT'")
    val defaultLearnerCategory: String = "COLLEGE_STUDENT",
    @ColumnInfo(defaultValue = "0")
    val isMinor: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val guardianConsentGiven: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * 2. Content Pack Entity
 */
@Entity(tableName = "content_packs")
data class ContentPackEntity(
    @PrimaryKey
    val packId: String,
    val title: String,
    val version: String,
    val minAppVersion: String,
    val category: String,
    @ColumnInfo(defaultValue = "'en'")
    val primaryLanguage: String = "en",
    @ColumnInfo(defaultValue = "'[\"en\"]'")
    val supportedLanguagesJson: String = "[\"en\"]",
    val authorOrProvider: String,
    val provenanceNotice: String,
    val checksumSha256: String,
    @ColumnInfo(defaultValue = "1")
    val isInstalled: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isDeprecated: Boolean = false,
    val deprecationNotice: String? = null,
    val installedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * 3. Normalized Content Pack Dependencies Entity (Dual Foreign Keys)
 */
@Entity(
    tableName = "pack_dependencies",
    primaryKeys = ["packId", "dependencyPackId"],
    indices = [
        Index(value = ["dependencyPackId"], name = "index_pack_dependencies_dependencyPackId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ContentPackEntity::class,
            parentColumns = ["packId"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = ContentPackEntity::class,
            parentColumns = ["packId"],
            childColumns = ["dependencyPackId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class PackDependencyEntity(
    val packId: String,
    val dependencyPackId: String,
    val versionConstraint: String
)

/**
 * 4. Normalized Curriculum Node Entity
 */
@Entity(
    tableName = "curriculum_nodes",
    indices = [
        Index(value = ["parentNodeId"], name = "index_curriculum_nodes_parentNodeId"),
        Index(value = ["packId"], name = "index_curriculum_nodes_packId"),
        Index(value = ["nodeType"], name = "index_curriculum_nodes_type")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ContentPackEntity::class,
            parentColumns = ["packId"],
            childColumns = ["packId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["parentNodeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class CurriculumNodeEntity(
    @PrimaryKey
    val nodeId: String,
    val parentNodeId: String?,
    val packId: String,
    val nodeType: String,
    val code: String,
    val title: String,
    @ColumnInfo(defaultValue = "''")
    val description: String = "",
    @ColumnInfo(defaultValue = "30")
    val estimatedMinutes: Int = 30,
    @ColumnInfo(defaultValue = "0")
    val orderIndex: Int = 0
)

/**
 * 5. Learner Track Entity (Multi-Track Root Aggregate)
 */
@Entity(
    tableName = "learner_tracks",
    indices = [
        Index(value = ["userId"], name = "index_learner_tracks_userId"),
        Index(value = ["userId", "isTrackActive"], name = "index_learner_tracks_active"),
        Index(value = ["targetCurriculumNodeId"], name = "index_learner_tracks_targetNode"),
        Index(value = ["trackId", "userId"], unique = true, name = "index_learner_tracks_trackId_userId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LearnerProfileEntity::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["targetCurriculumNodeId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class LearnerTrackEntity(
    @PrimaryKey
    val trackId: String,
    val userId: String,
    val title: String,
    @ColumnInfo(defaultValue = "'ACADEMIC'")
    val learningMode: String = "ACADEMIC",
    val targetCurriculumNodeId: String?,
    val targetExamName: String? = null,
    val targetExamDateEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "60")
    val dailyCommitmentMinutes: Int = 60,
    @ColumnInfo(defaultValue = "1")
    val isTrackActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastAccessedEpochMs: Long = System.currentTimeMillis()
)

/**
 * 6. Node Prerequisite Entity
 */
@Entity(
    tableName = "node_prerequisites",
    indices = [
        Index(value = ["targetNodeId"], name = "index_prereqs_target"),
        Index(value = ["requiredNodeId"], name = "index_prereqs_required")
    ],
    foreignKeys = [
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["targetNodeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["requiredNodeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class NodePrerequisiteEntity(
    @PrimaryKey
    val prerequisiteId: String,
    val targetNodeId: String,
    val requiredNodeId: String,
    @ColumnInfo(defaultValue = "'MANDATORY'")
    val requirementType: String = "MANDATORY"
)

/**
 * 7. Curriculum Resource Entity
 */
@Entity(
    tableName = "curriculum_resources",
    indices = [
        Index(value = ["nodeId"], name = "index_curriculum_resources_nodeId"),
        Index(value = ["nodeId", "languageCode"], name = "index_curriculum_resources_lang")
    ],
    foreignKeys = [
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class CurriculumResourceEntity(
    @PrimaryKey
    val resourceId: String,
    val nodeId: String,
    @ColumnInfo(defaultValue = "'en'")
    val languageCode: String = "en",
    val verifiedExplanation: String,
    val referenceTitle: String,
    val referenceAuthor: String,
    val referenceEdition: String,
    val referenceChapter: String,
    val referenceSection: String,
    val referenceIdentifier: String,
    @ColumnInfo(defaultValue = "'[]'")
    val coreKeywordsJson: String = "[]"
)

/**
 * 8. Assessment Blueprint Entity
 */
@Entity(tableName = "assessment_blueprints")
data class AssessmentBlueprintEntity(
    @PrimaryKey
    val blueprintId: String,
    val title: String,
    val assessmentType: String,
    val targetNodeId: String,
    val totalQuestions: Int,
    val timeLimitMinutes: Int?,
    @ColumnInfo(defaultValue = "1.0")
    val marksPerCorrect: Double = 1.0,
    @ColumnInfo(defaultValue = "0.0")
    val negativeMarksPerIncorrect: Double = 0.0,
    @ColumnInfo(defaultValue = "50.0")
    val passPercentageThreshold: Double = 50.0,
    @ColumnInfo(defaultValue = "0")
    val allowNegativeTotal: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val shuffleQuestions: Boolean = true,
    @ColumnInfo(defaultValue = "0")
    val immediateFeedback: Boolean = false,
    @ColumnInfo(defaultValue = "'2.0'")
    val scoringSchemeVersion: String = "2.0"
)

/**
 * 9. Assessment Question v2 Entity
 */
@Entity(
    tableName = "assessment_questions_v2",
    indices = [
        Index(value = ["nodeId"], name = "index_assessment_questions_v2_nodeId"),
        Index(value = ["questionFormat"], name = "index_assessment_questions_v2_format")
    ],
    foreignKeys = [
        ForeignKey(
            entity = CurriculumNodeEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["nodeId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class AssessmentQuestionV2Entity(
    @PrimaryKey
    val questionId: String,
    val nodeId: String,
    val blueprintId: String?,
    @ColumnInfo(defaultValue = "'General'")
    val sectionName: String = "General",
    @ColumnInfo(defaultValue = "'SINGLE_CHOICE'")
    val questionFormat: String = "SINGLE_CHOICE",
    val questionText: String,
    val optionsJson: String,
    val correctIndicesJson: String,
    val numericalLowerBound: Double? = null,
    val numericalUpperBound: Double? = null,
    @ColumnInfo(defaultValue = "1.0")
    val marks: Double = 1.0,
    @ColumnInfo(defaultValue = "0.0")
    val negativePenalty: Double = 0.0,
    @ColumnInfo(defaultValue = "'NONE'")
    val partialMarkingScheme: String = "NONE",
    val verifiedExplanation: String,
    @ColumnInfo(defaultValue = "'INTERMEDIATE'")
    val difficultyLevel: String = "INTERMEDIATE",
    @ColumnInfo(defaultValue = "''")
    val examSourceTag: String = "",
    @ColumnInfo(defaultValue = "'en'")
    val languageCode: String = "en"
)

/**
 * 10. Track-Scoped Personalised Roadmap Entity
 */
@Entity(
    tableName = "personalised_roadmaps",
    indices = [
        Index(value = ["userId"], name = "index_personalised_roadmaps_userId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LearnerTrackEntity::class,
            parentColumns = ["trackId", "userId"],
            childColumns = ["trackId", "userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class PersonalisedRoadmapEntity(
    @PrimaryKey
    val trackId: String,
    val userId: String,
    val domain: String,
    val subdomain: String,
    val goal: String,
    val targetRole: String,
    val totalSteps: Int,
    val stepsJson: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * 11. Track-Scoped Skill Gap Report Entity
 */
@Entity(
    tableName = "skill_gap_reports",
    indices = [
        Index(value = ["userId"], name = "index_skill_gap_reports_userId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LearnerTrackEntity::class,
            parentColumns = ["trackId", "userId"],
            childColumns = ["trackId", "userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class SkillGapReportEntity(
    @PrimaryKey
    val trackId: String,
    val userId: String,
    val domain: String,
    val subdomain: String,
    val overallScorePercent: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val strengthsJson: String,
    val weaknessesJson: String,
    val skillGapsJson: String,
    val generatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * 12. Track-Scoped Daily Study Task Entity
 */
@Entity(
    tableName = "study_tasks",
    indices = [
        Index(value = ["trackId"], name = "index_study_tasks_trackId"),
        Index(value = ["userId", "dayIndex"], name = "index_study_tasks_userId_dayIndex"),
        Index(value = ["userId", "isCompleted"], name = "index_study_tasks_userId_isCompleted")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LearnerTrackEntity::class,
            parentColumns = ["trackId", "userId"],
            childColumns = ["trackId", "userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class StudyTaskEntity(
    @PrimaryKey
    val taskId: String,
    val trackId: String,
    val userId: String,
    val roadmapStepId: String,
    val dayIndex: Int,
    val weekIndex: Int,
    val title: String,
    val topic: String,
    val durationMinutes: Int,
    @ColumnInfo(defaultValue = "0")
    val isCompleted: Boolean = false,
    val completedAtEpochMs: Long? = null,
    @ColumnInfo(defaultValue = "''")
    val notes: String = ""
)

/**
 * 13. Track-Scoped Chat Session Entity
 */
@Entity(
    tableName = "chat_sessions",
    indices = [
        Index(value = ["trackId"], name = "index_chat_sessions_trackId"),
        Index(value = ["userId", "updatedAtEpochMs"], name = "index_chat_sessions_userId_updatedAtEpochMs"),
        Index(value = ["sessionId", "userId"], unique = true, name = "index_chat_sessions_sessionId_userId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = LearnerTrackEntity::class,
            parentColumns = ["trackId", "userId"],
            childColumns = ["trackId", "userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class ChatSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val trackId: String,
    val userId: String,
    val domain: String,
    val title: String,
    @ColumnInfo(defaultValue = "1")
    val isAiEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * 14. Chat Message Entity (Composite Session-User FK enforcing Message Ownership)
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["sessionId", "timestampEpochMs"], name = "index_chat_messages_sessionId_timestampEpochMs"),
        Index(value = ["userId"], name = "index_chat_messages_userId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["sessionId", "userId"],
            childColumns = ["sessionId", "userId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION
        )
    ]
)
data class ChatMessageEntity(
    @PrimaryKey
    val messageId: String,
    val sessionId: String,
    val userId: String,
    val senderRole: String, // "USER", "TUTOR", "SYSTEM"
    val text: String,
    val isAiGenerated: Boolean,
    @ColumnInfo(defaultValue = "0")
    val promptTokensEstimate: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val generatedTokens: Int = 0,
    val ttftMs: Long? = null,
    val tokensPerSec: Double? = null,
    val errorText: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

/**
 * 15. Verified subject and topic taxonomy entity (Preserved Gate 4 baseline).
 */
@Entity(
    tableName = "verified_topics",
    indices = [Index(value = ["domain", "subdomain"])]
)
data class VerifiedTopicEntity(
    @PrimaryKey
    val topicId: String,
    val domain: String,
    val subdomain: String,
    val title: String,
    val description: String,
    val verifiedExplanation: String,
    val coreKeywordsJson: String = "[]",
    val prerequisiteTopicIdsJson: String = "[]",
    val estimatedMinutes: Int = 30,
    val referenceTitle: String = "",
    val referenceAuthor: String = "",
    val referenceEdition: String = "",
    val referenceChapter: String = "",
    val referenceSection: String = "",
    val referenceIdentifier: String = ""
)

/**
 * 16. Diagnostic assessment question entity with validated answer keys (Preserved Gate 4 baseline).
 */
@Entity(
    tableName = "assessment_questions",
    indices = [Index(value = ["domain", "topic"])]
)
data class AssessmentQuestionEntity(
    @PrimaryKey
    val questionId: String,
    val domain: String,
    val subdomain: String,
    val topic: String,
    val questionText: String,
    val optionsJson: String,
    val correctIndex: Int,
    val verifiedExplanation: String,
    val difficultyLevel: String = "Intermediate"
)

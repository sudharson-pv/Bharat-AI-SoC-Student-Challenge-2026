package com.learnmate.ai.domain.lifecycle

import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import java.io.File

/**
 * Gate 6.9: Learner Data Portability, Privacy & Lifecycle Domain Models.
 *
 * Implements:
 * 1. Documented backup manifest and unencrypted snapshot payload definitions.
 * 2. Authenticated backup & restore result sum-types.
 * 3. Transparent data inventory and storage usage audit reporting.
 * 4. Human-readable and JSON export containers.
 */

data class LearnerBackupManifest(
    val backupId: String,
    val formatVersion: Int = 2,
    val roomSchemaVersion: Int = 4,
    val appVersion: String = "1.0.0",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val userId: String,
    val userName: String,
    val targetDomain: String,
    val tracksCount: Int,
    val completedTopicsCount: Int,
    val assessmentAttemptsCount: Int,
    val spacedRepetitionItemsCount: Int,
    val checksumSha256: String,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val keyDerivation: String = "PBKDF2WithHmacSHA256",
    val pbkdf2Iterations: Int = 210_000
)

data class LearnerBackupPayload(
    val manifest: LearnerBackupManifest,
    val profile: LearnerProfile,
    val tracks: List<LearningTrack>,
    val roadmaps: List<PersonalisedRoadmap> = emptyList(),
    val skillGapReports: List<SkillGapReport> = emptyList(),
    val studyTasks: List<StudyTask> = emptyList(),
    val topicProgress: List<TrackTopicProgress> = emptyList(),
    val assessmentAttempts: List<AssessmentAttemptRecord> = emptyList(),
    val spacedRepetitionItems: List<SpacedRepetitionItem> = emptyList(),
    val chatSessions: List<ChatSession> = emptyList(),
    val chatMessages: List<ChatMessage> = emptyList(),
    val userPreferences: Map<String, String> = emptyMap()
)

sealed class BackupResult {
    data class Success(
        val backupFile: File,
        val manifest: LearnerBackupManifest,
        val fileSizeBytes: Long
    ) : BackupResult()

    data class Failure(
        val errorCode: String,
        val message: String,
        val cause: Throwable? = null
    ) : BackupResult()
}

sealed class RestoreResult {
    data class Success(
        val manifest: LearnerBackupManifest,
        val restoredTracksCount: Int,
        val restoredAttemptsCount: Int,
        val restoredSrsItemsCount: Int
    ) : RestoreResult()

    data class ConfirmationRequired(
        val existingUserId: String,
        val manifest: LearnerBackupManifest,
        val message: String = "Existing learner data detected for user '$existingUserId'. Explicit confirmation required to overwrite."
    ) : RestoreResult()

    data class Failure(
        val errorCode: String,
        val message: String,
        val cause: Throwable? = null
    ) : RestoreResult()
}

data class DataInventoryItem(
    val category: String,
    val dataElements: List<String>,
    val storageLocation: String,
    val sensitivityLevel: String, // "LOCAL_USER_IDENTIFIER", "EDUCATIONAL_METRIC", "LOCAL_PREFERENCE"
    val retentionPolicy: String,
    val userDeletable: Boolean = true,
    val exportedInBackup: Boolean = true
)

data class StorageUsageReport(
    val databaseSizeBytes: Long,
    val installedContentPacksBytes: Long,
    val backupsSizeBytes: Long,
    val stagingAndTempBytes: Long,
    val totalAppStorageBytes: Long,
    val installedPacksCount: Int,
    val activeTracksCount: Int,
    val orphanedFilesPurgedCount: Int = 0
)

data class StorageCleanupResult(
    val purgedBytes: Long,
    val purgedFilesCount: Int,
    val details: List<String>
)

data class LearnerDataExport(
    val exportTimestampEpochMs: Long,
    val userId: String,
    val profile: LearnerProfile,
    val tracks: List<LearningTrack>,
    val topicProgress: List<TrackTopicProgress>,
    val assessmentAttempts: List<AssessmentAttemptRecord>,
    val spacedRepetitionItems: List<SpacedRepetitionItem>,
    val analyticsSummary: Map<String, Any>
)


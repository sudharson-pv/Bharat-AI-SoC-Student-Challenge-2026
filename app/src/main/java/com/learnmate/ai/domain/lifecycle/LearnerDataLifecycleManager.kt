package com.learnmate.ai.domain.lifecycle

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.learnmate.ai.data.db.LearnMateDatabase
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Gate 6.9: Learner Data Portability, Privacy & Lifecycle Management Engine.
 *
 * Implements:
 * 1. Authenticated offline-first backup creation using AES-256-GCM + PBKDF2.
 * 2. Controlled, fail-closed restore with integrity verification and overwrite protection.
 * 3. Human-readable (Markdown) and machine-readable (JSON) portable exports.
 * 4. User-directed cascade erasure of profiles and tracks honoring foreign key constraints.
 * 5. On-device storage usage audit and orphaned temporary file cleanup.
 * 6. Explicit privacy data inventory and non-cloud local persistence disclosures.
 */
class LearnerDataLifecycleManager(
    private val learningRepository: LearningRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── 1. Authenticated Encrypted Backup ─────────────────────────────────────

    suspend fun createBackup(
        userId: String,
        passphrase: CharArray,
        targetFile: File,
        userPreferences: Map<String, String> = emptyMap(),
        formatVersion: Byte = LearnerDataSecurity.CURRENT_FORMAT_VERSION
    ): BackupResult = withContext(ioDispatcher) {
        try {
            if (passphrase.isEmpty()) {
                return@withContext BackupResult.Failure(
                    "EMPTY_PASSPHRASE",
                    "Passphrase must not be empty"
                )
            }

            val profile = learningRepository.getProfile(userId)
                ?: return@withContext BackupResult.Failure(
                    "PROFILE_NOT_FOUND",
                    "No learner profile found for user: $userId"
                )

            val tracks = learningRepository.getTracksForUser(userId)
            val roadmaps = mutableListOf<PersonalisedRoadmap>()
            val skillGaps = mutableListOf<SkillGapReport>()
            val studyTasks = mutableListOf<StudyTask>()
            val attempts = mutableListOf<AssessmentAttemptRecord>()
            val srsItems = mutableListOf<SpacedRepetitionItem>()
            val topicProgress = mutableListOf<TrackTopicProgress>()
            val chatSessions = mutableListOf<ChatSession>()
            val chatMessages = mutableListOf<ChatMessage>()

            // Aggregate data across all tracks owned by the user
            for (track in tracks) {
                learningRepository.getRoadmapForTrack(track.trackId)?.let { roadmaps.add(it) }
                learningRepository.getSkillGapReportForTrack(track.trackId)?.let { skillGaps.add(it) }
                studyTasks.addAll(learningRepository.getStudyTasksForTrack(track.trackId))
                attempts.addAll(learningRepository.getAllAssessmentAttemptsForTrack(track.trackId, userId))
                srsItems.addAll(learningRepository.getSpacedRepetitionQueue(track.trackId, userId))
                topicProgress.addAll(learningRepository.getAllTopicProgressForTrack(track.trackId, userId))

                val sessions = learningRepository.getChatSessionsForTrack(track.trackId)
                chatSessions.addAll(sessions)
                for (s in sessions) {
                    chatMessages.addAll(learningRepository.getMessagesForSession(s.sessionId))
                }
            }

            // Also check global/unassigned profile artifacts
            learningRepository.getRoadmap(userId)?.let { if (it !in roadmaps) roadmaps.add(it) }
            learningRepository.getSkillGapReport(userId)?.let { if (it !in skillGaps) skillGaps.add(it) }
            val globalTasks = learningRepository.getStudyTasks(userId)
            for (gt in globalTasks) {
                if (studyTasks.none { it.id == gt.id }) studyTasks.add(gt)
            }
            val globalSessions = learningRepository.getChatSessions(userId)
            for (gs in globalSessions) {
                if (chatSessions.none { it.sessionId == gs.sessionId }) {
                    chatSessions.add(gs)
                    chatMessages.addAll(learningRepository.getMessagesForSession(gs.sessionId))
                }
            }

            // Assemble raw snapshot for checksum computation
            val completedTopicsCount = topicProgress.count { it.status == LearningProgressStatus.COMPLETED }
            val rawSnapshotForChecksum = mapOf(
                "userId" to userId,
                "tracks" to tracks,
                "attempts" to attempts,
                "srs" to srsItems,
                "progress" to topicProgress
            )
            val checksum = LearnerDataSecurity.computeSha256(gson.toJson(rawSnapshotForChecksum))

            val iterations = if (formatVersion == LearnerDataSecurity.FORMAT_VERSION_1) {
                LearnerDataSecurity.PBKDF2_ITERATIONS_V1
            } else {
                LearnerDataSecurity.PBKDF2_ITERATIONS_V2
            }

            val manifest = LearnerBackupManifest(
                backupId = "backup_${UUID.randomUUID().toString().take(12)}",
                formatVersion = formatVersion.toInt(),
                roomSchemaVersion = 4,
                appVersion = "1.0.0",
                createdAtEpochMs = System.currentTimeMillis(),
                userId = userId,
                userName = profile.name,
                targetDomain = profile.targetDomain,
                tracksCount = tracks.size,
                completedTopicsCount = completedTopicsCount,
                assessmentAttemptsCount = attempts.size,
                spacedRepetitionItemsCount = srsItems.size,
                checksumSha256 = checksum,
                pbkdf2Iterations = iterations
            )

            val payload = LearnerBackupPayload(
                manifest = manifest,
                profile = profile,
                tracks = tracks,
                roadmaps = roadmaps,
                skillGapReports = skillGaps,
                studyTasks = studyTasks,
                topicProgress = topicProgress,
                assessmentAttempts = attempts,
                spacedRepetitionItems = srsItems,
                chatSessions = chatSessions,
                chatMessages = chatMessages,
                userPreferences = userPreferences
            )

            val payloadJson = gson.toJson(payload)
            val encryptResult = LearnerDataSecurity.encryptPayload(
                payloadJson = payloadJson,
                passphrase = passphrase,
                targetFile = targetFile,
                formatVersion = formatVersion,
                iterations = iterations
            )

            encryptResult.fold(
                onSuccess = { fileSize ->
                    BackupResult.Success(targetFile, manifest, fileSize)
                },
                onFailure = { error ->
                    BackupResult.Failure("ENCRYPTION_FAILED", error.message ?: "Failed to encrypt backup", error)
                }
            )
        } catch (e: Exception) {
            BackupResult.Failure("BACKUP_FAILED", e.message ?: "Unexpected error during backup creation", e)
        }
    }

    // ── 2. Authenticated Restore & Recovery ───────────────────────────────────

    suspend fun restoreBackup(
        sourceFile: File,
        passphrase: CharArray,
        confirmOverwrite: Boolean = false,
        targetUserId: String? = null
    ): RestoreResult = withContext(ioDispatcher) {
        try {
            // 1. Authenticated Decryption
            val decryptResult = LearnerDataSecurity.decryptPayload(sourceFile, passphrase)
            val payloadJson = decryptResult.getOrElse { error ->
                return@withContext RestoreResult.Failure(
                    "AUTHENTICATION_FAILED",
                    error.message ?: "Authentication tag verification failed or wrong passphrase",
                    error
                )
            }

            // 2. Parse Payload JSON
            val payload = try {
                gson.fromJson(payloadJson, LearnerBackupPayload::class.java)
            } catch (e: Exception) {
                return@withContext RestoreResult.Failure(
                    "CORRUPT_PAYLOAD_STRUCTURE",
                    "Backup payload is not valid JSON",
                    e
                )
            }

            // 3. Validate Schema Version Compatibility
            if (payload.manifest.roomSchemaVersion > 4) {
                return@withContext RestoreResult.Failure(
                    "INCOMPATIBLE_SCHEMA_VERSION",
                    "Backup requires database schema v${payload.manifest.roomSchemaVersion}, current supported schema is v4"
                )
            }

            // 4. Validate SHA-256 Checksum Integrity
            val rawSnapshotForChecksum = mapOf(
                "userId" to payload.profile.userId,
                "tracks" to payload.tracks,
                "attempts" to payload.assessmentAttempts,
                "srs" to payload.spacedRepetitionItems,
                "progress" to payload.topicProgress
            )
            val computedChecksum = LearnerDataSecurity.computeSha256(gson.toJson(rawSnapshotForChecksum))
            if (computedChecksum != payload.manifest.checksumSha256) {
                return@withContext RestoreResult.Failure(
                    "CHECKSUM_MISMATCH",
                    "Backup payload checksum does not match manifest header"
                )
            }

            // 5. Overwrite Protection & Confirmation Gate
            val effectiveUserId = targetUserId ?: payload.profile.userId
            val existingProfile = learningRepository.getProfile(effectiveUserId)
            if (existingProfile != null && !confirmOverwrite) {
                return@withContext RestoreResult.ConfirmationRequired(
                    existingUserId = effectiveUserId,
                    manifest = payload.manifest
                )
            }

            // 6. Snapshot existing state for atomic rollback protection
            val rollbackSnapshot = if (existingProfile != null) {
                learningRepository.createLearnerSnapshot(effectiveUserId)
            } else null

            // 7. Execute Transactional Restore with Fail-Closed Rollback
            try {
                val importSuccess = learningRepository.importLearnerDataSnapshot(payload, targetUserId)
                if (!importSuccess) {
                    rollbackSnapshot?.let { learningRepository.importLearnerDataSnapshot(it, effectiveUserId) }
                    return@withContext RestoreResult.Failure(
                        "IMPORT_FAILED_ROLLED_BACK",
                        "Failed to import learner records into database. Original profile state was safely rolled back."
                    )
                }
            } catch (e: Exception) {
                rollbackSnapshot?.let { learningRepository.importLearnerDataSnapshot(it, effectiveUserId) }
                return@withContext RestoreResult.Failure(
                    "RESTORE_TRANSACTION_FAILED_ROLLED_BACK",
                    "Database restore failed unexpectedly (${e.message}). Original profile state was safely rolled back.",
                    e
                )
            }

            RestoreResult.Success(
                manifest = payload.manifest,
                restoredTracksCount = payload.tracks.size,
                restoredAttemptsCount = payload.assessmentAttempts.size,
                restoredSrsItemsCount = payload.spacedRepetitionItems.size
            )
        } catch (e: Exception) {
            RestoreResult.Failure("RESTORE_FAILED", e.message ?: "Unexpected error during restore", e)
        }
    }

    // ── 3. Data Export (Portable JSON & Markdown Summary) ─────────────────────

    suspend fun exportLearnerDataJson(
        userId: String,
        targetFile: File
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val profile = learningRepository.getProfile(userId)
                ?: return@withContext Result.failure(IllegalArgumentException("Profile not found for user: $userId"))

            val tracks = learningRepository.getTracksForUser(userId)
            val topicProgress = mutableListOf<TrackTopicProgress>()
            val attempts = mutableListOf<AssessmentAttemptRecord>()
            val srsItems = mutableListOf<SpacedRepetitionItem>()

            for (track in tracks) {
                topicProgress.addAll(learningRepository.getAllTopicProgressForTrack(track.trackId, userId))
                attempts.addAll(learningRepository.getAllAssessmentAttemptsForTrack(track.trackId, userId))
                srsItems.addAll(learningRepository.getSpacedRepetitionQueue(track.trackId, userId))
            }

            val analytics = mapOf(
                "totalTracks" to tracks.size,
                "completedTopics" to topicProgress.count { it.status == LearningProgressStatus.COMPLETED },
                "totalAssessments" to attempts.size,
                "averageScorePercent" to if (attempts.isNotEmpty()) attempts.map { it.scorePercent }.average() else 0.0,
                "activeSpacedRepetitionQueue" to srsItems.size
            )

            val export = LearnerDataExport(
                exportTimestampEpochMs = System.currentTimeMillis(),
                userId = userId,
                profile = profile,
                tracks = tracks,
                topicProgress = topicProgress,
                assessmentAttempts = attempts,
                spacedRepetitionItems = srsItems,
                analyticsSummary = analytics
            )

            targetFile.parentFile?.mkdirs()
            targetFile.writeText(gson.toJson(export), Charsets.UTF_8)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportProgressSummaryMarkdown(
        userId: String,
        targetFile: File
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val profile = learningRepository.getProfile(userId)
                ?: return@withContext Result.failure(IllegalArgumentException("Profile not found for user: $userId"))

            val tracks = learningRepository.getTracksForUser(userId)
            val topicProgress = mutableListOf<TrackTopicProgress>()
            val attempts = mutableListOf<AssessmentAttemptRecord>()
            val srsItems = mutableListOf<SpacedRepetitionItem>()

            for (track in tracks) {
                topicProgress.addAll(learningRepository.getAllTopicProgressForTrack(track.trackId, userId))
                attempts.addAll(learningRepository.getAllAssessmentAttemptsForTrack(track.trackId, userId))
                srsItems.addAll(learningRepository.getSpacedRepetitionQueue(track.trackId, userId))
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val exportDate = dateFormat.format(Date())

            val md = buildString {
                appendLine("# LearnMate AI — Learner Progress & Data Export Report")
                appendLine()
                appendLine("**Export Date:** $exportDate  ")
                appendLine("**Learner ID:** `${profile.userId}`  ")
                appendLine("**Name:** ${profile.name}  ")
                appendLine("**Primary Domain:** ${profile.targetDomain}  ")
                appendLine("**Academic Background:** ${profile.educationLevel.displayName}  ")
                appendLine("**Learner Category:** ${profile.learnerCategory.displayName}  ")
                appendLine("**Daily Study Commitment:** ${profile.dailyStudyMinutes} minutes/day  ")
                appendLine("**Storage Mode:** 100% On-Device Offline (Zero Cloud Sync)  ")
                appendLine()
                appendLine("---")
                appendLine()

                // Section 1: Tracks
                appendLine("## 1. Learning Tracks (${tracks.size})")
                if (tracks.isEmpty()) {
                    appendLine("*No learning tracks configured.*")
                } else {
                    appendLine("| Track Title | Mode | Active | Created | Target Curriculum |")
                    appendLine("| :--- | :--- | :---: | :--- | :--- |")
                    for (t in tracks) {
                        val createdStr = dateFormat.format(Date(t.createdAtEpochMs))
                        appendLine("| ${t.title} | ${t.learningMode.name} | ${if (t.isTrackActive) "YES" else "No"} | $createdStr | ${t.targetCurriculumNodeId ?: "General"} |")
                    }
                }
                appendLine()

                // Section 2: Topic Progress
                appendLine("## 2. Topic Progress & Study Status (${topicProgress.size})")
                if (topicProgress.isEmpty()) {
                    appendLine("*No topic activity recorded.*")
                } else {
                    appendLine("| Node ID | Status | Score | Last Accessed |")
                    appendLine("| :--- | :--- | :---: | :--- |")
                    for (tp in topicProgress) {
                        val accessStr = dateFormat.format(Date(tp.lastAccessedEpochMs))
                        appendLine("| `${tp.nodeId}` | ${tp.status.name} | ${tp.scorePercent?.toInt()?.let { "$it%" } ?: "N/A"} | $accessStr |")
                    }
                }
                appendLine()

                // Section 3: Assessment History
                appendLine("## 3. Assessment History (${attempts.size})")
                if (attempts.isEmpty()) {
                    appendLine("*No diagnostic assessments attempted.*")
                } else {
                    appendLine("| Attempt ID | Topic | Score | Questions | Date |")
                    appendLine("| :--- | :--- | :---: | :---: | :--- |")
                    for (att in attempts.sortedByDescending { it.attemptEpochMs }) {
                        val attDate = dateFormat.format(Date(att.attemptEpochMs))
                        appendLine("| `${att.attemptId}` | ${att.topicTitle} | **${att.scorePercent.toInt()}%** | ${att.correctCount}/${att.totalQuestions} | $attDate |")
                    }
                }
                appendLine()

                // Section 4: Spaced Repetition Queue
                appendLine("## 4. Spaced Repetition Queue (${srsItems.size})")
                if (srsItems.isEmpty()) {
                    appendLine("*No topics currently scheduled for spaced revision.*")
                } else {
                    appendLine("| Topic | Repetition # | Interval | State | Due Date |")
                    appendLine("| :--- | :---: | :---: | :--- | :--- |")
                    for (s in srsItems.sortedBy { it.dueEpochMs }) {
                        val dueDate = dateFormat.format(Date(s.dueEpochMs))
                        appendLine("| ${s.topicTitle} | ${s.repetitionNumber} | ${s.intervalDays}d | ${s.scheduleState.name} | $dueDate |")
                    }
                }
                appendLine()

                // Section 5: Data Privacy Notice
                appendLine("---")
                appendLine("## 5. Privacy & Storage Notice")
                appendLine("This progress summary was generated locally from on-device Room v4 SQLite storage.")
                appendLine("LearnMate AI maintains zero internet permissions and transmits no telemetry to external servers.")
                appendLine("All learning analytics, revision intervals, and assessment scores are strictly private to this device.")
            }

            targetFile.parentFile?.mkdirs()
            targetFile.writeText(md, Charsets.UTF_8)
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 4. Data Erasure & Retention ──────────────────────────────────────────

    suspend fun deleteLearnerData(
        userId: String,
        confirm: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (!confirm) {
            return@withContext Result.failure(
                IllegalStateException("Explicit user confirmation required to execute learner deletion")
            )
        }

        try {
            val success = learningRepository.deleteLearnerProfile(userId)
            if (success) Result.success(Unit) else Result.failure(Exception("Failed to delete learner profile"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTrackData(
        trackId: String,
        userId: String,
        confirm: Boolean
    ): Result<Unit> = withContext(ioDispatcher) {
        if (!confirm) {
            return@withContext Result.failure(
                IllegalStateException("Explicit user confirmation required to execute track deletion")
            )
        }

        try {
            val success = learningRepository.deleteLearningTrack(trackId, userId)
            if (success) Result.success(Unit) else Result.failure(Exception("Failed to delete learning track"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── 5. Storage Audit & Orphaned File Cleanup ──────────────────────────────

    suspend fun calculateStorageUsage(
        context: Context? = null,
        baseStorageDir: File,
        databaseFile: File? = null
    ): StorageUsageReport = withContext(ioDispatcher) {
        val dbFile = databaseFile ?: try {
            context?.getDatabasePath(LearnMateDatabase.DATABASE_NAME)
        } catch (e: Exception) {
            null
        } ?: File(baseStorageDir, LearnMateDatabase.DATABASE_NAME)

        val walFile = File(dbFile.parentFile ?: baseStorageDir, "${dbFile.name}-wal")
        val shmFile = File(dbFile.parentFile ?: baseStorageDir, "${dbFile.name}-shm")

        var dbSize = 0L
        if (dbFile.exists()) dbSize += dbFile.length()
        if (walFile.exists()) dbSize += walFile.length()
        if (shmFile.exists()) dbSize += shmFile.length()

        val installedDir = File(baseStorageDir, "installed")
        val contentPacksSize = computeDirectorySize(installedDir)

        val backupsDir = File(baseStorageDir, "backups")
        val backupsSize = computeDirectorySize(backupsDir)

        val stagingDir = File(baseStorageDir, ".staging")
        val cacheDir = try { context?.cacheDir } catch (e: Exception) { null }
        var stagingAndTempSize = computeDirectorySize(stagingDir) + computeDirectorySize(cacheDir)

        val installedPacks = learningRepository.getAllContentPacks()
        val allTracks = learningRepository.getTracksForUser("default_learner")

        val totalBytes = dbSize + contentPacksSize + backupsSize + stagingAndTempSize

        StorageUsageReport(
            databaseSizeBytes = dbSize,
            installedContentPacksBytes = contentPacksSize,
            backupsSizeBytes = backupsSize,
            stagingAndTempBytes = stagingAndTempSize,
            totalAppStorageBytes = totalBytes,
            installedPacksCount = installedPacks.size,
            activeTracksCount = allTracks.size
        )
    }

    suspend fun cleanupTemporaryStorage(
        context: Context? = null,
        baseStorageDir: File,
        customCacheDir: File? = null,
        minAgeMs: Long = 0L
    ): StorageCleanupResult = withContext(ioDispatcher) {
        var purgedBytes = 0L
        var purgedFilesCount = 0
        val details = mutableListOf<String>()
        val cutoffTime = System.currentTimeMillis() - minAgeMs

        // 1. Purge stale items in .staging directory (skips active files newer than cutoffTime)
        val stagingDir = File(baseStorageDir, ".staging")
        if (stagingDir.exists()) {
            val stagingFiles = stagingDir.listFiles() ?: emptyArray()
            for (file in stagingFiles) {
                if (file.lastModified() <= cutoffTime && !file.name.endsWith(".lock")) {
                    val size = if (file.isDirectory) computeDirectorySize(file) else file.length()
                    val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (deleted) {
                        purgedBytes += size
                        purgedFilesCount++
                        details.add("Purged staging item '${file.name}' ($size bytes)")
                    }
                }
            }
        }

        // 2. Scan and remove orphaned backup directories ending in _backup (older than cutoffTime)
        val orphanedBackups = baseStorageDir.listFiles { file -> file.isDirectory && file.name.endsWith("_backup") }
        orphanedBackups?.forEach { dir ->
            if (dir.lastModified() <= cutoffTime && !dir.name.endsWith(".lock")) {
                val size = computeDirectorySize(dir)
                if (dir.deleteRecursively()) {
                    purgedBytes += size
                    purgedFilesCount++
                    details.add("Removed orphaned backup directory '${dir.name}' ($size bytes)")
                }
            }
        }

        // 3. Purge cache directory temporary staging files (older than cutoffTime)
        val cacheDir = customCacheDir ?: try { context?.cacheDir } catch (e: Exception) { null }
        val cacheFiles = cacheDir?.listFiles { file -> file.name.contains(".staging") || file.name.endsWith(".tmp") }
        cacheFiles?.forEach { file ->
            if (file.lastModified() <= cutoffTime && !file.name.endsWith(".lock")) {
                val size = file.length()
                if (file.delete()) {
                    purgedBytes += size
                    purgedFilesCount++
                    details.add("Removed temporary cache file '${file.name}' ($size bytes)")
                }
            }
        }

        StorageCleanupResult(purgedBytes, purgedFilesCount, details)
    }

    private fun computeDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) computeDirectorySize(f) else f.length()
        }
        return size
    }

    // ── 6. Data Privacy & Inventory Disclosures ───────────────────────────────

    fun getDataInventory(): List<DataInventoryItem> = listOf(
        DataInventoryItem(
            category = "Learner Profile",
            dataElements = listOf("userId", "name", "educationLevel", "learnerCategory", "targetDomain", "goal", "dailyStudyMinutes"),
            storageLocation = "Room SQLite (learner_profiles)",
            sensitivityLevel = "LOCAL_USER_IDENTIFIER",
            retentionPolicy = "Retained on-device until user-directed profile deletion or app data clear.",
            userDeletable = true,
            exportedInBackup = true
        ),
        DataInventoryItem(
            category = "Multi-Track Curricula",
            dataElements = listOf("trackId", "userId", "title", "learningMode", "targetCurriculumNodeId", "targetExamName"),
            storageLocation = "Room SQLite (learner_tracks)",
            sensitivityLevel = "EDUCATIONAL_METRIC",
            retentionPolicy = "Retained on-device until user-directed track deletion.",
            userDeletable = true,
            exportedInBackup = true
        ),
        DataInventoryItem(
            category = "Diagnostic Assessments",
            dataElements = listOf("attemptId", "nodeId", "scorePercent", "correctCount", "totalQuestions", "questionResponses"),
            storageLocation = "Room SQLite (study_tasks JSON notes)",
            sensitivityLevel = "EDUCATIONAL_METRIC",
            retentionPolicy = "Retained on-device. Recency weighted and bounded retention decay after 60 days.",
            userDeletable = true,
            exportedInBackup = true
        ),
        DataInventoryItem(
            category = "Spaced Repetition Schedule",
            dataElements = listOf("repetitionId", "intervalDays", "easeFactor", "scheduleState", "dueEpochMs"),
            storageLocation = "Room SQLite (study_tasks JSON notes)",
            sensitivityLevel = "EDUCATIONAL_METRIC",
            retentionPolicy = "Retained on-device. Computed deterministically via SM-2 interval expansion.",
            userDeletable = true,
            exportedInBackup = true
        ),
        DataInventoryItem(
            category = "AI Tutor & Lesson Dialogue",
            dataElements = listOf("sessionId", "messageId", "senderRole", "text", "isAiGenerated", "timestamp"),
            storageLocation = "Room SQLite (chat_sessions, chat_messages)",
            sensitivityLevel = "LOCAL_USER_IDENTIFIER",
            retentionPolicy = "Retained on-device until session deletion or track deletion.",
            userDeletable = true,
            exportedInBackup = true
        ),
        DataInventoryItem(
            category = "System Preferences",
            dataElements = listOf("preferredLanguageCode", "isDarkTheme", "audioSettings"),
            storageLocation = "Room SQLite & Encrypted Backup Metadata",
            sensitivityLevel = "LOCAL_PREFERENCE",
            retentionPolicy = "Retained on-device until reset.",
            userDeletable = true,
            exportedInBackup = true
        )
    )

    fun getPrivacyPolicyDisclosure(): String = """
        LearnMate AI — Offline-First Privacy Architecture Notice
        
        1. 100% On-Device Processing: LearnMate AI operates exclusively on-device without network permissions (zero <uses-permission> in AndroidManifest.xml).
        2. Zero Cloud Transmissions: No personal information, study logs, quiz scores, or usage statistics are ever transmitted to any remote server or cloud service.
        3. Local Authenticated Encryption: Offline backups are protected via AES-256-GCM authenticated encryption with PBKDF2 key derivation (210,000 iterations for Format V2, with backward compatibility for 100,000 iterations Format V1). Encryption keys are derived dynamically from your user passphrase, never stored on disk, and wiped from memory after use.
        4. Independent Content Ownership: Removing curriculum content packs does not delete your study progress or assessment records. Similarly, deleting a learning track or profile does not remove installed curriculum packs.
        5. User-Controlled Erasure vs External Backups: You have absolute control to delete specific learning tracks or your entire learner profile at any time. Local application deletion immediately purges all associated SQLite database records, study history, and internal temporary files. However, externally exported files (such as .lmbackup archives, JSON data exports, and Markdown summaries) saved to user-selected storage via Android Storage Access Framework (SAF) remain under the user's custody and are not deleted by in-app database purge operations.
        6. Storage Architecture & AI Transparency: Application data stored in the local SQLite/Room database relies on Android user-space sandbox isolation and OS-level storage encryption; database-level encryption (e.g. SQLCipher) is not active. The production generative AI engine remains strictly disabled (kill switch active, operating in Verified Textbook Mode).
        7. Statutory Notice: This technical data portability and erasure architecture provides local data minimization and control. It does not constitute formal statutory certification under the Digital Personal Data Protection (DPDP) Act or GDPR without independent organizational legal counsel.
        8. OS-Managed Backup & Device-to-Device Migration Disclosure:
           - Cloud Backup: Strictly DISABLED across all domains (database, shared preferences, internal storage). Your data is never uploaded to Google Drive or any remote cloud backup service.
           - Device Migration: During direct physical cable or nearby migration when setting up a new device, your study database and non-sensitive UI preferences are transferred to prevent data loss. Heavy AI model files (803 MB) and temporary caches are explicitly excluded.
           - Manual Encrypted Backup: For secure, portable backups across devices and platforms, use the application's manual encrypted .lmbackup export with your own passphrase.
    """.trimIndent()
}


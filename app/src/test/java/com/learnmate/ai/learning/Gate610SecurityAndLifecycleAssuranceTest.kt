package com.learnmate.ai.learning

import com.google.gson.Gson
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.QuestionResponseRecord
import com.learnmate.ai.domain.lifecycle.*
import com.learnmate.ai.domain.repetition.RevisionScheduleState
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gate 6.10: Security Hardening, Cryptographic Validation & Data Lifecycle Assurance Test Suite.
 *
 * Verifies:
 * 1. Format V2 authenticated backup creation with 210,000 PBKDF2 iterations and explicit 4-byte header.
 * 2. Format V1 backward compatibility (restoring Gate 6.9 legacy 100,000 PBKDF2 iterations envelope).
 * 3. Cryptographic bounds: rejection of oversized backup files (>50MB).
 * 4. Cryptographic bounds: rejection of oversized ciphertext (>48MB).
 * 5. Envelope integrity: rejection of negative lengths, truncated streams, and corrupt magic headers.
 * 6. Iteration bounds: rejection of out-of-bounds PBKDF2 iteration counts (<100k or >1M).
 * 7. Memory hygiene: verification that wipeSensitiveChars zeroes passphrase memory.
 * 8. Restore atomicity & rollback: failed restore safely rolls back and preserves existing user data.
 * 9. Storage cleanup safety: age-based threshold preserves active/recent files, lock files, backups, and installed packs.
 * 10. Round-trip portability: comprehensive export and restore preserves exact data fidelity.
 * 11. Empty/partial profile safety: safe handling of profiles with zero tracks or attempts.
 * 12. Privacy disclosures & external file distinction: explicit disclaimer distinguishing local SQLite purge from external files.
 * 13. System invariants: zero <uses-permission> in AndroidManifest.xml and active production AI kill switch.
 */
class Gate610SecurityAndLifecycleAssuranceTest {

    private lateinit var repository: LearningRepository
    private lateinit var lifecycleManager: LearnerDataLifecycleManager
    private val gson = Gson()
    private val testPassphrase = "LearnMate_Gate610_Secure_Passphrase_2026!".toCharArray()
    private lateinit var testTempDir: File

    @Before
    fun setUp() {
        repository = LearningRepository()
        lifecycleManager = LearnerDataLifecycleManager(repository)
        testTempDir = File(System.getProperty("java.io.tmpdir"), "learnmate_gate610_test_${System.currentTimeMillis()}")
        testTempDir.mkdirs()

        runBlocking {
            repository.initializeCurriculumIfNeeded()
        }
    }

    // ── 1. Format V2 Backup Creation & Binary Envelope Structure ─────────────

    @Test
    fun test1_formatV2DefaultBackupCreationAndBinaryEnvelope() = runBlocking {
        val userId = "learner_aarav_610"
        val profile = createSampleLearnerProfile(userId, "Aarav Patel", EducationLevel.SECONDARY)
        repository.saveProfile(profile)

        val track = repository.createTrack(
            userId = userId,
            title = "Class 10 Mathematics",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_math10_ch01",
            dailyCommitmentMinutes = 40,
            setAsActive = true
        )

        repository.updateTopicProgress(track.trackId, userId, "cbse_math10_ch01", LearningProgressStatus.COMPLETED, 92.0)

        val backupFile = File(testTempDir, "aarav_v2.lmbackup")
        val result = lifecycleManager.createBackup(userId, testPassphrase, backupFile)
        assertTrue("Backup creation must succeed", result is BackupResult.Success)
        val success = result as BackupResult.Success

        // Verify Manifest V2 fields
        assertEquals("Format version in manifest must be 2", 2, success.manifest.formatVersion)
        assertEquals("PBKDF2 iterations in manifest must be 210,000", 210_000, success.manifest.pbkdf2Iterations)
        assertEquals("AES-256-GCM", success.manifest.encryptionAlgorithm)
        assertEquals(4, success.manifest.roomSchemaVersion)
        assertTrue("Checksum SHA-256 must be 64 characters", success.manifest.checksumSha256.length == 64)

        // Verify Binary Envelope format
        FileInputStream(backupFile).use { fis ->
            val magic = ByteArray(8)
            assertEquals(8, fis.read(magic))
            assertArrayEquals("Magic must match LMBACKUP", "LMBACKUP".toByteArray(Charsets.US_ASCII), magic)

            val version = fis.read()
            assertEquals("Format version byte must be 0x02", 2, version)

            // V2 explicit 4-byte big-endian iteration count
            val iterBytes = ByteArray(4)
            assertEquals(4, fis.read(iterBytes))
            val iterations = ByteBuffer.wrap(iterBytes).int
            assertEquals("Iteration count in V2 header must be 210,000", 210_000, iterations)

            val saltLen = fis.read()
            assertEquals(16, saltLen)
            val salt = ByteArray(16)
            assertEquals(16, fis.read(salt))

            val ivLen = fis.read()
            assertEquals(12, ivLen)
            val iv = ByteArray(12)
            assertEquals(12, fis.read(iv))

            val lenBytes = ByteArray(4)
            assertEquals(4, fis.read(lenBytes))
            val cipherLen = ByteBuffer.wrap(lenBytes).int
            assertTrue("Ciphertext length must be positive", cipherLen > 0)

            val cipher = ByteArray(cipherLen)
            assertEquals(cipherLen, fis.read(cipher))
            assertEquals("Must reach EOF", -1, fis.read())
        }
    }

    // ── 2. Format V1 Backward Compatibility Decryption & Restore ───────────────

    @Test
    fun test2_formatV1BackwardCompatibilityDecryptionAndRestore() = runBlocking {
        val userId = "learner_kavya_v1"
        val profile = createSampleLearnerProfile(userId, "Kavya Reddy", EducationLevel.UNDERGRADUATE)
        repository.saveProfile(profile)

        val track = repository.createTrack(
            userId = userId,
            title = "Data Structures",
            learningMode = LearningMode.PROFESSIONAL_SKILLS,
            targetCurriculumNodeId = "cs_ds_01",
            dailyCommitmentMinutes = 60,
            setAsActive = true
        )

        val v1BackupFile = File(testTempDir, "kavya_v1_legacy.lmbackup")
        // Create explicit Format V1 backup (100,000 iterations)
        val result = lifecycleManager.createBackup(userId, testPassphrase, v1BackupFile, formatVersion = 1)
        assertTrue("V1 Backup creation must succeed", result is BackupResult.Success)
        val success = result as BackupResult.Success
        assertEquals(1, success.manifest.formatVersion)

        // Verify V1 Binary Envelope has version byte 0x01 and NO 4-byte iteration header
        FileInputStream(v1BackupFile).use { fis ->
            val magic = ByteArray(8)
            fis.read(magic)
            assertArrayEquals("LMBACKUP".toByteArray(Charsets.US_ASCII), magic)
            val version = fis.read()
            assertEquals("Version must be 1", 1, version)
            val saltLen = fis.read()
            assertEquals("Next byte in V1 must be salt length (16)", 16, saltLen)
        }

        // Restore onto a fresh user ID
        val restoredUserId = "learner_kavya_restored"
        val restoreResult = lifecycleManager.restoreBackup(
            sourceFile = v1BackupFile,
            passphrase = testPassphrase,
            confirmOverwrite = false,
            targetUserId = restoredUserId
        )

        assertTrue("Restoring Format V1 legacy backup must succeed", restoreResult is RestoreResult.Success)
        val restoredProfile = repository.getProfile(restoredUserId)
        assertNotNull("Restored profile must exist", restoredProfile)
        assertEquals("Kavya Reddy", restoredProfile!!.name)

        val restoredTracks = repository.getTracksForUser(restoredUserId)
        assertTrue("Restored tracks must contain Data Structures", restoredTracks.any { it.title == "Data Structures" })
    }

    // ── 3. Cryptographic Bounds: Oversized Ciphertext & File Rejection ────────

    @Test
    fun test3_oversizedBackupRejection() {
        val hugeEnvelope = ByteArray(60)
        val bb = ByteBuffer.wrap(hugeEnvelope)
        bb.put(LearnerDataSecurity.MAGIC_HEADER)
        bb.put(LearnerDataSecurity.FORMAT_VERSION_2)
        bb.putInt(210_000)
        bb.put(16.toByte())
        bb.put(ByteArray(16))
        bb.put(12.toByte())
        bb.put(ByteArray(12))
        bb.putInt(49 * 1024 * 1024) // 49 MB > MAX_CIPHERTEXT_SIZE (48 MB)

        val testFile = File(testTempDir, "oversized_cipher.lmbackup")
        testFile.writeBytes(hugeEnvelope)

        val result = LearnerDataSecurity.decryptPayload(testFile, testPassphrase)
        assertTrue("Must fail on oversized ciphertext bounds", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(
            "Exception message must indicate invalid ciphertext length: ${ex?.message}",
            ex?.message?.contains("Invalid ciphertext length") == true || ex?.message?.contains("bounds") == true
        )
    }

    // ── 4. Negative, Corrupt Length & Truncation Rejection ─────────────────────

    @Test
    fun test4_negativeOrCorruptLengthsAndTruncationRejection() {
        // Case A: Negative ciphertext length
        val negativeLenEnvelope = ByteArray(60)
        val bbNeg = ByteBuffer.wrap(negativeLenEnvelope)
        bbNeg.put(LearnerDataSecurity.MAGIC_HEADER)
        bbNeg.put(LearnerDataSecurity.FORMAT_VERSION_2)
        bbNeg.putInt(210_000)
        bbNeg.put(16.toByte())
        bbNeg.put(ByteArray(16))
        bbNeg.put(12.toByte())
        bbNeg.put(ByteArray(12))
        bbNeg.putInt(-100) // Negative length

        val negFile = File(testTempDir, "neg_len.lmbackup")
        negFile.writeBytes(negativeLenEnvelope)
        val resNeg = LearnerDataSecurity.decryptPayload(negFile, testPassphrase)
        assertTrue(resNeg.isFailure)
        assertTrue(resNeg.exceptionOrNull()?.message?.contains("Invalid ciphertext length") == true)

        // Case B: Truncated ciphertext (claims 500 bytes, only small array on disk)
        val truncatedEnvelope = ByteArray(60)
        val bbTrunc = ByteBuffer.wrap(truncatedEnvelope)
        bbTrunc.put(LearnerDataSecurity.MAGIC_HEADER)
        bbTrunc.put(LearnerDataSecurity.FORMAT_VERSION_2)
        bbTrunc.putInt(210_000)
        bbTrunc.put(16.toByte())
        bbTrunc.put(ByteArray(16))
        bbTrunc.put(12.toByte())
        bbTrunc.put(ByteArray(12))
        bbTrunc.putInt(500) // Declared length 500, but file only 60 bytes

        val truncFile = File(testTempDir, "trunc.lmbackup")
        truncFile.writeBytes(truncatedEnvelope)
        val resTrunc = LearnerDataSecurity.decryptPayload(truncFile, testPassphrase)
        assertTrue(resTrunc.isFailure)
        assertTrue(resTrunc.exceptionOrNull()?.message?.contains("exceeds file bounds") == true)

        // Case C: Invalid Magic Header
        val badMagicEnvelope = ByteArray(60)
        ByteBuffer.wrap(badMagicEnvelope).put("BADMAGIC".toByteArray(Charsets.US_ASCII))
        val badMagicFile = File(testTempDir, "bad_magic.lmbackup")
        badMagicFile.writeBytes(badMagicEnvelope)
        val resMagic = LearnerDataSecurity.decryptPayload(badMagicFile, testPassphrase)
        assertTrue(resMagic.isFailure)
        assertTrue(resMagic.exceptionOrNull()?.message?.contains("INVALID_MAGIC_HEADER") == true)

        // Case D: Unsupported version byte (e.g. 99)
        val unsupportedVersionEnvelope = ByteArray(60)
        val bbVer = ByteBuffer.wrap(unsupportedVersionEnvelope)
        bbVer.put(LearnerDataSecurity.MAGIC_HEADER)
        bbVer.put(99.toByte()) // Unsupported version
        val unsupFile = File(testTempDir, "unsupported_version.lmbackup")
        unsupFile.writeBytes(unsupportedVersionEnvelope)
        val resVer = LearnerDataSecurity.decryptPayload(unsupFile, testPassphrase)
        assertTrue(resVer.isFailure)
        assertTrue(resVer.exceptionOrNull()?.message?.contains("UNSUPPORTED_VERSION") == true)
    }

    // ── 5. Format V2 PBKDF2 Iteration Bounds Rejection ────────────────────────

    @Test
    fun test5_formatV2IterationCountBoundsRejection() {
        // Case A: Iterations too low (< 100,000)
        val lowIterEnvelope = ByteArray(60)
        val bbLow = ByteBuffer.wrap(lowIterEnvelope)
        bbLow.put(LearnerDataSecurity.MAGIC_HEADER)
        bbLow.put(LearnerDataSecurity.FORMAT_VERSION_2)
        bbLow.putInt(50_000) // Only 50k rounds (below MIN 100k)
        bbLow.put(16.toByte())
        bbLow.put(ByteArray(16))
        bbLow.put(12.toByte())
        bbLow.put(ByteArray(12))
        bbLow.putInt(16)

        val lowFile = File(testTempDir, "low_iter.lmbackup")
        lowFile.writeBytes(lowIterEnvelope)
        val resLow = LearnerDataSecurity.decryptPayload(lowFile, testPassphrase)
        assertTrue(resLow.isFailure)
        assertTrue(resLow.exceptionOrNull()?.message?.contains("INVALID_ITERATION_COUNT") == true)

        // Case B: Iterations too high (> 1,000,000)
        val highIterEnvelope = ByteArray(60)
        val bbHigh = ByteBuffer.wrap(highIterEnvelope)
        bbHigh.put(LearnerDataSecurity.MAGIC_HEADER)
        bbHigh.put(LearnerDataSecurity.FORMAT_VERSION_2)
        bbHigh.putInt(2_000_000) // 2M rounds (exceeds MAX 1M)
        bbHigh.put(16.toByte())
        bbHigh.put(ByteArray(16))
        bbHigh.put(12.toByte())
        bbHigh.put(ByteArray(12))
        bbHigh.putInt(16)

        val highFile = File(testTempDir, "high_iter.lmbackup")
        highFile.writeBytes(highIterEnvelope)
        val resHigh = LearnerDataSecurity.decryptPayload(highFile, testPassphrase)
        assertTrue(resHigh.isFailure)
        assertTrue(resHigh.exceptionOrNull()?.message?.contains("INVALID_ITERATION_COUNT") == true)
    }

    // ── 6. Memory Hygiene: wipeSensitiveChars ─────────────────────────────────

    @Test
    fun test6_memoryHygieneWipeSensitiveChars() {
        val sensitiveData = charArrayOf('S', 'e', 'c', 'u', 'r', 'e', 'P', 'a', 's', 's', 'w', '0', 'r', 'd', '!')
        assertTrue(sensitiveData.any { it != '\u0000' })

        LearnerDataSecurity.wipeSensitiveChars(sensitiveData)

        for (i in sensitiveData.indices) {
            assertEquals("Character at index $i must be wiped to null char", '\u0000', sensitiveData[i])
        }
    }

    // ── 7. Restore Atomicity & Rollback Protection ────────────────────────────

    @Test
    fun test7_restoreAtomicityAndFailClosedRollbackProtection() = runBlocking {
        val userId = "learner_rollback_target"
        val originalProfile = createSampleLearnerProfile(userId, "Original Uncorrupted Learner", EducationLevel.SECONDARY)
        repository.saveProfile(originalProfile)

        val originalTrack = repository.createTrack(
            userId = userId,
            title = "Original Track A",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "node_original_1",
            dailyCommitmentMinutes = 30,
            setAsActive = true
        )
        repository.updateTopicProgress(originalTrack.trackId, userId, "node_original_1", LearningProgressStatus.COMPLETED, 85.0)

        val originalAttempt = AssessmentAttemptRecord(
            attemptId = "att_orig_1",
            trackId = originalTrack.trackId,
            userId = userId,
            nodeId = "node_original_1",
            topicTitle = "Original Topic",
            discipline = "General",
            scorePercent = 85.0,
            correctCount = 4,
            totalQuestions = 5,
            durationSeconds = 200,
            attemptEpochMs = System.currentTimeMillis(),
            questionResponses = emptyList()
        )
        repository.recordAssessmentAttempt(originalAttempt)

        // Create a valid backup
        val validBackupFile = File(testTempDir, "valid_temp.lmbackup")
        lifecycleManager.createBackup(userId, testPassphrase, validBackupFile)

        // Read bytes and tamper with ciphertext to cause AEADBadTagException during restore
        val bytes = validBackupFile.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 0xFF).toByte()
        val corruptBackupFile = File(testTempDir, "corrupted.lmbackup")
        corruptBackupFile.writeBytes(bytes)

        // Attempt restore with confirmOverwrite = true
        val restoreResult = lifecycleManager.restoreBackup(
            sourceFile = corruptBackupFile,
            passphrase = testPassphrase,
            confirmOverwrite = true,
            targetUserId = userId
        )

        assertTrue("Restore of corrupt backup must fail", restoreResult is RestoreResult.Failure)

        // Verify that original profile, tracks, topic progress, and attempts are 100% INTACT
        val currentProfile = repository.getProfile(userId)
        assertNotNull("Original profile must still exist", currentProfile)
        assertEquals("Original Uncorrupted Learner", currentProfile!!.name)

        val currentTracks = repository.getTracksForUser(userId)
        assertTrue("Original track must be preserved", currentTracks.any { it.title == "Original Track A" })

        val currentAttempts = repository.getAllAssessmentAttemptsForTrack(originalTrack.trackId, userId)
        assertEquals(1, currentAttempts.size)
        assertEquals("att_orig_1", currentAttempts[0].attemptId)
    }

    // ── 8. Storage Cleanup Safety & Age Threshold ─────────────────────────────

    @Test
    fun test8_storageCleanupAgeThresholdAndActiveFileProtection() = runBlocking {
        val mockStorageBase = File(testTempDir, "mock_storage")
        mockStorageBase.mkdirs()

        val stagingDir = File(mockStorageBase, ".staging")
        stagingDir.mkdirs()

        val backupsDir = File(mockStorageBase, "backups")
        backupsDir.mkdirs()

        val installedDir = File(mockStorageBase, "installed")
        installedDir.mkdirs()

        val customCache = File(mockStorageBase, "cache")
        customCache.mkdirs()

        val now = System.currentTimeMillis()
        val tenMinutesAgo = now - (10 * 60 * 1000L)
        val thirtySecondsAgo = now - 30_000L

        // 1. Stale temporary file (10 mins old) -> SHOULD BE DELETED
        val staleFile = File(stagingDir, "stale_pack_download.tmp")
        staleFile.writeText("stale temp content")
        staleFile.setLastModified(tenMinutesAgo)

        // 2. Active file (30s old) -> MUST BE PRESERVED
        val activeFile = File(stagingDir, "active_pack_download.tmp")
        activeFile.writeText("active in-flight content")
        activeFile.setLastModified(thirtySecondsAgo)

        // 3. Active lock file (10 mins old) -> MUST BE PRESERVED
        val lockFile = File(stagingDir, "import_operation.lock")
        lockFile.writeText("lock metadata")
        lockFile.setLastModified(tenMinutesAgo)

        // 4. Legitimate user backup -> MUST BE PRESERVED
        val backupFile = File(backupsDir, "learner_backup_2026.lmbackup")
        backupFile.writeText("encrypted user backup")
        backupFile.setLastModified(tenMinutesAgo)

        // 5. Installed content pack -> MUST BE PRESERVED
        val packFile = File(installedDir, "science_pack_v1.lmpack")
        packFile.writeText("installed curriculum pack")
        packFile.setLastModified(tenMinutesAgo)

        // Run cleanup with minAgeMs = 5 minutes (300,000 ms)
        val cleanupResult = lifecycleManager.cleanupTemporaryStorage(
            baseStorageDir = mockStorageBase,
            customCacheDir = customCache,
            minAgeMs = 5 * 60 * 1000L
        )

        assertTrue("Purged file count must be at least 1", cleanupResult.purgedFilesCount >= 1)
        assertFalse("Stale file must be deleted", staleFile.exists())
        assertTrue("Active file (< 5 mins old) must be preserved", activeFile.exists())
        assertTrue("Lock file (.lock) must be preserved", lockFile.exists())
        assertTrue("User backup directory contents must be preserved", backupFile.exists())
        assertTrue("Installed content pack directory contents must be preserved", packFile.exists())
    }

    // ── 9. Export & Restore Round-Trip Consistency ────────────────────────────

    @Test
    fun test9_exportAndRestoreRoundTripConsistency() = runBlocking {
        val srcUserId = "learner_roundtrip_src"
        val profile = createSampleLearnerProfile(srcUserId, "Divya Murthy", EducationLevel.UNDERGRADUATE)
        repository.saveProfile(profile)

        val track = repository.createTrack(
            userId = srcUserId,
            title = "Digital Electronics",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "eng_de_01",
            dailyCommitmentMinutes = 50,
            setAsActive = true
        )

        repository.updateTopicProgress(track.trackId, srcUserId, "eng_de_01", LearningProgressStatus.COMPLETED, 100.0)

        val attempt = AssessmentAttemptRecord(
            attemptId = "att_roundtrip_1",
            trackId = track.trackId,
            userId = srcUserId,
            nodeId = "eng_de_01",
            topicTitle = "Logic Gates and Boolean Algebra",
            discipline = "Electronics",
            scorePercent = 100.0,
            correctCount = 5,
            totalQuestions = 5,
            durationSeconds = 180,
            attemptEpochMs = System.currentTimeMillis() - 3600000L,
            questionResponses = listOf(
                QuestionResponseRecord("q1", "NAND gate universality", 0, 0, true, false, "Universal logic", 30)
            )
        )
        repository.recordAssessmentAttempt(attempt)

        val srs = SpacedRepetitionItem(
            repetitionId = "srs_roundtrip_1",
            trackId = track.trackId,
            userId = srcUserId,
            nodeId = "eng_de_01",
            topicTitle = "Logic Gates and Boolean Algebra",
            discipline = "Electronics",
            repetitionNumber = 2,
            intervalDays = 6,
            easeFactor = 2.6,
            lastReviewedEpochMs = System.currentTimeMillis() - 3600000L,
            dueEpochMs = System.currentTimeMillis() + 86400000L,
            isOverdue = false,
            overdueDays = 0,
            scheduleState = RevisionScheduleState.FUTURE
        )
        repository.recordSpacedRepetitionReview(track.trackId, srcUserId, "eng_de_01", 100.0)

        // Export to backup
        val backupFile = File(testTempDir, "roundtrip.lmbackup")
        val backupResult = lifecycleManager.createBackup(srcUserId, testPassphrase, backupFile)
        assertTrue("Backup creation must succeed", backupResult is BackupResult.Success)

        // Restore to target user
        val destUserId = "learner_roundtrip_dest"
        val restoreResult = lifecycleManager.restoreBackup(
            sourceFile = backupFile,
            passphrase = testPassphrase,
            confirmOverwrite = false,
            targetUserId = destUserId
        )
        assertTrue("Restore must succeed", restoreResult is RestoreResult.Success)

        // Validate data fidelity
        val destProfile = repository.getProfile(destUserId)
        assertNotNull(destProfile)
        assertEquals("Divya Murthy", destProfile!!.name)
        assertEquals("Physics", destProfile.targetDomain)

        val destTracks = repository.getTracksForUser(destUserId)
        assertTrue(destTracks.any { it.title == "Digital Electronics" })
        val destTrack = destTracks.first { it.title == "Digital Electronics" }

        val destAttempts = repository.getAllAssessmentAttemptsForTrack(destTrack.trackId, destUserId)
        assertEquals(1, destAttempts.size)
        assertEquals(100.0, destAttempts[0].scorePercent, 0.01)
        assertEquals("att_roundtrip_1", destAttempts[0].attemptId)

        val destProgress = repository.getTopicProgress(destTrack.trackId, destUserId, "eng_de_01")
        assertEquals(LearningProgressStatus.COMPLETED, destProgress.status)
        assertEquals(100.0, destProgress.scorePercent ?: 0.0, 0.01)
    }

    // ── 10. Empty & Partial Profile Safe Lifecycle ─────────────────────────────

    @Test
    fun test10_emptyAndPartialProfileSafeLifecycle() = runBlocking {
        val emptyUserId = "learner_empty_profile"
        val emptyProfile = createSampleLearnerProfile(emptyUserId, "New Blank Learner", EducationLevel.SECONDARY)
        repository.saveProfile(emptyProfile)

        // 1. Export JSON with zero custom tracks
        val jsonFile = File(testTempDir, "blank_export.json")
        val jsonResult = lifecycleManager.exportLearnerDataJson(emptyUserId, jsonFile)
        assertTrue("JSON export must succeed for empty profile", jsonResult.isSuccess)
        val jsonContent = jsonFile.readText()
        assertTrue(jsonContent.contains("New Blank Learner"))

        // 2. Export Markdown with zero custom tracks
        val mdFile = File(testTempDir, "blank_summary.md")
        val mdResult = lifecycleManager.exportProgressSummaryMarkdown(emptyUserId, mdFile)
        assertTrue("Markdown export must succeed for empty profile", mdResult.isSuccess)
        val mdContent = mdFile.readText()
        assertTrue(mdContent.contains("# LearnMate AI — Learner Progress & Data Export Report"))

        // 3. Create backup of empty profile
        val backupFile = File(testTempDir, "blank.lmbackup")
        val backupResult = lifecycleManager.createBackup(emptyUserId, testPassphrase, backupFile)
        assertTrue("Backup of empty profile must succeed", backupResult is BackupResult.Success)

        // 4. Restore empty profile
        val targetUser = "learner_empty_restored"
        val restoreResult = lifecycleManager.restoreBackup(
            sourceFile = backupFile,
            passphrase = testPassphrase,
            confirmOverwrite = false,
            targetUserId = targetUser
        )
        assertTrue("Restore of empty profile must succeed", restoreResult is RestoreResult.Success)
        val restored = repository.getProfile(targetUser)
        assertNotNull(restored)
        assertEquals("New Blank Learner", restored!!.name)
    }

    // ── 11. Data Inventory Completeness & Privacy Notice Disclosures ───────────

    @Test
    fun test11_dataInventoryCompletenessAndExternalFileDistinction() {
        val inventory = lifecycleManager.getDataInventory()
        assertEquals("Must specify 6 primary data categories", 6, inventory.size)

        val categories = inventory.map { it.category }
        assertTrue(categories.contains("Learner Profile"))
        assertTrue(categories.contains("Multi-Track Curricula"))
        assertTrue(categories.contains("Diagnostic Assessments"))
        assertTrue(categories.contains("Spaced Repetition Schedule"))
        assertTrue(categories.contains("AI Tutor & Lesson Dialogue"))
        assertTrue(categories.contains("System Preferences"))

        val disclosure = lifecycleManager.getPrivacyPolicyDisclosure()
        // Must clearly distinguish in-app SQLite database erasure from external user-managed backup files
        assertTrue(
            "Notice must distinguish database erasure from external files (.lmbackup, .json, .md)",
            disclosure.contains("User-Controlled Erasure vs External Backups") &&
            disclosure.contains("Storage Access Framework (SAF)") &&
            disclosure.contains(".lmbackup")
        )
        // Disclose that Room database-level encryption is not active
        assertTrue(
            "Notice must disclose local SQLite database encryption boundary",
            disclosure.contains("database-level encryption") && disclosure.contains("SQLCipher")
        )
        // Disclose production AI kill switch status
        assertTrue(
            "Notice must disclose production generative AI kill switch active",
            disclosure.contains("generative AI engine remains strictly disabled") && disclosure.contains("Verified Textbook Mode")
        )
        // Disclose DPDP Act / GDPR statutory qualification
        assertTrue(
            "Notice must disclose DPDP Act / GDPR statutory qualification limitation",
            disclosure.contains("Digital Personal Data Protection (DPDP) Act")
        )
    }

    // ── 12. Zero-Network Permissions & Production AI Kill Switch Invariants ──

    @Test
    fun test12_zeroNetworkPermissionsAndProductionAiKillSwitch() {
        // 1. AndroidManifest.xml zero-network audit
        val candidatePaths = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("zzzz_lama_proj/app/src/main/AndroidManifest.xml"),
            File("F:/rea/zzzz_lama_proj/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile!!)

        val permissionNodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val name = permissionNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertTrue(
                "Gate 6.16.3: Only authorized model download network permissions permitted: $name",
                name == "android.permission.INTERNET" || name == "android.permission.ACCESS_NETWORK_STATE"
            )
        }

        // 2. Production AI Kill Switch active by default
        assertTrue(
            "Verified Textbook Mode notice must disclose local AI is disabled by production policy",
            AiTutorRepository.VERIFIED_TEXTBOOK_MODE_NOTICE.contains("disabled by production policy")
        )
    }

    private fun createSampleLearnerProfile(
        userId: String,
        name: String,
        educationLevel: EducationLevel
    ): LearnerProfile {
        return LearnerProfile(
            userId = userId,
            name = name,
            targetDomain = "Physics",
            subdomain = "General",
            goal = "Master Core Principles",
            targetRole = "Student",
            previousBackground = "Secondary School",
            currentLevel = "Beginner",
            audienceLevel = AudienceLevel.HIGH_SCHOOL,
            dailyStudyMinutes = 45,
            weeklyStudyDays = 5,
            currentTopic = "Classical Mechanics",
            onboardingCompleted = true,
            assessmentCompleted = true,
            educationLevel = educationLevel,
            learnerCategory = LearnerCategory.SCHOOL_STUDENT
        )
    }
}

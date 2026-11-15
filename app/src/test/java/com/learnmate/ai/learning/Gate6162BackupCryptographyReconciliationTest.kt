package com.learnmate.ai.learning

import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.lifecycle.BackupResult
import com.learnmate.ai.domain.lifecycle.LearnerDataLifecycleManager
import com.learnmate.ai.domain.lifecycle.LearnerDataSecurity
import com.learnmate.ai.domain.lifecycle.RestoreResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * Gate 6.16.2 Priority 1: Backup Cryptography Reconciliation & Binary Envelope Validation.
 *
 * Verifies:
 * 1. Restore a real V1 backup (100,000 PBKDF2 iterations, no 4-byte header).
 * 2. Restore an existing V2 backup with legacy iteration count (100,000 PBKDF2 iterations in 4-byte header).
 * 3. Generate a new V2 backup (210,000 PBKDF2 iterations, explicit 4-byte header).
 * 4. Inspect actual raw header bytes (Magic 8B, Version 1B, Iterations 4B, Salt 16B, IV 12B, Length 4B, Tag 16B).
 * 5. Test correct and incorrect passphrases (fail-closed AEADBadTagException).
 * 6. Test single-bit ciphertext and header tampering (fail-closed integrity rejection).
 * 7. Test atomic rollback on failed restore to ensure existing learner data remains intact.
 */
class Gate6162BackupCryptographyReconciliationTest {

    private lateinit var repository: LearningRepository
    private lateinit var lifecycleManager: LearnerDataLifecycleManager
    private lateinit var testTempDir: File
    private val testPassphrase = "CorrectHorseBatteryStaple2026!".toCharArray()
    private val wrongPassphrase = "IncorrectPassword123!".toCharArray()

    @Before
    fun setUp() {
        repository = LearningRepository()
        lifecycleManager = LearnerDataLifecycleManager(repository)
        testTempDir = File(System.getProperty("java.io.tmpdir"), "gate6162_backup_crypto_${System.currentTimeMillis()}")
        testTempDir.mkdirs()

        runBlocking {
            repository.initializeCurriculumIfNeeded()
        }
    }

    @After
    fun tearDown() {
        testTempDir.deleteRecursively()
    }

    private fun createProfile(userId: String, name: String): LearnerProfile {
        return LearnerProfile(
            userId = userId,
            name = name,
            targetDomain = "Universal Science",
            subdomain = "Applied Physics",
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
            educationLevel = EducationLevel.SECONDARY,
            learnerCategory = LearnerCategory.SCHOOL_STUDENT
        )
    }

    @Test
    fun test1_RestoreRealV1Backup_100kIterations() = runBlocking {
        val originalUserId = "user_v1_legacy"
        val profile = createProfile(originalUserId, "Vikram Sharma")
        repository.saveProfile(profile)

        val track = repository.createTrack(
            userId = originalUserId,
            title = "Class 10 Physics Mechanics",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch01",
            dailyCommitmentMinutes = 45,
            setAsActive = true
        )
        repository.updateTopicProgress(track.trackId, originalUserId, "cbse_sci10_ch01", LearningProgressStatus.COMPLETED, 95.0)

        // 1. Generate real Format V1 backup (100,000 iterations)
        val v1File = File(testTempDir, "vikram_v1.lmbackup")
        val createResult = lifecycleManager.createBackup(
            userId = originalUserId,
            passphrase = testPassphrase,
            targetFile = v1File,
            formatVersion = LearnerDataSecurity.FORMAT_VERSION_1
        )
        assertTrue("V1 backup creation must succeed", createResult is BackupResult.Success)
        val manifest = (createResult as BackupResult.Success).manifest
        assertEquals(1, manifest.formatVersion)
        assertEquals(100_000, manifest.pbkdf2Iterations)

        // Save reference copy for physical inspection
        val reportsDir = File("build/reports/backups").apply { mkdirs() }
        v1File.copyTo(File(reportsDir, "reference_v1.lmbackup"), overwrite = true)
        FileInputStream(v1File).use { fis ->
            val magic = ByteArray(8)
            assertEquals(8, fis.read(magic))
            assertArrayEquals("Magic header must be LMBACKUP", LearnerDataSecurity.MAGIC_HEADER, magic)

            val version = fis.read()
            assertEquals("Version byte must be 0x01", 1, version)

            // V1 does NOT have the 4-byte iteration header; next byte is Salt Length (16)
            val saltLen = fis.read()
            assertEquals("Salt length byte must be 16", 16, saltLen)

            val salt = ByteArray(16)
            assertEquals(16, fis.read(salt))

            val ivLen = fis.read()
            assertEquals("IV length byte must be 12", 12, ivLen)

            val iv = ByteArray(12)
            assertEquals(12, fis.read(iv))

            val lenBytes = ByteArray(4)
            assertEquals(4, fis.read(lenBytes))
            val cipherLen = ByteBuffer.wrap(lenBytes).int
            assertTrue("Ciphertext length must be positive", cipherLen > 0)
        }

        // 3. Restore onto fresh target user ID
        val restoredUserId = "user_v1_restored"
        val restoreResult = lifecycleManager.restoreBackup(
            sourceFile = v1File,
            passphrase = testPassphrase,
            confirmOverwrite = false,
            targetUserId = restoredUserId
        )
        assertTrue("Restoring Format V1 backup must succeed", restoreResult is RestoreResult.Success)
        val restoredProfile = repository.getProfile(restoredUserId)
        assertNotNull("Restored profile must exist", restoredProfile)
        assertEquals("Vikram Sharma", restoredProfile?.name)
    }

    @Test
    fun test2_RestoreExistingV2BackupWith100kLegacyIterations() = runBlocking {
        // Test backward compatibility if an older V2 backup stored 100,000 iterations in its 4-byte header
        val payloadJson = """{"test":"payload_content","marker":"legacy_v2_100k"}"""
        val legacyV2File = File(testTempDir, "legacy_v2_100k.lmbackup")

        // Encrypt with Format V2 but explicitly 100,000 iterations
        val encResult = LearnerDataSecurity.encryptPayload(
            payloadJson = payloadJson,
            passphrase = testPassphrase,
            targetFile = legacyV2File,
            formatVersion = LearnerDataSecurity.FORMAT_VERSION_2,
            iterations = 100_000
        )
        assertTrue(encResult.isSuccess)

        // Verify header has version=2 and iterations=100000
        FileInputStream(legacyV2File).use { fis ->
            val magic = ByteArray(8)
            fis.read(magic)
            val ver = fis.read()
            assertEquals(2, ver)
            val iterBytes = ByteArray(4)
            fis.read(iterBytes)
            val iters = ByteBuffer.wrap(iterBytes).int
            assertEquals(100_000, iters)
        }

        // Decrypt must succeed using the 100k iterations specified in the header
        val decResult = LearnerDataSecurity.decryptPayload(legacyV2File, testPassphrase)
        assertTrue("Decrypting legacy V2 archive with 100k iterations must succeed", decResult.isSuccess)
        assertEquals(payloadJson, decResult.getOrThrow())
    }

    @Test
    fun test3_GenerateNewV2BackupAndInspectHeaderParameters() = runBlocking {
        val originalUserId = "user_v2_new"
        val profile = createProfile(originalUserId, "Ananya Sen")
        repository.saveProfile(profile)

        val v2File = File(testTempDir, "ananya_v2_standard.lmbackup")
        val createResult = lifecycleManager.createBackup(
            userId = originalUserId,
            passphrase = testPassphrase,
            targetFile = v2File,
            formatVersion = LearnerDataSecurity.FORMAT_VERSION_2
        )
        assertTrue("V2 backup creation must succeed", createResult is BackupResult.Success)
        val manifest = (createResult as BackupResult.Success).manifest
        assertEquals(2, manifest.formatVersion)
        assertEquals(210_000, manifest.pbkdf2Iterations)

        // Save reference copy for physical inspection
        val reportsDir = File("build/reports/backups").apply { mkdirs() }
        v2File.copyTo(File(reportsDir, "reference_v2.lmbackup"), overwrite = true)
        FileInputStream(v2File).use { fis ->
            val magic = ByteArray(8)
            assertEquals(8, fis.read(magic))
            assertArrayEquals(LearnerDataSecurity.MAGIC_HEADER, magic)

            val version = fis.read()
            assertEquals("Format version byte must be 2", 2, version)

            // V2 explicit 4-byte big-endian iteration count: 210,000
            val iterBytes = ByteArray(4)
            assertEquals(4, fis.read(iterBytes))
            val iterations = ByteBuffer.wrap(iterBytes).int
            assertEquals("Iterations in header must be 210,000", 210_000, iterations)

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
            assertTrue("Ciphertext length must be > 0", cipherLen > 0)

            val cipherBytes = ByteArray(cipherLen)
            val readTotal = fis.read(cipherBytes)
            assertEquals(cipherLen, readTotal)
            assertEquals("Must be at EOF", -1, fis.read())
        }
    }

    @Test
    fun test5_CorrectAndIncorrectPassphraseRejection() = runBlocking {
        val payloadJson = """{"secret":"learner_study_history"}"""
        val testFile = File(testTempDir, "auth_test.lmbackup")

        LearnerDataSecurity.encryptPayload(
            payloadJson = payloadJson,
            passphrase = testPassphrase,
            targetFile = testFile,
            formatVersion = LearnerDataSecurity.FORMAT_VERSION_2,
            iterations = 210_000
        )

        // Correct passphrase succeeds
        val correctResult = LearnerDataSecurity.decryptPayload(testFile, testPassphrase)
        assertTrue(correctResult.isSuccess)
        assertEquals(payloadJson, correctResult.getOrThrow())

        // Wrong passphrase fails closed
        val wrongResult = LearnerDataSecurity.decryptPayload(testFile, wrongPassphrase)
        assertTrue("Decryption with wrong passphrase must fail", wrongResult.isFailure)
        val ex = wrongResult.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(
            "Exception must indicate authentication failure or bad tag: ${ex?.message}",
            ex?.message?.contains("Tag mismatch") == true ||
                    ex?.message?.contains("AEADBadTagException") == true ||
                    ex?.message?.contains("AUTHENTICATION_FAILED") == true ||
                    ex is javax.crypto.AEADBadTagException ||
                    ex?.cause is javax.crypto.AEADBadTagException
        )
    }

    @Test
    fun test6_CorruptedAndTamperedBackupRejection() = runBlocking {
        val payloadJson = """{"accredited":"content_integrity"}"""
        val validFile = File(testTempDir, "integrity_test.lmbackup")

        LearnerDataSecurity.encryptPayload(
            payloadJson = payloadJson,
            passphrase = testPassphrase,
            targetFile = validFile,
            formatVersion = LearnerDataSecurity.FORMAT_VERSION_2,
            iterations = 210_000
        )

        val rawBytes = validFile.readBytes()

        // Tamper 1: Modify a single bit in the ciphertext / tag region (last byte)
        val tamperedCipher = rawBytes.clone()
        tamperedCipher[tamperedCipher.size - 1] = (tamperedCipher[tamperedCipher.size - 1].toInt() xor 0x01).toByte()
        val tamperedFile = File(testTempDir, "tampered_cipher.lmbackup")
        tamperedFile.writeBytes(tamperedCipher)

        val resTampered = LearnerDataSecurity.decryptPayload(tamperedFile, testPassphrase)
        assertTrue("Tampered ciphertext must fail authenticated decryption", resTampered.isFailure)

        // Tamper 2: Corrupt Magic Header
        val badMagic = rawBytes.clone()
        badMagic[0] = 'X'.code.toByte()
        val badMagicFile = File(testTempDir, "bad_magic.lmbackup")
        badMagicFile.writeBytes(badMagic)

        val resMagic = LearnerDataSecurity.decryptPayload(badMagicFile, testPassphrase)
        assertTrue("Tampered magic header must fail closed", resMagic.isFailure)
    }

    @Test
    fun test7_FailedRestorationRollbackPreservesExistingData() = runBlocking {
        val existingUserId = "user_safe_existing"
        val originalProfile = createProfile(existingUserId, "Original Unaltered Learner")
        repository.saveProfile(originalProfile)

        val originalTrack = repository.createTrack(
            userId = existingUserId,
            title = "Unaltered Mathematics Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch01",
            dailyCommitmentMinutes = 30,
            setAsActive = true
        )

        val beforeTracks = repository.getTracksForUser(existingUserId)
        val initialTrackCount = beforeTracks.size
        assertTrue(beforeTracks.any { it.title == "Unaltered Mathematics Track" })

        // Create corrupt backup file
        val corruptFile = File(testTempDir, "corrupted_payload.lmbackup")
        corruptFile.writeBytes("INVALID_NON_ENCRYPTED_TRASH_BYTES".toByteArray())

        // Attempt restore over existing user
        val result = lifecycleManager.restoreBackup(
            sourceFile = corruptFile,
            passphrase = testPassphrase,
            confirmOverwrite = true,
            targetUserId = existingUserId
        )
        assertTrue("Restore must fail", result is RestoreResult.Failure)

        // Verify existing learner profile and track remain completely intact
        val afterProfile = repository.getProfile(existingUserId)
        assertNotNull("Existing profile must still exist", afterProfile)
        assertEquals("Original Unaltered Learner", afterProfile?.name)

        val afterTracks = repository.getTracksForUser(existingUserId)
        assertEquals(initialTrackCount, afterTracks.size)
        assertTrue("Unaltered Mathematics Track must still exist", afterTracks.any { it.title == "Unaltered Mathematics Track" })
    }
}

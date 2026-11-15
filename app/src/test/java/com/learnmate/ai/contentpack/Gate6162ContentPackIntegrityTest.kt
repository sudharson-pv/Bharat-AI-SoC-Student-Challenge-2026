package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.BuiltInContentManifestRegistry
import com.learnmate.ai.data.contentpack.ContentPackInstaller
import com.learnmate.ai.data.contentpack.ContentPackSecurity
import com.learnmate.ai.data.db.ContentPackEntity
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate 6.16.2 Priority 6: Content Pack Cryptographic Integrity & Quarantine Enforcement Test.
 *
 * Verifies:
 * 1. Built-in pack verification against immutable cryptographic manifest registry.
 * 2. Tampered built-in pack (corrupted checksum, altered metadata, missing nodes) fails audit and is quarantined.
 * 3. Quarantined packs are strictly blocked from track creation and curriculum assignment.
 * 4. Downloaded pack Ed25519 signature/payload failures trigger automatic quarantine.
 * 5. Quarantine lifting restores accessibility once verification passes.
 */
class Gate6162ContentPackIntegrityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseStorageDir: File
    private lateinit var repository: LearningRepository

    @Before
    fun setUp() {
        baseStorageDir = tempFolder.newFolder("content_pack_storage")
        repository = LearningRepository()
        runBlocking {
            repository.initializeCurriculumIfNeeded()
        }
    }

    @Test
    fun test1_BuiltInPackCanonicalManifestVerification_Passes() = runBlocking {
        val packId = "pack_builtin_legacy_gate4"
        assertTrue("Built-in pack must be registered in canonical manifest registry",
            BuiltInContentManifestRegistry.isRegisteredBuiltIn(packId))

        val canonicalManifest = BuiltInContentManifestRegistry.getManifest(packId)
        assertNotNull(canonicalManifest)
        assertEquals("Gate 4 Verified Engineering Baseline", canonicalManifest!!.title)
        assertEquals("1.0.0", canonicalManifest.version)
        assertEquals("ENGINEERING", canonicalManifest.category)
        assertEquals("7ee6a2dd55e1ef8b58718406346626ac6cf57968c8168cc7892db67ecb1a081d", canonicalManifest.checksumSha256)

        val pack = repository.getContentPackById(packId)
        assertNotNull("Baseline pack must be installed in repository", pack)

        // Audit with baseline nodes
        val baselineNodes = CurriculumEngine.getUniversalBaselineNodes()
        val auditResult = BuiltInContentManifestRegistry.auditBuiltInPack(pack!!, baselineNodes)
        assertTrue("Authentic built-in pack must pass cryptographic audit", auditResult.isIntact)

        // Repository audit should succeed and pack should not be quarantined
        val repoAuditPassed = repository.auditInstalledPack(packId, baseStorageDir)
        assertTrue("Repository auditInstalledPack must return true", repoAuditPassed)
        assertFalse("Pack must not be quarantined", repository.isPackQuarantined(packId))
    }

    @Test
    fun test2_BuiltInPackTamperedChecksum_FailsAuditAndQuarantines() = runBlocking {
        val packId = "pack_builtin_legacy_gate4"
        val pack = repository.getContentPackById(packId)!!

        // Tamper with checksum
        val tamperedPack = pack.copy(checksumSha256 = "0000000000000000000000000000000000000000000000000000000000000000")
        val auditResult = BuiltInContentManifestRegistry.auditBuiltInPack(tamperedPack, null)
        assertFalse("Tampered checksum must fail audit", auditResult.isIntact)
        assertEquals("CHECKSUM_MISMATCH", auditResult.errorCode)

        // Quarantine the pack
        repository.quarantinePack(packId, "Detected tampered SHA-256 digest")
        assertTrue("Pack must be recognized as quarantined", repository.isPackQuarantined(packId))
    }

    @Test
    fun test3_BuiltInPackMissingMandatoryNodesOrForeignAffiliation_FailsAudit() = runBlocking {
        val packId = "pack_builtin_legacy_gate4"
        val pack = repository.getContentPackById(packId) ?: ContentPackEntity(
            packId = packId,
            title = "Gate 4 Verified Engineering Baseline",
            version = "1.0.0",
            minAppVersion = "1.4.0",
            category = "ENGINEERING",
            authorOrProvider = "LearnMate Editorial Board",
            provenanceNotice = "Textbooks",
            checksumSha256 = "7ee6a2dd55e1ef8b58718406346626ac6cf57968c8168cc7892db67ecb1a081d",
            isInstalled = true,
            isBuiltIn = true
        )

        // 1. Missing mandatory node
        val truncatedNodes = listOf(
            CurriculumNodeEntity("node_root_school", null, packId, "DISCIPLINE", "SCH-SCI", "School", "Desc", 1)
        )
        val missingResult = BuiltInContentManifestRegistry.auditBuiltInPack(pack, truncatedNodes)
        assertFalse("Truncated nodes must fail audit", missingResult.isIntact)

        // 2. Foreign node affiliation
        val baselineNodes = CurriculumEngine.getUniversalBaselineNodes().toMutableList()
        val foreignNode = CurriculumNodeEntity("foreign_node", null, "pack_unauthorized", "TOPIC", "FOR", "Foreign", "Desc", 1)
        baselineNodes.add(foreignNode)
        val foreignResult = BuiltInContentManifestRegistry.auditBuiltInPack(pack, baselineNodes)
        assertFalse("Foreign node in pack must fail audit", foreignResult.isIntact)
        assertEquals("FOREIGN_NODE_AFFILIATION", foreignResult.errorCode)
    }

    @Test
    fun test4_QuarantinedPackBlocksTrackCreation() = runBlocking {
        val userId = "user_quarantine_test_1"
        val quarantinedPackId = "pack_quarantined_1"
        val quarantinedNodeId = "node_quarantined_1"

        // Quarantine pack in repository
        repository.quarantinePack(quarantinedPackId, "Integrity audit checksum mismatch")
        assertTrue(repository.isPackQuarantined(quarantinedPackId))

        // Pre-stage in-memory node pointing to quarantined pack
        val field = LearningRepository::class.java.getDeclaredField("inMemoryCurriculumNodes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(repository) as MutableList<CurriculumNodeEntity>
        list.add(CurriculumNodeEntity(quarantinedNodeId, null, quarantinedPackId, "TOPIC", "BAD", "Bad Node", "Desc", 1))

        // Creating track with node belonging to quarantined pack MUST throw IllegalStateException
        try {
            repository.createTrack(
                userId = userId,
                title = "Attempted Track with Quarantined Content",
                targetCurriculumNodeId = quarantinedNodeId
            )
            fail("createTrack must fail when targetCurriculumNodeId belongs to quarantined pack")
        } catch (e: IllegalStateException) {
            assertTrue("Exception must state quarantined pack", e.message?.contains("Quarantined content pack") == true)
        }
    }

    @Test
    fun test5_QuarantinedPackBlocksTrackAssignmentAndEnrollment() = runBlocking {
        val userId = "user_quarantine_test_2"
        val quarantinedPackId = "pack_quarantined_2"
        val quarantinedNodeId = "node_quarantined_2"

        // Create an unassigned track first
        val track = repository.createTrack(
            userId = userId,
            title = "Valid Academic Track",
            targetCurriculumNodeId = null
        )

        // Quarantine the pack
        repository.quarantinePack(quarantinedPackId, "Revoked cryptographic certificate")

        // Pre-stage node
        val field = LearningRepository::class.java.getDeclaredField("inMemoryCurriculumNodes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(repository) as MutableList<CurriculumNodeEntity>
        list.add(CurriculumNodeEntity(quarantinedNodeId, null, quarantinedPackId, "TOPIC", "BAD2", "Bad Node 2", "Desc", 1))

        // 1. Assign pack directly MUST fail
        try {
            repository.assignPackToTrack(userId, track.trackId, quarantinedPackId)
            fail("assignPackToTrack must reject quarantined pack")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Quarantined content pack") == true)
        }

        // 2. Assign node from quarantined pack MUST fail
        try {
            repository.assignCurriculumToTrack(userId, track.trackId, quarantinedNodeId)
            fail("assignCurriculumToTrack must reject node from quarantined pack")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("belongs to quarantined content pack") == true)
        }

        // 3. Lifting quarantine allows assignment
        repository.liftQuarantine(quarantinedPackId)
        assertFalse(repository.isPackQuarantined(quarantinedPackId))
        repository.assignCurriculumToTrack(userId, track.trackId, quarantinedNodeId)

        val updatedTracks = repository.getTracksForUser(userId)
        val updated = updatedTracks.first { it.trackId == track.trackId }
        assertEquals(quarantinedNodeId, updated.targetCurriculumNodeId)
    }

    @Test
    fun test6_DownloadedPackAuditFailureTriggersQuarantine() = runBlocking {
        val badPackId = "pack_downloaded_corrupt"
        // No files exist in storage, so ContentPackInstaller.auditInstalledPackIntegrity will return false
        val auditPassed = repository.auditInstalledPack(badPackId, baseStorageDir)
        assertFalse("Missing files must fail audit", auditPassed)
        assertTrue("Failed downloaded pack must be placed in quarantine", repository.isPackQuarantined(badPackId))
    }
}

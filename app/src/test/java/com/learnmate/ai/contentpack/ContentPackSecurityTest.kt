package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ContentPackSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val keyPair = ContentPackSecurity.generateTestKeyPair()
    private val md = MessageDigest.getInstance("SHA-256")

    private lateinit var baseStorageDir: File

    @Before
    fun setUp() {
        baseStorageDir = tempFolder.newFolder("content_packs_storage")
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["test_key_01"] = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }

    @org.junit.After
    fun tearDown() {
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS.remove("test_key_01")
        ContentPackSecurity.REVOKED_PUBLISHER_KEY_HASHES.clear()
    }

    private fun sha256(bytes: ByteArray): String {
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun createSampleManifest(
        packId: String = "pack_sample_test",
        formatVersion: String = "1.0.0",
        dependencies: List<PackDependencyItem> = emptyList(),
        payloads: List<PayloadFileEntry> = emptyList()
    ): ContentPackManifest {
        return ContentPackManifest(
            formatVersion = formatVersion,
            packId = packId,
            version = "1.0.0",
            minAppVersion = "1.4.0",
            title = "Sample Test Curriculum Pack",
            category = "ENGINEERING",
            primaryLanguage = "en",
            supportedLanguages = listOf("en"),
            authorOrProvider = "LearnMate Test Board",
            provenanceNotice = "Test accreditation",
            publisherKeyId = "test_key_01",
            dependencies = dependencies,
            curriculumNodes = listOf(
                CurriculumNodeItem("node_test_root", null, "DISCIPLINE", "TEST-ROOT", "Test Root", "Description", 30, 1),
                CurriculumNodeItem("node_test_topic", "node_test_root", "TOPIC", "TEST-TOPIC", "Test Topic", "Description", 30, 1)
            ),
            prerequisites = emptyList(),
            resources = listOf(
                ResourceItem(
                    resourceId = "res_test_01",
                    nodeId = "node_test_topic",
                    languageCode = "en",
                    verifiedExplanation = "Test explanation",
                    referenceTitle = "Test Book",
                    referenceAuthor = "Test Author",
                    referenceEdition = "1st",
                    referenceChapter = "1",
                    referenceSection = "1.1",
                    referenceIdentifier = "TEST-REF-01"
                )
            ),
            payloadFiles = payloads
        )
    }

    // ── 1. Valid Content Pack ──────────────────────────────────────────────────

    @Test
    fun testValidContentPackValidation() {
        val payloadData = "Verified textbook content for chapter 1".toByteArray(Charsets.UTF_8)
        val payloadPath = "resources/ch1.txt"
        val payloadHash = sha256(payloadData)

        val manifest = createSampleManifest(
            payloads = listOf(PayloadFileEntry(payloadPath, payloadHash, payloadData.size.toLong()))
        )

        val packFile = File(tempFolder.root, "sample_valid.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(payloadPath to payloadData)
        )

        val result = ContentPackSecurity.validateArchive(
            archiveFile = packFile,
            trustedPublicKey = keyPair.public
        )

        assertTrue("Valid pack must pass validation", result.isValid)
        assertNotNull(result.manifest)
        assertEquals("pack_sample_test", result.manifest?.packId)
        assertEquals(1, result.manifest?.payloadFiles?.size)
    }

    // ── 2. Signature Security Tests ────────────────────────────────────────────

    @Test
    fun testInvalidSignatureRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "corrupted_sig.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            corruptSignature = true
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Corrupted signature must be rejected", result.isValid)
        assertEquals("INVALID_SIGNATURE", result.errorCode)
    }

    @Test
    fun testWrongSigningKeyRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "wrong_key.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair
        )

        val otherKeyPair = ContentPackSecurity.generateTestKeyPair()
        val result = ContentPackSecurity.validateArchive(packFile, otherKeyPair.public)
        assertFalse("Wrong public key must reject archive", result.isValid)
        assertTrue(
            "Wrong public key must reject archive with INVALID_SIGNATURE or PUBLISHER_KEY_MISMATCH (got ${result.errorCode})",
            result.errorCode == "INVALID_SIGNATURE" || result.errorCode == "PUBLISHER_KEY_MISMATCH"
        )
    }

    @Test
    fun testTamperedManifestArchiveRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "tampered_manifest.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair
        )

        // Read archive, tamper with manifest bytes, rewrite
        val zis = java.util.zip.ZipInputStream(packFile.inputStream())
        val entries = mutableMapOf<String, ByteArray>()
        var entry = zis.nextEntry
        while (entry != null) {
            val data = zis.readBytes()
            entries[entry.name] = data
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()

        // Tamper with manifest
        val tamperedManifest = String(entries["manifest.json"]!!).replace("Sample Test", "Malicious Injected").toByteArray()
        entries["manifest.json"] = tamperedManifest

        val tamperedFile = File(tempFolder.root, "tampered_result.lmpack")
        java.util.zip.ZipOutputStream(tamperedFile.outputStream()).use { zos ->
            entries.forEach { (name, bytes) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        val result = ContentPackSecurity.validateArchive(tamperedFile, keyPair.public)
        assertFalse("Tampered manifest must fail signature verification", result.isValid)
        assertEquals("INVALID_SIGNATURE", result.errorCode)
    }

    // ── 3. Payload Integrity & Hash Tests ──────────────────────────────────────

    @Test
    fun testModifiedPayloadFileRejected() {
        val originalData = "Clean textbook data".toByteArray()
        val payloadPath = "resources/book.dat"
        val payloadHash = sha256(originalData)

        val manifest = createSampleManifest(
            payloads = listOf(PayloadFileEntry(payloadPath, payloadHash, originalData.size.toLong()))
        )

        // Inject modified payload data into zip with exact same byte length to test hash mismatch
        val modifiedData = "Dirty textbook data".toByteArray()
        assertEquals(originalData.size, modifiedData.size)
        val packFile = File(tempFolder.root, "tampered_payload.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(payloadPath to modifiedData)
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Modified payload must be rejected by SHA-256 check", result.isValid)
        assertEquals("PAYLOAD_HASH_MISMATCH", result.errorCode)
    }

    @Test
    fun testPayloadSizeMismatchRejected() {
        val originalData = "Clean textbook data".toByteArray()
        val payloadPath = "resources/book.dat"
        val payloadHash = sha256(originalData)

        val manifest = createSampleManifest(
            payloads = listOf(PayloadFileEntry(payloadPath, payloadHash, originalData.size.toLong()))
        )

        // Inject modified payload data with different length
        val modifiedData = "Much longer payload content".toByteArray()
        val packFile = File(tempFolder.root, "size_mismatch.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(payloadPath to modifiedData)
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Payload size mismatch must be rejected", result.isValid)
        assertEquals("PAYLOAD_SIZE_MISMATCH", result.errorCode)
    }

    @Test
    fun testIncorrectSha256InManifestRejected() {
        val payloadData = "Real payload".toByteArray()
        val payloadPath = "resources/real.dat"
        val fakeHash = "0000000000000000000000000000000000000000000000000000000000000000"

        val manifest = createSampleManifest(
            payloads = listOf(PayloadFileEntry(payloadPath, fakeHash, payloadData.size.toLong()))
        )

        val packFile = File(tempFolder.root, "fake_hash.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(payloadPath to payloadData)
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Incorrect hash declaration must be rejected", result.isValid)
        assertEquals("PAYLOAD_HASH_MISMATCH", result.errorCode)
    }

    // ── 4. Zip Slip & Path Traversal Tests ──────────────────────────────────────

    @Test
    fun testPathTraversalZipSlipRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "zip_slip.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            injectPathTraversal = "../../etc/shadow"
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Zip slip path traversal must be rejected", result.isValid)
        assertEquals("PATH_TRAVERSAL_DETECTED", result.errorCode)
    }

    @Test
    fun testAbsoluteWindowsPathRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "windows_abs_path.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            injectPathTraversal = "C:\\Windows\\System32\\calc.exe"
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Absolute Windows path must be rejected", result.isValid)
        assertEquals("PATH_TRAVERSAL_DETECTED", result.errorCode)
    }

    @Test
    fun testDuplicateEntryRejected() {
        val packFile = File(tempFolder.root, "duplicate_entry.lmpack")

        // Craft a raw zip stream containing two local headers with the identical entry name
        val rawZipBytes = ByteArrayOutputStream().apply {
            fun writeLocalHeader(name: String, content: ByteArray) {
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                val crc = java.util.zip.CRC32().apply { update(content) }.value

                write(byteArrayOf(0x50, 0x4b, 0x03, 0x04)) // Signature
                write(byteArrayOf(20, 0))                   // Version
                write(byteArrayOf(0, 0))                    // Flags
                write(byteArrayOf(0, 0))                    // Method: Stored
                write(byteArrayOf(0, 0, 0, 0))              // Time & Date
                write(byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte(), ((crc shr 16) and 0xFF).toByte(), ((crc shr 24) and 0xFF).toByte()))
                val size = content.size
                write(byteArrayOf((size and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte(), ((size shr 16) and 0xFF).toByte(), ((size shr 24) and 0xFF).toByte()))
                write(byteArrayOf((size and 0xFF).toByte(), ((size shr 8) and 0xFF).toByte(), ((size shr 16) and 0xFF).toByte(), ((size shr 24) and 0xFF).toByte()))
                val nLen = nameBytes.size
                write(byteArrayOf((nLen and 0xFF).toByte(), ((nLen shr 8) and 0xFF).toByte()))
                write(byteArrayOf(0, 0))                    // Extra field length
                write(nameBytes)
                write(content)
            }

            // Write first entry: manifest.json
            writeLocalHeader("manifest.json", "{}".toByteArray())
            // Write duplicate entry: manifest.json
            writeLocalHeader("manifest.json", "{}".toByteArray())
        }.toByteArray()

        packFile.writeBytes(rawZipBytes)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Duplicate entries must be rejected", result.isValid)
        assertEquals("DUPLICATE_ENTRY", result.errorCode)
    }

    // ── 5. Denial of Service & Zip Bomb Bounds ─────────────────────────────────

    @Test
    fun testExcessiveEntryCountRejected() {
        val packFile = File(tempFolder.root, "excessive_entries.lmpack")
        java.util.zip.ZipOutputStream(packFile.outputStream()).use { zos ->
            // Write 1005 entries
            for (i in 0..1004) {
                zos.putNextEntry(ZipEntry("file_$i.txt"))
                zos.write(byteArrayOf(1))
                zos.closeEntry()
            }
        }

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Excessive entry count (>1000) must be rejected", result.isValid)
        assertEquals("EXCESSIVE_ENTRY_COUNT", result.errorCode)
    }

    @Test
    fun testCorruptedArchiveRejected() {
        val packFile = File(tempFolder.root, "corrupted.lmpack")
        packFile.writeBytes("Not a zip file header at all".toByteArray())

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Corrupted archive bytes must be rejected", result.isValid)
        assertEquals("MISSING_MANIFEST", result.errorCode) // ZipInputStream returns 0 entries, fails on missing manifest
    }

    // ── 6. Semantic & Dependency Structure Tests ───────────────────────────────

    @Test
    fun testCircularPackDependencyRejected() {
        val manifest = createSampleManifest(
            packId = "pack_alpha",
            dependencies = listOf(PackDependencyItem(packId = "pack_alpha", versionConstraint = ">=1.0.0"))
        )

        val packFile = File(tempFolder.root, "circular_dep.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Circular self-dependency must be rejected", result.isValid)
        assertEquals("CIRCULAR_PACK_DEPENDENCY", result.errorCode)
    }

    @Test
    fun testUnsupportedFormatVersionRejected() {
        val manifest = createSampleManifest(
            formatVersion = "2.5.0"
        )

        val packFile = File(tempFolder.root, "future_version.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Unsupported format version must be rejected", result.isValid)
        assertEquals("UNSUPPORTED_FORMAT_VERSION", result.errorCode)
    }

    // ── 7. Recoverable Staged Installation & Crash Recovery ───────────────────

    @Test
    fun testRecoverableInstallationSuccess() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        val payload = "Unit 1 Lecture notes".toByteArray()
        val path = "resources/notes.txt"
        val manifest = createSampleManifest(
            packId = "pack_install_success",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "install_success.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(path to payload)
        )

        val installResult = installer.installPack(packFile, keyPair.public)
        assertTrue("Install must succeed", installResult is ContentPackInstaller.InstallResult.Success)

        // Check installed files exist on disk in active storage
        val installedFile = File(baseStorageDir, "installed/pack_install_success/$path")
        assertTrue("Installed asset must exist in active directory", installedFile.exists())
        assertEquals(payload.size.toLong(), installedFile.length())

        // Staging directory must be empty/purged
        val stagingDir = File(baseStorageDir, ".staging/pack_install_success")
        assertFalse("Staging directory must be cleaned up after success", stagingDir.exists())
    }

    @Test
    fun testInterruptedInstallationLeavesNoCorruptFiles() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        // Simulate failed validation (corrupted signature)
        val manifest = createSampleManifest(packId = "pack_failed_interrupted")
        val packFile = File(tempFolder.root, "fail_install.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            corruptSignature = true
        )

        val installResult = installer.installPack(packFile, keyPair.public)
        assertTrue("Install must report failure", installResult is ContentPackInstaller.InstallResult.Failure)

        // Verify active directory was NOT created
        val installedDir = File(baseStorageDir, "installed/pack_failed_interrupted")
        assertFalse("Failed install must not create installed folder", installedDir.exists())

        // Verify staging directory was purged
        val stagingDir = File(baseStorageDir, ".staging/pack_failed_interrupted")
        assertFalse("Staging directory must not be left behind", stagingDir.exists())
    }

    @Test
    fun testStartupReconciliationPurgesLeftoverStaging() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        // Artificially create a stranded .staging directory simulating app crash mid-copy
        val strandedDir = File(baseStorageDir, ".staging/stranded_pack_101")
        strandedDir.mkdirs()
        File(strandedDir, "partial.bin").writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(strandedDir.exists())

        // Run startup reconciler
        installer.reconcilePendingInstallations()

        // Verify stranded staging directory is cleanly purged
        assertFalse("Reconciler must purge incomplete staging directory on startup", strandedDir.exists())
    }

    // ── 8. Dual-Engine Ed25519 Fallback & Raw Key Tests ────────────────────────

    @Test
    fun testDualEngineEd25519Verification() {
        val message = "Manifest verification payload".toByteArray(Charsets.UTF_8)
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(message)
        val signature = sig.sign()

        // Test standard 44-byte DER encoded key
        val derValid = ContentPackSecurity.verifyEd25519Signature(
            publicKeyBytes = keyPair.public.encoded,
            data = message,
            signature = signature
        )
        assertTrue("DER-encoded key must verify via dual-engine", derValid)

        // Test raw 32-byte key
        val rawKey = keyPair.public.encoded.copyOfRange(12, 44)
        val rawValid = ContentPackSecurity.verifyEd25519Signature(
            publicKeyBytes = rawKey,
            data = message,
            signature = signature
        )
        assertTrue("Raw 32-byte key must verify via BouncyCastle fallback", rawValid)

        // Corrupted signature must fail
        val badSig = signature.clone()
        badSig[0] = (badSig[0].toInt() xor 0xFF).toByte()
        val badSigResult = ContentPackSecurity.verifyEd25519Signature(
            publicKeyBytes = rawKey,
            data = message,
            signature = badSig
        )
        assertFalse("Corrupted signature must fail verification", badSigResult)
    }

    // ── 9. Control Character & Null Byte Filename Tests ─────────────────────────

    @Test
    fun testNullByteInEntryNameRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "null_byte_entry.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            injectPathTraversal = "safe_name\u0000injected.txt"
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Entry with null byte must be rejected", result.isValid)
        assertEquals("MALFORMED_ENTRY_NAME", result.errorCode)
    }

    @Test
    fun testControlCharacterInEntryNameRejected() {
        val manifest = createSampleManifest()
        val packFile = File(tempFolder.root, "ctrl_char_entry.lmpack")

        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            injectPathTraversal = "safe\u0007bell.txt"
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Entry with control character must be rejected", result.isValid)
        assertEquals("MALFORMED_ENTRY_NAME", result.errorCode)
    }

    // ── 10. Unlisted Extra File Rejection ──────────────────────────────────────

    @Test
    fun testUnlistedExtraFileRejected() {
        val payload = "Declared payload".toByteArray()
        val payloadPath = "resources/declared.txt"
        val manifest = createSampleManifest(
            payloads = listOf(PayloadFileEntry(payloadPath, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "unlisted_file.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(payloadPath to payload),
            injectUnlistedFile = Pair("resources/secret_unlisted.txt", "Untracked data".toByteArray())
        )

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Unlisted extra file must be rejected", result.isValid)
        assertEquals("UNLISTED_EXTRA_FILE", result.errorCode)
    }

    // ── 11. Semantic Manifest Hierarchy & Integrity Tests ──────────────────────

    @Test
    fun testOrphanCurriculumNodeRejected() {
        val manifest = createSampleManifest().copy(
            curriculumNodes = listOf(
                CurriculumNodeItem("node_orphan", "node_nonexistent_parent", "TOPIC", "ORPHAN-01", "Orphan Topic", "", 30, 1)
            )
        )

        val packFile = File(tempFolder.root, "orphan_node.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Orphan node must be rejected", result.isValid)
        assertEquals("ORPHAN_CURRICULUM_NODE", result.errorCode)
    }

    @Test
    fun testHierarchyCycleRejected() {
        val manifest = createSampleManifest().copy(
            curriculumNodes = listOf(
                CurriculumNodeItem("node_A", "node_B", "TOPIC", "NODE-A", "Node A", "", 30, 1),
                CurriculumNodeItem("node_B", "node_A", "TOPIC", "NODE-B", "Node B", "", 30, 2)
            )
        )

        val packFile = File(tempFolder.root, "cycle_hierarchy.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Hierarchy cycle must be rejected", result.isValid)
        assertEquals("HIERARCHY_CYCLE_DETECTED", result.errorCode)
    }

    @Test
    fun testCircularPrerequisiteRejected() {
        val manifest = createSampleManifest().copy(
            prerequisites = listOf(
                PrerequisiteItem("prereq_self", "node_test_topic", "node_test_topic", "MANDATORY")
            )
        )

        val packFile = File(tempFolder.root, "circular_prereq.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Circular prerequisite must be rejected", result.isValid)
        assertEquals("CIRCULAR_PREREQUISITE", result.errorCode)
    }

    @Test
    fun testInvalidResourceNodeRejected() {
        val manifest = createSampleManifest().copy(
            resources = listOf(
                ResourceItem(
                    resourceId = "res_invalid_node",
                    nodeId = "node_does_not_exist",
                    languageCode = "en",
                    verifiedExplanation = "Invalid",
                    referenceTitle = "Book",
                    referenceAuthor = "Author",
                    referenceEdition = "1st",
                    referenceChapter = "1",
                    referenceSection = "1",
                    referenceIdentifier = "REF-01"
                )
            )
        )

        val packFile = File(tempFolder.root, "invalid_res_node.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair)

        val result = ContentPackSecurity.validateArchive(packFile, keyPair.public)
        assertFalse("Resource referencing invalid node must be rejected", result.isValid)
        assertEquals("INVALID_RESOURCE_NODE", result.errorCode)
    }

    // ── 12. Persistent State Machine & Crash Injection Tests ────────────────────

    @Test
    fun testCrashInjectionAtExtractingPayloadStaging_reconciledOnStartup() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.EXTRACTING_PAYLOAD_STAGING

        val payload = "Crash test payload".toByteArray()
        val path = "resources/crash_data.txt"
        val manifest = createSampleManifest(
            packId = "pack_crash_extract",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "crash_extract.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(path to payload)
        )

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {
            // Expected abrupt crash simulation
        }

        assertEquals(ContentPackInstaller.InstallState.EXTRACTING_PAYLOAD_STAGING, installer.currentState)

        // Verify journal exists on disk before reconciliation
        val journalFile = File(baseStorageDir, ".staging/pack_crash_extract.journal")
        assertTrue("Journal file must exist before recovery", journalFile.exists())

        // Run recovery reconciler
        installer.reconcilePendingInstallations()

        // Journal and staging artifacts must be purged
        assertFalse("Journal must be purged after reconciliation", journalFile.exists())
        val stagingDir = File(baseStorageDir, ".staging/pack_crash_extract")
        assertFalse("Staging directory must be purged after reconciliation", stagingDir.exists())
    }

    @Test
    fun testCrashInjectionAtFinalizingStorage_reconciledOnStartup() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.FINALIZING_STORAGE

        val payload = "Finalizing crash test payload".toByteArray()
        val path = "resources/finalize_crash.txt"
        val manifest = createSampleManifest(
            packId = "pack_crash_finalize",
            payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong()))
        )

        val packFile = File(tempFolder.root, "crash_finalize.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = mapOf(path to payload)
        )

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {
            // Expected abrupt crash simulation
        }

        assertEquals(ContentPackInstaller.InstallState.FINALIZING_STORAGE, installer.currentState)

        // Run recovery reconciler
        installer.reconcilePendingInstallations()

        val journalFile = File(baseStorageDir, ".staging/pack_crash_finalize.journal")
        assertFalse("Journal must be purged after recovery", journalFile.exists())
        val installedDir = File(baseStorageDir, "installed/pack_crash_finalize")
        assertFalse("Installed directory must not remain for unfinalized pack", installedDir.exists())
    }

    // ── 13. Comprehensive Ed25519 Cryptographic Verification (Phase 1 & 7) ────────

    @Test
    fun testPinnedOfficialPublicKeyIsExactly44BytesAndValidRFC8410() {
        val b64 = ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64
        assertEquals("Base64 string must be 60 characters", 60, b64.length)

        val decoded = java.util.Base64.getDecoder().decode(b64)
        assertEquals("SubjectPublicKeyInfo DER length must be exactly 44 bytes", 44, decoded.size)

        assertTrue(
            "Public key encoding must pass strict RFC 8410 validation",
            ContentPackSecurity.isValidPublicKeyEncoding(decoded)
        )

        val pubKey = ContentPackSecurity.getPinnedOfficialPublicKey()
        assertNotNull("Generated PublicKey from pinned string must not be null", pubKey)
        assertTrue("Algorithm must be Ed25519 or EdDSA", pubKey?.algorithm?.startsWith("Ed") == true)
    }

    @Test
    fun testRejectMalformedDERHeaderKey() {
        // 44 bytes but invalid DER prefix
        val malformedBytes = ByteArray(44) { 0x00.toByte() }
        assertFalse(
            "Malformed DER prefix must be rejected",
            ContentPackSecurity.isValidPublicKeyEncoding(malformedBytes)
        )

        val fakeSig = ByteArray(64) { 0x01.toByte() }
        val fakeData = "manifest data".toByteArray()
        assertFalse(
            "verifyEd25519Signature must reject malformed DER header key",
            ContentPackSecurity.verifyEd25519Signature(malformedBytes, fakeData, fakeSig)
        )
    }

    @Test
    fun testRejectOld50ByteMockKey() {
        // Old Gate 6.2 mock key which decoded to 50 bytes
        val oldMockB64 = "MCowBQYDK2VwAyEALearnM4t3OfficialEd25519KeyForGate6BaselineVerify00="
        val decoded = java.util.Base64.getDecoder().decode(oldMockB64)
        assertEquals("Old mock key was 50 bytes", 50, decoded.size)

        assertFalse(
            "Old 50-byte mock key must be rejected by strict encoder validation",
            ContentPackSecurity.isValidPublicKeyEncoding(decoded)
        )

        val fakeSig = ByteArray(64) { 0x01.toByte() }
        val fakeData = "manifest data".toByteArray()
        assertFalse(
            "verifyEd25519Signature must reject 50-byte key",
            ContentPackSecurity.verifyEd25519Signature(decoded, fakeData, fakeSig)
        )
    }

    @Test
    fun testAcceptRaw32BytePublicKey() {
        val encodedSPKI = keyPair.public.encoded
        assertEquals(44, encodedSPKI.size)

        // Raw 32-byte key is the last 32 bytes of the 44-byte SPKI
        val raw32Bytes = encodedSPKI.copyOfRange(12, 44)
        assertEquals(32, raw32Bytes.size)

        assertTrue(
            "Raw 32-byte Ed25519 key must be accepted",
            ContentPackSecurity.isValidPublicKeyEncoding(raw32Bytes)
        )

        val testData = "Test payload for raw key verification".toByteArray()
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(testData)
        val signatureBytes = sig.sign()

        assertTrue(
            "verifyEd25519Signature must verify cleanly with raw 32-byte public key",
            ContentPackSecurity.verifyEd25519Signature(raw32Bytes, testData, signatureBytes)
        )
    }

    @Test
    fun testRejectWrongPublisherKey() {
        val wrongKeyPair = ContentPackSecurity.generateTestKeyPair()
        val testData = "Important curriculum manifest".toByteArray()

        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(testData)
        val signatureBytes = sig.sign()

        // Verify with wrong public key
        assertFalse(
            "Signature must fail verification with wrong publisher key",
            ContentPackSecurity.verifyEd25519Signature(wrongKeyPair.public.encoded, testData, signatureBytes)
        )
    }

    @Test
    fun testCorruptedSignatureRejected() {
        val testData = "Signed curriculum text".toByteArray()
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(testData)
        val signatureBytes = sig.sign()

        // Flip a bit in the signature
        signatureBytes[0] = (signatureBytes[0].toInt() xor 0xFF).toByte()

        assertFalse(
            "Corrupted signature must be rejected",
            ContentPackSecurity.verifyEd25519Signature(keyPair.public.encoded, testData, signatureBytes)
        )
    }

    @Test
    fun testTamperedManifestBytesFailsSignature() {
        val testData = "Original curriculum text".toByteArray()
        val sig = java.security.Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(testData)
        val signatureBytes = sig.sign()

        val tamperedData = "Tampered curriculum text".toByteArray()
        assertFalse(
            "Tampered manifest bytes must fail signature check",
            ContentPackSecurity.verifyEd25519Signature(keyPair.public.encoded, tamperedData, signatureBytes)
        )
    }

    // ── 14. Full-Lifecycle Crash Recovery Tests Across All States (Phase 4) ────────

    @Test
    fun testCrashInjectionAtValidatingArchive_reconciled() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.VALIDATING_ARCHIVE

        val payload = "Data for validate crash".toByteArray()
        val path = "resources/val_crash.txt"
        val manifest = createSampleManifest("pack_crash_val", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_val.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        assertFalse(File(baseStorageDir, ".staging/pack_crash_val").exists())
    }

    @Test
    fun testCrashInjectionAtValidated_reconciled() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.VALIDATED

        val payload = "Data for validated crash".toByteArray()
        val path = "resources/validated_crash.txt"
        val manifest = createSampleManifest("pack_crash_validated", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_validated.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        assertFalse(File(baseStorageDir, ".staging/pack_crash_validated").exists())
    }

    @Test
    fun testCrashInjectionAtVerifyingDependencies_reconciled() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.VERIFYING_DEPENDENCIES

        val payload = "Data for dep crash".toByteArray()
        val path = "resources/dep_crash.txt"
        val manifest = createSampleManifest("pack_crash_dep", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_dep.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        assertFalse(File(baseStorageDir, ".staging/pack_crash_dep").exists())
    }

    @Test
    fun testCrashRecoveryPreservesExistingInstalledPacks() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        // 1. Install pack A cleanly
        val payloadA = "Pack A established content".toByteArray()
        val pathA = "resources/pack_a.txt"
        val manifestA = createSampleManifest("pack_installed_a", payloads = listOf(PayloadFileEntry(pathA, sha256(payloadA), payloadA.size.toLong())))
        val packFileA = File(tempFolder.root, "pack_a.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileA, manifestA, keyPair, mapOf(pathA to payloadA))
        val resultA = installer.installPack(packFileA, keyPair.public)
        assertTrue(resultA is ContentPackInstaller.InstallResult.Success)

        val installedFileA = File(baseStorageDir, "installed/pack_installed_a/$pathA")
        assertTrue("Pack A file must exist", installedFileA.exists())

        // 2. Incur crash during installation of pack B
        installer.crashInjectionPoint = ContentPackInstaller.InstallState.EXTRACTING_PAYLOAD_STAGING
        val payloadB = "Pack B failing content".toByteArray()
        val pathB = "resources/pack_b.txt"
        val manifestB = createSampleManifest("pack_failed_b", payloads = listOf(PayloadFileEntry(pathB, sha256(payloadB), payloadB.size.toLong())))
        val packFileB = File(tempFolder.root, "pack_b.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileB, manifestB, keyPair, mapOf(pathB to payloadB))

        try {
            installer.installPack(packFileB, keyPair.public)
            fail("Expected SimulatedCrashException")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        // 3. Reconcile on startup
        installer.reconcilePendingInstallations()

        // 4. Invariant: Pack A is 100% PRESERVED, Pack B incomplete artifacts are PURGED
        assertTrue("Pack A file must remain intact after reconciliation", installedFileA.exists())
        assertFalse("Pack B staging must be purged", File(baseStorageDir, ".staging/pack_failed_b").exists())
        assertFalse("Pack B installed dir must not exist", File(baseStorageDir, "installed/pack_failed_b").exists())
    }

    @Test
    fun testReconciliationIsIdempotent() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        // Leave stranded journal and staging directory
        val strandedDir = File(baseStorageDir, ".staging/stranded_idem")
        strandedDir.mkdirs()
        File(strandedDir, "test.bin").writeBytes(byteArrayOf(1, 2, 3))
        val journal = File(baseStorageDir, ".staging/stranded_idem.journal")
        journal.writeText("packId=stranded_idem\nstate=EXTRACTING_PAYLOAD_STAGING\n")

        // Run reconciler 3 times consecutively
        installer.reconcilePendingInstallations()
        installer.reconcilePendingInstallations()
        installer.reconcilePendingInstallations()

        assertFalse(strandedDir.exists())
        assertFalse(journal.exists())
    }

    // ── 15. Gate 6.2.2 Process-Termination Recovery at Exact Boundaries ────────

    @Test
    fun testProcessTermination_afterDatabaseCommit() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.boundaryCrashHook = { boundary ->
            if (boundary == "AFTER_DATABASE_COMMIT") {
                throw ContentPackInstaller.SimulatedCrashException("Crash after DB commit")
            }
        }

        val payload = "After DB commit data".toByteArray()
        val path = "resources/after_db.txt"
        val manifest = createSampleManifest("pack_crash_after_db", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_after_db.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected crash")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        // Verify incomplete staging exists prior to recovery
        assertTrue(File(baseStorageDir, ".staging/pack_crash_after_db").exists())

        // Reconcile on startup
        installer.reconcilePendingInstallations()

        // Verify unfinalized pack rolled back cleanly
        assertFalse(File(baseStorageDir, ".staging/pack_crash_after_db").exists())
        assertFalse(File(baseStorageDir, "installed/pack_crash_after_db").exists())
    }

    @Test
    fun testProcessTermination_duringDirectoryMovement() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.boundaryCrashHook = { boundary ->
            if (boundary == "DURING_DIRECTORY_MOVEMENT") {
                throw ContentPackInstaller.SimulatedCrashException("Crash during dir move")
            }
        }

        val payload = "During move data".toByteArray()
        val path = "resources/during_move.txt"
        val manifest = createSampleManifest("pack_crash_during_move", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_during_move.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected crash")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        assertFalse(File(baseStorageDir, ".staging/pack_crash_during_move").exists())
        assertFalse(File(baseStorageDir, "installed/pack_crash_during_move").exists())
    }

    @Test
    fun testProcessTermination_afterFinalDirectoryMovement() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.boundaryCrashHook = { boundary ->
            if (boundary == "AFTER_FINAL_DIRECTORY_MOVEMENT") {
                throw ContentPackInstaller.SimulatedCrashException("Crash after final dir move")
            }
        }

        val payload = "After final move data".toByteArray()
        val path = "resources/after_final_move.txt"
        val manifest = createSampleManifest("pack_crash_after_final", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_after_final.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected crash")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        // Because DB was not marked installed, incomplete pack must not remain in installed directory
        assertFalse(File(baseStorageDir, "installed/pack_crash_after_final").exists())
    }

    @Test
    fun testProcessTermination_beforeInstalledStateDatabaseUpdate() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.boundaryCrashHook = { boundary ->
            if (boundary == "BEFORE_INSTALLED_STATE_DATABASE_UPDATE") {
                throw ContentPackInstaller.SimulatedCrashException("Crash before DB mark installed")
            }
        }

        val payload = "Before DB update data".toByteArray()
        val path = "resources/before_db_update.txt"
        val manifest = createSampleManifest("pack_crash_before_db_up", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_before_db_up.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected crash")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        installer.reconcilePendingInstallations()
        assertFalse(File(baseStorageDir, "installed/pack_crash_before_db_up").exists())
    }

    @Test
    fun testProcessTermination_beforeJournalDeletion() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        installer.boundaryCrashHook = { boundary ->
            if (boundary == "BEFORE_JOURNAL_DELETION") {
                throw ContentPackInstaller.SimulatedCrashException("Crash before journal deletion")
            }
        }

        val payload = "Before journal delete data".toByteArray()
        val path = "resources/before_journal_del.txt"
        val manifest = createSampleManifest("pack_crash_before_j_del", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "crash_before_j_del.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        try {
            installer.installPack(packFile, keyPair.public)
            fail("Expected crash")
        } catch (_: ContentPackInstaller.SimulatedCrashException) {}

        // Journal still exists because crash occurred right before deleting it
        val journal = File(baseStorageDir, ".staging/pack_crash_before_j_del.journal")
        assertTrue(journal.exists())

        // Reconcile on startup cleans up the stranded journal
        installer.reconcilePendingInstallations()
        assertFalse(journal.exists())
    }

    @Test
    fun testProcessTermination_duringStartupReconciliation() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        val strandedDir = File(baseStorageDir, ".staging/stranded_mid_recon")
        strandedDir.mkdirs()
        File(strandedDir, "file.txt").writeText("temp")
        val journal = File(baseStorageDir, ".staging/stranded_mid_recon.journal")
        journal.writeText("packId=stranded_mid_recon\nstate=EXTRACTING_PAYLOAD_STAGING\n")

        // First reconciliation attempt
        installer.reconcilePendingInstallations()
        assertFalse(strandedDir.exists())
        assertFalse(journal.exists())

        // Second idempotent reconciliation attempt
        installer.reconcilePendingInstallations()
        assertFalse(strandedDir.exists())
        assertFalse(journal.exists())
    }

    // ── 16. Gate 6.2.3 Installed Pack Deep Integrity Audit ─────────────────────

    @Test
    fun testAuditInstalledPackIntegrity_validPackPasses() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Clean valid payload".toByteArray()
        val path = "resources/valid.txt"
        val manifest = createSampleManifest("pack_deep_valid", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "deep_valid.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        val result = installer.installPack(packFile, keyPair.public)
        assertTrue(result is ContentPackInstaller.InstallResult.Success)

        assertTrue(installer.auditInstalledPackIntegrity("pack_deep_valid"))
    }

    @Test
    fun testAuditInstalledPackIntegrity_missingManifestFails() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val packDir = File(baseStorageDir, "installed/pack_no_manifest")
        packDir.mkdirs()
        File(packDir, "resources").mkdirs()
        File(packDir, "resources/file.txt").writeText("content")

        assertFalse(installer.auditInstalledPackIntegrity("pack_no_manifest"))
    }

    @Test
    fun testAuditInstalledPackIntegrity_tamperedFileSizeFails() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Original payload bytes".toByteArray()
        val path = "resources/test_size.txt"
        val manifest = createSampleManifest("pack_tamper_size", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "tamper_size.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        installer.installPack(packFile, keyPair.public)
        assertTrue(installer.auditInstalledPackIntegrity("pack_tamper_size"))

        // Tamper with file size (append bytes)
        val installedFile = File(baseStorageDir, "installed/pack_tamper_size/$path")
        installedFile.appendBytes("Tampered extra bytes".toByteArray())

        assertFalse("Size mismatch must fail integrity audit", installer.auditInstalledPackIntegrity("pack_tamper_size"))
    }

    @Test
    fun testAuditInstalledPackIntegrity_tamperedFileHashFails() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Exact same size1".toByteArray()
        val path = "resources/test_hash.txt"
        val manifest = createSampleManifest("pack_tamper_hash", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "tamper_hash.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        installer.installPack(packFile, keyPair.public)
        assertTrue(installer.auditInstalledPackIntegrity("pack_tamper_hash"))

        // Tamper content with exact same length to evade size check
        val installedFile = File(baseStorageDir, "installed/pack_tamper_hash/$path")
        installedFile.writeBytes("Exact same size2".toByteArray())

        assertFalse("Hash mismatch must fail integrity audit", installer.auditInstalledPackIntegrity("pack_tamper_hash"))
    }

    @Test
    fun testAuditInstalledPackIntegrity_undeclaredExtraFileFails() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)
        val payload = "Declared file".toByteArray()
        val path = "resources/declared.txt"
        val manifest = createSampleManifest("pack_undeclared", payloads = listOf(PayloadFileEntry(path, sha256(payload), payload.size.toLong())))
        val packFile = File(tempFolder.root, "undeclared.lmpack")
        ContentPackSecurity.createLmpackArchive(packFile, manifest, keyPair, mapOf(path to payload))

        installer.installPack(packFile, keyPair.public)
        assertTrue(installer.auditInstalledPackIntegrity("pack_undeclared"))

        // Inject unlisted backdoor/payload file into installed directory
        val rogueFile = File(baseStorageDir, "installed/pack_undeclared/resources/rogue.sh")
        rogueFile.writeText("echo evil")

        assertFalse("Undeclared extra file must fail integrity audit", installer.auditInstalledPackIntegrity("pack_undeclared"))
    }

    @Test
    fun testAuditInstalledPackIntegrity_purgesTamperedPackWithoutTouchingValidPack() {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = null)

        // 1. Install Pack A (Valid)
        val payloadA = "Pack A content".toByteArray()
        val pathA = "resources/pack_a.txt"
        val manifestA = createSampleManifest("pack_safe_a", payloads = listOf(PayloadFileEntry(pathA, sha256(payloadA), payloadA.size.toLong())))
        val packFileA = File(tempFolder.root, "safe_a.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileA, manifestA, keyPair, mapOf(pathA to payloadA))
        installer.installPack(packFileA, keyPair.public)

        // 2. Install Pack B (To be tampered)
        val payloadB = "Pack B content".toByteArray()
        val pathB = "resources/pack_b.txt"
        val manifestB = createSampleManifest("pack_corrupt_b", payloads = listOf(PayloadFileEntry(pathB, sha256(payloadB), payloadB.size.toLong())))
        val packFileB = File(tempFolder.root, "corrupt_b.lmpack")
        ContentPackSecurity.createLmpackArchive(packFileB, manifestB, keyPair, mapOf(pathB to payloadB))
        installer.installPack(packFileB, keyPair.public)

        // Tamper Pack B (delete resource file)
        File(baseStorageDir, "installed/pack_corrupt_b/$pathB").delete()

        // 3. Reconcile on startup
        installer.reconcilePendingInstallations()

        // Invariant: Pack A is untouched and remains installed; Pack B is cleanly purged
        assertTrue("Valid Pack A must be preserved", File(baseStorageDir, "installed/pack_safe_a/$pathA").exists())
        assertTrue("Valid Pack A must pass integrity", installer.auditInstalledPackIntegrity("pack_safe_a"))
        assertFalse("Tampered Pack B must be purged", File(baseStorageDir, "installed/pack_corrupt_b").exists())
    }
}


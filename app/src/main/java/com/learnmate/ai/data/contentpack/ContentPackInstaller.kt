package com.learnmate.ai.data.contentpack

import com.google.gson.Gson
import com.learnmate.ai.data.db.*
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * Handles staged installation, transaction commit, file finalization,
 * crash injection testing, and crash-recovery for LearnMate Content Packs.
 */
class ContentPackInstaller(
    private val baseStorageDir: File,
    private val dao: LearnMateDao? = null
) {

    private val stagingRootDir = File(baseStorageDir, ".staging")
    private val installedRootDir = File(baseStorageDir, "installed")
    private val gson = Gson()
    val installerTrustedKeys = mutableMapOf<String, PublicKey>()

    init {
        stagingRootDir.mkdirs()
        installedRootDir.mkdirs()
    }

    /**
     * 10-state lifecycle model for persistent, crash-safe content-pack installation.
     */
    enum class InstallState {
        IDLE,
        VALIDATING_ARCHIVE,
        VALIDATED,
        VERIFYING_DEPENDENCIES,
        EXTRACTING_PAYLOAD_STAGING,
        WRITING_DATABASE_RECORDS,
        FINALIZING_STORAGE,
        MARKING_DATABASE_INSTALLED,
        COMPLETED,
        FAILED_ROLLED_BACK
    }

    class SimulatedCrashException(message: String) : RuntimeException(message)

    var currentState: InstallState = InstallState.IDLE
        private set

    /**
     * Test hook to simulate process death or abrupt failure at a specific stage.
     */
    var crashInjectionPoint: InstallState? = null

    /**
     * Fine-grained test hook for process termination testing at exact execution boundaries.
     */
    var boundaryCrashHook: ((boundary: String) -> Unit)? = null

    sealed class InstallResult {
        data class Success(val packId: String, val title: String, val nodesInstalled: Int) : InstallResult()
        data class Failure(val errorCode: String, val message: String) : InstallResult()
    }

    private fun getJournalFile(packId: String): File = File(stagingRootDir, "$packId.journal")

    private fun transitionTo(
        packId: String,
        newState: InstallState,
        isUpdate: Boolean = false,
        oldVersion: String? = null,
        newVersion: String? = null
    ) {
        currentState = newState
        val journal = getJournalFile(packId)
        try {
            journal.writeText(
                "packId=$packId\n" +
                "state=$newState\n" +
                "isUpdate=$isUpdate\n" +
                "oldVersion=${oldVersion ?: ""}\n" +
                "newVersion=${newVersion ?: ""}\n" +
                "timestampEpochMs=${System.currentTimeMillis()}\n"
            )
        } catch (_: Exception) {
            // Best effort journal write
        }
        if (crashInjectionPoint == newState) {
            throw SimulatedCrashException("Simulated crash injected at state: $newState")
        }
    }

    /**
     * Executes the 8-stage recoverable installation process across the 10-state lifecycle.
     */
    fun installPack(
        archiveFile: File,
        trustedPublicKey: PublicKey? = null,
        allowTestKeyIfNull: Boolean = false
    ): InstallResult {
        var packId = "unknown_pack"
        try {
            // Stage 1: Validation
            transitionTo(packId, InstallState.VALIDATING_ARCHIVE)
            val validation = ContentPackSecurity.validateArchive(archiveFile, trustedPublicKey, allowTestKeyIfNull)
            if (!validation.isValid || validation.manifest == null) {
                transitionTo(packId, InstallState.FAILED_ROLLED_BACK)
                return InstallResult.Failure(
                    errorCode = validation.errorCode ?: "VALIDATION_FAILED",
                    message = validation.errorMessage ?: "Archive validation failed"
                )
            }

            val manifest = validation.manifest
            packId = manifest.packId
            val packStagingDir = File(stagingRootDir, packId)
            val packInstalledDir = File(installedRootDir, packId)
            val packBackupDir = File(stagingRootDir, "${packId}_backup")

            // Check if pack is already installed (Version Update Lifecycle)
            val existingPack = dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId && it.isInstalled }
                ?: if (packInstalledDir.exists() && File(packInstalledDir, "manifest.json").exists()) {
                    try {
                        gson.fromJson(File(packInstalledDir, "manifest.json").readText(), ContentPackManifest::class.java)?.let {
                            ContentPackEntity(
                                packId = it.packId,
                                title = it.title,
                                version = it.version,
                                minAppVersion = it.minAppVersion,
                                category = it.category,
                                primaryLanguage = it.primaryLanguage,
                                supportedLanguagesJson = gson.toJson(it.supportedLanguages),
                                authorOrProvider = it.authorOrProvider,
                                provenanceNotice = it.provenanceNotice,
                                checksumSha256 = "",
                                isInstalled = true,
                                isBuiltIn = false,
                                isDeprecated = false,
                                deprecationNotice = null,
                                installedAtEpochMs = 0L
                            )
                        }
                    } catch (_: Exception) { null }
                } else null

            val isUpdate = existingPack != null
            if (isUpdate) {
                val cmp = compareVersions(manifest.version, existingPack!!.version)
                if (cmp == 0) {
                    transitionTo(packId, InstallState.FAILED_ROLLED_BACK)
                    return InstallResult.Failure(
                        errorCode = "DUPLICATE_VERSION",
                        message = "Pack '$packId' version '${manifest.version}' is already installed"
                    )
                }
                if (cmp < 0) {
                    transitionTo(packId, InstallState.FAILED_ROLLED_BACK)
                    return InstallResult.Failure(
                        errorCode = "VERSION_DOWNGRADE_DISALLOWED",
                        message = "Cannot downgrade pack '$packId' from '${existingPack.version}' to '${manifest.version}'"
                    )
                }
            }

            transitionTo(packId, InstallState.VALIDATED, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)

            // Stage 1.5 (Update): Create backup of existing installed version
            if (isUpdate && packInstalledDir.exists()) {
                boundaryCrashHook?.invoke("UPDATE_BEFORE_BACKUP_CREATION")
                if (packBackupDir.exists()) packBackupDir.deleteRecursively()
                val backedUp = packInstalledDir.copyRecursively(packBackupDir, overwrite = true)
                if (!backedUp) {
                    transitionTo(packId, InstallState.FAILED_ROLLED_BACK)
                    return InstallResult.Failure("BACKUP_FAILED", "Failed to stage backup of existing pack version")
                }
                boundaryCrashHook?.invoke("UPDATE_AFTER_BACKUP_CREATION")
            }

            // Stage 2: Verify Dependencies Against Database
            transitionTo(packId, InstallState.VERIFYING_DEPENDENCIES, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)
            if (dao != null && manifest.dependencies.isNotEmpty()) {
                val installedPacks = dao.getAllContentPacksSync().filter { it.isInstalled }.map { it.packId }.toSet()
                for (dep in manifest.dependencies) {
                    if (!installedPacks.contains(dep.packId)) {
                        transitionTo(packId, InstallState.FAILED_ROLLED_BACK)
                        return InstallResult.Failure(
                            errorCode = "MISSING_DEPENDENCY",
                            message = "Required dependency '${dep.packId}' is not installed"
                        )
                    }
                }
            }

            // Stage 3: Stage Extracted Files
            transitionTo(packId, InstallState.EXTRACTING_PAYLOAD_STAGING, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)
            if (packStagingDir.exists()) {
                packStagingDir.deleteRecursively()
            }
            packStagingDir.mkdirs()

            boundaryCrashHook?.invoke("UPDATE_DURING_EXTRACTION")

            for ((relPath, data) in validation.extractedFiles) {
                val destFile = File(packStagingDir, relPath)
                destFile.parentFile?.mkdirs()
                destFile.writeBytes(data)
            }

            boundaryCrashHook?.invoke("UPDATE_AFTER_EXTRACTION")

            // Stage 4: Commit Database Records (marked isInstalled = 0 initially)
            transitionTo(packId, InstallState.WRITING_DATABASE_RECORDS, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)
            boundaryCrashHook?.invoke("UPDATE_DURING_DATABASE_UPDATE")

            if (dao != null) {
                val packEntity = ContentPackEntity(
                    packId = packId,
                    title = manifest.title,
                    version = manifest.version,
                    minAppVersion = manifest.minAppVersion,
                    category = manifest.category,
                    primaryLanguage = manifest.primaryLanguage,
                    supportedLanguagesJson = gson.toJson(manifest.supportedLanguages),
                    authorOrProvider = manifest.authorOrProvider,
                    provenanceNotice = manifest.provenanceNotice,
                    checksumSha256 = "TODO_CHECKSUM",
                    isInstalled = false, // Pending finalization
                    isBuiltIn = false,
                    isDeprecated = false,
                    deprecationNotice = null,
                    installedAtEpochMs = System.currentTimeMillis()
                )

                val dependencyEntities = manifest.dependencies.map {
                    PackDependencyEntity(packId = packId, dependencyPackId = it.packId, versionConstraint = it.versionConstraint)
                }

                val nodeEntities = manifest.curriculumNodes.map {
                    CurriculumNodeEntity(
                        nodeId = it.nodeId,
                        parentNodeId = it.parentNodeId,
                        packId = packId,
                        nodeType = it.nodeType,
                        code = it.code,
                        title = it.title,
                        description = it.description,
                        estimatedMinutes = it.estimatedMinutes,
                        orderIndex = it.orderIndex
                    )
                }

                val prereqEntities = manifest.prerequisites.map {
                    NodePrerequisiteEntity(
                        prerequisiteId = it.prerequisiteId,
                        targetNodeId = it.targetNodeId,
                        requiredNodeId = it.requiredNodeId,
                        requirementType = it.requirementType
                    )
                }

                val resourceEntities = manifest.resources.map {
                    CurriculumResourceEntity(
                        resourceId = it.resourceId,
                        nodeId = it.nodeId,
                        languageCode = it.languageCode,
                        verifiedExplanation = it.verifiedExplanation,
                        referenceTitle = it.referenceTitle,
                        referenceAuthor = it.referenceAuthor,
                        referenceEdition = it.referenceEdition,
                        referenceChapter = it.referenceChapter,
                        referenceSection = it.referenceSection,
                        referenceIdentifier = it.referenceIdentifier,
                        coreKeywordsJson = it.coreKeywordsJson
                    )
                }

                dao.insertContentPackSync(packEntity)
                dependencyEntities.forEach { dao.insertPackDependency(it) }
                nodeEntities.forEach { dao.insertCurriculumNode(it) }
                prereqEntities.forEach { dao.insertNodePrerequisite(it) }
                resourceEntities.forEach { dao.insertCurriculumResource(it) }
            }
            boundaryCrashHook?.invoke("AFTER_DATABASE_COMMIT")

            // Stage 5: Finalize Staged Files (Atomic replacement of installed directory)
            transitionTo(packId, InstallState.FINALIZING_STORAGE, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)
            boundaryCrashHook?.invoke("UPDATE_DURING_DIRECTORY_REPLACEMENT")
            boundaryCrashHook?.invoke("DURING_DIRECTORY_MOVEMENT")

            if (packInstalledDir.exists()) {
                packInstalledDir.deleteRecursively()
            }
            val moved = packStagingDir.renameTo(packInstalledDir)
            if (!moved) {
                // Fallback copy if cross-filesystem or atomic rename is unavailable
                packStagingDir.copyRecursively(packInstalledDir, overwrite = true)
                packStagingDir.deleteRecursively()
            }
            boundaryCrashHook?.invoke("UPDATE_AFTER_DIRECTORY_REPLACEMENT")
            boundaryCrashHook?.invoke("AFTER_FINAL_DIRECTORY_MOVEMENT")

            // Stage 6: Mark isInstalled = 1 in Database
            boundaryCrashHook?.invoke("BEFORE_INSTALLED_STATE_DATABASE_UPDATE")
            transitionTo(packId, InstallState.MARKING_DATABASE_INSTALLED, isUpdate = isUpdate, oldVersion = existingPack?.version, newVersion = manifest.version)
            dao?.markContentPackInstalled(packId, isInstalled = true)
            boundaryCrashHook?.invoke("AFTER_INSTALLED_STATE_UPDATE")

            // Stage 7: Post-finalize deep integrity verification
            boundaryCrashHook?.invoke("UPDATE_DURING_INTEGRITY_AUDIT")
            if (!auditInstalledPackIntegrity(packId, trustedPublicKey)) {
                // Restore backup if update failed
                if (isUpdate && packBackupDir.exists()) {
                    packInstalledDir.deleteRecursively()
                    packBackupDir.renameTo(packInstalledDir)
                    if (dao != null && existingPack != null) {
                        dao.insertContentPackSync(existingPack)
                    }
                }
                rollbackFailedInstall(packId)
                currentState = InstallState.FAILED_ROLLED_BACK
                return InstallResult.Failure(
                    errorCode = "UPDATE_INTEGRITY_AUDIT_FAILED",
                    message = "Integrity audit failed for installed pack '$packId'. Restored previous working version."
                )
            }

            // Stage 8: Mark Completed & Clean Journal & Update Backup
            currentState = InstallState.COMPLETED
            boundaryCrashHook?.invoke("UPDATE_BEFORE_BACKUP_DELETION")
            boundaryCrashHook?.invoke("BEFORE_JOURNAL_DELETION")
            getJournalFile(packId).delete()
            if (packBackupDir.exists()) {
                packBackupDir.deleteRecursively()
            }
            boundaryCrashHook?.invoke("UPDATE_AFTER_BACKUP_DELETION")

            if (trustedPublicKey != null) {
                installerTrustedKeys[packId] = trustedPublicKey
            }

            return InstallResult.Success(
                packId = packId,
                title = manifest.title,
                nodesInstalled = manifest.curriculumNodes.size
            )

        } catch (crash: SimulatedCrashException) {
            // A crash halts execution immediately without clean in-memory rollback,
            // leaving partial artifacts on disk for startup reconciliation.
            throw crash
        } catch (e: Exception) {
            // Stage 8: Rollback on Graceful Exception (preserving previous working curriculum)
            val packBackupDir = File(stagingRootDir, "${packId}_backup")
            val packInstalledDir = File(installedRootDir, packId)
            if (packBackupDir.exists()) {
                if (packInstalledDir.exists()) packInstalledDir.deleteRecursively()
                packBackupDir.renameTo(packInstalledDir)
            }
            rollbackFailedInstall(packId)
            currentState = InstallState.FAILED_ROLLED_BACK
            return InstallResult.Failure(
                errorCode = "INSTALLATION_INTERRUPTED",
                message = "Installation failed and rolled back cleanly: ${e.message}"
            )
        }
    }

    /**
     * Cleans up incomplete staging directories, deletes journal, and rolls back unfinalized DB records.
     */
    private fun rollbackFailedInstall(packId: String) {
        val packStagingDir = File(stagingRootDir, packId)
        if (packStagingDir.exists()) {
            packStagingDir.deleteRecursively()
        }
        val journal = getJournalFile(packId)
        if (journal.exists()) {
            journal.delete()
        }
        val packInstalledDir = File(installedRootDir, packId)
        if (!packInstalledDir.exists()) {
            dao?.deleteContentPackSync(packId)
        }
    }

    internal fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Strict verification of an installed content pack directory against manifest,
     * Ed25519 publisher signature, revocation status, payload hashes, and DB records.
     * Enforces:
     * - Manifest exists and is valid format version
     * - Pack ID and version match DB
     * - Signature file exists and is valid 64-byte Ed25519 signature
     * - Publisher key is recognized and NOT revoked
     * - Signature mathematically verifies over manifest.json bytes
     * - Declared payload paths are valid and safe
     * - All declared files exist on disk
     * - File sizes match manifest declarations exactly
     * - SHA-256 hashes match manifest declarations exactly
     * - No undeclared extra files exist in installed directory
     * - Database state is consistent (isInstalled == true)
     */
    fun auditInstalledPackIntegrity(packId: String, trustedPublicKey: PublicKey? = null): Boolean {
        val installedDir = File(installedRootDir, packId)
        if (!installedDir.exists() || !installedDir.isDirectory) return false

        // 1. Verify DB record if DAO is available
        val dbPack = dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId }
        if (dao != null && (dbPack == null || !dbPack.isInstalled)) {
            return false
        }

        // 2. Verify manifest.json exists and parses
        val manifestFile = File(installedDir, "manifest.json")
        if (!manifestFile.exists() || !manifestFile.isFile) return false

        val manifest = try {
            gson.fromJson(manifestFile.readText(), ContentPackManifest::class.java)
        } catch (_: Exception) {
            return false
        } ?: return false

        if (!manifest.formatVersion.startsWith("1.") || manifest.packId != packId) return false
        if (dbPack != null && (dbPack.version != manifest.version || dbPack.packId != manifest.packId)) return false

        // 3. Mandatory Ed25519 signature verification against trusted, non-revoked publisher key
        val sigFile = File(installedDir, "signature.bin")
        if (!sigFile.exists() || !sigFile.isFile || sigFile.length() != 64L) return false

        // Look up ONLY the exact publisherKeyId in the trusted publisher-key registry
        val trustedB64 = ContentPackSecurity.TRUSTED_PUBLISHER_KEYS[manifest.publisherKeyId] ?: return false
        val pubKeyBytes = try {
            Base64.getDecoder().decode(trustedB64)
        } catch (_: Exception) {
            return false
        } ?: return false

        if (pubKeyBytes.isEmpty() || !ContentPackSecurity.isValidPublicKeyEncoding(pubKeyBytes)) {
            return false
        }

        // Caller-provided key must match the registered key for this publisher ID (cannot inject arbitrary untrusted keys)
        if (trustedPublicKey != null) {
            val callerRaw = ContentPackSecurity.extractRawPublicKeyBytes(trustedPublicKey.encoded)
            val registeredRaw = ContentPackSecurity.extractRawPublicKeyBytes(pubKeyBytes)
            if (callerRaw == null || registeredRaw == null || !callerRaw.contentEquals(registeredRaw)) {
                return false
            }
        }

        if (ContentPackSecurity.isKeyRevoked(pubKeyBytes)) {
            return false
        }

        val manifestBytes = manifestFile.readBytes()
        val signatureBytes = sigFile.readBytes()
        if (!ContentPackSecurity.verifyEd25519Signature(pubKeyBytes, manifestBytes, signatureBytes)) {
            return false
        }

        // 4. Track expected relative files
        val expectedRelativePaths = mutableSetOf<String>()
        expectedRelativePaths.add("manifest.json")
        expectedRelativePaths.add("signature.bin")

        // 5. Verify all declared payload files
        for (payloadEntry in manifest.payloadFiles) {
            val relPath = payloadEntry.path
            if (relPath.contains("..") || relPath.startsWith("/") || relPath.startsWith("\\") || relPath.contains(":")) {
                return false
            }
            val targetFile = File(installedDir, relPath)
            if (!targetFile.exists() || !targetFile.isFile) return false
            if (targetFile.length() != payloadEntry.sizeBytes) return false
            val actualHash = calculateSha256(targetFile)
            if (!actualHash.equals(payloadEntry.sha256, ignoreCase = true)) return false

            expectedRelativePaths.add(relPath)
        }

        // 5. Verify no undeclared extra files exist
        val actualFiles = installedDir.walkTopDown().filter { it.isFile }.toList()
        for (file in actualFiles) {
            val relPath = file.relativeTo(installedDir).path.replace('\\', '/')
            if (!expectedRelativePaths.contains(relPath)) {
                return false
            }
        }

        return true
    }

    /**
     * Startup reconciliation: audits staging and installed storage vs database records
     * and idempotently purges unfinalized or tampered state from any interrupted installation.
     */
    fun reconcilePendingInstallations() {
        // 1. Audit and purge/rollback based on journal files in staging
        val journalFiles = stagingRootDir.listFiles { _, name -> name.endsWith(".journal") }
        journalFiles?.forEach { journal ->
            val lines = try {
                journal.readLines().associate { line ->
                    val parts = line.split("=", limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
                }
            } catch (_: Exception) {
                emptyMap()
            }
            val packId = lines["packId"]
            val state = lines["state"]
            val isUpdate = lines["isUpdate"]?.toBoolean() ?: false
            val oldVersion = lines["oldVersion"]

            if (!packId.isNullOrEmpty()) {
                val installedDir = File(installedRootDir, packId)
                val packBackupDir = File(stagingRootDir, "${packId}_backup")
                val packStagingDir = File(stagingRootDir, packId)
                val wasMarkedInstalled = state == InstallState.MARKING_DATABASE_INSTALLED.name || state == InstallState.COMPLETED.name

                if (wasMarkedInstalled && auditInstalledPackIntegrity(packId)) {
                    // Update/install finalized cleanly and passed deep audit: clean up leftover staging files and backup
                    if (packStagingDir.exists()) packStagingDir.deleteRecursively()
                    if (packBackupDir.exists()) packBackupDir.deleteRecursively()
                } else if (packBackupDir.exists()) {
                    // Update was interrupted before finalization or failed audit: RESTORE BACKUP!
                    if (installedDir.exists()) installedDir.deleteRecursively()
                    val restored = packBackupDir.renameTo(installedDir)
                    if (!restored) {
                        packBackupDir.copyRecursively(installedDir, overwrite = true)
                        packBackupDir.deleteRecursively()
                    }
                    if (packStagingDir.exists()) packStagingDir.deleteRecursively()

                    // Resync DB to old version
                    if (dao != null) {
                        val manifestFile = File(installedDir, "manifest.json")
                        if (manifestFile.exists()) {
                            try {
                                val oldManifest = gson.fromJson(manifestFile.readText(), ContentPackManifest::class.java)
                                if (oldManifest != null) {
                                    val restoredEntity = ContentPackEntity(
                                        packId = oldManifest.packId,
                                        title = oldManifest.title,
                                        version = oldManifest.version,
                                        minAppVersion = oldManifest.minAppVersion,
                                        category = oldManifest.category,
                                        primaryLanguage = oldManifest.primaryLanguage,
                                        supportedLanguagesJson = gson.toJson(oldManifest.supportedLanguages),
                                        authorOrProvider = oldManifest.authorOrProvider,
                                        provenanceNotice = oldManifest.provenanceNotice,
                                        checksumSha256 = "",
                                        isInstalled = true,
                                        isBuiltIn = false,
                                        isDeprecated = false,
                                        deprecationNotice = null,
                                        installedAtEpochMs = System.currentTimeMillis()
                                    )
                                    dao.insertContentPackSync(restoredEntity)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                } else if (isUpdate && installedDir.exists() && auditInstalledPackIntegrity(packId)) {
                    // Update crashed before backup was created or directory was replaced:
                    // The existing installed version is completely intact and healthy! Preserve it!
                    if (packStagingDir.exists()) packStagingDir.deleteRecursively()
                } else {
                    // Incomplete fresh installation or corrupted installed directory: roll back cleanly
                    rollbackFailedInstall(packId)
                    if (installedDir.exists()) {
                        installedDir.deleteRecursively()
                    }
                }
            }
            journal.delete()
        }

        // 2. Scan for orphaned backup directories (in case journal was lost or deleted)
        val backupDirs = stagingRootDir.listFiles { file -> file.isDirectory && file.name.endsWith("_backup") }
        backupDirs?.forEach { backupDir ->
            val packId = backupDir.name.removeSuffix("_backup")
            val installedDir = File(installedRootDir, packId)
            if (!installedDir.exists() || !auditInstalledPackIntegrity(packId)) {
                if (installedDir.exists()) installedDir.deleteRecursively()
                backupDir.renameTo(installedDir)
            } else {
                backupDir.deleteRecursively()
            }
        }

        // 3. Purge any remaining stranded .staging subdirectories
        val staged = stagingRootDir.listFiles { file -> file.isDirectory }
        staged?.forEach { dir ->
            dir.deleteRecursively()
        }

        // 4. Scan DB for unfinalized packs (isInstalled == false) and purge them
        if (dao != null) {
            val allPacks = dao.getAllContentPacksSync()
            allPacks.filter { !it.isInstalled }.forEach { unfinalizedPack ->
                dao.deleteContentPackSync(unfinalizedPack.packId)
                val installedDir = File(installedRootDir, unfinalizedPack.packId)
                if (installedDir.exists()) {
                    installedDir.deleteRecursively()
                }
            }
        }

        // 5. Audit all directories in installedRootDir: purge any that fail deep integrity audit
        val installedDirs = installedRootDir.listFiles { file -> file.isDirectory }
        installedDirs?.forEach { dir ->
            val packId = dir.name
            if (!auditInstalledPackIntegrity(packId)) {
                rollbackFailedInstall(packId)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }

        // 6. Purge abandoned temporary SAF import files left behind by forced process terminations
        ContentPackDocumentImporter.reconcilePendingSafImports(baseStorageDir, maxAgeMs = 0L, isStartup = true)
    }

    companion object {
        /**
         * Compares two semantic version strings (e.g. "1.0.0" vs "1.1.0").
         * Returns positive if v1 > v2, negative if v1 < v2, zero if equal.
         */
        fun compareVersions(v1: String, v2: String): Int {
            val p1 = v1.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val p2 = v2.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val maxLen = maxOf(p1.size, p2.size)
            for (i in 0 until maxLen) {
                val n1 = p1.getOrElse(i) { 0 }
                val n2 = p2.getOrElse(i) { 0 }
                if (n1 != n2) return n1.compareTo(n2)
            }
            return 0
        }
    }
}


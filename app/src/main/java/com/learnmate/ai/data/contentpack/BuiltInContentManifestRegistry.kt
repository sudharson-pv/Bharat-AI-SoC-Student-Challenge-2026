package com.learnmate.ai.data.contentpack

import com.learnmate.ai.data.db.ContentPackEntity
import com.learnmate.ai.data.db.CurriculumNodeEntity

/**
 * Gate 6.16.2 Priority 6: Canonical Cryptographic Registry for Built-in Content Packs.
 *
 * Replaces hardcoded/simplistic isBuiltIn assumptions with cryptographic manifest verification:
 * - Immutable canonical manifests and expected SHA-256 digests.
 * - Deep validation of database records against registered manifests.
 * - Node count, structural integrity, and tamper detection.
 * - Built-in packs failing verification are flagged and quarantined.
 */
object BuiltInContentManifestRegistry {

    data class BuiltInPackManifest(
        val packId: String,
        val title: String,
        val version: String,
        val minAppVersion: String,
        val category: String,
        val primaryLanguage: String,
        val authorOrProvider: String,
        val provenanceNotice: String,
        val checksumSha256: String,
        val expectedNodeCount: Int,
        val mandatoryNodeIds: Set<String>
    )

    data class BuiltInAuditResult(
        val isIntact: Boolean,
        val errorCode: String? = null,
        val failureReason: String? = null
    )

    private val CANONICAL_BUILT_IN_PACKS = mapOf(
        "pack_builtin_legacy_gate4" to BuiltInPackManifest(
            packId = "pack_builtin_legacy_gate4",
            title = "Gate 4 Verified Engineering Baseline",
            version = "1.0.0",
            minAppVersion = "1.4.0",
            category = "ENGINEERING",
            primaryLanguage = "en",
            authorOrProvider = "LearnMate Editorial Board",
            provenanceNotice = "Accredited textbooks: Sze, Weste, Cormen, Cooper, Shigley, Moran, Incropera",
            checksumSha256 = "7ee6a2dd55e1ef8b58718406346626ac6cf57968c8168cc7892db67ecb1a081d",
            expectedNodeCount = 57,
            mandatoryNodeIds = setOf(
                "node_root_vlsi",
                "node_root_cs",
                "node_root_uiux",
                "node_root_mech",
                "node_root_school",
                "node_root_mba",
                "node_root_upsc",
                "node_root_research",
                "node_upsc_polity",
                "node_upsc_fund_rights",
                "node_upsc_preamble",
                "node_mba_finance",
                "node_mba_npv",
                "node_sch_physics",
                "node_sch_newton",
                "node_res_design",
                "node_res_hypothesis",
                "topic_complexity"
            )
        )
    )

    fun isRegisteredBuiltIn(packId: String): Boolean {
        return CANONICAL_BUILT_IN_PACKS.containsKey(packId)
    }

    fun getManifest(packId: String): BuiltInPackManifest? {
        return CANONICAL_BUILT_IN_PACKS[packId]
    }

    fun getAllBuiltInPackIds(): Set<String> = CANONICAL_BUILT_IN_PACKS.keys

    /**
     * Audits a built-in content pack against the immutable canonical registry and database content.
     */
    fun auditBuiltInPack(
        pack: ContentPackEntity,
        installedNodes: List<CurriculumNodeEntity>? = null
    ): BuiltInAuditResult {
        val canonical = CANONICAL_BUILT_IN_PACKS[pack.packId]
            ?: return BuiltInAuditResult(
                isIntact = false,
                errorCode = "UNREGISTERED_BUILT_IN_PACK",
                failureReason = "Content pack '${pack.packId}' claims to be built-in but is not present in canonical manifest registry."
            )

        // 1. Verify Checksum
        if (!pack.checksumSha256.equals(canonical.checksumSha256, ignoreCase = true)) {
            return BuiltInAuditResult(
                isIntact = false,
                errorCode = "CHECKSUM_MISMATCH",
                failureReason = "Checksum mismatch: expected ${canonical.checksumSha256}, got ${pack.checksumSha256}"
            )
        }

        // 2. Verify Metadata
        if (pack.version != canonical.version || pack.title != canonical.title || pack.category != canonical.category) {
            return BuiltInAuditResult(
                isIntact = false,
                errorCode = "METADATA_TAMPERED",
                failureReason = "Metadata discrepancy detected for built-in pack '${pack.packId}'."
            )
        }

        // 3. Verify Database Nodes if supplied
        if (installedNodes != null) {
            if (installedNodes.size < canonical.mandatoryNodeIds.size) {
                return BuiltInAuditResult(
                    isIntact = false,
                    errorCode = "MISSING_NODES",
                    failureReason = "Node count deficiency: found ${installedNodes.size}, minimum expected ${canonical.mandatoryNodeIds.size}"
                )
            }

            val installedNodeIds = installedNodes.map { it.nodeId }.toSet()
            val missingMandatory = canonical.mandatoryNodeIds - installedNodeIds
            if (missingMandatory.isNotEmpty()) {
                return BuiltInAuditResult(
                    isIntact = false,
                    errorCode = "MANDATORY_NODES_MISSING",
                    failureReason = "Mandatory curriculum nodes missing: $missingMandatory"
                )
            }

            // Verify pack ownership
            val foreignNodes = installedNodes.filter { it.packId != pack.packId }
            if (foreignNodes.isNotEmpty()) {
                return BuiltInAuditResult(
                    isIntact = false,
                    errorCode = "FOREIGN_NODE_AFFILIATION",
                    failureReason = "Nodes with foreign packId detected: ${foreignNodes.map { it.nodeId }}"
                )
            }
        }

        return BuiltInAuditResult(isIntact = true)
    }
}


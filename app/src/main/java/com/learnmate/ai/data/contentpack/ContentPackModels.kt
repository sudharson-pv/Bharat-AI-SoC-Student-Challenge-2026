package com.learnmate.ai.data.contentpack

/**
 * Data contracts and models for offline-first LearnMate Content Packs (.lmpack).
 */
data class ContentPackManifest(
    val formatVersion: String = "1.0.0",
    val packId: String,
    val version: String,
    val minAppVersion: String = "1.4.0",
    val title: String,
    val category: String,
    val primaryLanguage: String = "en",
    val supportedLanguages: List<String> = listOf("en"),
    val authorOrProvider: String,
    val provenanceNotice: String,
    val publisherKeyId: String,
    val dependencies: List<PackDependencyItem> = emptyList(),
    val curriculumNodes: List<CurriculumNodeItem> = emptyList(),
    val prerequisites: List<PrerequisiteItem> = emptyList(),
    val resources: List<ResourceItem> = emptyList(),
    val payloadFiles: List<PayloadFileEntry> = emptyList()
)

data class PackDependencyItem(
    val packId: String,
    val versionConstraint: String
)

data class CurriculumNodeItem(
    val nodeId: String,
    val parentNodeId: String?,
    val nodeType: String,
    val code: String,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int = 30,
    val orderIndex: Int = 0
)

data class PrerequisiteItem(
    val prerequisiteId: String,
    val targetNodeId: String,
    val requiredNodeId: String,
    val requirementType: String = "MANDATORY"
)

data class ResourceItem(
    val resourceId: String,
    val nodeId: String,
    val languageCode: String = "en",
    val verifiedExplanation: String,
    val referenceTitle: String,
    val referenceAuthor: String,
    val referenceEdition: String,
    val referenceChapter: String,
    val referenceSection: String,
    val referenceIdentifier: String,
    val coreKeywordsJson: String = "[]"
)

data class PayloadFileEntry(
    val path: String,
    val sha256: String,
    val sizeBytes: Long
)


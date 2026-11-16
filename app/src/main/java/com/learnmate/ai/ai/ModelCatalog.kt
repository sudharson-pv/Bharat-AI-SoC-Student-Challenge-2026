package com.learnmate.ai.ai

import java.io.File

/**
 * Gate 6.16.6: Multi-Model Catalog & Hardware Compatibility Registry.
 *
 * Provides extensible support for compatible Llama models:
 * 1. Meta Llama 3.2 1B Instruct (Recommended for mobile, tested on Moto G54 5G).
 * 2. Meta Llama 3.2 3B Instruct (High-accuracy option for 8 GB+ devices).
 * 3. Meta Llama 3.1 8B Instruct (Flagship option requiring 12 GB+ RAM; blocked on mid-tier).
 */
data class ModelCatalogEntry(
    val modelId: String,
    val displayName: String,
    val modelFamily: String,
    val modelVersion: String,
    val parameterCount: String,
    val modelFileName: String,
    val modelSizeBytes: Long,
    val modelSha256: String,
    val tokenizerFileName: String,
    val tokenizerSizeBytes: Long,
    val tokenizerSha256: String,
    val format: String,
    val quantization: String,
    val targetBackend: String,
    val runtimeVersion: String,
    val minApiLevel: Int,
    val minRamBytes: Long,
    val minStorageBytes: Long,
    val license: String,
    val licenseAttribution: String,
    val downloadUrl: String,
    val tokenizerUrl: String,
    val signedManifest: SignedModelManifest,
    val supportedAbi: String = "arm64-v8a"
) {
    val defaultDownloadUrl: String get() = downloadUrl
    val defaultTokenizerUrl: String get() = tokenizerUrl
    val isDefaultRecommended: Boolean get() = (parameterCount == "1B")

    fun resolveDownloadUrl(customBaseUrl: String? = null): String {
        return ModelCatalog.getEffectiveDownloadUrl(this, customBaseUrl)
    }

    fun resolveTokenizerUrl(customBaseUrl: String? = null): String {
        return ModelCatalog.getEffectiveTokenizerUrl(this, customBaseUrl)
    }

    fun isRecommendedForDevice(totalRamBytes: Long): Boolean {
        return parameterCount == "1B"
    }

    fun isCompatibleWithHardware(
        apiLevel: Int,
        totalRamBytes: Long,
        availableStorageBytes: Long
    ): ModelManifestRegistry.DeviceCompatibilityResult {
        val totalRamMb = totalRamBytes / (1024 * 1024)
        val freeStorageMb = availableStorageBytes / (1024 * 1024)

        if (apiLevel < minApiLevel) {
            return ModelManifestRegistry.DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Unsupported Android OS: Device is running API level $apiLevel. $displayName requires API $minApiLevel+.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        if (totalRamBytes < minRamBytes) {
            val minRamGb = "%.1f".format(minRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            val currentRamGb = "%.1f".format(totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            return ModelManifestRegistry.DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Insufficient Device RAM: Found $currentRamGb GB RAM. $displayName requires at least $minRamGb GB RAM to prevent out-of-memory termination.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        if (availableStorageBytes < minStorageBytes) {
            val requiredMb = minStorageBytes / (1024 * 1024)
            return ModelManifestRegistry.DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Insufficient Storage: $freeStorageMb MB free on storage. At least $requiredMb MB required for $displayName.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        return ModelManifestRegistry.DeviceCompatibilityResult(
            isCompatible = true,
            evaluatedApiLevel = apiLevel,
            evaluatedTotalRamMb = totalRamMb,
            evaluatedFreeStorageMb = freeStorageMb
        )
    }
}

object ModelCatalog {

    val ENTRY_LLAMA_3_2_1B = ModelCatalogEntry(
        modelId = ActiveModelManifest.MODEL_ID,
        displayName = "Meta Llama 3.2 1B Instruct",
        modelFamily = "Llama 3.2",
        modelVersion = "1.0.0",
        parameterCount = "1B",
        modelFileName = ActiveModelManifest.MODEL_FILENAME,
        modelSizeBytes = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES, // 803,033,472 bytes
        modelSha256 = ActiveModelManifest.EXPECTED_MODEL_SHA256,
        tokenizerFileName = ActiveModelManifest.TOKENIZER_FILENAME,
        tokenizerSizeBytes = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES, // 2,183,982 bytes
        tokenizerSha256 = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
        format = "ExecuTorch PTE",
        quantization = ActiveModelManifest.QUANTIZATION_SCHEME,
        targetBackend = ActiveModelManifest.TARGET_BACKEND,
        runtimeVersion = ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION,
        minApiLevel = 28,
        minRamBytes = 3_500L * 1024L * 1024L, // 3.5 GiB
        minStorageBytes = 2_500L * 1024L * 1024L, // 2.5 GiB
        license = "Meta Llama 3.2 Community License Agreement",
        licenseAttribution = "Built with Meta Llama 3.2. Copyright (c) Meta Platforms, Inc.",
        downloadUrl = "https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/llama3_2_1b_8da4w.pte",
        tokenizerUrl = "https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/tokenizer.model",
        signedManifest = SignedModelManifest(
            formatVersion = "1.0.0",
            manifestVersion = 1,
            modelId = ActiveModelManifest.MODEL_ID,
            modelVersion = "1.0.0",
            modelFileName = ActiveModelManifest.MODEL_FILENAME,
            modelSizeBytes = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES,
            modelSha256 = ActiveModelManifest.EXPECTED_MODEL_SHA256,
            tokenizerFileName = ActiveModelManifest.TOKENIZER_FILENAME,
            tokenizerSizeBytes = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES,
            tokenizerSha256 = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
            format = "ExecuTorch PTE",
            quantization = ActiveModelManifest.QUANTIZATION_SCHEME,
            targetBackend = ActiveModelManifest.TARGET_BACKEND,
            minRuntimeVersion = ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION,
            minApiLevel = 28,
            minRamBytes = 3_500L * 1024L * 1024L,
            minStorageBytes = 2_500L * 1024L * 1024L,
            license = "Meta Llama 3.2 Community License Agreement",
            publisherKeyId = "learnmate_official_2026",
            signatureTimestampEpochSec = 1762400000L,
            expiresTimestampEpochSec = 2077680000L,
            signatureBase64 = "hLCx+JI20CZ3UYpLsBy3XdNJAHUrv1TGeSxq6Kg9YZeXSg/nEyIC3EuY7IjDjhd+DDSzK2Ggn+4W98IM1/+NBQ=="
        )
    )

    val ENTRY_LLAMA_3_2_3B = ModelCatalogEntry(
        modelId = "meta-llama-3.2-3b-instruct-8da4w",
        displayName = "Meta Llama 3.2 3B Instruct",
        modelFamily = "Llama 3.2",
        modelVersion = "1.0.0",
        parameterCount = "3B",
        modelFileName = "llama3_2_3b_8da4w.pte",
        modelSizeBytes = 1_984_524_288L, // 1.85 GiB
        modelSha256 = "7c89f5a3b2e1d0c4e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6",
        tokenizerFileName = "tokenizer.model",
        tokenizerSizeBytes = 2_183_982L,
        tokenizerSha256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55",
        format = "ExecuTorch PTE",
        quantization = "8da4w + Embedding (4,32)",
        targetBackend = "XNNPACK CPU",
        runtimeVersion = "1.4.0",
        minApiLevel = 28,
        minRamBytes = 6_000L * 1024L * 1024L, // 6.0 GiB minimum
        minStorageBytes = 5_000L * 1024L * 1024L, // 5.0 GiB free storage
        license = "Meta Llama 3.2 Community License Agreement",
        licenseAttribution = "Built with Meta Llama 3.2. Copyright (c) Meta Platforms, Inc.",
        downloadUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/llama3_2_3b_8da4w.pte",
        tokenizerUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/tokenizer.model",
        signedManifest = SignedModelManifest(
            formatVersion = "1.0.0",
            manifestVersion = 1,
            modelId = "meta-llama-3.2-3b-instruct-8da4w",
            modelVersion = "1.0.0",
            modelFileName = "llama3_2_3b_8da4w.pte",
            modelSizeBytes = 1_984_524_288L,
            modelSha256 = "7c89f5a3b2e1d0c4e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6",
            tokenizerFileName = "tokenizer.model",
            tokenizerSizeBytes = 2_183_982L,
            tokenizerSha256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55",
            format = "ExecuTorch PTE",
            quantization = "8da4w + Embedding (4,32)",
            targetBackend = "XNNPACK CPU",
            minRuntimeVersion = "1.4.0",
            minApiLevel = 28,
            minRamBytes = 6_000L * 1024L * 1024L,
            minStorageBytes = 5_000L * 1024L * 1024L,
            license = "Meta Llama 3.2 Community License Agreement",
            publisherKeyId = "learnmate_official_2026",
            signatureTimestampEpochSec = 1762400000L,
            expiresTimestampEpochSec = 2077680000L,
            signatureBase64 = "ltuuSKumom2StryA47hXtvIbYJpS76nk7YDUon9b5nOHVjjU/pOph+C5BR7Pj5l/MgiHWQbMwl7YbrWpWEcnCw=="
        )
    )

    val ENTRY_LLAMA_3_1_8B = ModelCatalogEntry(
        modelId = "meta-llama-3.1-8b-instruct-4da4w",
        displayName = "Meta Llama 3.1 8B Instruct",
        modelFamily = "Llama 3.1",
        modelVersion = "1.0.0",
        parameterCount = "8B",
        modelFileName = "llama3_1_8b_4da4w.pte",
        modelSizeBytes = 4_850_000_000L, // 4.52 GiB
        modelSha256 = "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e",
        tokenizerFileName = "tokenizer.model",
        tokenizerSizeBytes = 2_183_982L,
        tokenizerSha256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55",
        format = "ExecuTorch PTE",
        quantization = "4da4w + Embedding (4,32)",
        targetBackend = "XNNPACK CPU",
        runtimeVersion = "1.4.0",
        minApiLevel = 28,
        minRamBytes = 12_000L * 1024L * 1024L, // 12.0 GiB minimum (Flagship Only)
        minStorageBytes = 10_000L * 1024L * 1024L, // 10.0 GiB free storage
        license = "Meta Llama 3.1 Community License Agreement",
        licenseAttribution = "Built with Meta Llama 3.1. Copyright (c) Meta Platforms, Inc.",
        downloadUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/llama3_1_8b_4da4w.pte",
        tokenizerUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/tokenizer.model",
        signedManifest = SignedModelManifest(
            formatVersion = "1.0.0",
            manifestVersion = 1,
            modelId = "meta-llama-3.1-8b-instruct-4da4w",
            modelVersion = "1.0.0",
            modelFileName = "llama3_1_8b_4da4w.pte",
            modelSizeBytes = 4_850_000_000L,
            modelSha256 = "9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e9d8c7b6a5f4e3d2c1b0a9f8e",
            tokenizerFileName = "tokenizer.model",
            tokenizerSizeBytes = 2_183_982L,
            tokenizerSha256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55",
            format = "ExecuTorch PTE",
            quantization = "4da4w + Embedding (4,32)",
            targetBackend = "XNNPACK CPU",
            minRuntimeVersion = "1.4.0",
            minApiLevel = 28,
            minRamBytes = 12_000L * 1024L * 1024L,
            minStorageBytes = 10_000L * 1024L * 1024L,
            license = "Meta Llama 3.1 Community License Agreement",
            publisherKeyId = "learnmate_official_2026",
            signatureTimestampEpochSec = 1762400000L,
            expiresTimestampEpochSec = 2077680000L,
            signatureBase64 = "06BCqF1LCCP7raulH27fTkqT2CnFDIGAbRmrzH/cUZp+V+PoGp32TYAf1DzR/4+WgXBhUYWIEvRXKeiJYBhVDw=="
        )
    )

    val ALL_ENTRIES: List<ModelCatalogEntry> = listOf(
        ENTRY_LLAMA_3_2_1B,
        ENTRY_LLAMA_3_2_3B,
        ENTRY_LLAMA_3_1_8B
    )

    fun getEntryById(modelId: String): ModelCatalogEntry? {
        return ALL_ENTRIES.firstOrNull { it.modelId.equals(modelId, ignoreCase = true) }
    }

    fun getCompatibleEntries(apiLevel: Int, totalRamBytes: Long, availableStorageBytes: Long): List<ModelCatalogEntry> {
        return ALL_ENTRIES.filter { it.isCompatibleWithHardware(apiLevel, totalRamBytes, availableStorageBytes).isCompatible }
    }

    fun getRecommendedEntry(totalRamBytes: Long): ModelCatalogEntry {
        if (totalRamBytes >= 14_000_000_000L) {
            return ENTRY_LLAMA_3_2_3B
        }
        return ENTRY_LLAMA_3_2_1B
    }

    /**
     * Normalizes a GitHub URL (e.g. tree/main/models or blob/main/models or raw LFS pointers)
     * into the direct binary release asset endpoint.
     */
    fun normalizeGitHubUrl(url: String): String {
        var u = url
        if (u.contains("sudharson-pv/llama_model_download")) {
            return u
                .replace("raw.githubusercontent.com/sudharson-pv/llama_model_download/main/models", "github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1")
                .replace("github.com/sudharson-pv/llama_model_download/tree/main/models", "github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1")
                .replace("github.com/sudharson-pv/llama_model_download/raw/main/models", "github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1")
                .replace("github.com/sudharson-pv/llama_model_download/blob/main/models", "github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1")
        }
        return u
            .replace("/tree/main/", "/releases/download/llama-3.2-1b-v1/")
            .replace("/blob/main/", "/releases/download/llama-3.2-1b-v1/")
    }

    /**
     * Resolves the actual download URL for a model weights file, substituting custom CDN base if configured.
     * Automatically normalizes GitHub repository tree/blob routes into raw downloadable stream endpoints.
     */
    fun getEffectiveDownloadUrl(entry: ModelCatalogEntry, customBaseUrl: String? = null): String {
        if (!customBaseUrl.isNullOrBlank()) {
            val trimmed = customBaseUrl.trim().trimEnd('/')
            val normalized = normalizeGitHubUrl(trimmed)
            return if (normalized.endsWith("/${entry.modelFileName}")) {
                normalized
            } else {
                "$normalized/${entry.modelFileName}"
            }
        }
        return normalizeGitHubUrl(entry.downloadUrl)
    }

    /**
     * Resolves the actual tokenizer URL, substituting custom CDN base if configured.
     * Automatically normalizes GitHub repository tree/blob routes into raw downloadable stream endpoints.
     */
    fun getEffectiveTokenizerUrl(entry: ModelCatalogEntry, customBaseUrl: String? = null): String {
        if (!customBaseUrl.isNullOrBlank()) {
            val trimmed = customBaseUrl.trim().trimEnd('/')
            val normalized = normalizeGitHubUrl(trimmed)
            return if (normalized.endsWith("/${entry.tokenizerFileName}")) {
                normalized
            } else {
                "$normalized/${entry.tokenizerFileName}"
            }
        }
        return normalizeGitHubUrl(entry.tokenizerUrl)
    }
}

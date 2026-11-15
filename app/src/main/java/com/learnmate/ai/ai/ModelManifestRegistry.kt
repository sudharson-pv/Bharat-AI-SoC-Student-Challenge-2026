package com.learnmate.ai.ai

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Gate 6.16.3: Authoritative Registry & Hardware Compatibility Validator
 * for On-Device Llama-3.2 Model Distribution.
 *
 * Enforces:
 * 1. Strict cryptographic checksums and file bounds.
 * 2. Hardware prerequisites: API level >= 28, RAM >= 3.5 GB, Storage >= 2.5 GB.
 * 3. Immutable licensing and runtime specification.
 */
object ModelManifestRegistry {

    data class ModelDistributionManifest(
        val modelId: String,
        val displayName: String,
        val modelFileName: String,
        val modelSizeBytes: Long,
        val modelSha256: String,
        val tokenizerFileName: String,
        val tokenizerSizeBytes: Long,
        val tokenizerSha256: String,
        val quantization: String,
        val targetBackend: String,
        val runtimeVersion: String,
        val license: String,
        val minApiLevel: Int,
        val minRamBytes: Long,
        val minStorageBytes: Long,
        val defaultDownloadUrl: String,
        val defaultTokenizerUrl: String
    )

    data class DeviceCompatibilityResult(
        val isCompatible: Boolean,
        val failureReason: String? = null,
        val evaluatedApiLevel: Int,
        val evaluatedTotalRamMb: Long,
        val evaluatedFreeStorageMb: Long
    )

    val PRIMARY_1B_MANIFEST = ModelDistributionManifest(
        modelId = "meta-llama-3.2-1b-instruct-8da4w",
        displayName = "Meta Llama 3.2 1B Instruct (8da4w)",
        modelFileName = ActiveModelManifest.MODEL_FILENAME,
        modelSizeBytes = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES, // 803,033,472 bytes (765.83 MiB)
        modelSha256 = ActiveModelManifest.EXPECTED_MODEL_SHA256,
        tokenizerFileName = ActiveModelManifest.TOKENIZER_FILENAME,
        tokenizerSizeBytes = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES, // 2,183,982 bytes (2.08 MiB)
        tokenizerSha256 = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
        quantization = ActiveModelManifest.QUANTIZATION_SCHEME,
        targetBackend = ActiveModelManifest.TARGET_BACKEND,
        runtimeVersion = ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION,
        license = "Meta Llama 3.2 Community License Agreement",
        minApiLevel = 28, // Android 9.0 Pie+
        minRamBytes = 3_500L * 1024L * 1024L, // 3.5 GiB minimum
        minStorageBytes = 2_500L * 1024L * 1024L, // 2.5 GiB free storage minimum
        defaultDownloadUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/llama3_2_1b_8da4w.pte",
        defaultTokenizerUrl = "https://github.com/sudharson-pv/llama_model_download/raw/main/models/tokenizer.model"
    )

    /**
     * Evaluates whether the physical device meets the minimum OS, RAM, and Storage requirements
     * to safely load and run the ExecuTorch on-device AI runtime.
     */
    fun evaluateDeviceCompatibility(
        apiLevel: Int,
        totalRamBytes: Long,
        availableStorageBytes: Long,
        manifest: ModelDistributionManifest = PRIMARY_1B_MANIFEST
    ): DeviceCompatibilityResult {
        val totalRamMb = totalRamBytes / (1024 * 1024)
        val freeStorageMb = availableStorageBytes / (1024 * 1024)

        // 1. API Level Check
        if (apiLevel < manifest.minApiLevel) {
            return DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Unsupported Android OS: Device is running API level $apiLevel (Android 8.1 or older). LearnMate AI requires Android 9.0+ (API ${manifest.minApiLevel}) for ExecuTorch JNI and Compose runtime.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        // 2. RAM Check
        if (totalRamBytes < manifest.minRamBytes) {
            val minRamGb = "%.1f".format(manifest.minRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            val currentRamGb = "%.1f".format(totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
            return DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Insufficient Device RAM: Found $currentRamGb GB RAM ($totalRamMb MB). On-device Llama-3.2 1B execution requires at least $minRamGb GB RAM to prevent out-of-memory termination.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        // 3. Storage Check
        if (availableStorageBytes < manifest.minStorageBytes) {
            val requiredMb = manifest.minStorageBytes / (1024 * 1024)
            return DeviceCompatibilityResult(
                isCompatible = false,
                failureReason = "Insufficient Storage: $freeStorageMb MB free on /data. At least $requiredMb MB required for model download, verification, and runtime staging.",
                evaluatedApiLevel = apiLevel,
                evaluatedTotalRamMb = totalRamMb,
                evaluatedFreeStorageMb = freeStorageMb
            )
        }

        return DeviceCompatibilityResult(
            isCompatible = true,
            evaluatedApiLevel = apiLevel,
            evaluatedTotalRamMb = totalRamMb,
            evaluatedFreeStorageMb = freeStorageMb
        )
    }

    /**
     * Streams and verifies the SHA-256 digest of a downloaded file.
     */
    fun calculateFileSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}


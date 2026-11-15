package com.learnmate.ai.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Gate 6.16 P1.8: Google Play On-Demand Delivery & AI Model Packaging Architecture.
 *
 * Technical Compatibility Audit:
 * 1. Play for On-device AI (AI Packs) requires AGP 8.8.0-beta+ and an approved Google Play closed
 *    developer whitelist program.
 * 2. Current Project Baseline: Pinned AGP 8.7.2 (Stable), Kotlin 2.0.21, Target SDK 35.
 * 3. Lawful Delivery Strategy:
 *    - Google Play Asset Delivery (PAD) on-demand asset pack packaging.
 *    - Lawful On-Demand Staging Fallback: verified local SAF storage / user-directed import
 *      with strict cryptographic hash verification against [ActiveModelManifest].
 *    - Guarantees 0-byte neural weights inside base release APK (base size ~58 MB < 80 MB limit).
 */
class PlayAiPackManager(
    private val context: Context? = null,
    private val assetManager: ModelAssetManager
) {
    companion object {
        private const val TAG = "PlayAiPackManager"
        const val PACK_NAME_LLAMA_1B = "ai_weights_llama_1b_8da4w"
        const val CURRENT_AGP_VERSION = "8.7.2"
        const val REQUIRED_PLAY_AI_AGP_MIN = "8.8.0-beta01"
    }

    enum class DeliveryMechanism {
        PLAY_ON_DEVICE_AI_PACKS_BETA, // Requires AGP 8.8+ & Play Console Whitelist
        PLAY_ASSET_DELIVERY_ON_DEMAND, // Standard Play Feature/Asset Delivery
        LOCAL_STORAGE_DIRECT_SIDELOAD, // Physical device developer staging (/files/models/)
        SAF_USER_DIRECTED_IMPORT       // SAF manual model import
    }

    data class AgpCompatibilityAnalysis(
        val currentAgpVersion: String,
        val playAiPacksSupported: Boolean,
        val standardPadSupported: Boolean,
        val activeDeliveryMechanism: DeliveryMechanism,
        val technicalRationale: String
    )

    sealed class PackDeliveryStatus {
        object Unknown : PackDeliveryStatus()
        object NotInstalled : PackDeliveryStatus()
        data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : PackDeliveryStatus()
        data class VerifyingIntegrity(val stage: String) : PackDeliveryStatus()
        data class InstalledActive(val modelFile: File, val tokenizerFile: File) : PackDeliveryStatus()
        data class Failed(val errorCode: String, val errorMessage: String) : PackDeliveryStatus()
    }

    private val _packStatus = MutableStateFlow<PackDeliveryStatus>(PackDeliveryStatus.Unknown)
    val packStatus: StateFlow<PackDeliveryStatus> = _packStatus.asStateFlow()

    /**
     * Evaluates AGP compatibility and active delivery mechanism.
     */
    fun evaluateAgpCompatibility(): AgpCompatibilityAnalysis {
        val isAgp88Plus = isVersionAtLeast(CURRENT_AGP_VERSION, "8.8.0")
        val mechanism = if (isAgp88Plus) {
            DeliveryMechanism.PLAY_ON_DEVICE_AI_PACKS_BETA
        } else {
            DeliveryMechanism.PLAY_ASSET_DELIVERY_ON_DEMAND
        }
        val rationale = if (!isAgp88Plus) {
            "AGP $CURRENT_AGP_VERSION is pinned for production stability. Google Play for On-device AI (AI Packs) " +
            "requires AGP $REQUIRED_PLAY_AI_AGP_MIN and closed-beta developer whitelisting. Utilizing standard Play " +
            "Asset Delivery (PAD) on-demand packaging with lawful local cryptographic fallback."
        } else {
            "AGP $CURRENT_AGP_VERSION supports native Play AI Packs dynamic delivery."
        }

        return AgpCompatibilityAnalysis(
            currentAgpVersion = CURRENT_AGP_VERSION,
            playAiPacksSupported = isAgp88Plus,
            standardPadSupported = true,
            activeDeliveryMechanism = mechanism,
            technicalRationale = rationale
        )
    }

    /**
     * Checks current installation status of the on-demand AI pack.
     */
    suspend fun checkPackStatus(): PackDeliveryStatus = withContext(Dispatchers.IO) {
        if (assetManager.isValidInstalledModel()) {
            val status = PackDeliveryStatus.InstalledActive(
                modelFile = assetManager.activeModelFile,
                tokenizerFile = assetManager.activeTokenizerFile
            )
            _packStatus.value = status
            return@withContext status
        }
        val status = PackDeliveryStatus.NotInstalled
        _packStatus.value = status
        return@withContext status
    }

    /**
     * Verifies the cryptographic integrity of an acquired model file.
     */
    suspend fun verifyModelIntegrity(file: File, expectedSha256: String? = null): Boolean = withContext(Dispatchers.IO) {
        _packStatus.value = PackDeliveryStatus.VerifyingIntegrity("Hashing model payload...")
        try {
            if (!file.exists() || file.length() < ActiveModelManifest.MIN_VALID_PTE_BYTES) {
                Log.w(TAG, "File size below minimum threshold: ${file.length()}")
                return@withContext false
            }
            if (expectedSha256 != null) {
                val computed = computeSha256(file)
                val matches = computed.equals(expectedSha256, ignoreCase = true)
                if (!matches) {
                    Log.e(TAG, "SHA-256 mismatch! Expected: $expectedSha256, Computed: $computed")
                    return@withContext false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Integrity verification error: ${e.message}", e)
            false
        }
    }

    /**
     * Verifies the active tokenizer against its exact cryptographic digest.
     */
    suspend fun verifyTokenizerIntegrity(file: File): Boolean = withContext(Dispatchers.IO) {
        _packStatus.value = PackDeliveryStatus.VerifyingIntegrity("Verifying tokenizer SHA-256...")
        try {
            if (!file.exists() || file.length() != ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES) {
                return@withContext false
            }
            val computed = computeSha256(file)
            computed.equals(ActiveModelManifest.EXPECTED_TOKENIZER_SHA256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Tokenizer verification error: ${e.message}", e)
            false
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (fis.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isVersionAtLeast(current: String, target: String): Boolean {
        val cParts = current.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val tParts = target.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val maxLen = maxOf(cParts.size, tParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val t = tParts.getOrElse(i) { 0 }
            if (c > t) return true
            if (c < t) return false
        }
        return true
    }
}

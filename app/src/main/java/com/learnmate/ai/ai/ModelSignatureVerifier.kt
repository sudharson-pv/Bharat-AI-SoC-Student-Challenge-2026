package com.learnmate.ai.ai

import com.learnmate.ai.data.contentpack.ContentPackSecurity
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * Gate 6.16.5: Versioned Model Manifest with Ed25519 Publisher Authenticity.
 *
 * Enforces:
 * 1. Digital signature verification with embedded trusted public key.
 * 2. Publisher authenticity separate from SHA-256 binary integrity.
 * 3. Strict rejection of invalid signatures, expired manifests, and mismatched IDs.
 * 4. Zero private keys embedded in the client application.
 */
data class SignedModelManifest(
    val formatVersion: String = "1.0.0",
    val manifestVersion: Int = 1,
    val modelId: String = ActiveModelManifest.MODEL_ID,
    val modelVersion: String = "1.0.0",
    val modelFileName: String = ActiveModelManifest.MODEL_FILENAME,
    val modelSizeBytes: Long = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES,
    val modelSha256: String = ActiveModelManifest.EXPECTED_MODEL_SHA256,
    val tokenizerFileName: String = ActiveModelManifest.TOKENIZER_FILENAME,
    val tokenizerSizeBytes: Long = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES,
    val tokenizerSha256: String = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
    val format: String = "ExecuTorch PTE",
    val quantization: String = ActiveModelManifest.QUANTIZATION_SCHEME,
    val targetBackend: String = ActiveModelManifest.TARGET_BACKEND,
    val minRuntimeVersion: String = ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION,
    val minApiLevel: Int = 28,
    val minRamBytes: Long = 3_500L * 1024L * 1024L,
    val minStorageBytes: Long = 2_500L * 1024L * 1024L,
    val license: String = "Meta Llama 3.2 Community License Agreement",
    val publisherKeyId: String = "learnmate_official_2026",
    val signatureTimestampEpochSec: Long = 1762400000L,
    val expiresTimestampEpochSec: Long = 2077680000L,
    val signatureBase64: String = "hLCx+JI20CZ3UYpLsBy3XdNJAHUrv1TGeSxq6Kg9YZeXSg/nEyIC3EuY7IjDjhd+DDSzK2Ggn+4W98IM1/+NBQ=="
) {
    /**
     * Canonical serialization for Ed25519 signing and verification.
     * Excludes the signature field itself.
     */
    fun canonicalPayload(): ByteArray {
        val payload = "formatVersion=$formatVersion\n" +
            "manifestVersion=$manifestVersion\n" +
            "modelId=$modelId\n" +
            "modelVersion=$modelVersion\n" +
            "modelFileName=$modelFileName\n" +
            "modelSizeBytes=$modelSizeBytes\n" +
            "modelSha256=${modelSha256.lowercase()}\n" +
            "tokenizerFileName=$tokenizerFileName\n" +
            "tokenizerSizeBytes=$tokenizerSizeBytes\n" +
            "tokenizerSha256=${tokenizerSha256.lowercase()}\n" +
            "format=$format\n" +
            "quantization=$quantization\n" +
            "targetBackend=$targetBackend\n" +
            "minRuntimeVersion=$minRuntimeVersion\n" +
            "minApiLevel=$minApiLevel\n" +
            "minRamBytes=$minRamBytes\n" +
            "minStorageBytes=$minStorageBytes\n" +
            "license=$license\n" +
            "publisherKeyId=$publisherKeyId\n" +
            "signatureTimestampEpochSec=$signatureTimestampEpochSec\n" +
            "expiresTimestampEpochSec=$expiresTimestampEpochSec"
        return payload.toByteArray(Charsets.UTF_8)
    }
}

object ModelSignatureVerifier {

    val SUPPORTED_FORMAT_VERSIONS = setOf("1.0.0")

    sealed class VerificationResult {
        object Valid : VerificationResult()
        data class Invalid(val reason: String, val errorCode: String) : VerificationResult()
    }

    /**
     * Verifies the authenticity, freshness, format version, and signature of a model manifest.
     */
    fun verifyManifest(
        manifest: SignedModelManifest,
        trustedPublicKeyB64: String = ContentPackSecurity.PINNED_OFFICIAL_PUBLIC_KEY_BASE64,
        currentEpochSec: Long = System.currentTimeMillis() / 1000L
    ): VerificationResult {
        // 1. Format Version Validation
        if (!SUPPORTED_FORMAT_VERSIONS.contains(manifest.formatVersion)) {
            return VerificationResult.Invalid(
                "Unsupported manifest format version: ${manifest.formatVersion}. Supported: $SUPPORTED_FORMAT_VERSIONS",
                "UNSUPPORTED_VERSION"
            )
        }

        // 2. Manifest Version Validation
        if (manifest.manifestVersion < 1) {
            return VerificationResult.Invalid(
                "Invalid manifest version: ${manifest.manifestVersion}",
                "INVALID_MANIFEST_VERSION"
            )
        }

        // 3. Expiration Check
        if (currentEpochSec > manifest.expiresTimestampEpochSec) {
            return VerificationResult.Invalid(
                "Model manifest expired on epoch ${manifest.expiresTimestampEpochSec} (current: $currentEpochSec)",
                "EXPIRED_MANIFEST"
            )
        }

        // 4. Model ID Validation (Gate 6.16.6: Multi-Model Catalog Validation)
        val isCatalogModel = ModelCatalog.getEntryById(manifest.modelId) != null || manifest.modelId == ActiveModelManifest.MODEL_ID
        if (!isCatalogModel) {
            return VerificationResult.Invalid(
                "Unexpected model identity: '${manifest.modelId}' is not registered in the model catalog.",
                "UNEXPECTED_MODEL_ID"
            )
        }

        // 5. Publisher Trust Validation
        if (!ContentPackSecurity.TRUSTED_PUBLISHER_KEYS.containsKey(manifest.publisherKeyId)) {
            return VerificationResult.Invalid(
                "Untrusted publisher key ID: '${manifest.publisherKeyId}'. Not present in trust registry.",
                "UNTRUSTED_PUBLISHER"
            )
        }

        // 6. Base64 Signature Decoding
        val signatureBytes = try {
            Base64.getDecoder().decode(manifest.signatureBase64)
        } catch (_: Exception) {
            return VerificationResult.Invalid("Malformed Base64 in manifest digital signature", "MALFORMED_SIGNATURE")
        }

        if (signatureBytes.size != 64) {
            return VerificationResult.Invalid(
                "Ed25519 signature must be exactly 64 bytes (got ${signatureBytes.size})",
                "INVALID_SIGNATURE_SIZE"
            )
        }

        // 7. Public Key Extraction
        val pubKeyBytes = try {
            Base64.getDecoder().decode(trustedPublicKeyB64)
        } catch (_: Exception) {
            return VerificationResult.Invalid("Malformed trusted public key in trust store", "MALFORMED_PUBLIC_KEY")
        }

        // 8. Cryptographic Signature Verification
        val canonicalData = manifest.canonicalPayload()
        val isValidSig = ContentPackSecurity.verifyEd25519Signature(pubKeyBytes, canonicalData, signatureBytes)
        if (!isValidSig) {
            return VerificationResult.Invalid(
                "Ed25519 signature verification failed for publisher '${manifest.publisherKeyId}'",
                "INVALID_SIGNATURE"
            )
        }

        return VerificationResult.Valid
    }
}


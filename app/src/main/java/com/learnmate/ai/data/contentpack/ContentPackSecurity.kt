package com.learnmate.ai.data.contentpack

import com.google.gson.Gson
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Enforces cryptographic integrity, streaming decompression limits, path safety,
 * and semantic manifest integrity for offline LearnMate Content Packs (.lmpack).
 */
object ContentPackSecurity {

    const val CANONICAL_EXTENSION = ".lmpack"
    val SUPPORTED_FORMAT_VERSIONS = setOf("1.0.0")

    // Security bounds against Zip Bomb, DOS, and resource exhaustion
    const val MAX_ENTRY_COUNT = 1000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 50L * 1024 * 1024 // 50 MB
    const val MAX_SINGLE_FILE_BYTES = 20L * 1024 * 1024        // 20 MB
    const val MAX_COMPRESSION_RATIO = 10.0
    const val RATIO_CHECK_MIN_BYTES = 64 * 1024                // Only enforce ratio above 64 KB

    private val gson = Gson()

    // Official Pinned Ed25519 Public Key for LearnMate Staging & Reference Authority
    // DER-encoded SubjectPublicKeyInfo in Base64 (RFC 8410) - Exactly 44 bytes (60 chars Base64)
    // SPKI SHA-256: fd91e470e0e1d835e8206c9ae5a592ae4294e029d466923c8def5d17b6934064
    // Raw 32-byte Ed25519 Point (bytes 12..43 of SPKI) in Base64: N77dwAlm5tC1Nzatm0F/6jbLXvdXmDZw8tXbsNaWl+A= (44 chars Base64)
    // Raw 32-byte SHA-256: 23b9e8f3eb5f1a39af304766efbf4806b40ac8f6445afdb9a6c55b41d5c25100
    const val PINNED_OFFICIAL_PUBLIC_KEY_BASE64 =
        "MCowBQYDK2VwAyEAN77dwAlm5tC1Nzatm0F/6jbLXvdXmDZw8tXbsNaWl+A="

    const val STAGING_REFERENCE_KEY_SPKI_BASE64 =
        "MCowBQYDK2VwAyEAN77dwAlm5tC1Nzatm0F/6jbLXvdXmDZw8tXbsNaWl+A="

    const val STAGING_REFERENCE_KEY_RAW_BASE64 =
        "N77dwAlm5tC1Nzatm0F/6jbLXvdXmDZw8tXbsNaWl+A="

    const val STAGING_REFERENCE_KEY_SPKI_SHA256 =
        "fd91e470e0e1d835e8206c9ae5a592ae4294e029d466923c8def5d17b6934064"
    const val STAGING_REFERENCE_KEY_RAW_SHA256 =
        "23b9e8f3eb5f1a39af304766efbf4806b40ac8f6445afdb9a6c55b41d5c25100"

    // Staging Authority Key Identifiers (Strictly forbidden in Production Distribution)
    val STAGING_KEY_IDS: Set<String> = setOf("learnmate_official_2026", "staging_ref_key")

    // Immutable Production Trust Registry (Populated during distribution signing ceremony)
    val IMMUTABLE_PRODUCTION_KEYS: Map<String, String> = java.util.Collections.unmodifiableMap(
        mapOf<String, String>(
            // Production HSM/KMS keys will be provisioned here
        )
    )

    // Multi-key trust map: publisherKeyId -> Base64 SPKI
    val TRUSTED_PUBLISHER_KEYS: MutableMap<String, String> = mutableMapOf(
        "learnmate_official_2026" to PINNED_OFFICIAL_PUBLIC_KEY_BASE64,
        "staging_ref_key" to PINNED_OFFICIAL_PUBLIC_KEY_BASE64
    )

    // Set of revoked publisher key SHA-256 fingerprints (both SPKI DER and raw 32-byte representations checked)
    val REVOKED_PUBLISHER_KEY_HASHES: MutableSet<String> = mutableSetOf()

    // Standard RFC 8410 DER prefix for id-Ed25519 SubjectPublicKeyInfo (12 bytes)
    val RFC_8410_DER_PREFIX = byteArrayOf(
        0x30.toByte(), 0x2a.toByte(), 0x30.toByte(), 0x05.toByte(),
        0x06.toByte(), 0x03.toByte(), 0x2b.toByte(), 0x65.toByte(),
        0x70.toByte(), 0x03.toByte(), 0x21.toByte(), 0x00.toByte()
    )

    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * Extracts canonical 32-byte raw Ed25519 public key bytes from either
     * a 44-byte RFC 8410 SPKI DER representation or a raw 32-byte representation.
     */
    fun extractRawPublicKeyBytes(publicKeyBytes: ByteArray): ByteArray? {
        return when {
            publicKeyBytes.size == 32 -> publicKeyBytes.copyOf()
            publicKeyBytes.size == 44 && publicKeyBytes.sliceArray(0 until 12).contentEquals(RFC_8410_DER_PREFIX) ->
                publicKeyBytes.copyOfRange(12, 44)
            else -> null
        }
    }

    /**
     * Converts a raw 32-byte Ed25519 public key into a standard 44-byte RFC 8410 SPKI DER representation.
     */
    fun toSpkiDer(raw32Bytes: ByteArray): ByteArray {
        require(raw32Bytes.size == 32) { "Raw Ed25519 public key must be exactly 32 bytes (got ${raw32Bytes.size})" }
        return RFC_8410_DER_PREFIX + raw32Bytes
    }

    /**
     * Normalizes a public key into its canonical representations: Pair(raw32Bytes, spki44Bytes).
     */
    fun normalizePublicKey(publicKeyBytes: ByteArray): Pair<ByteArray, ByteArray>? {
        val raw = extractRawPublicKeyBytes(publicKeyBytes) ?: return null
        val spki = toSpkiDer(raw)
        return Pair(raw, spki)
    }

    /**
     * Checks if a public key is explicitly revoked by SHA-256 fingerprint.
     * Evaluates BOTH canonical raw 32-byte and canonical 44-byte SPKI representations.
     * Any match in the revocation registry immediately triggers fail-closed revocation.
     */
    fun isKeyRevoked(publicKeyBytes: ByteArray): Boolean {
        if (REVOKED_PUBLISHER_KEY_HASHES.isEmpty()) return false
        val pair = normalizePublicKey(publicKeyBytes) ?: return true // Malformed key is failed closed
        val rawHash = calculateSha256(pair.first).lowercase()
        val spkiHash = calculateSha256(pair.second).lowercase()
        return REVOKED_PUBLISHER_KEY_HASHES.contains(rawHash) || REVOKED_PUBLISHER_KEY_HASHES.contains(spkiHash)
    }

    /**
     * Revokes a public key by adding both its raw 32-byte SHA-256 and SPKI 44-byte SHA-256 to the revocation set.
     */
    fun revokeKey(publicKeyBytes: ByteArray) {
        val pair = normalizePublicKey(publicKeyBytes) ?: return
        REVOKED_PUBLISHER_KEY_HASHES.add(calculateSha256(pair.first).lowercase())
        REVOKED_PUBLISHER_KEY_HASHES.add(calculateSha256(pair.second).lowercase())
    }

    /**
     * Removes a public key from the revocation set.
     */
    fun unrevokeKey(publicKeyBytes: ByteArray) {
        val pair = normalizePublicKey(publicKeyBytes) ?: return
        REVOKED_PUBLISHER_KEY_HASHES.remove(calculateSha256(pair.first).lowercase())
        REVOKED_PUBLISHER_KEY_HASHES.remove(calculateSha256(pair.second).lowercase())
    }

    /**
     * Resolves a public key by publisherKeyId from the trusted publisher map.
     */
    fun getPublicKeyForPublisher(publisherKeyId: String): PublicKey? {
        val b64 = TRUSTED_PUBLISHER_KEYS[publisherKeyId] ?: return null
        return try {
            val keyBytes = Base64.getDecoder().decode(b64)
            if (!isValidPublicKeyEncoding(keyBytes)) return null
            if (isKeyRevoked(keyBytes)) return null
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("Ed25519").generatePublic(spec)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Strict public key validation:
     * Accepts only genuine 44-byte RFC 8410 SPKI DER keys or raw 32-byte Ed25519 keys.
     * Rejects incorrect lengths and malformed DER prefixes.
     */
    fun isValidPublicKeyEncoding(publicKeyBytes: ByteArray): Boolean {
        if (publicKeyBytes.size == 32) return true
        if (publicKeyBytes.size == 44) {
            return publicKeyBytes.sliceArray(0 until 12).contentEquals(RFC_8410_DER_PREFIX)
        }
        return false
    }

    fun getPinnedOfficialPublicKey(): PublicKey? {
        return try {
            val keyBytes = Base64.getDecoder().decode(PINNED_OFFICIAL_PUBLIC_KEY_BASE64)
            if (!isValidPublicKeyEncoding(keyBytes)) return null
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("Ed25519").generatePublic(spec)
        } catch (_: Exception) {
            null
        }
    }

    fun getPinnedOfficialPublicKeyBytes(): ByteArray {
        return Base64.getDecoder().decode(PINNED_OFFICIAL_PUBLIC_KEY_BASE64)
    }

    fun generateTestKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("Ed25519")
        return kpg.generateKeyPair()
    }

    /**
     * Verifies Ed25519 signature with dual-engine support:
     * 1. Java Cryptography Architecture (JCA/JCE) (API 33+ & JDK 15+)
     * 2. BouncyCastle RFC 8032 fallback (guaranteed on Android API 28–32)
     */
    fun verifyEd25519Signature(
        publicKeyBytes: ByteArray,
        data: ByteArray,
        signature: ByteArray
    ): Boolean {
        // Strict key & signature validation: Reject any invalid size or malformed DER header immediately
        if (!isValidPublicKeyEncoding(publicKeyBytes)) {
            return false
        }
        if (signature.size != 64) {
            return false
        }

        // 1. Try platform JCE first
        try {
            val keyBytes = if (publicKeyBytes.size == 44) {
                publicKeyBytes
            } else {
                // Wrap raw 32-byte Ed25519 key in SubjectPublicKeyInfo DER header (RFC 8410)
                RFC_8410_DER_PREFIX + publicKeyBytes
            }
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("Ed25519")
            val pubKey = kf.generatePublic(spec)
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(pubKey)
            sig.update(data)
            if (sig.verify(signature)) {
                return true
            }
        } catch (_: Exception) {
            // Platform lacks Ed25519 or failed; proceed to BouncyCastle fallback
        }

        // 2. BouncyCastle fallback
        return try {
            val rawKey = if (publicKeyBytes.size == 44) {
                publicKeyBytes.copyOfRange(12, 44)
            } else {
                publicKeyBytes
            }
            val pubParams = Ed25519PublicKeyParameters(rawKey, 0)
            val signer = Ed25519Signer()
            signer.init(false, pubParams)
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errorCode: String? = null,
        val errorMessage: String? = null,
        val manifest: ContentPackManifest? = null,
        val extractedFiles: Map<String, ByteArray> = emptyMap()
    )

    /**
     * Validates an input file as a secure .lmpack archive.
     * Enforces limits during streaming.
     */
    fun validateArchive(
        archiveFile: File,
        trustedPublicKey: PublicKey? = null,
        allowTestKeyIfNull: Boolean = false,
        customRegistry: Map<String, String>? = null,
        isProductionDistribution: Boolean = false
    ): ValidationResult {
        if (!archiveFile.name.endsWith(CANONICAL_EXTENSION, ignoreCase = true)) {
            return ValidationResult(
                isValid = false,
                errorCode = "INVALID_EXTENSION",
                errorMessage = "Content pack must use canonical extension $CANONICAL_EXTENSION"
            )
        }
        if (!archiveFile.exists() || archiveFile.length() == 0L) {
            return ValidationResult(
                isValid = false,
                errorCode = "EMPTY_OR_MISSING_ARCHIVE",
                errorMessage = "Archive file does not exist or is empty"
            )
        }

        return try {
            archiveFile.inputStream().use { stream ->
                validateArchiveStream(stream, trustedPublicKey, allowTestKeyIfNull, customRegistry, isProductionDistribution)
            }
        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorCode = "CORRUPTED_ARCHIVE",
                errorMessage = "Failed to parse archive: ${e.message}"
            )
        }
    }

    /**
     * Streams through the ZIP container and validates limits, paths, signature, and hashes.
     */
    fun validateArchiveStream(
        inputStream: InputStream,
        trustedPublicKey: PublicKey? = null,
        allowTestKeyIfNull: Boolean = false,
        customRegistry: Map<String, String>? = null,
        isProductionDistribution: Boolean = false
    ): ValidationResult {
        val zis = ZipInputStream(inputStream)
        val visitedEntries = mutableSetOf<String>()
        val fileContents = mutableMapOf<String, ByteArray>()

        var totalEntries = 0
        var totalBytesUncompressed = 0L

        var manifestBytes: ByteArray? = null
        var signatureBytes: ByteArray? = null

        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            totalEntries++
            if (totalEntries > MAX_ENTRY_COUNT) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "EXCESSIVE_ENTRY_COUNT",
                    errorMessage = "Archive exceeds maximum allowed entry count ($MAX_ENTRY_COUNT)"
                )
            }

            val rawName = entry.name

            // 1. Control Characters & Null Byte Defense
            if (rawName.any { it == '\u0000' || (it.code < 0x20 && it != '\t') || it.code == 0x7F }) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "MALFORMED_ENTRY_NAME",
                    errorMessage = "Entry '$rawName' contains invalid control characters or null bytes"
                )
            }

            // 2. Path Traversal & Zip Slip Defense
            if (rawName.contains("..") || rawName.startsWith("/") || rawName.startsWith("\\") || rawName.contains(":") ) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "PATH_TRAVERSAL_DETECTED",
                    errorMessage = "Entry '$rawName' attempts path traversal or absolute location"
                )
            }

            // 3. Duplicate Entry Defense
            if (!visitedEntries.add(rawName)) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "DUPLICATE_ENTRY",
                    errorMessage = "Duplicate archive entry detected: '$rawName'"
                )
            }

            if (!entry.isDirectory) {
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var bytesRead: Int
                var entryBytesRead = 0L

                while (zis.read(chunk).also { bytesRead = it } != -1) {
                    entryBytesRead += bytesRead
                    totalBytesUncompressed += bytesRead

                    // Enforce Single File Size Bound
                    if (entryBytesRead > MAX_SINGLE_FILE_BYTES) {
                        return ValidationResult(
                            isValid = false,
                            errorCode = "EXCESSIVE_FILE_SIZE",
                            errorMessage = "Entry '$rawName' exceeds single file limit of $MAX_SINGLE_FILE_BYTES bytes"
                        )
                    }

                    // Enforce Total Archive Uncompressed Size Bound
                    if (totalBytesUncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        return ValidationResult(
                            isValid = false,
                            errorCode = "EXCESSIVE_TOTAL_SIZE",
                            errorMessage = "Archive uncompressed size exceeds $MAX_TOTAL_UNCOMPRESSED_BYTES bytes"
                        )
                    }

                    // Enforce Compression Ratio Bound (Zip Bomb Defense)
                    if (entryBytesRead > RATIO_CHECK_MIN_BYTES && entry.compressedSize > 0) {
                        val ratio = entryBytesRead.toDouble() / entry.compressedSize.toDouble()
                        if (ratio > MAX_COMPRESSION_RATIO) {
                            return ValidationResult(
                                isValid = false,
                                errorCode = "ZIP_BOMB_DETECTED",
                                errorMessage = "Entry '$rawName' has dangerous compression ratio ($ratio > $MAX_COMPRESSION_RATIO)"
                            )
                        }
                    }

                    buffer.write(chunk, 0, bytesRead)
                }

                val data = buffer.toByteArray()
                fileContents[rawName] = data

                if (rawName == "manifest.json") {
                    manifestBytes = data
                } else if (rawName == "signature.bin") {
                    signatureBytes = data
                }
            }

            zis.closeEntry()
            entry = zis.nextEntry
        }

        // 4. Presence of Mandatory Manifest and Signature
        if (manifestBytes == null) {
            return ValidationResult(
                isValid = false,
                errorCode = "MISSING_MANIFEST",
                errorMessage = "Archive does not contain mandatory manifest.json"
            )
        }
        if (signatureBytes == null) {
            return ValidationResult(
                isValid = false,
                errorCode = "MISSING_SIGNATURE",
                errorMessage = "Archive does not contain mandatory signature.bin"
            )
        }
        if (signatureBytes.size != 64) {
            return ValidationResult(
                isValid = false,
                errorCode = "INVALID_SIGNATURE",
                errorMessage = "Ed25519 signature must be exactly 64 bytes (got ${signatureBytes.size})"
            )
        }

        // 5. Parse and Validate Manifest JSON
        val manifest: ContentPackManifest = try {
            val jsonStr = String(manifestBytes, Charsets.UTF_8)
            gson.fromJson(jsonStr, ContentPackManifest::class.java)
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorCode = "MALFORMED_MANIFEST_JSON",
                errorMessage = "Failed to parse manifest.json: ${e.message}"
            )
        }

        // 6. Format Version Check
        if (!SUPPORTED_FORMAT_VERSIONS.contains(manifest.formatVersion)) {
            return ValidationResult(
                isValid = false,
                errorCode = "UNSUPPORTED_FORMAT_VERSION",
                errorMessage = "Format version '${manifest.formatVersion}' is not supported. Supported: $SUPPORTED_FORMAT_VERSIONS"
            )
        }

        // 7. Strict Fail-Closed Publisher-Key Resolution
        if (isProductionDistribution && STAGING_KEY_IDS.contains(manifest.publisherKeyId)) {
            return ValidationResult(
                isValid = false,
                errorCode = "STAGING_KEY_IN_PRODUCTION_DISALLOWED",
                errorMessage = "Publisher key ID '${manifest.publisherKeyId}' is a staging key and cannot be used in production distribution"
            )
        }

        val registry = customRegistry ?: TRUSTED_PUBLISHER_KEYS
        val trustedB64 = registry[manifest.publisherKeyId]
        if (trustedB64 == null) {
            return ValidationResult(
                isValid = false,
                errorCode = "UNKNOWN_PUBLISHER_KEY_ID",
                errorMessage = "Publisher key ID '${manifest.publisherKeyId}' is not registered in trusted publisher authorities"
            )
        }

        val trustedBytes = try {
            Base64.getDecoder().decode(trustedB64)
        } catch (_: Exception) {
            return ValidationResult(
                isValid = false,
                errorCode = "MALFORMED_PUBLIC_KEY",
                errorMessage = "Registered publisher key for '${manifest.publisherKeyId}' has invalid Base64 encoding"
            )
        }

        if (!isValidPublicKeyEncoding(trustedBytes)) {
            return ValidationResult(
                isValid = false,
                errorCode = "MALFORMED_PUBLIC_KEY",
                errorMessage = "Registered publisher key for '${manifest.publisherKeyId}' has invalid length or format (${trustedBytes.size} bytes)"
            )
        }

        // Caller-provided key must match the registered key for this publisher ID (cannot inject arbitrary untrusted keys)
        if (trustedPublicKey != null) {
            val callerRaw = extractRawPublicKeyBytes(trustedPublicKey.encoded)
            val registeredRaw = extractRawPublicKeyBytes(trustedBytes)
            if (callerRaw == null || registeredRaw == null || !callerRaw.contentEquals(registeredRaw)) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "PUBLISHER_KEY_MISMATCH",
                    errorMessage = "Caller-supplied public key does not match trusted registered key for '${manifest.publisherKeyId}'"
                )
            }
        }

        if (isKeyRevoked(trustedBytes)) {
            return ValidationResult(
                isValid = false,
                errorCode = "REVOKED_PUBLISHER_KEY",
                errorMessage = "The publisher key '${manifest.publisherKeyId}' used to sign this content pack has been revoked"
            )
        }

        val isSignatureValid = verifyEd25519Signature(trustedBytes, manifestBytes, signatureBytes)
        if (!isSignatureValid) {
            return ValidationResult(
                isValid = false,
                errorCode = "INVALID_SIGNATURE",
                errorMessage = "Ed25519 signature verification failed for manifest.json with publisher '${manifest.publisherKeyId}'"
            )
        }

        // 8. Circular Dependency Check in Pack Declaration
        if (manifest.dependencies.any { it.packId == manifest.packId }) {
            return ValidationResult(
                isValid = false,
                errorCode = "CIRCULAR_PACK_DEPENDENCY",
                errorMessage = "Pack cannot declare a dependency on itself: ${manifest.packId}"
            )
        }

        // 9. Unlisted Extra Files Defense (Strict archive whitelist)
        val declaredPayloadPaths = manifest.payloadFiles.map { it.path }.toSet()
        for (entryPath in fileContents.keys) {
            if (entryPath != "manifest.json" && entryPath != "signature.bin") {
                if (!declaredPayloadPaths.contains(entryPath)) {
                    return ValidationResult(
                        isValid = false,
                        errorCode = "UNLISTED_EXTRA_FILE",
                        errorMessage = "Archive contains undeclared payload file: '$entryPath'"
                    )
                }
            }
        }

        // 10. Payload Hashes & Size Check (SHA-256 for all declared assets)
        val md = MessageDigest.getInstance("SHA-256")
        for (payload in manifest.payloadFiles) {
            val data = fileContents[payload.path]
            if (data == null) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "MISSING_PAYLOAD_FILE",
                    errorMessage = "Declared payload file '${payload.path}' missing from archive"
                )
            }
            if (data.size.toLong() != payload.sizeBytes) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "PAYLOAD_SIZE_MISMATCH",
                    errorMessage = "Payload '${payload.path}' size ${data.size} does not match declared ${payload.sizeBytes}"
                )
            }
            val hash = md.digest(data).joinToString("") { "%02x".format(it) }
            if (!hash.equals(payload.sha256, ignoreCase = true)) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "PAYLOAD_HASH_MISMATCH",
                    errorMessage = "Payload '${payload.path}' hash $hash does not match declared ${payload.sha256}"
                )
            }
        }

        // 11. Semantic Manifest Node Hierarchy & Integrity
        val nodeMap = manifest.curriculumNodes.associateBy { it.nodeId }
        if (nodeMap.size != manifest.curriculumNodes.size) {
            return ValidationResult(
                isValid = false,
                errorCode = "DUPLICATE_CURRICULUM_NODE",
                errorMessage = "Duplicate curriculum node ID found in manifest"
            )
        }

        for (node in manifest.curriculumNodes) {
            if (node.parentNodeId != null) {
                if (!nodeMap.containsKey(node.parentNodeId)) {
                    return ValidationResult(
                        isValid = false,
                        errorCode = "ORPHAN_CURRICULUM_NODE",
                        errorMessage = "Node '${node.nodeId}' references non-existent parent '${node.parentNodeId}'"
                    )
                }
            }
        }

        // Hierarchy Cycle Detection
        for (node in manifest.curriculumNodes) {
            val visited = mutableSetOf<String>()
            var current: String? = node.nodeId
            while (current != null) {
                if (!visited.add(current)) {
                    return ValidationResult(
                        isValid = false,
                        errorCode = "HIERARCHY_CYCLE_DETECTED",
                        errorMessage = "Cyclic parent relationship detected involving node '$current'"
                    )
                }
                current = nodeMap[current]?.parentNodeId
            }
        }

        // 12. Semantic Prerequisites Integrity
        for (prereq in manifest.prerequisites) {
            if (!nodeMap.containsKey(prereq.targetNodeId)) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "INVALID_PREREQUISITE_TARGET",
                    errorMessage = "Prerequisite target node '${prereq.targetNodeId}' does not exist in pack"
                )
            }
            if (prereq.targetNodeId == prereq.requiredNodeId) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "CIRCULAR_PREREQUISITE",
                    errorMessage = "Node '${prereq.targetNodeId}' cannot declare prerequisite on itself"
                )
            }
        }

        // 13. Semantic Resources Integrity
        for (resource in manifest.resources) {
            if (!nodeMap.containsKey(resource.nodeId)) {
                return ValidationResult(
                    isValid = false,
                    errorCode = "INVALID_RESOURCE_NODE",
                    errorMessage = "Resource '${resource.resourceId}' references non-existent node '${resource.nodeId}'"
                )
            }
        }

        return ValidationResult(
            isValid = true,
            manifest = manifest,
            extractedFiles = fileContents
        )
    }

    /**
     * Utility to create a cryptographically valid .lmpack archive for testing.
     */
    fun createLmpackArchive(
        outputFile: File,
        manifest: ContentPackManifest,
        keyPair: KeyPair,
        payloadFiles: Map<String, ByteArray> = emptyMap(),
        corruptSignature: Boolean = false,
        injectPathTraversal: String? = null,
        duplicateEntry: Boolean = false,
        injectUnlistedFile: Pair<String, ByteArray>? = null
    ) {
        val manifestJson = gson.toJson(manifest)
        val manifestBytes = manifestJson.toByteArray(Charsets.UTF_8)

        val sig = Signature.getInstance("Ed25519")
        sig.initSign(keyPair.private)
        sig.update(manifestBytes)
        val signatureBytes = sig.sign()

        if (corruptSignature) {
            signatureBytes[0] = (signatureBytes[0].toInt() xor 0xFF).toByte()
        }

        java.util.zip.ZipOutputStream(outputFile.outputStream()).use { zos ->
            // 1. Write manifest
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            // 2. Write signature
            zos.putNextEntry(ZipEntry("signature.bin"))
            zos.write(signatureBytes)
            zos.closeEntry()

            // 3. Write payload files
            for ((path, bytes) in payloadFiles) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }

            // Path traversal injection if requested
            if (injectPathTraversal != null) {
                zos.putNextEntry(ZipEntry(injectPathTraversal))
                zos.write("traversal payload".toByteArray())
                zos.closeEntry()
            }

            // Duplicate entry injection if requested
            if (duplicateEntry) {
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write("duplicate".toByteArray())
                zos.closeEntry()
            }

            // Unlisted file injection if requested
            if (injectUnlistedFile != null) {
                zos.putNextEntry(ZipEntry(injectUnlistedFile.first))
                zos.write(injectUnlistedFile.second)
                zos.closeEntry()
            }
        }
    }
}



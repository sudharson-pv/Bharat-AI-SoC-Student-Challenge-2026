package com.learnmate.ai.domain.lifecycle

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gate 6.10: Hardened Cryptographic Engine for Authenticated Offline Learner Backups.
 *
 * Security Architecture & Parameter Justification:
 * 1. Cipher: AES-256-GCM (AES/GCM/NoPadding) with 128-bit authentication tag.
 * 2. Key Derivation: PBKDF2WithHmacSHA256:
 *    - Format V1 (Legacy Gate 6.9): 100,000 iterations (fixed header).
 *    - Format V2 (Hardened Gate 6.10): 210,000 iterations (OWASP recommended baseline), stored explicitly in 4-byte header.
 * 3. Nonce/IV: 12-byte cryptographically secure random IV per backup (SecureRandom).
 * 4. Salt: 16-byte cryptographically secure random salt per backup (SecureRandom).
 * 5. Bounded Resource Guards:
 *    - Maximum allowable backup file size: 50 MB (MAX_BACKUP_FILE_SIZE).
 *    - Maximum allowable ciphertext size: 48 MB (MAX_CIPHERTEXT_SIZE).
 *    - Bounded iteration counts: 100,000 to 1,000,000 rounds.
 *    - Rejection of negative lengths, integer overflow, and truncated payloads.
 * 6. Memory Hygiene:
 *    - Intermediate derived key bytes and sensitive buffers wiped using `Arrays.fill(..., 0)`.
 *    - PBEKeySpec cleared via `keySpec.clearPassword()`.
 *    - Public memory wiping helper `wipeSensitiveChars()` provided for caller passphrases.
 * 7. Backward & Forward Compatibility:
 *    - Format V1 and V2 backups are fully supported and seamlessly authenticated.
 *    - Old backups are never silently reinterpreted; versions are parsed strictly and deterministically.
 */
object LearnerDataSecurity {

    val MAGIC_HEADER: ByteArray = "LMBACKUP".toByteArray(Charsets.US_ASCII)
    const val FORMAT_VERSION_1: Byte = 1
    const val FORMAT_VERSION_2: Byte = 2
    const val CURRENT_FORMAT_VERSION: Byte = FORMAT_VERSION_2

    const val SALT_SIZE_BYTES = 16
    const val IV_SIZE_BYTES = 12
    const val TAG_SIZE_BITS = 128
    const val KEY_LENGTH_BITS = 256

    const val PBKDF2_ITERATIONS_V1 = 100_000
    const val PBKDF2_ITERATIONS_V2 = 210_000
    const val MIN_PBKDF2_ITERATIONS = 100_000
    const val MAX_PBKDF2_ITERATIONS = 1_000_000

    const val MIN_BACKUP_FILE_SIZE = 8 + 1 + 1 + SALT_SIZE_BYTES + 1 + IV_SIZE_BYTES + 4 + (TAG_SIZE_BITS / 8)
    const val MAX_BACKUP_FILE_SIZE = 50 * 1024 * 1024L // 50 MB
    const val MAX_CIPHERTEXT_SIZE = 48 * 1024 * 1024   // 48 MB

    private val secureRandom = SecureRandom()

    /**
     * Encrypts learner payload JSON using authenticated AES-256-GCM and writes to target file.
     * Uses atomic file staging to survive unexpected power loss or process kill during backup creation.
     */
    fun encryptPayload(
        payloadJson: String,
        passphrase: CharArray,
        targetFile: File,
        formatVersion: Byte = CURRENT_FORMAT_VERSION,
        iterations: Int = if (formatVersion == FORMAT_VERSION_1) PBKDF2_ITERATIONS_V1 else PBKDF2_ITERATIONS_V2
    ): Result<Long> {
        if (passphrase.isEmpty()) {
            return Result.failure(IllegalArgumentException("Passphrase must not be empty"))
        }

        if (formatVersion != FORMAT_VERSION_1 && formatVersion != FORMAT_VERSION_2) {
            return Result.failure(IllegalArgumentException("Unsupported format version: $formatVersion"))
        }

        if (iterations < MIN_PBKDF2_ITERATIONS || iterations > MAX_PBKDF2_ITERATIONS) {
            return Result.failure(IllegalArgumentException("Iteration count $iterations out of safe bounds [$MIN_PBKDF2_ITERATIONS, $MAX_PBKDF2_ITERATIONS]"))
        }

        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        val stagingFile = File(parentDir, "${targetFile.name}.staging_${System.currentTimeMillis()}")
        var keyBytes = ByteArray(KEY_LENGTH_BITS / 8)
        var plainBytes = ByteArray(0)
        var cipherBytes = ByteArray(0)
        var keySpec: PBEKeySpec? = null

        try {
            // 1. Generate unique random salt and IV
            val salt = ByteArray(SALT_SIZE_BYTES)
            val iv = ByteArray(IV_SIZE_BYTES)
            secureRandom.nextBytes(salt)
            secureRandom.nextBytes(iv)

            // 2. Derive 256-bit AES key using PBKDF2
            keySpec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val secretKey = factory.generateSecret(keySpec)
            keyBytes = secretKey.encoded
            keySpec.clearPassword()

            // 3. Encrypt payload via AES-256-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            val aesKey = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec)

            plainBytes = payloadJson.toByteArray(Charsets.UTF_8)
            cipherBytes = cipher.doFinal(plainBytes)

            // 4. Assemble authenticated binary envelope
            // Envelope layout:
            // [8 bytes] Magic "LMBACKUP"
            // [1 byte]  Format Version (0x01 or 0x02)
            // [4 bytes] PBKDF2 Iterations (Only in Format V2)
            // [1 byte]  Salt Length (16)
            // [16 bytes] Salt
            // [1 byte]  IV Length (12)
            // [12 bytes] IV
            // [4 bytes] Ciphertext Length (Big-Endian Int)
            // [N bytes] Ciphertext + GCM Tag
            FileOutputStream(stagingFile).use { fos ->
                fos.write(MAGIC_HEADER)
                fos.write(formatVersion.toInt())
                if (formatVersion == FORMAT_VERSION_2) {
                    val iterBuffer = ByteBuffer.allocate(4).putInt(iterations).array()
                    fos.write(iterBuffer)
                }
                fos.write(salt.size)
                fos.write(salt)
                fos.write(iv.size)
                fos.write(iv)

                val lenBuffer = ByteBuffer.allocate(4).putInt(cipherBytes.size).array()
                fos.write(lenBuffer)
                fos.write(cipherBytes)
                fos.flush()
            }

            // 5. Atomic commit
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = stagingFile.renameTo(targetFile)
            if (!renamed) {
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()
            }

            return Result.success(targetFile.length())
        } catch (e: Exception) {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            return Result.failure(e)
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
            Arrays.fill(plainBytes, 0.toByte())
            Arrays.fill(cipherBytes, 0.toByte())
            keySpec?.clearPassword()
        }
    }

    /**
     * Decrypts and authenticates a backup file using AES-256-GCM.
     * Enforces fail-closed validation on header magic, format version (V1 and V2),
     * iteration count bounds, file size limits, and cryptographic authentication tags.
     */
    fun decryptPayload(
        sourceFile: File,
        passphrase: CharArray
    ): Result<String> {
        if (!sourceFile.exists()) {
            return Result.failure(IllegalArgumentException("Backup file does not exist: ${sourceFile.absolutePath}"))
        }

        val fileLength = sourceFile.length()
        if (fileLength < MIN_BACKUP_FILE_SIZE) {
            return Result.failure(SecurityException("INVALID_BACKUP_FILE: File too small or incomplete"))
        }
        if (fileLength > MAX_BACKUP_FILE_SIZE) {
            return Result.failure(SecurityException("OVERSIZED_BACKUP: File size ($fileLength bytes) exceeds maximum allowable limit ($MAX_BACKUP_FILE_SIZE bytes)"))
        }

        var keyBytes = ByteArray(KEY_LENGTH_BITS / 8)
        var cipherBytes = ByteArray(0)
        var decryptedBytes = ByteArray(0)
        var keySpec: PBEKeySpec? = null

        try {
            FileInputStream(sourceFile).use { fis ->
                // 1. Verify Magic Header (8 bytes)
                val magic = ByteArray(MAGIC_HEADER.size)
                val readMagic = fis.read(magic)
                if (readMagic != MAGIC_HEADER.size || !MessageDigest.isEqual(magic, MAGIC_HEADER)) {
                    return Result.failure(SecurityException("INVALID_MAGIC_HEADER: Not a valid LearnMate backup file"))
                }

                // 2. Verify Format Version (1 byte)
                val version = fis.read()
                val iterations = when (version) {
                    FORMAT_VERSION_1.toInt() -> PBKDF2_ITERATIONS_V1
                    FORMAT_VERSION_2.toInt() -> {
                        val iterBuffer = ByteArray(4)
                        if (fis.read(iterBuffer) != 4) {
                            return Result.failure(SecurityException("CORRUPT_HEADER: Incomplete iteration count header"))
                        }
                        val parsedIter = ByteBuffer.wrap(iterBuffer).int
                        if (parsedIter < MIN_PBKDF2_ITERATIONS || parsedIter > MAX_PBKDF2_ITERATIONS) {
                            return Result.failure(SecurityException("INVALID_ITERATION_COUNT: Iterations $parsedIter out of safe bounds [$MIN_PBKDF2_ITERATIONS, $MAX_PBKDF2_ITERATIONS]"))
                        }
                        parsedIter
                    }
                    else -> return Result.failure(SecurityException("UNSUPPORTED_VERSION: Backup format version $version is not supported"))
                }

                // 3. Read Salt
                val saltLen = fis.read()
                if (saltLen != SALT_SIZE_BYTES) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Invalid salt length"))
                }
                val salt = ByteArray(SALT_SIZE_BYTES)
                if (fis.read(salt) != SALT_SIZE_BYTES) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Incomplete salt bytes"))
                }

                // 4. Read IV
                val ivLen = fis.read()
                if (ivLen != IV_SIZE_BYTES) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Invalid IV length"))
                }
                val iv = ByteArray(IV_SIZE_BYTES)
                if (fis.read(iv) != IV_SIZE_BYTES) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Incomplete IV bytes"))
                }

                // 5. Read Ciphertext Length (4 bytes)
                val lenBuffer = ByteArray(4)
                if (fis.read(lenBuffer) != 4) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Incomplete payload length header"))
                }
                val cipherLen = ByteBuffer.wrap(lenBuffer).int
                if (cipherLen <= 0 || cipherLen > MAX_CIPHERTEXT_SIZE) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Invalid ciphertext length"))
                }
                val minHeaderBytes = 8 + 1 + (if (version == FORMAT_VERSION_2.toInt()) 4 else 0) + 1 + SALT_SIZE_BYTES + 1 + IV_SIZE_BYTES + 4
                if (cipherLen > (fileLength - minHeaderBytes)) {
                    return Result.failure(SecurityException("CORRUPT_HEADER: Ciphertext length exceeds file bounds"))
                }

                // 6. Read Ciphertext + GCM Tag
                cipherBytes = ByteArray(cipherLen)
                var bytesReadTotal = 0
                while (bytesReadTotal < cipherLen) {
                    val read = fis.read(cipherBytes, bytesReadTotal, cipherLen - bytesReadTotal)
                    if (read == -1) break
                    bytesReadTotal += read
                }
                if (bytesReadTotal != cipherLen) {
                    return Result.failure(SecurityException("CORRUPT_PAYLOAD: Unexpected EOF while reading ciphertext"))
                }

                // 7. Derive key via PBKDF2 with determined iterations
                keySpec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val secretKey = factory.generateSecret(keySpec)
                keyBytes = secretKey.encoded
                keySpec.clearPassword()

                // 8. Authenticated Decryption
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(TAG_SIZE_BITS, iv)
                val aesKey = SecretKeySpec(keyBytes, "AES")
                cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec)

                decryptedBytes = try {
                    cipher.doFinal(cipherBytes)
                } catch (e: AEADBadTagException) {
                    return Result.failure(SecurityException("INTEGRITY_OR_AUTH_FAILURE: Authentication tag verification failed or wrong passphrase", e))
                } catch (e: Exception) {
                    return Result.failure(SecurityException("DECRYPTION_FAILED: ${e.message}", e))
                }

                val jsonString = String(decryptedBytes, Charsets.UTF_8)
                return Result.success(jsonString)
            }
        } catch (e: SecurityException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(SecurityException("RESTORE_IO_ERROR: ${e.message}", e))
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
            Arrays.fill(cipherBytes, 0.toByte())
            Arrays.fill(decryptedBytes, 0.toByte())
            keySpec?.clearPassword()
        }
    }

    /**
     * Zeroes sensitive character buffers from memory.
     */
    fun wipeSensitiveChars(chars: CharArray) {
        Arrays.fill(chars, '\u0000')
    }

    /**
     * Computes SHA-256 digest in lowercase hexadecimal.
     */
    fun computeSha256(text: String): String {
        return computeSha256(text.toByteArray(Charsets.UTF_8))
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

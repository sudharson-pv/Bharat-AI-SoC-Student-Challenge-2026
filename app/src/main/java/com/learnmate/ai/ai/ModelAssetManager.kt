package com.learnmate.ai.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Android runtime model asset manager.
 * - Manages storage under: context.filesDir/models/
 *   * llama3_2_1b_8da4w.pte
 *   * tokenizer.model
 * - Supports staging from /data/local/tmp for development testing.
 * - Uses streaming I/O (64 KiB buffer) to avoid memory spikes.
 * - Writes to .partial temporary file and renames atomically upon completion.
 */
class ModelAssetManager(
    private val appFilesDir: File,
    private val devProvisioningDir: File = File("/data/local/tmp")
) {
    companion object {
        private const val TAG = "ModelAssetManager"
        private const val STREAM_BUFFER_BYTES = 64 * 1024 // 64 KiB buffer
    }

    data class ProvisioningResult(
        val state: ModelRuntimeState,
        val modelFile: File,
        val tokenizerFile: File,
        val copiedFromStaging: Boolean,
        val errorMessage: String? = null
    )

    val modelsDirectory: File
        get() = File(appFilesDir, "models")

    val activeModelFile: File
        get() = getModelFile(ActiveModelManifest.MODEL_ID)

    val activeTokenizerFile: File
        get() = getTokenizerFile(ActiveModelManifest.MODEL_ID)

    fun ensureModelsDirectory(): File {
        val dir = modelsDirectory
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Gate 6.16.6: Resolves isolated model directory for multi-model catalog.
     */
    fun getModelDirectory(modelId: String): File {
        val subDir = File(modelsDirectory, modelId)
        if (subDir.exists() && subDir.isDirectory) return subDir
        return modelsDirectory // Fallback to root models dir for 1B
    }

    fun getModelFile(modelId: String): File {
        val entry = ModelCatalog.getEntryById(modelId) ?: ModelCatalog.ENTRY_LLAMA_3_2_1B
        val specific = File(File(modelsDirectory, modelId), entry.modelFileName)
        if (specific.exists()) return specific
        return File(modelsDirectory, entry.modelFileName)
    }

    fun getTokenizerFile(modelId: String): File {
        val entry = ModelCatalog.getEntryById(modelId) ?: ModelCatalog.ENTRY_LLAMA_3_2_1B
        val specific = File(File(modelsDirectory, modelId), entry.tokenizerFileName)
        if (specific.exists()) return specific
        return File(modelsDirectory, entry.tokenizerFileName)
    }

    fun isModelInstalled(modelId: String): Boolean {
        val entry = ModelCatalog.getEntryById(modelId) ?: return false
        val pte = getModelFile(modelId)
        val tok = getTokenizerFile(modelId)
        return pte.exists() && pte.isFile && pte.length() == entry.modelSizeBytes &&
                tok.exists() && tok.isFile && tok.length() == entry.tokenizerSizeBytes
    }

    fun getInstalledModels(): List<ModelCatalogEntry> {
        return ModelCatalog.ALL_ENTRIES.filter { isModelInstalled(it.modelId) }
    }

    fun deleteModel(modelId: String, activeModelId: String): Boolean {
        if (modelId == activeModelId) {
            Log.w(TAG, "Cannot delete currently active model: $modelId")
            return false
        }
        val entry = ModelCatalog.getEntryById(modelId) ?: return false
        val subDir = File(modelsDirectory, modelId)
        if (subDir.exists()) {
            return subDir.deleteRecursively()
        } else {
            val pte = File(modelsDirectory, entry.modelFileName)
            var deleted = false
            if (pte.exists()) deleted = pte.delete()
            return deleted
        }
    }

    /**
     * Security Gate 6.16.6: Enforces rejection of GGUF files.
     */
    fun isGgufFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4) return false
        if (file.name.endsWith(".gguf", ignoreCase = true)) return true
        try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                if (fis.read(magic) == 4) {
                    return magic[0] == 0x47.toByte() && magic[1] == 0x47.toByte() &&
                            magic[2] == 0x55.toByte() && magic[3] == 0x46.toByte()
                }
            }
        } catch (_: Exception) {}
        return false
    }

    fun isValidInstalledModel(
        minPteBytes: Long = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES,
        minTokenizerBytes: Long = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES
    ): Boolean {
        val pte = activeModelFile
        val tok = activeTokenizerFile
        return pte.exists() && pte.isFile && pte.canRead() && pte.length() >= minPteBytes && pte.length() == ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES &&
            tok.exists() && tok.isFile && tok.canRead() && tok.length() >= minTokenizerBytes && tok.length() == ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES
    }

    suspend fun isCryptographicallyVerifiedModel(): Boolean = withContext(Dispatchers.IO) {
        if (!isValidInstalledModel()) return@withContext false
        val tokSha = computeFileSha256(activeTokenizerFile)
        if (!tokSha.equals(ActiveModelManifest.EXPECTED_TOKENIZER_SHA256, ignoreCase = true)) return@withContext false
        val pteSha = computeFileSha256(activeModelFile)
        pteSha.equals(ActiveModelManifest.EXPECTED_MODEL_SHA256, ignoreCase = true)
    }

    fun cleanUpStalePartialFiles() {
        val dir = modelsDirectory
        if (!dir.exists()) return
        dir.listFiles { _, name -> name.endsWith(".partial") }?.forEach { partial ->
            try {
                partial.delete()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun computeFileSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(STREAM_BUFFER_BYTES)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Provisions llama3_2_1b_8da4w.pte and tokenizer.model into context.filesDir/models/
     * using safe streaming I/O.
     */
    suspend fun ensureModelProvisioned(
        minPteBytes: Long = ActiveModelManifest.MIN_VALID_PTE_BYTES,
        minTokenizerBytes: Long = ActiveModelManifest.MIN_VALID_TOKENIZER_BYTES
    ): ProvisioningResult = withContext(Dispatchers.IO) {
        ensureModelsDirectory()
        cleanUpStalePartialFiles()

        if (isValidInstalledModel(minPteBytes, minTokenizerBytes)) {
            Log.i(TAG, "Valid model present at ${activeModelFile.absolutePath}")
            return@withContext ProvisioningResult(
                state = ModelRuntimeState.MODEL_READY,
                modelFile = activeModelFile,
                tokenizerFile = activeTokenizerFile,
                copiedFromStaging = false
            )
        }

        val stagedPte = File(devProvisioningDir, ActiveModelManifest.MODEL_FILENAME)
        val stagedTok = File(devProvisioningDir, ActiveModelManifest.TOKENIZER_FILENAME)

        if (!stagedPte.exists() || stagedPte.length() < minPteBytes) {
            val err = "Missing or truncated PTE file in ${stagedPte.absolutePath} (len=${if (stagedPte.exists()) stagedPte.length() else 0})"
            Log.e(TAG, err)
            return@withContext ProvisioningResult(
                state = ModelRuntimeState.MODEL_MISSING,
                modelFile = activeModelFile,
                tokenizerFile = activeTokenizerFile,
                copiedFromStaging = false,
                errorMessage = err
            )
        }

        if (!stagedTok.exists() || stagedTok.length() < minTokenizerBytes) {
            val err = "Missing or truncated tokenizer in ${stagedTok.absolutePath}"
            Log.e(TAG, err)
            return@withContext ProvisioningResult(
                state = ModelRuntimeState.MODEL_MISSING,
                modelFile = activeModelFile,
                tokenizerFile = activeTokenizerFile,
                copiedFromStaging = false,
                errorMessage = err
            )
        }

        try {
            copyStreaming(stagedTok, activeTokenizerFile)
            copyStreaming(stagedPte, activeModelFile)
            Log.i(TAG, "Successfully provisioned model and tokenizer into ${modelsDirectory.absolutePath}")
            ProvisioningResult(
                state = ModelRuntimeState.MODEL_READY,
                modelFile = activeModelFile,
                tokenizerFile = activeTokenizerFile,
                copiedFromStaging = true
            )
        } catch (t: Throwable) {
            val err = "Failed to copy staged model assets: ${t.message}"
            Log.e(TAG, err, t)
            ProvisioningResult(
                state = ModelRuntimeState.MODEL_ERROR,
                modelFile = activeModelFile,
                tokenizerFile = activeTokenizerFile,
                copiedFromStaging = false,
                errorMessage = err
            )
        }
    }

    private fun copyStreaming(src: File, dst: File) {
        val partial = File(dst.parentFile, "${dst.name}.partial")
        FileInputStream(src).use { input ->
            FileOutputStream(partial).use { output ->
                val buf = ByteArray(STREAM_BUFFER_BYTES)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    output.write(buf, 0, read)
                }
                output.flush()
            }
        }
        if (dst.exists()) dst.delete()
        if (!partial.renameTo(dst)) {
            throw java.io.IOException("Atomic rename failed from ${partial.name} to ${dst.name}")
        }
    }
}


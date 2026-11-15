package com.learnmate.ai.data.contentpack

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.learnmate.ai.data.db.LearnMateDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles safe streaming ingestion of .lmpack archives selected via Android Storage Access Framework (SAF).
 * Guarantees bounded memory usage, stream validation, private staging, and clean error reporting.
 */
class ContentPackDocumentImporter(
    private val baseStorageDir: File,
    private val dao: LearnMateDao? = null
) {
    sealed class ImportResult {
        data class Success(val packId: String, val title: String, val nodesInstalled: Int) : ImportResult()
        data class Failure(val errorCode: String, val errorMessage: String) : ImportResult()
    }

    companion object {
        // Startup timestamp of current process instance
        val processStartupTimeEpochMs: Long = System.currentTimeMillis()

        // Tracks active in-flight temporary import files to prevent concurrent deletion
        private val activeImportFiles = ConcurrentHashMap.newKeySet<String>()

        fun getActiveImportFiles(): Set<String> = activeImportFiles.toSet()

        fun registerActiveImport(path: String) {
            activeImportFiles.add(path)
        }

        fun unregisterActiveImport(path: String) {
            activeImportFiles.remove(path)
        }

        /**
         * Reconciles and purges abandoned temporary SAF import files left behind by forced
         * process terminations or crashes.
         *
         * Policy:
         * 1. Files in [activeImportFiles] are preserved (in-flight active imports in current process).
         * 2. Any file created before [processStartupTimeEpochMs] belongs to a crashed prior process
         *    and is immediately purged on startup or reconciliation.
         * 3. Any file in the current process not registered in [activeImportFiles] older than [maxAgeMs] is purged.
         */
        fun reconcilePendingSafImports(
            baseStorageDir: File,
            maxAgeMs: Long = 60_000L,
            isStartup: Boolean = false
        ): Int {
            val stagingDir = File(baseStorageDir, ".staging")
            if (!stagingDir.exists() || !stagingDir.isDirectory) return 0
            val files = stagingDir.listFiles { _, name -> name.startsWith("saf_import_") } ?: return 0
            var purgedCount = 0
            val now = System.currentTimeMillis()
            for (file in files) {
                if (activeImportFiles.contains(file.absolutePath)) {
                    continue
                }

                val lastModified = file.lastModified()
                val age = now - lastModified
                val isFromPriorProcess = lastModified < processStartupTimeEpochMs

                if (isStartup || isFromPriorProcess || age >= maxAgeMs || maxAgeMs == 0L) {
                    if (file.delete()) {
                        purgedCount++
                    }
                }
            }
            return purgedCount
        }
    }

    /**
     * Streams an .lmpack archive from a content:// URI into application-private staging storage
     * and performs staged, recoverable installation.
     */
    suspend fun importFromUri(
        context: Context,
        uri: Uri?,
        trustedPublicKey: PublicKey? = null,
        onProgress: ((stage: String, percent: Int) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        if (uri == null) {
            return@withContext ImportResult.Failure(
                errorCode = "SELECTION_CANCELLED",
                errorMessage = "Document selection was cancelled by the user"
            )
        }

        // 1. Take persistable URI permission during transfer if supported, releasing on completion
        // to prevent unbounded accumulation of permission grants.
        var persistablePermissionTaken = false
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            persistablePermissionTaken = true
        } catch (_: Exception) {
            // Some providers (e.g. temporary shares) do not support persistable permissions
        }

        try {
            // 2. Query document display name and declared size
            var displayName: String? = null
            var declaredSize = -1L
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) declaredSize = cursor.getLong(sizeIdx)
                    }
                }
            } catch (e: Exception) {
                return@withContext ImportResult.Failure(
                    errorCode = "DOCUMENT_METADATA_UNAVAILABLE",
                    errorMessage = "Failed to query document metadata: ${e.message}"
                )
            }

            // 3. Verify file extension and prepare stream
            val fileName = displayName ?: uri.lastPathSegment ?: "pack.lmpack"

            val inputStream = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: SecurityException) {
                return@withContext ImportResult.Failure(
                    errorCode = "PERMISSION_DENIED",
                    errorMessage = "Access to selected document was revoked or denied: ${e.message}"
                )
            } catch (e: Exception) {
                return@withContext ImportResult.Failure(
                    errorCode = "INPUT_STREAM_UNAVAILABLE",
                    errorMessage = "Cannot open document stream: ${e.message}"
                )
            } ?: return@withContext ImportResult.Failure(
                errorCode = "STREAM_NULL",
                errorMessage = "Failed to open input stream for URI: $uri"
            )

            importFromInputStream(
                inputStream = inputStream,
                fileName = fileName,
                declaredSize = declaredSize,
                trustedPublicKey = trustedPublicKey,
                onProgress = onProgress
            )
        } finally {
            if (persistablePermissionTaken) {
                try {
                    context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {
                    // Safe cleanup best effort
                }
            }
        }
    }

    /**
     * Streams an .lmpack archive from an InputStream into application-private staging storage
     * and performs staged, recoverable installation.
     */
    suspend fun importFromInputStream(
        inputStream: java.io.InputStream,
        fileName: String,
        declaredSize: Long = -1L,
        trustedPublicKey: PublicKey? = null,
        onProgress: ((stage: String, percent: Int) -> Unit)? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        // 1. Verify file extension
        if (!fileName.endsWith(ContentPackSecurity.CANONICAL_EXTENSION, ignoreCase = true)) {
            return@withContext ImportResult.Failure(
                errorCode = "INVALID_FILE_EXTENSION",
                errorMessage = "Selected file '$fileName' is not a valid LearnMate Content Pack (${ContentPackSecurity.CANONICAL_EXTENSION})"
            )
        }

        // 2. Enforce size bounds (max 50MB uncompressed staging)
        if (declaredSize > ContentPackSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES) {
            return@withContext ImportResult.Failure(
                errorCode = "FILE_TOO_LARGE",
                errorMessage = "Selected file exceeds maximum allowable pack size (${ContentPackSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES / (1024 * 1024)} MB)"
            )
        }

        // 3. Stream into application-private staging storage with unique temporary file
        val stagingDir = File(baseStorageDir, ".staging")
        stagingDir.mkdirs()
        val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val uniqueImportId = UUID.randomUUID().toString().take(8)
        val tempImportFile = File(stagingDir, "saf_import_${System.currentTimeMillis()}_${uniqueImportId}_$cleanName")

        // Register active import
        activeImportFiles.add(tempImportFile.absolutePath)

        try {
            onProgress?.invoke("STREAMING_DOCUMENT", 10)
            var totalBytesRead = 0L
            inputStream.use { input ->
                FileOutputStream(tempImportFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (totalBytesRead > ContentPackSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                            return@withContext ImportResult.Failure(
                                errorCode = "STREAM_EXCEEDED_LIMIT",
                                errorMessage = "Streamed content exceeded safety bound of ${ContentPackSecurity.MAX_TOTAL_UNCOMPRESSED_BYTES} bytes"
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (tempImportFile.length() == 0L) {
                return@withContext ImportResult.Failure(
                    errorCode = "EMPTY_FILE",
                    errorMessage = "Selected document contains zero bytes"
                )
            }

            onProgress?.invoke("VALIDATING_ARCHIVE", 40)
            val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = dao)
            onProgress?.invoke("INSTALLING_CONTENT", 70)
            val installResult = installer.installPack(
                archiveFile = tempImportFile,
                trustedPublicKey = trustedPublicKey,
                allowTestKeyIfNull = false
            )

            when (installResult) {
                is ContentPackInstaller.InstallResult.Success -> {
                    onProgress?.invoke("COMPLETED", 100)
                    ImportResult.Success(
                        packId = installResult.packId,
                        title = installResult.title,
                        nodesInstalled = installResult.nodesInstalled
                    )
                }
                is ContentPackInstaller.InstallResult.Failure -> {
                    ImportResult.Failure(
                        errorCode = installResult.errorCode,
                        errorMessage = installResult.message
                    )
                }
            }
        } catch (e: Exception) {
            ImportResult.Failure(
                errorCode = "IMPORT_FAILED",
                errorMessage = "Error importing document: ${e.message}"
            )
        } finally {
            // Unregister active import and purge temporary staged import file
            activeImportFiles.remove(tempImportFile.absolutePath)
            if (tempImportFile.exists()) {
                tempImportFile.delete()
            }
        }
    }
}


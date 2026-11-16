# Report 3: Model Storage Path and Filesystem Organization Report

## 1. Executive Summary
This report documents the resolution of storage path resolution fragmentation and filesystem lifecycle management for on-device LLM assets in LearnMate AI (`com.learnmate.ai`). Previously, dual-path resolution inconsistencies caused `ModelAssetManager` to fail resolving model weights stored in model-specific subdirectories, producing artificial "NOT INSTALLED" indicators while assets were physically present on disk.

---

## 2. Path Resolution Architecture & Unification

### 2.1 The Path Fragmentation Root Cause
Prior to remediation, two competing storage conventions existed in the codebase:
1. **Legacy Flat Path:** Assets expected directly under `context.filesDir/models/llama3_2_1b_8da4w.pte`.
2. **Catalog-Managed Structured Subdirectory:** `ModelDownloadEngine` downloaded assets into dedicated model directories:
   `context.filesDir/models/meta-llama-3.2-1b-instruct-8da4w/llama3_2_1b_8da4w.pte` and `tokenizer.model`.

`ModelAssetManager.activeModelFile` previously had a hardcoded definition:
```kotlin
// Former buggy implementation:
val activeModelFile: File
    get() = File(modelsDirectory, "llama3_2_1b_8da4w.pte")
```
When downloads finished under the structured directory, `activeModelFile.exists()` returned `false`. Consequently, `ExecuTorchLlamaEngine.refreshModelPresenceState()` marked the model as `NOT_INSTALLED`.

### 2.2 Unified Resolution Logic
`ModelAssetManager.kt` was restructured to provide unified path resolution:
```kotlin
val activeModelFile: File
    get() = getModelFile(ActiveModelManifest.MODEL_ID)

val activeTokenizerFile: File
    get() = getTokenizerFile(ActiveModelManifest.MODEL_ID)

fun getModelFile(modelId: String): File {
    val subfolderFile = File(getModelDirectory(modelId), ActiveModelManifest.MODEL_FILE_NAME)
    if (subfolderFile.exists()) return subfolderFile
    val rootFile = File(modelsDirectory, ActiveModelManifest.MODEL_FILE_NAME)
    if (rootFile.exists()) return rootFile
    return subfolderFile
}

fun getTokenizerFile(modelId: String): File {
    val subfolderFile = File(getModelDirectory(modelId), ActiveModelManifest.TOKENIZER_FILE_NAME)
    if (subfolderFile.exists()) return subfolderFile
    val rootFile = File(modelsDirectory, ActiveModelManifest.TOKENIZER_FILE_NAME)
    if (rootFile.exists()) return rootFile
    return subfolderFile
}
```
This guarantees backward compatibility for flat legacy paths while strictly supporting structured multi-model catalogs.

---

## 3. Storage Hierarchy & Permissions

| Asset Path | Type | Read/Write Permissions | Purpose |
| :--- | :--- | :--- | :--- |
| `context.filesDir/models/` | Directory | `0700` (`rwx------`) | Root storage for AI weights |
| `context.filesDir/models/meta-llama-3.2-1b-instruct-8da4w/` | Directory | `0700` (`rwx------`) | Model package sandbox |
| `.../llama3_2_1b_8da4w.pte.part` | File | `0600` (`rw-------`) | In-flight atomic download buffer |
| `.../llama3_2_1b_8da4w.pte` | File | `0600` (`rw-------`) | Immutable validated PTE model |
| `.../tokenizer.model.part` | File | `0600` (`rw-------`) | In-flight tokenizer buffer |
| `.../tokenizer.model` | File | `0600` (`rw-------`) | SentencePiece/BPE tokenizer file |

### 3.1 Security & Sandboxing
- Storage is exclusively contained within the application's private internal storage (`/data/user/0/com.learnmate.ai/files/models/`).
- External shared storage (`/sdcard/` or scoped public storage) is never used for runtime model execution, preventing unprivileged apps from tampering with or intercepting weights.
- Filesystem permissions are strictly locked to UID/GID of `com.learnmate.ai`.

---

## 4. Atomic Write and Lifecycle Invariants

1. **Atomic Rename Guarantee:**
   Assets are downloaded to temporary files suffixed with `.part`. Only after full byte transfer and cryptographic SHA-256 verification does an atomic `renameTo()` occur:
   ```kotlin
   val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")
   // Download stream completes...
   // verifySha256(tempFile, expectedChecksum) == true
   val renameSuccess = tempFile.renameTo(targetFile)
   ```
2. **Crash & Interruption Safety:**
   If a download is aborted or the app process is killed mid-stream, orphaned `.part` files are cleaned up upon re-triggering, preventing partially written weights from being loaded by the native runtime.
3. **Storage Headroom Check:**
   Prior to initiating any download, `ModelAssetManager.hasSufficientStorage(requiredBytes)` verifies that free internal storage exceeds the required asset size plus a 250 MB safety buffer for SQLite and OS operations.

---

## 5. Verification Status
- Unit tests validating unified path resolution: Passed (`Gate6166MultiModelCatalogTest`, `ModelDownloadTest`).
- Dynamic path lookup: Verified under both root and subfolder configurations.

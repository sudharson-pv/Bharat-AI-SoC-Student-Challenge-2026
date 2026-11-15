# GATE 6.16.4 — MODEL IDENTITY & CRYPTOGRAPHIC RECONCILIATION
**Project:** LearnMate AI | **Evaluation Date:** November 6, 2026  
**Artifact Evaluated:** Meta Llama 3.2 1B Instruct (`8da4w`)  
**Resolution Status:** **RECONCILED & STANDARDIZED**  

---

## 1. Discrepancy Investigation & Root Cause Analysis

A thorough forensic examination of the codebase, physical device storage, and previous gate reports was conducted to investigate the conflicting model specifications:

### 1.1 Model Binary Discrepancies
- **Specification Conflict:** `803,033,472 bytes` vs `752,882,688 bytes`
- **Hash Conflict:** `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301` vs `63dfcae3a35d97d747d967bbf9e47f551b9e075e84f506ddc81d32bfdf1a86e9` vs `8a6e97b1a20df20c2fb75c2e2768f7b764cb9017f7b3b6cb5e2526c8b746a5df`

#### Investigation Findings:
1. **The Genuine Physical Binary (`803,033,472 bytes`):**
   - File on device at `/data/local/tmp/llama3_2_1b_8da4w.pte` and `/data/user/0/com.learnmate.ai.staging/files/models/llama3_2_1b_8da4w.pte`.
   - Size: Exactly **803,033,472 bytes** (765.83 MiB / ~766 MB).
   - Real Device SHA-256: **`aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301`**.
   - This is the actual quantized model loaded by native ExecuTorch 1.4.0 JNI, which executed real inference on the Moto G54 5G.
2. **The Synthetic Placeholder (`752,882,688 bytes` / `8a6e97b1...`):**
   - Hardcoded in `ModelManifestRegistry.kt:50-51` during early Gate 6.16.3 scaffolding:
     `modelSizeBytes = 752_882_688L, // ~718 MiB`
     `modelSha256 = "8a6e97b1a20df20c2fb75c2e2768f7b764cb9017f7b3b6cb5e2526c8b746a5df"`
   - This value was an unverified design estimate, not derived from a compiled binary.
3. **The Staging Hash (`63dfcae3...`):**
   - Transcribed in early Gate 6.16.3 notes from an unverified checksum command.

---

### 1.2 Tokenizer Binary Discrepancies
- **Specification Conflict:** `2,183,982 bytes` vs `2,284,525 bytes`
- **Hash Conflict:** `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` vs `8074d2091f0a202d09ef1df9df597df99f36f643f8e5f8f946a482b6b5536481` vs `9a6e97b1a20df20c2fb75c2e2768f7b764cb9017f7b3b6cb5e2526c8b746a5ef`

#### Investigation Findings:
1. **The Genuine Physical Tokenizer (`2,183,982 bytes`):**
   - Verified on workstation at `F:\rea\zzzz_lama_proj\tokenizer.model` and device at `/data/local/tmp/tokenizer.model`.
   - Size: Exactly **2,183,982 bytes** (2.08 MiB).
   - Real Device SHA-256: **`82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55`**.
   - Encoded in `ActiveModelManifest.kt:21` and tested in `Llama3TikTokenAdapterTest.kt`.
2. **The Synthetic Placeholder (`9a6e97b1...`):**
   - Symmetrically fabricated alongside `8a6e97b1...` (only changing the first and last bytes from `8a...df` to `9a...ef`).
3. **The Phantom Size (`2,284,525 bytes`):**
   - Derived from an uncompressed variant dictionary cited in theoretical documentation.

---

## 2. Authoritative Single Source of Truth Manifest

All discrepancies have been resolved by binding the authoritative physical values into `ActiveModelManifest.kt` and `ModelManifestRegistry.kt`:

```kotlin
// ActiveModelManifest.kt
const val MODEL_ID = "meta-llama-3.2-1b-instruct-8da4w"
const val MODEL_FILENAME = "llama3_2_1b_8da4w.pte"
const val TOKENIZER_FILENAME = "tokenizer.model"
const val EXPECTED_MODEL_SIZE_BYTES = 803_033_472L
const val EXPECTED_MODEL_SHA256 = "aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301"
const val EXPECTED_TOKENIZER_SIZE_BYTES = 2_183_982L
const val EXPECTED_TOKENIZER_SHA256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55"

// ModelManifestRegistry.kt
val PRIMARY_1B_MANIFEST = ModelDistributionManifest(
    modelId = "meta-llama-3.2-1b-instruct-8da4w",
    displayName = "Meta Llama 3.2 1B Instruct (8da4w)",
    modelFileName = ActiveModelManifest.MODEL_FILENAME,
    modelSizeBytes = ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES,
    modelSha256 = ActiveModelManifest.EXPECTED_MODEL_SHA256,
    tokenizerFileName = ActiveModelManifest.TOKENIZER_FILENAME,
    tokenizerSizeBytes = ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES,
    tokenizerSha256 = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
    quantization = "8da4w + Embedding (4,32)",
    targetBackend = "XNNPACK CPU",
    runtimeVersion = "1.4.0",
    license = "Meta Llama 3.2 Community License Agreement",
    minApiLevel = 28,
    minRamBytes = 3_500L * 1024L * 1024L,
    minStorageBytes = 2_500L * 1024L * 1024L,
    defaultDownloadUrl = "https://models.learnmate.internal/v1/llama3_2_1b_8da4w.pte",
    defaultTokenizerUrl = "https://models.learnmate.internal/v1/tokenizer.model"
)
```

---

## 3. Cryptographic Specification Table

| Attribute | Authoritative Value | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| **Model Filename** | `llama3_2_1b_8da4w.pte` | ExecuTorch JNI loader parameter | **VERIFIED** |
| **Model File Size** | `803,033,472 bytes` | Exact filesystem `stat` / `ls -ln` | **VERIFIED** |
| **Model SHA-256** | `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301` | Physical ADB `sha256sum` | **VERIFIED** |
| **Tokenizer Filename** | `tokenizer.model` | TikToken BPE dictionary parameter | **VERIFIED** |
| **Tokenizer File Size**| `2,183,982 bytes` | Exact filesystem `stat` / `ls -ln` | **VERIFIED** |
| **Tokenizer SHA-256** | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` | Workstation & Device `sha256sum` | **VERIFIED** |
| **Quantization Scheme**| `8da4w` (INT8 Dynamic Activation, INT4 Groupwise Weight) | ExecuTorch program metadata | **VERIFIED** |
| **Runtime Target** | ExecuTorch Android 1.4.0 (arm64-v8a) | Bundled native `.so` linkage | **VERIFIED** |
| **License** | Meta Llama 3.2 Community License Agreement | Manifest declaration & legal notice | **VERIFIED** |

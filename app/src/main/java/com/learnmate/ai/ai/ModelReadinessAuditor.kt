package com.learnmate.ai.ai

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Gate 6.16.5: Authoritative 8-Step Verification & AI READY Auditor.
 *
 * Enforces all 8 mandatory readiness conditions:
 * 1. Model file exists.
 * 2. Exact expected model size matches.
 * 3. Model SHA-256 matches.
 * 4. Tokenizer exists.
 * 5. Exact expected tokenizer size matches.
 * 6. Tokenizer SHA-256 matches.
 * 7. ExecuTorch initializes successfully.
 * 8. A real inference smoke test succeeds.
 *
 * Never displays AI Model Ready for unverified files.
 */
object ModelReadinessAuditor {

    private const val TAG = "ModelReadinessAuditor"

    data class StepAudit(
        val stepNumber: Int,
        val stepName: String,
        val passed: Boolean,
        val detail: String
    )

    data class AuditResult(
        val isReady: Boolean,
        val state: ModelInstallationState,
        val provenance: ModelProvenance,
        val steps: List<StepAudit>,
        val failedStep: StepAudit? = null,
        val failureReason: String? = null,
        val recoveryAction: String? = null
    )

    /**
     * Executes the comprehensive 8-step verification pipeline.
     */
    suspend fun auditModelReadiness(
        modelsDirectory: File,
        manifest: SignedModelManifest = SignedModelManifest(),
        runtimeBridge: ExecuTorchLlamaEngine.NativeLlmBridge? = null,
        skipRuntimeInit: Boolean = false
    ): AuditResult = withContext(Dispatchers.IO) {
        val steps = mutableListOf<StepAudit>()
        val modelFile = if (File(modelsDirectory, manifest.modelFileName).exists()) {
            File(modelsDirectory, manifest.modelFileName)
        } else {
            File(File(modelsDirectory, manifest.modelId), manifest.modelFileName)
        }
        val tokenizerFile = if (File(modelsDirectory, manifest.tokenizerFileName).exists()) {
            File(modelsDirectory, manifest.tokenizerFileName)
        } else {
            File(File(modelsDirectory, manifest.modelId), manifest.tokenizerFileName)
        }

        // Pre-Flight: Publisher Manifest Digital Signature Verification
        val manifestVerification = ModelSignatureVerifier.verifyManifest(manifest)
        if (manifestVerification is ModelSignatureVerifier.VerificationResult.Invalid) {
            val step = StepAudit(0, "Manifest Digital Signature", false, manifestVerification.reason)
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.NONE,
                steps = steps,
                failedStep = step,
                failureReason = "Publisher Signature Verification Failed: ${manifestVerification.reason}",
                recoveryAction = "Fetch authentic signed manifest from trusted publisher"
            )
        }

        // Step 1: Model file exists
        if (!modelFile.exists() || !modelFile.isFile) {
            val step = StepAudit(1, "Model File Existence", false, "Model file not found: ${modelFile.name}")
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.NOT_INSTALLED,
                provenance = ModelProvenance.NONE,
                steps = steps,
                failedStep = step,
                failureReason = "AI Model file is missing from application storage (${modelFile.absolutePath}).",
                recoveryAction = "Download AI Model from setup screen"
            )
        }
        steps.add(StepAudit(1, "Model File Existence", true, "Found: ${modelFile.name} (${modelFile.length()} bytes)"))

        // Security Gate 6.16.6: Explicit GGUF file format rejection
        if (modelFile.name.endsWith(".gguf", ignoreCase = true) || isGgufFile(modelFile)) {
            val step = StepAudit(1, "Model Format Verification", false, "GGUF format rejected. ExecuTorch requires flatbuffer .pte")
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Incompatible File Format: GGUF files cannot be loaded through the ExecuTorch PTE loader. Use PyTorch export to generate .pte.",
                recoveryAction = "Export model to .pte flatbuffer using ExecuTorch toolchain"
            )
        }

        // Step 2: Exact expected model size matches
        if (modelFile.length() != manifest.modelSizeBytes) {
            val step = StepAudit(
                2, "Exact Model Size", false,
                "Model size mismatch: expected ${manifest.modelSizeBytes} bytes, found ${modelFile.length()} bytes"
            )
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Model File Truncated or Modified: Expected exactly ${manifest.modelSizeBytes} bytes, but found ${modelFile.length()} bytes.",
                recoveryAction = "Reinstall or re-download model"
            )
        }
        steps.add(StepAudit(2, "Exact Model Size", true, "Exact match: ${manifest.modelSizeBytes} bytes"))

        // Step 3: Model SHA-256 matches
        val modelSha = calculateSha256(modelFile)
        if (!modelSha.equals(manifest.modelSha256, ignoreCase = true)) {
            val step = StepAudit(
                3, "Model SHA-256 Digest", false,
                "Model hash mismatch: expected ${manifest.modelSha256}, calculated $modelSha"
            )
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Integrity Check Failed: Model SHA-256 digest mismatch. The model file has been modified or corrupted.",
                recoveryAction = "Delete corrupted file and reinstall authentic weights"
            )
        }
        steps.add(StepAudit(3, "Model SHA-256 Digest", true, "Verified: $modelSha"))

        // Step 4: Tokenizer exists
        if (!tokenizerFile.exists() || !tokenizerFile.isFile) {
            val step = StepAudit(4, "Tokenizer File Existence", false, "Tokenizer file not found: ${tokenizerFile.name}")
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Tokenizer file missing: ${tokenizerFile.name}",
                recoveryAction = "Download tokenizer asset"
            )
        }
        steps.add(StepAudit(4, "Tokenizer File Existence", true, "Found: ${tokenizerFile.name} (${tokenizerFile.length()} bytes)"))

        // Step 5: Exact expected tokenizer size matches
        if (tokenizerFile.length() != manifest.tokenizerSizeBytes) {
            val step = StepAudit(
                5, "Exact Tokenizer Size", false,
                "Tokenizer size mismatch: expected ${manifest.tokenizerSizeBytes} bytes, found ${tokenizerFile.length()} bytes"
            )
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Tokenizer File Truncated: Expected ${manifest.tokenizerSizeBytes} bytes, found ${tokenizerFile.length()} bytes.",
                recoveryAction = "Reinstall tokenizer"
            )
        }
        steps.add(StepAudit(5, "Exact Tokenizer Size", true, "Exact match: ${manifest.tokenizerSizeBytes} bytes"))

        // Step 6: Tokenizer SHA-256 matches
        val tokSha = calculateSha256(tokenizerFile)
        if (!tokSha.equals(manifest.tokenizerSha256, ignoreCase = true)) {
            val step = StepAudit(
                6, "Tokenizer SHA-256 Digest", false,
                "Tokenizer hash mismatch: expected ${manifest.tokenizerSha256}, calculated $tokSha"
            )
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.LOCAL_UNVERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Tokenizer Integrity Failed: SHA-256 digest mismatch.",
                recoveryAction = "Re-download authentic tokenizer"
            )
        }
        steps.add(StepAudit(6, "Tokenizer SHA-256 Digest", true, "Verified: $tokSha"))

        if (skipRuntimeInit) {
            return@withContext AuditResult(
                isReady = true,
                state = ModelInstallationState.VERIFIED,
                provenance = ModelProvenance.CRYPTOGRAPHICALLY_VERIFIED,
                steps = steps
            )
        }

        // Step 7: ExecuTorch initializes successfully
        val bridge = runtimeBridge ?: ReflectionExecuTorchLlmBridge()
        var moduleInstance: Any? = null
        try {
            moduleInstance = bridge.createAndLoadModule(
                modelFile.absolutePath,
                tokenizerFile.absolutePath,
                0.7f
            )
            steps.add(StepAudit(7, "ExecuTorch Runtime Initialization", true, "Native LlmModule initialized and loaded into memory"))
        } catch (t: Throwable) {
            val step = StepAudit(7, "ExecuTorch Runtime Initialization", false, "Initialization failed: ${t.javaClass.simpleName}: ${t.message}")
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.CRYPTOGRAPHICALLY_VERIFIED,
                steps = steps,
                failedStep = step,
                failureReason = "Native ExecuTorch Runtime Initialization Failed: ${t.message}",
                recoveryAction = "Verify device architecture and runtime compatibility"
            )
        }

        // Step 8: Real inference smoke test succeeds
        try {
            var tokensGenerated = 0
            val status = bridge.generate(
                moduleInstance = moduleInstance,
                prompt = "<|start_header_id|>user<|end_header_id|>\n\nHi<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n",
                maxNewTokens = 5,
                onToken = { token ->
                    if (token.isNotEmpty() && !token.contains("<|eot_id|>")) {
                        tokensGenerated++
                    }
                },
                onStats = {}
            )
            if (tokensGenerated == 0) {
                val step = StepAudit(8, "Inference Smoke Test", false, "LlmModule generated 0 tokens (status=$status)")
                steps.add(step)
                return@withContext AuditResult(
                    isReady = false,
                    state = ModelInstallationState.FAILED,
                    provenance = ModelProvenance.RUNTIME_INITIALIZED,
                    steps = steps,
                    failedStep = step,
                    failureReason = "Inference Smoke Test Failed: Zero tokens produced by native runtime.",
                    recoveryAction = "Retry smoke test or inspect native runtime logs"
                )
            }
            steps.add(StepAudit(8, "Inference Smoke Test", true, "Successfully generated $tokensGenerated tokens"))
        } catch (t: Throwable) {
            val step = StepAudit(8, "Inference Smoke Test", false, "Smoke test exception: ${t.javaClass.simpleName}: ${t.message}")
            steps.add(step)
            return@withContext AuditResult(
                isReady = false,
                state = ModelInstallationState.FAILED,
                provenance = ModelProvenance.RUNTIME_INITIALIZED,
                steps = steps,
                failedStep = step,
                failureReason = "Inference Smoke Test Error: ${t.message}",
                recoveryAction = "Inspect native runtime logs and re-initialize"
            )
        }

        return@withContext AuditResult(
            isReady = true,
            state = ModelInstallationState.AI_READY,
            provenance = ModelProvenance.SMOKE_TEST_PASSED,
            steps = steps
        )
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis ->
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

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
}


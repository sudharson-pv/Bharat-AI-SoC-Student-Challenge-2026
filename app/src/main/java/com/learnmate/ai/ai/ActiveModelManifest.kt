package com.learnmate.ai.ai

/**
 * Single authoritative manifest for the active on-device Llama model.
 * Enforces verified filenames and runtime bounds for Gate 2 inference spike.
 */
object ActiveModelManifest {
    const val MODEL_ID = "meta-llama-3.2-1b-instruct-8da4w"
    const val DISPLAY_NAME = "Meta Llama 3.2 1B Instruct (8da4w)"
    const val MODEL_FILENAME = "llama3_2_1b_8da4w.pte"
    const val TOKENIZER_FILENAME = "tokenizer.model"
    const val QUANTIZATION_SCHEME = "8da4w + Embedding (4,32)"
    const val TARGET_BACKEND = "XNNPACK CPU"
    const val EXECUTORCH_RUNTIME_VERSION = "1.4.0"
    const val MAX_SEQ_LENGTH = 2048
    const val MAX_CONTEXT_LENGTH = 2048
    const val MIN_VALID_PTE_BYTES = 100L * 1024L * 1024L // >= 100 MiB sanity check
    const val MIN_VALID_TOKENIZER_BYTES = 100L * 1024L   // >= 100 KiB sanity check

    // Authoritative Gate 6.16.4 Model Specifications (Verified Physical Hardware Artifacts)
    const val EXPECTED_MODEL_SIZE_BYTES = 803_033_472L
    const val EXPECTED_MODEL_SHA256 = "aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301"

    // Tokenizer & Sequence Arithmetic Invariants (Gate 5.8 Hardened, Gate 6.17 1000-Token Expansion)
    const val EXPECTED_TOKENIZER_SHA256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55"
    const val EXPECTED_TOKENIZER_SIZE_BYTES = 2_183_982L
    const val BASE_VOCAB_SIZE = 128_000
    const val SPECIAL_TOKENS_COUNT = 256
    const val TOTAL_VOCAB_SIZE = 128_256
    const val MAX_PREFILL_TOKENS = 368
    const val MAX_NEW_TOKENS = 1000
    const val MAX_AI_TUTOR_NEW_TOKENS = 1000
    const val HISTORICAL_MAX_NEW_TOKENS = 144
    const val STRUCTURAL_OVERHEAD_TOKENS = 15
    const val MAX_RAW_CONTENT_TOKENS = 353 // 368 - 15

    // Reference historical BF16 artifact metadata (preserved strictly for audit/reference, not active runtime)
    const val HISTORICAL_BF16_FILENAME = "llama3_2_1b.pte"
    const val HISTORICAL_BF16_SIZE_BYTES = 5_994_310_272L
    const val HISTORICAL_BF16_SHA256 = "3AE188DC5EC4A0C33A75385852F9B3E786ED44DBFB439167E7D2AD1EA1D07774"
}

/**
 * Authoritative model runtime states distinguishing physical file presence,
 * cryptographic verification, native runtime initialization, and smoke test execution.
 */
enum class ModelRuntimeState {
    NOT_INSTALLED,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    DOWNLOADED,
    VERIFYING,
    VERIFIED,
    INITIALIZING,
    INITIALIZATION_FAILED,
    READY,
    INFERENCE_FAILED;

    companion object {
        @JvmField val MODEL_MISSING = NOT_INSTALLED
        @JvmField val LOADING_MODEL = INITIALIZING
        @JvmField val MODEL_LOADED = VERIFIED
        @JvmField val MODEL_READY = VERIFIED
        @JvmField val MODEL_ERROR = INITIALIZATION_FAILED
        @JvmField val GENERATING = READY
        @JvmField val COMPLETED = READY
    }
}


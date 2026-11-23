package com.learnmate.ai.domain.provenance

/**
 * Shared AI operation provenance record.
 * Tracks and proves genuine execution of on-device Meta Llama 3.2 1B inference
 * versus verified deterministic fallback across all educational operations.
 *
 * Stored strictly locally; never uploaded to remote servers.
 */
enum class AiProvenanceLabel(val displayName: String, val badgeColorHex: Long) {
    AI_GENERATED("AI Generated", 0xFF673AB7),
    AI_GENERATED_AND_VALIDATED("AI Generated & Validated", 0xFF2E7D32),
    AI_GENERATED_REPAIRED("AI Generated (Repaired)", 0xFF673AB7),
    DETERMINISTIC_FALLBACK("Deterministic Fallback", 0xFFF57C00),
    USER_CANCELLED("User Cancelled", 0xFFE65100),
    NOT_GENERATED("Not Generated", 0xFF757575)
}

data class AiOperationProvenance(
    val operationId: String,
    val learnerId: String,
    val trackId: String,
    val profileFingerprint: String,
    val modelId: String = "meta-llama/Llama-3.2-1B-Instruct",
    val modelVersion: String = "XNNPACK-Q8DA4W-1.0",
    val operationName: String,
    val isRealInferenceExecuted: Boolean,
    val isDeterministicFallbackUsed: Boolean,
    val fallbackReason: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val validationStatus: String = if (isRealInferenceExecuted) "AI_VALIDATED" else "FALLBACK",
    val generationDurationMs: Long = 0L,
    val tokenCount: Int = 0,
    val promptTokensEstimate: Int = 0,
    val inferenceAttempted: Boolean = true,
    val nativeInvocationStarted: Boolean = isRealInferenceExecuted,
    val decodeStarted: Boolean = isRealInferenceExecuted || tokenCount > 0,
    val tokensGenerated: Int = tokenCount,
    val resultSource: String = if (isRealInferenceExecuted) "LLM" else "DETERMINISTIC",
    val nativeTerminationStatus: String = if (isRealInferenceExecuted) "NATURAL_COMPLETION" else "NOT_STARTED"
) {
    val displayLabel: AiProvenanceLabel
        get() = when {
            fallbackReason == "CANCELLED_BY_USER" -> AiProvenanceLabel.USER_CANCELLED
            isRealInferenceExecuted && validationStatus == "AI_VALIDATED" -> AiProvenanceLabel.AI_GENERATED_AND_VALIDATED
            isRealInferenceExecuted && validationStatus == "AI_REPAIRED" -> AiProvenanceLabel.AI_GENERATED_REPAIRED
            isRealInferenceExecuted -> AiProvenanceLabel.AI_GENERATED
            isDeterministicFallbackUsed -> AiProvenanceLabel.DETERMINISTIC_FALLBACK
            else -> AiProvenanceLabel.NOT_GENERATED
        }
}

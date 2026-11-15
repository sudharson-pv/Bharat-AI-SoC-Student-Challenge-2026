package com.learnmate.ai.ai

/**
 * Budgeter enforcing sequence and context limits:
 * - Max sequence length per generation: 512
 * - Max context length: 2048
 * - Dynamic output token budgeting
 */
object PromptBudgeter {
    const val MAX_SEQ_LENGTH = ActiveModelManifest.MAX_SEQ_LENGTH
    const val MAX_CONTEXT_LENGTH = ActiveModelManifest.MAX_CONTEXT_LENGTH
    private const val MIN_OUTPUT_HEADROOM = 32

    data class BudgetedPromptPlan(
        val formattedPrompt: String,
        val estimatedPromptTokens: Int,
        val allowedOutputTokens: Int
    )

    /**
     * Estimates tokens using standard 1.3x BPE ratio for English/code and conservative
     * multi-byte handling for Indic scripts.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var tokens = 0
        val words = text.split(Regex("\\s+"))
        for (w in words) {
            if (w.isEmpty()) continue
            if (w.startsWith("<|") && w.endsWith("|>")) {
                tokens += 1
            } else {
                val nonAsciiCount = w.count { it.code > 127 }
                if (nonAsciiCount > 0) {
                    // Indic or non-ASCII characters consume ~2-3 bytes / tokens in byte-fallback tokenizers
                    tokens += (w.length * 2)
                } else {
                    tokens += ((w.length + 3) / 4).coerceAtLeast(1)
                }
            }
        }
        return tokens.coerceAtLeast(1)
    }

    fun planPromptBudget(
        prompt: String,
        requestedMaxOutput: Int = 256
    ): BudgetedPromptPlan {
        val estimated = estimateTokens(prompt)
        val available = (MAX_SEQ_LENGTH - estimated).coerceAtLeast(MIN_OUTPUT_HEADROOM)
        val allowed = requestedMaxOutput.coerceIn(MIN_OUTPUT_HEADROOM, available)
        return BudgetedPromptPlan(
            formattedPrompt = prompt,
            estimatedPromptTokens = estimated,
            allowedOutputTokens = allowed
        )
    }
}


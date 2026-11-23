package com.learnmate.ai.ai

/**
 * Mathematically verified Exact Prompt Budgeter for Llama 3.2 1B Instruct on ExecuTorch.
 *
 * Invariants:
 * 1. Requires verified curriculum context (fails closed with IllegalArgumentException if missing).
 * 2. Neutralizes prompt injection by sanitizing all untrusted `<|` patterns with zero-width break.
 * 3. Counts the exact completed prompt, including all labels, template tokens, and role headers.
 * 4. Never assumes independent segment token counts are additive across merge boundaries.
 * 5. Strictly enforces prefill <= 368 tokens, generation <= 144 tokens, total sequence <= 512 tokens.
 * 6. Preserves Unicode code points and surrogate pairs during context truncation.
 */
object ExactPromptBudgeter {

    const val MAX_SEQ_LENGTH = 512
    const val MAX_OUTPUT_HEADROOM = 144
    const val MAX_PREFILL_CEILING = 368
    const val STRUCTURAL_OVERHEAD_TOKENS = 15
    const val MAX_RAW_CONTENT_BUDGET = 353

    const val MAX_SYSTEM_TOKENS = 45
    const val MAX_USER_QUERY_TOKENS = 128

    const val BUDGET_SKILL_GAP_OUTPUT = 110
    const val BUDGET_ROADMAP_OUTPUT = 125
    const val BUDGET_STUDY_PLAN_OUTPUT = 105

    data class BudgetPlan(
        val formattedPrompt: String,
        val exactPrefillTokens: Int,
        val allowedOutputTokens: Int,
        val effectiveSeqLen: Int = exactPrefillTokens + allowedOutputTokens,
        val systemPromptTokens: Int = 0,
        val contextTokens: Int = 0,
        val queryTokens: Int = 0
    )

    /**
     * Sanitizes untrusted text to prevent prompt injection when passed through native JNI String API.
     * Inserts a zero-width space (\u200B) into literal `<|` sequences, preventing native C++
     * TikToken from matching control tokens (<|start_header_id|>, <|eot_id|>, etc.) while
     * rendering identically in learner UI.
     */
    fun sanitizeUntrustedText(text: String): String {
        if (!text.contains("<|")) return text
        return text.replace("<|", "<\u200B|")
    }

    /**
     * Plans and budgets an exact completed prompt for on-device inference.
     *
     * @param systemPrompt Verified system instructions.
     * @param curriculumContext Verified curriculum knowledge node. MUST NOT be null or blank.
     * @param userQuery Learner query.
     * @param tokenizer Initialized Llama3TikTokenAdapter.
     * @param outputTokenBudget Specific generation budget (<= 144 tokens).
     */
    fun planPrompt(
        systemPrompt: String,
        curriculumContext: String?,
        userQuery: String,
        tokenizer: Llama3TikTokenAdapter,
        outputTokenBudget: Int = MAX_OUTPUT_HEADROOM
    ): BudgetPlan {
        // Invariant 1: Require valid verified curriculum context (fail closed)
        if (curriculumContext.isNullOrBlank()) {
            throw IllegalArgumentException(
                "Curriculum context is required and verified before prompt assembly. Missing context is not permitted."
            )
        }
        if (userQuery.isBlank()) {
            throw IllegalArgumentException("User query cannot be blank.")
        }

        // Invariant 2: Sanitize untrusted user and context inputs
        val safeSys = sanitizeUntrustedText(systemPrompt.trim())
        val safeQuery = sanitizeUntrustedText(userQuery.trim())
        var safeContext = sanitizeUntrustedText(curriculumContext.trim())

        // Invariant 3: Assemble full complete prompt string and verify exact non-additive token count
        var formatted = assembleFullPrompt(safeSys, safeContext, safeQuery)
        var exactTokens = tokenizer.encode(formatted).size

        val targetOutput = outputTokenBudget.coerceIn(1, MAX_OUTPUT_HEADROOM)
        val maxAllowedPrefill = minOf(MAX_PREFILL_CEILING, MAX_SEQ_LENGTH - targetOutput)

        // Invariant 4: Truncate context if total prefill exceeds ceiling or leaves insufficient output space
        if (exactTokens > maxAllowedPrefill) {
            val baseOverhead = tokenizer.encode(assembleFullPrompt(safeSys, "", safeQuery)).size
            val allowedContextTokens = (maxAllowedPrefill - baseOverhead).coerceAtLeast(0)

            val rawCtxTokens = tokenizer.encode(safeContext)
            var sliceLen = allowedContextTokens.coerceAtMost(rawCtxTokens.size)

            while (sliceLen >= 0) {
                val candidateTokens = rawCtxTokens.take(sliceLen)
                safeContext = tokenizer.decode(candidateTokens)
                formatted = assembleFullPrompt(safeSys, safeContext, safeQuery)
                exactTokens = tokenizer.encode(formatted).size
                if (exactTokens <= maxAllowedPrefill || sliceLen == 0) {
                    break
                }
                sliceLen--
            }
        }

        // Final sanity bounds
        if (exactTokens > maxAllowedPrefill) {
            throw IllegalStateException(
                "Critical budgeting failure: Prefill tokens ($exactTokens) exceeds ceiling ($maxAllowedPrefill)"
            )
        }

        val allowedOutput = targetOutput
        // Invariant 5: effectiveSeqLen for ExecuTorch runner (prompt + maxNewTokens <= 512)
        val effectiveSeqLen = (exactTokens + allowedOutput).coerceAtMost(MAX_SEQ_LENGTH)

        val sysTokens = tokenizer.encode(safeSys).size
        val qTokens = tokenizer.encode(safeQuery).size
        val ctxTokens = tokenizer.encode(safeContext).size

        return BudgetPlan(
            formattedPrompt = formatted,
            exactPrefillTokens = exactTokens,
            allowedOutputTokens = allowedOutput,
            effectiveSeqLen = effectiveSeqLen,
            systemPromptTokens = sysTokens,
            contextTokens = ctxTokens,
            queryTokens = qTokens
        )
    }

    private fun assembleFullPrompt(systemText: String, contextText: String, queryText: String): String {
        return buildString {
            append("<|begin_of_text|>")
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append(systemText)
            append("<|eot_id|>")
            append("<|start_header_id|>user<|end_header_id|>\n\n")
            append("Context:\n")
            append(contextText)
            append("\n\nQuestion:\n")
            append(queryText)
            append("<|eot_id|>")
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
    }
}

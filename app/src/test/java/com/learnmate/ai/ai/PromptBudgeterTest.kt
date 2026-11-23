package com.learnmate.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 6.17 Unit Test Suite: 1000-Token Expansion & Context-Aware Prompt Budgeter.
 *
 * Verifies:
 * 1. Requested budgets: 144, 256, 512, 768, 999, 1000, 1001, 2048, Int.MAX_VALUE.
 * 2. Empty, short, and long prompts.
 * 3. Context exhaustion and remaining headroom clamping (prompt + output <= 2048).
 * 4. Indic multibyte script token estimation safety.
 */
class PromptBudgeterTest {

    @Test
    fun testPromptBudgeter_RequestedBudgetClampingAcrossSpectrum() {
        val prompt = "Explain CMOS inverter operation in detail."
        val estimated = PromptBudgeter.estimateTokens(prompt)
        assertTrue("Estimated prompt tokens should be realistic", estimated in 5..25)

        val testBudgets = listOf(
            0 to 1,
            -50 to 1,
            1 to 1,
            144 to 144,
            256 to 256,
            512 to 512,
            768 to 768,
            999 to 999,
            1000 to 1000,
            1001 to 1000, // Clamped to MAX_NEW_TOKENS (1000)
            1500 to 1000, // Clamped to MAX_NEW_TOKENS (1000)
            2048 to 1000, // Clamped to MAX_NEW_TOKENS (1000)
            Int.MAX_VALUE to 1000 // Clamped to MAX_NEW_TOKENS (1000)
        )

        for ((requested, expectedAllowed) in testBudgets) {
            val plan = PromptBudgeter.planPromptBudget(prompt, requestedMaxOutput = requested)
            assertEquals("Requested $requested should yield $expectedAllowed allowed tokens", expectedAllowed, plan.allowedOutputTokens)
            assertTrue(
                "Estimated prompt ($estimated) + allowed output (${plan.allowedOutputTokens}) must fit in MAX_CONTEXT_LENGTH (2048)",
                plan.estimatedPromptTokens + plan.allowedOutputTokens <= ActiveModelManifest.MAX_CONTEXT_LENGTH
            )
        }
    }

    @Test
    fun testPromptBudgeter_DefaultOutputIs1000() {
        val prompt = "Explain setup and hold time with examples."
        val plan = PromptBudgeter.planPromptBudget(prompt)
        assertEquals(1000, plan.allowedOutputTokens)
    }

    @Test
    fun testPromptBudgeter_EmptyPrompt() {
        val plan = PromptBudgeter.planPromptBudget("", requestedMaxOutput = 1000)
        assertEquals(0, plan.estimatedPromptTokens)
        assertEquals(1000, plan.allowedOutputTokens)
    }

    @Test
    fun testPromptBudgeter_ContextExhaustionClamping() {
        // Construct a prompt of ~1400 tokens
        val longText = "Pipelined processor architecture stage hazard forward branch predict. ".repeat(70)
        val estimated = PromptBudgeter.estimateTokens(longText)
        assertTrue("Estimated prompt tokens should be between 1200 and 1600", estimated in 1200..1600)

        val plan = PromptBudgeter.planPromptBudget(longText, requestedMaxOutput = 1000)

        // Must clamp allowed tokens so estimated + allowed + 32 headroom <= 2048
        val maxAvailable = (PromptBudgeter.MAX_SEQ_LENGTH - estimated - 32).coerceAtLeast(1)
        val expected = minOf(maxAvailable, 1000)
        assertEquals(expected, plan.allowedOutputTokens)
        assertTrue(
            "Total window (prompt $estimated + output ${plan.allowedOutputTokens}) must not exceed 2048",
            plan.estimatedPromptTokens + plan.allowedOutputTokens <= 2048
        )
    }

    @Test
    fun testPromptBudgeter_ExtremeContextSaturation() {
        // Construct a prompt that almost fills the 2048 window (~2020 tokens)
        val saturatedText = "Word ".repeat(2100)
        val estimated = PromptBudgeter.estimateTokens(saturatedText)
        assertTrue("Estimated should be >= 2000", estimated >= 2000)

        val plan = PromptBudgeter.planPromptBudget(saturatedText, requestedMaxOutput = 1000)
        // With less than 32 tokens headroom available, allowedOutputTokens must be clamped to minimum 1
        assertEquals(1, plan.allowedOutputTokens)
    }

    @Test
    fun testPromptBudgeter_SpecialControlTokensHandled() {
        val textWithSpecial = "<|begin_of_text|> <|start_header_id|> system <|end_header_id|> Hello world <|eot_id|>"
        val tokens = PromptBudgeter.estimateTokens(textWithSpecial)
        // Special tokens counted as 1 token each
        assertTrue(tokens in 5..10)
    }

    @Test
    fun testPromptBudgeter_IndicScriptConservativeBudgeting() {
        val tamilPrompt = "ஒரு டிஜிட்டல் இன்வெர்ட்டர் எவ்வாறு செயல்படுகிறது என்பதை விரிவாக விளக்குக."
        val estimated = PromptBudgeter.estimateTokens(tamilPrompt)
        assertTrue("Multibyte Indic script should estimate conservative token count", estimated > 30)

        val plan = PromptBudgeter.planPromptBudget(tamilPrompt, requestedMaxOutput = 1000)
        assertEquals(1000, plan.allowedOutputTokens)
        assertTrue(plan.estimatedPromptTokens + plan.allowedOutputTokens <= 2048)
    }
}

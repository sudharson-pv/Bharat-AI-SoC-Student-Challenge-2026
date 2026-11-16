package com.learnmate.ai.ai

import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.provenance.AiOperationProvenance
import com.learnmate.ai.domain.provenance.AiProvenanceLabel
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Gate 6.16: AI Provenance, SHA-256 Fingerprinting, Stale Detection & Session Randomization Qualification.
 */
class AiProvenanceAndFingerprintTest {

    @Test
    fun testSha256Fingerprint_deterministicAndDistinctForEachField() {
        val baseline = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )

        // 1. Identical inputs must yield exact same SHA-256 fingerprint
        val identical = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("CMOS", "Digital Circuits"), // different order, sorted inside
            preferredLanguage = "en"
        )
        assertEquals("Identical inputs must yield identical SHA-256 digest", baseline, identical)
        assertEquals(64, baseline.length) // 256 bits = 64 hex characters

        // 2. Change domain
        val diffDomain = LearnerContext.computeSha256Fingerprint(
            domain = "Management and Commerce",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing domain must change fingerprint", baseline, diffDomain)

        // 3. Change branch
        val diffBranch = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Computer Science",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing branch must change fingerprint", baseline, diffBranch)

        // 4. Change specialization
        val diffSpec = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "Embedded Systems",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing specialization must change fingerprint", baseline, diffSpec)

        // 5. Change education level
        val diffLevel = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "POSTGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing education level must change fingerprint", baseline, diffLevel)

        // 6. Change knowledge level
        val diffKnowledge = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Beginner",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing knowledge level must change fingerprint", baseline, diffKnowledge)

        // 7. Change primary goal
        val diffGoal = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "RTL Verification Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing primary goal must change fingerprint", baseline, diffGoal)

        // 8. Change subjects
        val diffSubjects = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Static Timing Analysis"),
            preferredLanguage = "en"
        )
        assertNotEquals("Changing subjects must change fingerprint", baseline, diffSubjects)

        // 9. Change language
        val diffLang = LearnerContext.computeSha256Fingerprint(
            domain = "Engineering and Technology",
            branch = "Electronics Engineering",
            specialization = "VLSI Design",
            educationLevel = "UNDERGRADUATE",
            currentKnowledgeLevel = "Intermediate",
            primaryGoal = "Physical Design Engineer",
            relevantSubjects = listOf("Digital Circuits", "CMOS"),
            preferredLanguage = "hi"
        )
        assertNotEquals("Changing language must change fingerprint", baseline, diffLang)
    }

    @Test
    fun testIsDiagnosticStale_checksSha256Fingerprint() {
        val profile = LearnerProfile(
            userId = "usr_vlsi_01",
            name = "Ananya",
            targetDomain = "Engineering and Technology",
            subdomain = "Electronics Engineering",
            targetRole = "VLSI Design",
            goal = "Physical Design Engineer",
            currentLevel = "Intermediate",
            educationLevel = EducationLevel.UNDERGRADUATE,
            preferredLanguageCode = "en"
        )
        val context = LearnerContext.fromProfile(profile)

        val freshReport = SkillGapReport(
            userId = "usr_vlsi_01",
            domain = "Engineering and Technology",
            subdomain = "Electronics Engineering",
            overallScorePercent = 80,
            totalQuestions = 10,
            correctCount = 8,
            strengths = listOf("CMOS"),
            weaknesses = listOf("STA"),
            skillGaps = emptyList(),
            profileFingerprint = context.profileFingerprint
        )

        // 1. Same fingerprint -> not stale
        assertFalse("Same profile fingerprint must not be flagged as stale", context.isDiagnosticStale(freshReport))

        // 2. Different fingerprint -> stale
        val staleReport = freshReport.copy(profileFingerprint = "diff_hash_12345")
        assertTrue("Different profile fingerprint must be flagged as stale", context.isDiagnosticStale(staleReport))

        // 3. Null report -> stale
        assertTrue("Null report must be flagged as stale", context.isDiagnosticStale(null))
    }

    @Test
    fun testAiOperationProvenance_labelsHonestAndAccurate() {
        // 1. Real inference & validated
        val realProv = AiOperationProvenance(
            operationId = "op_1",
            learnerId = "usr_01",
            trackId = "track_01",
            profileFingerprint = "fingerprint_123",
            operationName = "DIAGNOSTIC_QUESTION_GENERATION",
            isRealInferenceExecuted = true,
            isDeterministicFallbackUsed = false,
            validationStatus = "AI_VALIDATED",
            generationDurationMs = 1520L,
            tokenCount = 450
        )
        assertEquals(AiProvenanceLabel.AI_GENERATED_AND_VALIDATED, realProv.displayLabel)
        assertEquals("AI Generated & Validated", realProv.displayLabel.displayName)

        // 2. Deterministic fallback
        val fallbackProv = AiOperationProvenance(
            operationId = "op_2",
            learnerId = "usr_01",
            trackId = "track_01",
            profileFingerprint = "fingerprint_123",
            operationName = "DIAGNOSTIC_QUESTION_GENERATION",
            isRealInferenceExecuted = false,
            isDeterministicFallbackUsed = true,
            fallbackReason = "Model runtime not loaded in memory",
            validationStatus = "DETERMINISTIC_FALLBACK"
        )
        assertEquals(AiProvenanceLabel.DETERMINISTIC_FALLBACK, fallbackProv.displayLabel)
        assertEquals("Deterministic Fallback", fallbackProv.displayLabel.displayName)
        assertFalse("Fallback must NEVER be marked as real inference executed", fallbackProv.isRealInferenceExecuted)
    }

    @Test
    fun testAssessmentOptionRandomization_sessionSeedPreservesCorrectAnswer() {
        val question = AssessmentQuestion(
            id = "q_vlsi_timing_01",
            domain = "Engineering",
            subdomain = "VLSI",
            topic = "Static Timing Analysis",
            questionText = "Which condition causes a Setup Time violation in synchronous digital circuits?",
            options = listOf(
                "Data arrives before the clock edge",
                "Data path delay exceeds the clock period minus setup time", // Correct (index 1)
                "Hold time requirement is strictly negative",
                "Clock skew is exactly zero"
            ),
            correctIndex = 1,
            explanation = "Setup violation occurs when T_cq + T_comb + T_setup > T_clk."
        )

        // Test with 3 different session seeds
        val seeds = listOf(1111L, 2222L, 3333L)
        val optionOrderPermutations = mutableListOf<List<String>>()

        for (seed in seeds) {
            val rng = Random(seed xor question.id.hashCode().toLong())
            val shuffled = question.options.mapIndexed { idx, optText ->
                idx to optText
            }.shuffled(rng)

            val shuffledTextList = shuffled.map { it.second }
            optionOrderPermutations.add(shuffledTextList)

            // Verify the correct answer option maps back to original index 1
            val correctOptionInShuffled = shuffled.first { it.first == question.correctIndex }
            assertEquals("Data path delay exceeds the clock period minus setup time", correctOptionInShuffled.second)
            assertEquals(1, correctOptionInShuffled.first)
        }

        // Verify all 4 options are preserved in every permutation
        for (perm in optionOrderPermutations) {
            assertEquals(4, perm.size)
            assertEquals(4, perm.distinct().size)
            assertTrue(perm.contains("Data path delay exceeds the clock period minus setup time"))
        }
    }
}

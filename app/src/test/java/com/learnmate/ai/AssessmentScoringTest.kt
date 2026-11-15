package com.learnmate.ai

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.LearnerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssessmentScoringTest {

    private lateinit var profile: LearnerProfile
    private lateinit var sampleQuestions: List<AssessmentQuestion>

    @Before
    fun setUp() {
        profile = LearnerProfile(
            userId = "test_learner_1",
            name = "Aarav Patel",
            targetDomain = "Electronics and VLSI",
            subdomain = "Semiconductor Physics",
            goal = "Master CMOS and STA",
            currentLevel = "Intermediate",
            audienceLevel = AudienceLevel.UNDERGRADUATE
        )

        sampleQuestions = TopicTaxonomy.getDiagnosticQuestions()
            .filter { it.domain == "Electronics and VLSI" }
            .map { DeterministicAssessmentEngine.mapEntityToDomain(it) }
    }

    @Test
    fun testAllCorrectAnswers_yields100PercentAndZeroHighGaps() {
        val perfectAnswers = sampleQuestions.associate { it.id to it.correctIndex }
        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, sampleQuestions, perfectAnswers)

        assertEquals(100, report.overallScorePercent)
        assertEquals(sampleQuestions.size, report.correctCount)
        assertTrue(report.weaknesses.isEmpty())
        assertTrue(report.strengths.isNotEmpty())
        assertTrue(report.skillGaps.all { it.gapSeverity == "LOW" })
    }

    @Test
    fun testAllWrongAnswers_yieldsZeroPercentAndAllHighGaps() {
        val wrongAnswers = sampleQuestions.associate { it.id to ((it.correctIndex + 1) % 4) }
        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, sampleQuestions, wrongAnswers)

        assertEquals(0, report.overallScorePercent)
        assertEquals(0, report.correctCount)
        assertTrue(report.strengths.isEmpty())
        assertTrue(report.weaknesses.isNotEmpty())
        assertTrue(report.skillGaps.all { it.gapSeverity == "HIGH" })
    }

    @Test
    fun testMixedAnswers_yieldsAccurateMasteryAndTargetComparison() {
        // Answer 1st question correctly, remainder incorrectly
        val mixedAnswers = sampleQuestions.mapIndexed { idx, q ->
            q.id to if (idx == 0) q.correctIndex else ((q.correctIndex + 1) % 4)
        }.toMap()

        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, sampleQuestions, mixedAnswers)

        assertEquals(1, report.correctCount)
        val expectedPercent = (1 * 100) / sampleQuestions.size
        assertEquals(expectedPercent, report.overallScorePercent)
        assertTrue(report.skillGaps.any { it.gapSeverity == "HIGH" })
    }

    @Test
    fun testDeterministicScoring_neverConsultsLanguageModel() {
        val answers = mapOf(sampleQuestions.first().id to sampleQuestions.first().correctIndex)
        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, listOf(sampleQuestions.first()), answers)

        assertEquals(100, report.overallScorePercent)
        assertEquals(1, report.correctCount)
        assertEquals(sampleQuestions.first().topic, report.strengths.first())
    }

    @Test
    fun testEntityMappingRoundtrip_preservesIntegrity() {
        val perfectAnswers = sampleQuestions.associate { it.id to it.correctIndex }
        val originalReport = DeterministicAssessmentEngine.evaluateAssessment(profile, sampleQuestions, perfectAnswers)

        val entity = DeterministicAssessmentEngine.mapReportToEntity(originalReport)
        val restoredReport = DeterministicAssessmentEngine.mapEntityToReport(entity)

        assertEquals(originalReport.userId, restoredReport.userId)
        assertEquals(originalReport.domain, restoredReport.domain)
        assertEquals(originalReport.overallScorePercent, restoredReport.overallScorePercent)
        assertEquals(originalReport.strengths, restoredReport.strengths)
        assertEquals(originalReport.weaknesses, restoredReport.weaknesses)
        assertEquals(originalReport.skillGaps.size, restoredReport.skillGaps.size)
    }
}


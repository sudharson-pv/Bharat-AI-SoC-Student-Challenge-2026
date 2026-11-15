package com.learnmate.ai.roadmap

import com.learnmate.ai.data.roadmap.AiTrackGenerationEngine
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.RoadmapStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTrackGenerationEngineTest {

    private val testProfile = LearnerProfile(
        userId = "test_learner_42",
        name = "Kavya",
        targetDomain = "Computer Science & AI",
        subdomain = "Data Structures",
        goal = "Crack technical engineering interviews",
        targetRole = "Software Engineer",
        educationLevel = EducationLevel.UNDERGRADUATE,
        learnerCategory = LearnerCategory.ENGINEERING_STUDENT,
        audienceLevel = AudienceLevel.UNDERGRADUATE,
        dailyStudyMinutes = 60,
        weeklyStudyDays = 5,
        onboardingCompleted = true,
        assessmentCompleted = true
    )

    @Test
    fun testGenerateTrackProposalStandardValidation() {
        val proposal = AiTrackGenerationEngine.generateTrackProposal(
            profile = testProfile,
            institution = "IIT Madras",
            semester = "Semester 4"
        )

        assertEquals("Computer Science & AI Accelerated Mastery", proposal.title)
        assertEquals(3, proposal.phases.size)
        assertEquals(3, proposal.milestones.size)
        assertTrue(proposal.totalEstimatedHours > 0)
        assertTrue(proposal.nodeIds.isNotEmpty())
        assertTrue("Proposal must pass deterministic Kotlin validation", proposal.validationReport.isValid)
        assertTrue(proposal.validationReport.duplicateNodes.isEmpty())
        assertTrue(proposal.validationReport.cyclicDependencies.isEmpty())
        assertTrue(proposal.validationReport.scheduleConsistent)
    }

    @Test
    fun testValidatorCatchesDuplicateNodes() {
        val duplicateSteps = listOf(
            RoadmapStep(1, "step_1", "Intro", "Intro", "Desc", emptyList(), 30, false),
            RoadmapStep(2, "step_1", "Intro Dup", "Intro", "Desc", emptyList(), 30, false)
        )

        val report = AiTrackGenerationEngine.validateTrackProposal(
            steps = duplicateSteps,
            profile = testProfile,
            totalWeeks = 10
        )

        assertFalse("Duplicate nodes must fail validation", report.isValid)
        assertTrue(report.duplicateNodes.contains("step_1"))
    }

    @Test
    fun testValidatorCatchesCyclicDependencies() {
        val cyclicSteps = listOf(
            RoadmapStep(1, "step_A", "Title A", "Topic A", "Desc", listOf("step_B"), 30, false),
            RoadmapStep(2, "step_B", "Title B", "Topic B", "Desc", listOf("step_A"), 30, false)
        )

        val report = AiTrackGenerationEngine.validateTrackProposal(
            steps = cyclicSteps,
            profile = testProfile,
            totalWeeks = 10
        )

        assertFalse("Cyclic dependencies must fail validation", report.isValid)
        assertTrue(report.cyclicDependencies.isNotEmpty())
    }
}


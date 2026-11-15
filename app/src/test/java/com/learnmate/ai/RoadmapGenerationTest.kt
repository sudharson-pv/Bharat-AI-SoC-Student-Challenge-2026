package com.learnmate.ai

import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.SkillGapItem
import com.learnmate.ai.domain.SkillGapReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapGenerationTest {

    @Test
    fun testRoadmapPrioritizesAssessmentWeaknesses() {
        val profile = LearnerProfile(
            userId = "user_2",
            name = "Kavya Murugan",
            targetDomain = "Electronics and VLSI",
            subdomain = "Physical Design",
            goal = "Pass VLSI Physical Design Interview",
            currentLevel = "Intermediate",
            audienceLevel = AudienceLevel.UNDERGRADUATE,
            dailyStudyMinutes = 45
        )

        val report = SkillGapReport(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            overallScorePercent = 50,
            totalQuestions = 4,
            correctCount = 2,
            strengths = listOf("PN Junction Physics & Diodes"),
            weaknesses = listOf("Static Timing Analysis (Setup & Hold)"),
            skillGaps = listOf(
                SkillGapItem(
                    topic = "Static Timing Analysis (Setup & Hold)",
                    masteryPercent = 0,
                    targetPercent = 80,
                    gapSeverity = "HIGH",
                    rationale = "0/1 correct on STA",
                    recommendedAction = "Prioritize STA remediation"
                )
            )
        )

        val roadmap = DeterministicRoadmapEngine.generateRoadmap(profile, report)

        assertNotNull(roadmap)
        assertEquals(profile.userId, roadmap.userId)
        assertTrue(roadmap.steps.isNotEmpty())

        // First step must address the prioritized weakness
        val firstStep = roadmap.steps.first()
        assertTrue(firstStep.addressesSkillGap)
        assertEquals("Static Timing Analysis (Setup & Hold)", firstStep.topic)
        assertTrue(firstStep.title.contains("Priority Remediation"))
        assertEquals("IN_PROGRESS", firstStep.status)
    }

    @Test
    fun testRoadmapPrerequisitesAreSequential() {
        val profile = LearnerProfile(
            userId = "user_3",
            name = "Priya Rao",
            targetDomain = "Computer Science and IT",
            subdomain = "Data Structures & Algorithms",
            goal = "Master Algorithms",
            dailyStudyMinutes = 60
        )

        val roadmap = DeterministicRoadmapEngine.generateRoadmap(profile, null)

        assertTrue(roadmap.steps.size >= 3)
        // Step 1 has no prerequisites
        assertTrue(roadmap.steps[0].prerequisites.isEmpty())
        // Step 2 has prerequisite Step 1
        assertTrue(roadmap.steps[1].prerequisites.isNotEmpty())
        assertTrue(roadmap.steps[1].prerequisites.first().contains("Step 1"))
        // Step 3 has prerequisite Step 2
        assertTrue(roadmap.steps[2].prerequisites.isNotEmpty())
        assertTrue(roadmap.steps[2].prerequisites.first().contains("Step 2"))
    }

    @Test
    fun testRoadmapEntityRoundtrip() {
        val profile = LearnerProfile(
            userId = "user_roundtrip",
            name = "Test User",
            targetDomain = "Mechanical Engineering",
            goal = "CAD Mastery",
            dailyStudyMinutes = 30
        )

        val originalRoadmap = DeterministicRoadmapEngine.generateRoadmap(profile, null)
        val entity = DeterministicRoadmapEngine.mapToEntity(originalRoadmap)
        val restoredRoadmap = DeterministicRoadmapEngine.mapFromEntity(entity)

        assertEquals(originalRoadmap.userId, restoredRoadmap.userId)
        assertEquals(originalRoadmap.domain, restoredRoadmap.domain)
        assertEquals(originalRoadmap.steps.size, restoredRoadmap.steps.size)
        assertEquals(originalRoadmap.steps.first().title, restoredRoadmap.steps.first().title)
    }
}


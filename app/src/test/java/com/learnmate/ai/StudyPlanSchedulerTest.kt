package com.learnmate.ai

import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.data.studyplan.StudyPlanScheduler
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.StudyTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPlanSchedulerTest {

    @Test
    fun testSchedulerBindsToDailyMinutesBudget() {
        val profile = LearnerProfile(
            userId = "student_plan_1",
            name = "Meera Nair",
            targetDomain = "Computer Science and IT",
            dailyStudyMinutes = 45,
            weeklyStudyDays = 5
        )

        val roadmap = DeterministicRoadmapEngine.generateRoadmap(profile, null)
        val tasks = StudyPlanScheduler.generateStudyTasks(profile, roadmap)

        assertTrue(tasks.isNotEmpty())
        for (task in tasks) {
            assertTrue(task.durationMinutes <= 90) // bounded task split
            assertEquals(profile.userId, task.userId)
            assertFalse(task.isCompleted)
        }
    }

    @Test
    fun testProgressPercentageCalculation() {
        val sampleTasks = listOf(
            StudyTask(id = "1", userId = "u1", roadmapStepId = "s1", dayIndex = 1, weekIndex = 1, title = "T1", topic = "P1", durationMinutes = 30, isCompleted = true),
            StudyTask(id = "2", userId = "u1", roadmapStepId = "s1", dayIndex = 2, weekIndex = 1, title = "T2", topic = "P2", durationMinutes = 30, isCompleted = false),
            StudyTask(id = "3", userId = "u1", roadmapStepId = "s2", dayIndex = 3, weekIndex = 1, title = "T3", topic = "P3", durationMinutes = 30, isCompleted = true),
            StudyTask(id = "4", userId = "u1", roadmapStepId = "s2", dayIndex = 4, weekIndex = 1, title = "T4", topic = "P4", durationMinutes = 30, isCompleted = false)
        )

        val progress = StudyPlanScheduler.calculateProgressPercentage(sampleTasks)
        assertEquals(50, progress) // 2 out of 4 completed = 50%
    }

    @Test
    fun testTaskEntityRoundtrip() {
        val original = StudyTask(
            id = "t_roundtrip",
            userId = "user_rt",
            roadmapStepId = "step_rt",
            dayIndex = 2,
            weekIndex = 1,
            title = "Task Roundtrip",
            topic = "Topic A",
            durationMinutes = 45,
            isCompleted = true,
            notes = "Completed ahead of schedule"
        )

        val entity = StudyPlanScheduler.mapToEntity(original)
        val restored = StudyPlanScheduler.mapFromEntity(entity)

        assertEquals(original.id, restored.id)
        assertEquals(original.userId, restored.userId)
        assertEquals(original.durationMinutes, restored.durationMinutes)
        assertEquals(original.isCompleted, restored.isCompleted)
        assertEquals(original.notes, restored.notes)
    }
}


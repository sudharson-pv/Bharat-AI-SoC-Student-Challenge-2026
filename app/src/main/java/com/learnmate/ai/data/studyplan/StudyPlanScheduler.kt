package com.learnmate.ai.data.studyplan

import com.learnmate.ai.data.db.StudyTaskEntity
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.PersonalisedRoadmap
import com.learnmate.ai.domain.StudyTask
import java.util.UUID

/**
 * Study Plan Scheduler.
 * Distributes personalized roadmap milestones into discrete daily tasks bounded by
 * the learner's daily study minutes capacity.
 */
object StudyPlanScheduler {

    fun generateStudyTasks(
        profile: LearnerProfile,
        roadmap: PersonalisedRoadmap
    ): List<StudyTask> {
        val tasks = mutableListOf<StudyTask>()
        val dailyBudget = profile.dailyStudyMinutes.coerceIn(15, 240)
        var currentDay = 1
        var currentWeek = 1

        for (step in roadmap.steps) {
            val stepMinutes = step.estimatedMinutes
            val tasksForStep = if (stepMinutes <= dailyBudget) {
                listOf(
                    StudyTask(
                        id = "task_${profile.userId}_step${step.stepNumber}_1",
                        userId = profile.userId,
                        roadmapStepId = step.id,
                        dayIndex = currentDay,
                        weekIndex = currentWeek,
                        title = "${step.title}: Core Conceptual Study",
                        topic = step.topic,
                        durationMinutes = stepMinutes,
                        isCompleted = false
                    )
                )
            } else {
                val half = stepMinutes / 2
                listOf(
                    StudyTask(
                        id = "task_${profile.userId}_step${step.stepNumber}_1",
                        userId = profile.userId,
                        roadmapStepId = step.id,
                        dayIndex = currentDay,
                        weekIndex = currentWeek,
                        title = "${step.title}: Theory & Verified Curriculum",
                        topic = step.topic,
                        durationMinutes = half,
                        isCompleted = false
                    ),
                    StudyTask(
                        id = "task_${profile.userId}_step${step.stepNumber}_2",
                        userId = profile.userId,
                        roadmapStepId = step.id,
                        dayIndex = currentDay + 1,
                        weekIndex = currentWeek,
                        title = "${step.title}: Practice & Synthesis",
                        topic = step.topic,
                        durationMinutes = stepMinutes - half,
                        isCompleted = false
                    )
                )
            }

            tasks.addAll(tasksForStep)
            currentDay += tasksForStep.size
            if (currentDay > profile.weeklyStudyDays) {
                currentWeek++
                currentDay = 1
            }
        }

        return tasks
    }

    fun mapToEntity(task: StudyTask, trackId: String = "track_${task.userId}_default"): StudyTaskEntity {
        return StudyTaskEntity(
            taskId = task.id,
            trackId = trackId,
            userId = task.userId,
            roadmapStepId = task.roadmapStepId,
            dayIndex = task.dayIndex,
            weekIndex = task.weekIndex,
            title = task.title,
            topic = task.topic,
            durationMinutes = task.durationMinutes,
            isCompleted = task.isCompleted,
            completedAtEpochMs = task.completedAtEpochMs,
            notes = task.notes
        )
    }

    fun mapFromEntity(entity: StudyTaskEntity): StudyTask {
        return StudyTask(
            id = entity.taskId,
            userId = entity.userId,
            roadmapStepId = entity.roadmapStepId,
            dayIndex = entity.dayIndex,
            weekIndex = entity.weekIndex,
            title = entity.title,
            topic = entity.topic,
            durationMinutes = entity.durationMinutes,
            isCompleted = entity.isCompleted,
            completedAtEpochMs = entity.completedAtEpochMs,
            notes = entity.notes
        )
    }

    fun calculateProgressPercentage(tasks: List<StudyTask>): Int {
        if (tasks.isEmpty()) return 0
        val completed = tasks.count { it.isCompleted }
        return (completed * 100) / tasks.size
    }
}


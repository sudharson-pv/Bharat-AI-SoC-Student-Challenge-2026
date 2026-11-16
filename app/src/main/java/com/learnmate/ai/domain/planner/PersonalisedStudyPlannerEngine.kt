package com.learnmate.ai.domain.planner

import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem

/**
 * Gate 6.6: Personalised Daily & Weekly Study Planner Engine.
 *
 * Implements an explainable study plan generator that respects:
 * 1. Learner-selected daily study commitment (15, 30, 45, 60, 90 mins).
 * 2. Active learning goal or target examination (e.g. CBSE 10, GATE, UPSC).
 * 3. Topic prerequisite DAG traversal (no advanced topics before foundations).
 * 4. Knowledge mastery state (NEEDS_IMPROVEMENT vs PROFICIENT/MASTERED).
 * 5. Retention-first Spaced Repetition scheduling (overdue reviews prioritized).
 * 6. 100% verified curriculum modules.
 *
 * Safeguard: AI models are NEVER used to arbitrarily fabricate schedules.
 */

enum class PlannedTaskType(val displayName: String, val badgeColorHex: Long) {
    SPACED_REPETITION_REVIEW("Spaced Repetition", 0xFF673AB7),
    CRITICAL_PREREQUISITE("Foundational Prerequisite", 0xFFD32F2F),
    SKILL_GAP_RECOVERY("Skill Gap Recovery", 0xFFF57C00),
    CURRICULUM_STEP("Curriculum Progression", 0xFF1976D2)
}

data class PlannedStudyTask(
    val taskId: String,
    val nodeId: String,
    val title: String,
    val discipline: String,
    val taskType: PlannedTaskType,
    val durationMinutes: Int,
    val priority: Int, // 1 = highest
    val explainableReason: String,
    val isCompleted: Boolean = false
)

data class DailyStudyPlan(
    val planDateEpochMs: Long,
    val targetDailyMinutes: Int,
    val allocatedMinutes: Int,
    val tasks: List<PlannedStudyTask>,
    val overdueRevisionCount: Int,
    val planSummary: String,
    val isOvercommitted: Boolean = allocatedMinutes > targetDailyMinutes,
    val overcommittedMinutes: Int = maxOf(0, allocatedMinutes - targetDailyMinutes)
)

data class WeeklyStudyPlan(
    val weekNumber: Int,
    val targetWeeklyMinutes: Int,
    val dailyPlans: List<DailyStudyPlan>,
    val weeklyFocusSummary: String
)

object PersonalisedStudyPlannerEngine {

    const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Generates an explainable daily study plan tailored to the learner's time commitment and goals.
     */
    fun generateDailyPlan(
        track: LearningTrack,
        targetDailyMinutes: Int = track.dailyCommitmentMinutes.coerceIn(15, 180),
        masteries: List<TopicMasteryResult>,
        srsItems: List<SpacedRepetitionItem>,
        nodes: List<CurriculumNodeEntity>,
        prerequisites: List<NodePrerequisiteEntity>,
        currentEpochMs: Long = System.currentTimeMillis()
    ): DailyStudyPlan {
        val nodeMap = nodes.associateBy { it.nodeId }
        val masteryMap = masteries.associateBy { it.nodeId }
        val completedNodeIds = masteries
            .filter { it.progressStatus == LearningProgressStatus.COMPLETED || it.masteryLevel == MasteryLevel.MASTERED }
            .map { it.nodeId }
            .toSet()

        val tasks = mutableListOf<PlannedStudyTask>()
        val scheduledNodeIds = mutableSetOf<String>()
        var allocatedMinutes = 0

        // ── 1. Priority 1: Overdue Spaced Repetition (Retention First) ───────────
        val overdueSrs = srsItems
            .filter { it.dueEpochMs < currentEpochMs }
            .sortedBy { it.dueEpochMs }

        for (srs in overdueSrs) {
            if (allocatedMinutes >= targetDailyMinutes) break
            val node = nodeMap[srs.nodeId]
            val duration = 15 // Focused spaced review
            scheduledNodeIds.add(srs.nodeId)
            allocatedMinutes += duration
            tasks.add(
                PlannedStudyTask(
                    taskId = "plan_srs_${srs.nodeId}_${currentEpochMs}",
                    nodeId = srs.nodeId,
                    title = srs.topicTitle,
                    discipline = srs.discipline,
                    taskType = PlannedTaskType.SPACED_REPETITION_REVIEW,
                    durationMinutes = duration,
                    priority = 1,
                    explainableReason = "Scheduled Spaced Repetition: Topic '${srs.topicTitle}' is ${srs.overdueDays} days overdue for review to counteract memory decay (Interval: ${srs.intervalDays}d, EF: ${srs.easeFactor})."
                )
            )
        }

        // ── 2. Priority 2: Critical Prerequisite Closures for Target Goal ─────────
        val targetGoalNodeId = track.targetCurriculumNodeId
        if (targetGoalNodeId != null && allocatedMinutes < targetDailyMinutes) {
            val transitivePrereqs = CurriculumEngine.getTransitivePrerequisites(targetGoalNodeId, prerequisites)
            for (prereqId in transitivePrereqs) {
                if (prereqId !in completedNodeIds && prereqId !in scheduledNodeIds) {
                    if (allocatedMinutes >= targetDailyMinutes) break
                    val pNode = nodeMap[prereqId]
                    if (pNode != null) {
                        val duration = minOf(30, pNode.estimatedMinutes)
                        scheduledNodeIds.add(prereqId)
                        allocatedMinutes += duration
                        tasks.add(
                            PlannedStudyTask(
                                taskId = "plan_prereq_${prereqId}_${currentEpochMs}",
                                nodeId = prereqId,
                                title = pNode.title,
                                discipline = pNode.code,
                                taskType = PlannedTaskType.CRITICAL_PREREQUISITE,
                                durationMinutes = duration,
                                priority = 2,
                                explainableReason = "Critical Prerequisite: Mandatory foundational concept for examination target '${track.targetExamName ?: track.title}'."
                            )
                        )
                    }
                }
            }
        }

        // ── 3. Priority 3: Skill Gap Recovery (NEEDS_IMPROVEMENT) ─────────────────
        if (allocatedMinutes < targetDailyMinutes) {
            val weakMasteries = masteries
                .filter { it.masteryLevel == MasteryLevel.NEEDS_IMPROVEMENT && it.nodeId !in scheduledNodeIds }
                .sortedBy { it.compositeScore }

            for (weak in weakMasteries) {
                if (allocatedMinutes >= targetDailyMinutes) break
                val node = nodeMap[weak.nodeId]
                val duration = 25
                scheduledNodeIds.add(weak.nodeId)
                allocatedMinutes += duration
                tasks.add(
                    PlannedStudyTask(
                        taskId = "plan_gap_${weak.nodeId}_${currentEpochMs}",
                        nodeId = weak.nodeId,
                        title = weak.topicTitle,
                        discipline = weak.discipline,
                        taskType = PlannedTaskType.SKILL_GAP_RECOVERY,
                        durationMinutes = duration,
                        priority = 3,
                        explainableReason = "Skill Gap Recovery: Diagnostic assessment identified weakness (${weak.compositeScore}% score). Guided textbook study and concept clarification required."
                    )
                )
            }
        }

        // ── 4. Priority 4: Next Unlocked Curriculum Progression ───────────────────
        if (allocatedMinutes < targetDailyMinutes) {
            val remainingNodes = nodes
                .filter { it.nodeId !in scheduledNodeIds && it.nodeId !in completedNodeIds && (it.nodeType == "TOPIC" || it.nodeType == "UNIT") }
                .sortedBy { it.orderIndex }

            for (node in remainingNodes) {
                val prereqValidation = CurriculumEngine.validatePrerequisites(node.nodeId, completedNodeIds, prerequisites)
                if (prereqValidation.isUnlocked) {
                    if (allocatedMinutes >= targetDailyMinutes) break
                    val duration = minOf(35, node.estimatedMinutes)
                    scheduledNodeIds.add(node.nodeId)
                    allocatedMinutes += duration
                    tasks.add(
                        PlannedStudyTask(
                            taskId = "plan_step_${node.nodeId}_${currentEpochMs}",
                            nodeId = node.nodeId,
                            title = node.title,
                            discipline = node.code,
                            taskType = PlannedTaskType.CURRICULUM_STEP,
                            durationMinutes = duration,
                            priority = 4,
                            explainableReason = "Curriculum Progression: All prerequisites satisfied. Next accredited module in curriculum roadmap."
                        )
                    )
                }
            }
        }

        val summary = if (tasks.isEmpty()) {
            "All curriculum milestones achieved and no revisions overdue. Outstanding mastery demonstrated across track."
        } else {
            "Personalised plan: ${tasks.size} tasks spanning $allocatedMinutes mins (Commitment: $targetDailyMinutes mins). Overdue revisions: ${overdueSrs.size}."
        }

        return DailyStudyPlan(
            planDateEpochMs = currentEpochMs,
            targetDailyMinutes = targetDailyMinutes,
            allocatedMinutes = allocatedMinutes,
            tasks = tasks,
            overdueRevisionCount = overdueSrs.size,
            planSummary = summary
        )
    }

    /**
     * Generates an explainable 7-day weekly study schedule balancing concept study, practice, and reviews.
     */
    fun generateWeeklyPlan(
        track: LearningTrack,
        targetDailyMinutes: Int = track.dailyCommitmentMinutes.coerceIn(15, 180),
        masteries: List<TopicMasteryResult>,
        srsItems: List<SpacedRepetitionItem>,
        nodes: List<CurriculumNodeEntity>,
        prerequisites: List<NodePrerequisiteEntity>,
        startEpochMs: Long = System.currentTimeMillis()
    ): WeeklyStudyPlan {
        val dailyPlans = mutableListOf<DailyStudyPlan>()
        for (dayOffset in 0 until 7) {
            val dayEpoch = startEpochMs + (dayOffset * MILLIS_PER_DAY)
            dailyPlans.add(
                generateDailyPlan(
                    track = track,
                    targetDailyMinutes = targetDailyMinutes,
                    masteries = masteries,
                    srsItems = srsItems,
                    nodes = nodes,
                    prerequisites = prerequisites,
                    currentEpochMs = dayEpoch
                )
            )
        }

        val totalWeeklyMinutes = dailyPlans.sumOf { it.allocatedMinutes }
        val focusSummary = "Weekly curriculum target: $totalWeeklyMinutes mins across 7 days. Focus: Prerequisite grounding & spaced retention for ${track.targetExamName ?: track.title}."

        return WeeklyStudyPlan(
            weekNumber = 1,
            targetWeeklyMinutes = targetDailyMinutes * 7,
            dailyPlans = dailyPlans,
            weeklyFocusSummary = focusSummary
        )
    }
}


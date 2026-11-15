package com.learnmate.ai.domain.recommendation

import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.domain.mastery.KnowledgeMasteryEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult

/**
 * Gate 6.5: Deterministic Personalised Learning Path & Revision Planner Engine.
 *
 * Combines:
 * 1. Prerequisite DAG Traversal (CurriculumEngine)
 * 2. Topic Mastery Classifications (KnowledgeMasteryEngine)
 * 3. Diagnostic Assessment Gaps
 * 4. Learner Goal & Active Track
 *
 * Generates an explainable sequence of recommended study and revision steps.
 * AI models are NEVER used to guess study order or hallucinate recommendations.
 */

enum class RecommendationPriority(val numericRank: Int, val label: String) {
    CRITICAL_PREREQUISITE(1, "Critical Prerequisite"),
    SKILL_GAP(2, "Skill Gap Closure"),
    REVISION(3, "Scheduled Revision"),
    NEXT_CURRICULUM_STEP(4, "Next Curriculum Step")
}

data class RecommendedTopicItem(
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val priority: RecommendationPriority,
    val reason: String,
    val estimatedMinutes: Int,
    val isUnlocked: Boolean,
    val masteryLevel: MasteryLevel,
    val compositeScore: Double
)

data class RevisionTopicItem(
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val priorityReason: String,
    val lastScore: Double,
    val masteryLevel: MasteryLevel,
    val isCompleted: Boolean = false
)

object PersonalisedLearningPathEngine {

    /**
     * Synthesizes an ordered, explainable personalised learning path for the learner's active track.
     */
    fun generateLearningPath(
        track: LearningTrack,
        nodes: List<CurriculumNodeEntity>,
        prerequisites: List<NodePrerequisiteEntity>,
        topicMasteries: List<TopicMasteryResult>
    ): List<RecommendedTopicItem> {
        if (nodes.isEmpty()) return emptyList()

        val nodeMap = nodes.associateBy { it.nodeId }
        val masteryMap = topicMasteries.associateBy { it.nodeId }

        val completedNodeIds = topicMasteries
            .filter { it.progressStatus == LearningProgressStatus.COMPLETED || it.masteryLevel == MasteryLevel.MASTERED }
            .map { it.nodeId }
            .toSet()

        val recommendations = mutableListOf<RecommendedTopicItem>()
        val seenNodeIds = mutableSetOf<String>()

        // ── 1. Target Goal and its Prerequisite Foundation ──────────────────────
        val targetGoalNodeId = track.targetCurriculumNodeId
        val targetNode = targetGoalNodeId?.let { nodeMap[it] }

        if (targetNode != null) {
            val transitivePrereqIds = CurriculumEngine.getTransitivePrerequisites(targetNode.nodeId, prerequisites)
            for (prereqId in transitivePrereqIds) {
                if (prereqId !in completedNodeIds && prereqId !in seenNodeIds) {
                    val pNode = nodeMap[prereqId]
                    if (pNode != null) {
                        seenNodeIds.add(prereqId)
                        val mResult = masteryMap[prereqId]
                        val isUnlocked = CurriculumEngine.validatePrerequisites(prereqId, completedNodeIds, prerequisites).isUnlocked
                        recommendations.add(
                            RecommendedTopicItem(
                                nodeId = prereqId,
                                topicTitle = pNode.title,
                                discipline = pNode.code,
                                priority = RecommendationPriority.CRITICAL_PREREQUISITE,
                                reason = "Mandatory foundational prerequisite required before advancing to goal topic '${targetNode.title}'.",
                                estimatedMinutes = pNode.estimatedMinutes,
                                isUnlocked = isUnlocked,
                                masteryLevel = mResult?.masteryLevel ?: MasteryLevel.NOT_ASSESSED,
                                compositeScore = mResult?.compositeScore ?: 0.0
                            )
                        )
                    }
                }
            }
        }

        // ── 2. Identified Skill Gaps (NEEDS_IMPROVEMENT) ─────────────────────────
        val weakTopics = topicMasteries
            .filter { it.masteryLevel == MasteryLevel.NEEDS_IMPROVEMENT && it.nodeId !in seenNodeIds }
            .sortedBy { it.compositeScore }

        for (weak in weakTopics) {
            val node = nodeMap[weak.nodeId]
            if (node != null) {
                seenNodeIds.add(weak.nodeId)
                val isUnlocked = CurriculumEngine.validatePrerequisites(weak.nodeId, completedNodeIds, prerequisites).isUnlocked
                recommendations.add(
                    RecommendedTopicItem(
                        nodeId = weak.nodeId,
                        topicTitle = weak.topicTitle,
                        discipline = weak.discipline,
                        priority = RecommendationPriority.SKILL_GAP,
                        reason = "Diagnostic assessment identified critical skill gap (${weak.compositeScore}% score). Review verified material to reinforce fundamental concepts.",
                        estimatedMinutes = node.estimatedMinutes,
                        isUnlocked = isUnlocked,
                        masteryLevel = weak.masteryLevel,
                        compositeScore = weak.compositeScore
                    )
                )
            }
        }

        // ── 3. Scheduled Revision Queue ─────────────────────────────────────────
        val revisionTopics = topicMasteries
            .filter { it.needsRevision && it.nodeId !in seenNodeIds }
            .sortedBy { it.compositeScore }

        for (rev in revisionTopics) {
            val node = nodeMap[rev.nodeId]
            if (node != null) {
                seenNodeIds.add(rev.nodeId)
                val isUnlocked = CurriculumEngine.validatePrerequisites(rev.nodeId, completedNodeIds, prerequisites).isUnlocked
                recommendations.add(
                    RecommendedTopicItem(
                        nodeId = rev.nodeId,
                        topicTitle = rev.topicTitle,
                        discipline = rev.discipline,
                        priority = RecommendationPriority.REVISION,
                        reason = "Retention reinforcement required: Score (${rev.compositeScore}%) below mastery retention threshold.",
                        estimatedMinutes = (node.estimatedMinutes * 0.6).toInt().coerceAtLeast(15),
                        isUnlocked = isUnlocked,
                        masteryLevel = rev.masteryLevel,
                        compositeScore = rev.compositeScore
                    )
                )
            }
        }

        // ── 4. Next Sequential Unlocked Curriculum Steps ────────────────────────
        val remainingNodes = nodes
            .filter { it.nodeId !in seenNodeIds && it.nodeId !in completedNodeIds && (it.nodeType == "TOPIC" || it.nodeType == "UNIT") }
            .sortedBy { it.orderIndex }

        for (node in remainingNodes) {
            val prereqValidation = CurriculumEngine.validatePrerequisites(node.nodeId, completedNodeIds, prerequisites)
            val mResult = masteryMap[node.nodeId]
            seenNodeIds.add(node.nodeId)
            recommendations.add(
                RecommendedTopicItem(
                    nodeId = node.nodeId,
                    topicTitle = node.title,
                    discipline = node.code,
                    priority = RecommendationPriority.NEXT_CURRICULUM_STEP,
                    reason = if (prereqValidation.isUnlocked) {
                        "Prerequisites satisfied. Next sequential topic in accredited curriculum sequence."
                    } else {
                        "Prerequisites pending (${prereqValidation.completedRequiredCount}/${prereqValidation.totalRequiredCount} completed)."
                    },
                    estimatedMinutes = node.estimatedMinutes,
                    isUnlocked = prereqValidation.isUnlocked,
                    masteryLevel = mResult?.masteryLevel ?: MasteryLevel.NOT_ASSESSED,
                    compositeScore = mResult?.compositeScore ?: 0.0
                )
            )
        }

        // Return sorted by priority rank, then unlocked status, then composite score
        return recommendations.sortedWith(
            compareBy<RecommendedTopicItem> { it.priority.numericRank }
                .thenByDescending { it.isUnlocked }
                .thenBy { it.compositeScore }
        )
    }

    /**
     * Builds the active revision queue containing topics requiring review and re-assessment.
     */
    fun generateRevisionQueue(topicMasteries: List<TopicMasteryResult>): List<RevisionTopicItem> {
        return topicMasteries
            .filter { it.needsRevision }
            .map { tm ->
                val reason = when {
                    tm.attemptsCount > 0 && tm.compositeScore < KnowledgeMasteryEngine.THRESHOLD_PROFICIENT ->
                        "Scored ${tm.compositeScore}% (< 70% proficiency threshold)."
                    tm.progressStatus == LearningProgressStatus.NEEDS_REVISION ->
                        "Flagged manually or by tutor for review."
                    tm.progressStatus == LearningProgressStatus.COMPLETED && tm.assessmentScore < 75.0 ->
                        "Assessment score ${tm.assessmentScore}% below 75% retention threshold."
                    else ->
                        "Periodic reinforcement recommended."
                }
                RevisionTopicItem(
                    nodeId = tm.nodeId,
                    topicTitle = tm.topicTitle,
                    discipline = tm.discipline,
                    priorityReason = reason,
                    lastScore = tm.compositeScore,
                    masteryLevel = tm.masteryLevel,
                    isCompleted = false
                )
            }
            .sortedBy { it.lastScore }
    }
}

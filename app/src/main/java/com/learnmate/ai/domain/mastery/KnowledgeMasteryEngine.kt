package com.learnmate.ai.domain.mastery

import com.learnmate.ai.domain.LearningProgressStatus
import kotlin.math.round

/**
 * Gate 6.6: Deterministic Knowledge Mastery Engine.
 *
 * Implements a transparent, explainable, and 100% deterministic knowledge mastery calculation.
 * AI models are NEVER used to fabricate percentages or hallucinate learner proficiency.
 *
 * Gate 6.6 Enhancements:
 * 1. Explicitly distinguishes NOT_ASSESSED from an actual zero assessment score. A learner who has
 *    not attempted a diagnostic assessment (attemptsCount == 0) is NEVER classified as having poor
 *    knowledge (NEEDS_IMPROVEMENT), regardless of whether study is NOT_STARTED, IN_PROGRESS, or COMPLETED.
 * 2. An actual zero score occurs strictly when attemptsCount > 0 and correctCount == 0.
 * 3. Logical study score hierarchy:
 *    NOT_STARTED (0.0%) < IN_PROGRESS (40.0%) < NEEDS_REVISION (60.0%) < COMPLETED (100.0%).
 * 4. Question-Bank Coverage Guard: When a topic has fewer than 3 unique questions in its bank,
 *    mastery is capped at PROFICIENT (cannot achieve MASTERED due to insufficient sample size).
 * 5. Stale Score Degradation: Assessment scores older than 60 days receive a 20% retention decay.
 *
 * Evaluation Dimensions:
 * 1. Diagnostic Assessment Score (Weight: 45%) - Ratio of verified correct answers to total questions.
 * 2. Study Module Completion (Weight: 35%) - Verified study status.
 * 3. Prerequisite Completion (Weight: 20%) - Ratio of completed mandatory foundational prerequisites.
 *
 * Mastery Classifications:
 * - NOT_ASSESSED: No diagnostic assessment attempts (attemptsCount == 0).
 * - NEEDS_IMPROVEMENT: Assessed (attemptsCount > 0) with composite score < 50.0%.
 * - DEVELOPING: 50.0% <= Composite score < 70.0%, or uncompleted mandatory prerequisites.
 * - PROFICIENT: 70.0% <= Composite score < 85.0% with all mandatory prerequisites satisfied.
 * - MASTERED: Composite score >= 85.0%, study status COMPLETED, assessment score >= 80.0%,
 *             adequate question bank coverage, and all prerequisites met.
 */

enum class MasteryLevel(
    val displayName: String,
    val badgeColorHex: Long,
    val description: String
) {
    NOT_ASSESSED("Not Assessed", 0xFF757575, "Diagnostic assessment pending. Baseline knowledge unverified."),
    NEEDS_IMPROVEMENT("Needs Improvement", 0xFFD32F2F, "Critical knowledge gaps identified. Targeted study and guided practice required."),
    DEVELOPING("Developing", 0xFFF57C00, "Foundational understanding established. Continued practice and prerequisite completion needed."),
    PROFICIENT("Proficient", 0xFF1976D2, "Strong conceptual and procedural mastery demonstrated across core objectives."),
    MASTERED("Mastered", 0xFF2E7D32, "Exemplary mastery demonstrated. High assessment score and full module completion achieved.");

    companion object {
        fun fromString(value: String?): MasteryLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOT_ASSESSED
    }
}

data class TopicMasteryResult(
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val masteryLevel: MasteryLevel,
    val compositeScore: Double,
    val assessmentScore: Double,
    val attemptsCount: Int,
    val correctCount: Int,
    val totalQuestions: Int,
    val progressStatus: LearningProgressStatus,
    val completedPrerequisitesCount: Int,
    val totalPrerequisitesCount: Int,
    val missingPrerequisiteNodeIds: List<String>,
    val needsRevision: Boolean,
    val lastAssessedEpochMs: Long?,
    val isAssessed: Boolean = attemptsCount > 0,
    val isZeroScore: Boolean = attemptsCount > 0 && correctCount == 0,
    val isStale: Boolean = false,
    val questionBankCoverage: String = "ADEQUATE"
)

object KnowledgeMasteryEngine {

    const val WEIGHT_ASSESSMENT = 0.45
    const val WEIGHT_STUDY_COMPLETION = 0.35
    const val WEIGHT_PREREQUISITES = 0.20

    const val THRESHOLD_NEEDS_IMPROVEMENT = 50.0
    const val THRESHOLD_PROFICIENT = 70.0
    const val THRESHOLD_MASTERED = 85.0
    const val THRESHOLD_ASSESSMENT_FOR_MASTERY = 80.0
    const val STALE_SCORE_MAX_AGE_DAYS = 60
    const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Deterministically calculates topic mastery from verifiable learner actions.
     */
    fun calculateTopicMastery(
        nodeId: String,
        topicTitle: String,
        discipline: String,
        progressStatus: LearningProgressStatus,
        attemptsCount: Int,
        correctCount: Int,
        totalQuestions: Int,
        directMandatoryPrerequisites: List<String>,
        completedNodeIds: Set<String>,
        lastAssessedEpochMs: Long? = null,
        availableQuestionsCount: Int = totalQuestions,
        currentEpochMs: Long = System.currentTimeMillis()
    ): TopicMasteryResult {
        val totalPrereqs = directMandatoryPrerequisites.size
        val missingPrereqs = directMandatoryPrerequisites.filter { it !in completedNodeIds }
        val completedPrereqsCount = totalPrereqs - missingPrereqs.size

        val coverageRating = if (availableQuestionsCount in 1..2) "INSUFFICIENT" else "ADEQUATE"

        // 1. Explicitly Distinguish NOT_ASSESSED from Zero Score:
        // If no diagnostic assessment has been attempted, topic is strictly NOT_ASSESSED
        // A learner is NEVER classified as having poor knowledge (NEEDS_IMPROVEMENT) without an assessment!
        if (attemptsCount == 0) {
            val needsRevision = progressStatus == LearningProgressStatus.NEEDS_REVISION
            return TopicMasteryResult(
                nodeId = nodeId,
                topicTitle = topicTitle,
                discipline = discipline,
                masteryLevel = MasteryLevel.NOT_ASSESSED,
                compositeScore = 0.0,
                assessmentScore = 0.0,
                attemptsCount = 0,
                correctCount = 0,
                totalQuestions = totalQuestions,
                progressStatus = progressStatus,
                completedPrerequisitesCount = completedPrereqsCount,
                totalPrerequisitesCount = totalPrereqs,
                missingPrerequisiteNodeIds = missingPrereqs,
                needsRevision = needsRevision,
                lastAssessedEpochMs = null,
                isAssessed = false,
                isZeroScore = false,
                isStale = false,
                questionBankCoverage = coverageRating
            )
        }

        // 2. Calculate Raw Assessment Score (0.0 - 100.0)
        var rawAssessmentScore = if (totalQuestions > 0) {
            (correctCount.toDouble() / totalQuestions.toDouble()) * 100.0
        } else {
            0.0
        }

        // Check staleness
        var isStale = false
        if (lastAssessedEpochMs != null) {
            val ageDays = ((currentEpochMs - lastAssessedEpochMs) / MILLIS_PER_DAY).toInt()
            if (ageDays > STALE_SCORE_MAX_AGE_DAYS) {
                isStale = true
                rawAssessmentScore *= 0.80 // 20% retention decay for stale assessment score
            }
        }
        val assessmentScore = roundToSingleDecimal(rawAssessmentScore)

        // 3. Calculate Study Completion Score (0.0 - 100.0)
        // Hierarchy: NOT_STARTED (0) < IN_PROGRESS (40) < NEEDS_REVISION (60) < COMPLETED (100)
        val studyCompletionScore = when (progressStatus) {
            LearningProgressStatus.COMPLETED -> 100.0
            LearningProgressStatus.IN_PROGRESS -> 40.0
            LearningProgressStatus.NEEDS_REVISION -> 60.0
            LearningProgressStatus.NOT_STARTED -> 0.0
        }

        // 4. Calculate Prerequisite Completion Score (0.0 - 100.0)
        val prerequisiteScore = if (totalPrereqs == 0) {
            100.0
        } else {
            (completedPrereqsCount.toDouble() / totalPrereqs.toDouble()) * 100.0
        }

        // 5. Calculate Weighted Composite Score
        val rawComposite = (assessmentScore * WEIGHT_ASSESSMENT) +
                (studyCompletionScore * WEIGHT_STUDY_COMPLETION) +
                (prerequisiteScore * WEIGHT_PREREQUISITES)

        var compositeScore = roundToSingleDecimal(rawComposite)

        // Prerequisite Gating Rule: If mandatory prerequisites are unfulfilled,
        // composite score cannot exceed 69.9% (cannot be Proficient or Mastered)
        val hasMissingPrerequisites = missingPrereqs.isNotEmpty()
        if (hasMissingPrerequisites && compositeScore >= THRESHOLD_PROFICIENT) {
            compositeScore = 69.9
        }

        // 6. Determine Mastery Level Classification
        val masteryLevel = when {
            hasMissingPrerequisites -> {
                if (compositeScore < THRESHOLD_NEEDS_IMPROVEMENT) {
                    MasteryLevel.NEEDS_IMPROVEMENT
                } else {
                    MasteryLevel.DEVELOPING
                }
            }
            compositeScore >= THRESHOLD_MASTERED &&
                    progressStatus == LearningProgressStatus.COMPLETED &&
                    assessmentScore >= THRESHOLD_ASSESSMENT_FOR_MASTERY -> {
                if (coverageRating == "INSUFFICIENT") {
                    // Insufficient question bank coverage caps mastery at PROFICIENT
                    MasteryLevel.PROFICIENT
                } else {
                    MasteryLevel.MASTERED
                }
            }
            compositeScore >= THRESHOLD_PROFICIENT -> {
                MasteryLevel.PROFICIENT
            }
            compositeScore >= THRESHOLD_NEEDS_IMPROVEMENT -> {
                MasteryLevel.DEVELOPING
            }
            else -> {
                MasteryLevel.NEEDS_IMPROVEMENT
            }
        }

        // 7. Determine Revision Flag
        val needsRevision = (progressStatus == LearningProgressStatus.NEEDS_REVISION) ||
                (compositeScore < THRESHOLD_PROFICIENT) ||
                (progressStatus == LearningProgressStatus.COMPLETED && assessmentScore < 75.0) ||
                isStale

        return TopicMasteryResult(
            nodeId = nodeId,
            topicTitle = topicTitle,
            discipline = discipline,
            masteryLevel = masteryLevel,
            compositeScore = compositeScore,
            assessmentScore = assessmentScore,
            attemptsCount = attemptsCount,
            correctCount = correctCount,
            totalQuestions = totalQuestions,
            progressStatus = progressStatus,
            completedPrerequisitesCount = completedPrereqsCount,
            totalPrerequisitesCount = totalPrereqs,
            missingPrerequisiteNodeIds = missingPrereqs,
            needsRevision = needsRevision,
            lastAssessedEpochMs = lastAssessedEpochMs,
            isAssessed = true,
            isZeroScore = (correctCount == 0),
            isStale = isStale,
            questionBankCoverage = coverageRating
        )
    }

    private fun roundToSingleDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }
}

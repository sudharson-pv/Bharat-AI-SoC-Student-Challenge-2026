package com.learnmate.ai.domain.assessment

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Gate 6.6: Assessment Reliability, History & Coverage Intelligence Engine.
 *
 * Implements:
 * 1. Question-level response auditing and preservation.
 * 2. Assessment history preservation and chronological trend tracking.
 * 3. Anti-Gaming Familiarity Discount: Prevents treating repeated attempts at identical questions
 *    as independent evidence of conceptual mastery.
 * 4. Question-Bank Coverage & Confidence Analysis: Identifies curriculum nodes with limited test items.
 * 5. Stale Score Invalidation: Flags assessment scores older than 60 days for re-testing.
 *
 * Safeguard: Grading and score aggregation remain 100% deterministic in Kotlin.
 */

data class QuestionResponseRecord(
    val questionId: String,
    val questionText: String,
    val selectedIndex: Int,
    val correctIndex: Int,
    val isCorrect: Boolean,
    val isRepeatedQuestion: Boolean,
    val verifiedExplanation: String,
    val timeTakenSeconds: Int = 0
)

data class AssessmentAttemptRecord(
    val attemptId: String,
    val trackId: String,
    val userId: String,
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val scorePercent: Double,
    val correctCount: Int,
    val totalQuestions: Int,
    val durationSeconds: Int,
    val attemptEpochMs: Long,
    val questionResponses: List<QuestionResponseRecord>,
    val isStale: Boolean = false,
    val questionBankCoverageRating: String = "ADEQUATE",
    val schemaVersion: Int = 1
)

data class QuestionBankCoverageReport(
    val nodeId: String,
    val topicTitle: String,
    val availableQuestionsCount: Int,
    val standardBenchmarkCount: Int = 5,
    val isSufficient: Boolean,
    val coverageRatio: Double,
    val confidenceRating: String, // "HIGH", "MEDIUM", "LOW"
    val explanation: String
)

data class AssessmentTrendPoint(
    val attemptEpochMs: Long,
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val scorePercent: Double
)

data class AggregatedAssessmentResult(
    val effectiveScorePercent: Double,
    val rawLatestScorePercent: Double,
    val historicalAverageScorePercent: Double,
    val totalAttemptsCount: Int,
    val uniqueQuestionsAttemptedCount: Int,
    val repeatedQuestionsAttemptedCount: Int,
    val familiarityDiscountApplied: Boolean,
    val isStale: Boolean,
    val coverageReport: QuestionBankCoverageReport
)

object AssessmentReliabilityEngine {

    const val STANDARD_BENCHMARK_QUESTIONS = 5
    const val MINIMUM_SUFFICIENT_QUESTIONS = 3
    const val REPEATED_QUESTION_FAMILIARITY_MULTIPLIER = 0.85
    const val STALE_SCORE_MAX_AGE_DAYS = 60
    const val MILLIS_PER_DAY = 86_400_000L

    /**
     * Generates a collision-safe, unique attempt ID incorporating track, node, timestamp, and UUID nonce.
     */
    fun generateAttemptId(
        trackId: String,
        nodeId: String,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): String {
        val uniqueSuffix = java.util.UUID.randomUUID().toString().take(8)
        return "attempt_${trackId}_${nodeId}_${timestampEpochMs}_$uniqueSuffix"
    }

    /**
     * Evaluates question bank coverage for a specific curriculum node against standard psychometric benchmarks.
     */
    fun evaluateQuestionBankCoverage(
        nodeId: String,
        topicTitle: String,
        availableQuestionsCount: Int
    ): QuestionBankCoverageReport {
        val coverageRatio = roundToTwoDecimals(min(1.0, availableQuestionsCount.toDouble() / STANDARD_BENCHMARK_QUESTIONS.toDouble()))
        val isSufficient = availableQuestionsCount >= MINIMUM_SUFFICIENT_QUESTIONS

        val confidenceRating = when {
            availableQuestionsCount >= STANDARD_BENCHMARK_QUESTIONS -> "HIGH"
            availableQuestionsCount >= MINIMUM_SUFFICIENT_QUESTIONS -> "MEDIUM"
            else -> "LOW"
        }

        val explanation = when (confidenceRating) {
            "HIGH" -> "Comprehensive question bank coverage ($availableQuestionsCount questions available). High diagnostic reliability."
            "MEDIUM" -> "Moderate question bank coverage ($availableQuestionsCount/$STANDARD_BENCHMARK_QUESTIONS standard questions). Adequate for baseline diagnostic."
            else -> "Insufficient question bank size ($availableQuestionsCount/$STANDARD_BENCHMARK_QUESTIONS standard questions). Repeated attempts evaluate familiar questions rather than broad domain mastery."
        }

        return QuestionBankCoverageReport(
            nodeId = nodeId,
            topicTitle = topicTitle,
            availableQuestionsCount = availableQuestionsCount,
            standardBenchmarkCount = STANDARD_BENCHMARK_QUESTIONS,
            isSufficient = isSufficient,
            coverageRatio = coverageRatio,
            confidenceRating = confidenceRating,
            explanation = explanation
        )
    }

    /**
     * Aggregates multiple assessment attempts for a topic, applying recency weighting,
     * familiarity discounts for repeated questions, and staleness penalties.
     */
    fun calculateAggregatedAssessmentScore(
        nodeId: String,
        topicTitle: String,
        attempts: List<AssessmentAttemptRecord>,
        availableQuestionsCount: Int,
        currentEpochMs: Long = System.currentTimeMillis()
    ): AggregatedAssessmentResult {
        val coverageReport = evaluateQuestionBankCoverage(nodeId, topicTitle, availableQuestionsCount)

        if (attempts.isEmpty()) {
            return AggregatedAssessmentResult(
                effectiveScorePercent = 0.0,
                rawLatestScorePercent = 0.0,
                historicalAverageScorePercent = 0.0,
                totalAttemptsCount = 0,
                uniqueQuestionsAttemptedCount = 0,
                repeatedQuestionsAttemptedCount = 0,
                familiarityDiscountApplied = false,
                isStale = false,
                coverageReport = coverageReport
            )
        }

        val sortedAttempts = attempts.sortedBy { it.attemptEpochMs }
        val latestAttempt = sortedAttempts.last()

        // Track unique vs repeated questions
        val allSeenQuestionIds = mutableSetOf<String>()
        var repeatedCount = 0
        var familiarityDiscountApplied = false

        for (att in sortedAttempts) {
            for (resp in att.questionResponses) {
                if (resp.questionId in allSeenQuestionIds) {
                    repeatedCount++
                } else {
                    allSeenQuestionIds.add(resp.questionId)
                }
            }
        }

        // Check if latest attempt has repeated questions
        val seenPriorToLatest = mutableSetOf<String>()
        for (i in 0 until sortedAttempts.size - 1) {
            sortedAttempts[i].questionResponses.forEach { seenPriorToLatest.add(it.questionId) }
        }

        var adjustedLatestCorrect = 0.0
        val latestTotal = latestAttempt.questionResponses.size

        if (latestTotal > 0) {
            for (resp in latestAttempt.questionResponses) {
                if (resp.isCorrect) {
                    if (resp.questionId in seenPriorToLatest) {
                        // Familiarity discount: repeated correct response discounted by 15%
                        adjustedLatestCorrect += REPEATED_QUESTION_FAMILIARITY_MULTIPLIER
                        familiarityDiscountApplied = true
                    } else {
                        adjustedLatestCorrect += 1.0
                    }
                }
            }
        }

        val adjustedLatestScore = if (latestTotal > 0) {
            (adjustedLatestCorrect / latestTotal.toDouble()) * 100.0
        } else {
            latestAttempt.scorePercent
        }

        // Calculate historical average
        val historicalScores = sortedAttempts.map { it.scorePercent }
        val historicalAvg = historicalScores.average()

        // Recency weighting: 60% latest attempt + 40% historical average
        val weightedScore = if (sortedAttempts.size == 1) {
            adjustedLatestScore
        } else {
            (adjustedLatestScore * 0.60) + (historicalAvg * 0.40)
        }

        // Staleness evaluation
        val ageDays = ((currentEpochMs - latestAttempt.attemptEpochMs) / MILLIS_PER_DAY).toInt()
        val isStale = ageDays > STALE_SCORE_MAX_AGE_DAYS

        // Stale discount: 20% decay penalty for assessments older than 60 days
        val finalEffectiveScore = if (isStale) {
            weightedScore * 0.80
        } else {
            weightedScore
        }

        return AggregatedAssessmentResult(
            effectiveScorePercent = roundToSingleDecimal(finalEffectiveScore),
            rawLatestScorePercent = roundToSingleDecimal(latestAttempt.scorePercent),
            historicalAverageScorePercent = roundToSingleDecimal(historicalAvg),
            totalAttemptsCount = sortedAttempts.size,
            uniqueQuestionsAttemptedCount = allSeenQuestionIds.size,
            repeatedQuestionsAttemptedCount = repeatedCount,
            familiarityDiscountApplied = familiarityDiscountApplied,
            isStale = isStale,
            coverageReport = coverageReport
        )
    }

    /**
     * Extracts chronological assessment score trend points.
     */
    fun generateAssessmentTrends(attempts: List<AssessmentAttemptRecord>): List<AssessmentTrendPoint> {
        return attempts.sortedBy { it.attemptEpochMs }.map {
            AssessmentTrendPoint(
                attemptEpochMs = it.attemptEpochMs,
                nodeId = it.nodeId,
                topicTitle = it.topicTitle,
                discipline = it.discipline,
                scorePercent = it.scorePercent
            )
        }
    }

    private fun roundToSingleDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}


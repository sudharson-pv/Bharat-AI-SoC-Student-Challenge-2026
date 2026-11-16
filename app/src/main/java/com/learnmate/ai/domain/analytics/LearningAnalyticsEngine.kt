package com.learnmate.ai.domain.analytics

import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.AssessmentTrendPoint
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlin.math.round

/**
 * Gate 6.6: Learner Progress Intelligence & Analytics Engine.
 *
 * Implements transparent, empirical learning analytics:
 * 1. Daily and weekly study activity (minutes studied, questions answered, accuracy rate).
 * 2. Assessment score trends and trajectory (improving, steady, declining).
 * 3. Topic mastery distribution across the 5 mastery tiers.
 * 4. Spaced repetition completion rate and overdue tracking.
 * 5. Learning streak calculation from verified activity timestamps.
 * 6. Track goal completion percentage.
 * 7. Data Sufficiency Guard: Transparently discloses when insufficient historical data exists (< 3 sessions),
 *    preventing fabricated graphs or misleading claims.
 */

data class LearnerAnalyticsReport(
    val trackId: String,
    val userId: String,
    val dailyActivityMinutes: Int,
    val weeklyActivityMinutes: Int,
    val totalQuestionsAnswered: Int,
    val overallAccuracyPercent: Double,
    val assessmentTrends: List<AssessmentTrendPoint>,
    val scoreTrajectory: String, // "IMPROVING", "STEADY", "DECLINING", "INSUFFICIENT_DATA"
    val masteryDistribution: Map<MasteryLevel, Int>,
    val revisionCompletionRate: Double,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val overdueTopicsCount: Int,
    val overdueTopicTitles: List<String>,
    val goalCompletionProgressPercent: Double,
    val hasSufficientData: Boolean,
    val dataSufficiencyNotice: String
)

object LearningAnalyticsEngine {

    const val MILLIS_PER_DAY = 86_400_000L
    const val MINIMUM_DATA_POINTS_THRESHOLD = 3

    /**
     * Synthesizes an empirical, verifiable analytics report for a specific track.
     */
    fun computeAnalytics(
        trackId: String,
        userId: String,
        attempts: List<AssessmentAttemptRecord>,
        srsItems: List<SpacedRepetitionItem>,
        masteries: List<TopicMasteryResult>,
        completedTaskEpochs: List<Long>,
        currentEpochMs: Long = System.currentTimeMillis()
    ): LearnerAnalyticsReport {
        val todayStartEpoch = (currentEpochMs / MILLIS_PER_DAY) * MILLIS_PER_DAY
        val sevenDaysAgoEpoch = currentEpochMs - (7 * MILLIS_PER_DAY)

        // 1. Calculate Activity Minutes
        val todayAttempts = attempts.filter { it.attemptEpochMs >= todayStartEpoch }
        val weekAttempts = attempts.filter { it.attemptEpochMs >= sevenDaysAgoEpoch }

        // Assume baseline 15 mins per assessment if duration not recorded
        val dailyActivityMinutes = todayAttempts.sumOf { maxOf(10, it.durationSeconds / 60) } +
                (completedTaskEpochs.count { it >= todayStartEpoch } * 30)

        val weeklyActivityMinutes = weekAttempts.sumOf { maxOf(10, it.durationSeconds / 60) } +
                (completedTaskEpochs.count { it >= sevenDaysAgoEpoch } * 30)

        // 2. Questions & Accuracy
        val totalQuestionsAnswered = attempts.sumOf { it.totalQuestions }
        val totalCorrect = attempts.sumOf { it.correctCount }
        val overallAccuracy = if (totalQuestionsAnswered > 0) {
            roundToSingleDecimal((totalCorrect.toDouble() / totalQuestionsAnswered.toDouble()) * 100.0)
        } else {
            0.0
        }

        // 3. Chronological Assessment Trends
        val sortedAttempts = attempts.sortedBy { it.attemptEpochMs }
        val assessmentTrends = sortedAttempts.map {
            AssessmentTrendPoint(
                attemptEpochMs = it.attemptEpochMs,
                nodeId = it.nodeId,
                topicTitle = it.topicTitle,
                discipline = it.discipline,
                scorePercent = it.scorePercent
            )
        }

        // 4. Data Sufficiency Check
        val distinctActiveDays = (attempts.map { it.attemptEpochMs / MILLIS_PER_DAY } +
                completedTaskEpochs.map { it / MILLIS_PER_DAY }).distinct().size
        val hasSufficientData = distinctActiveDays >= MINIMUM_DATA_POINTS_THRESHOLD || attempts.size >= MINIMUM_DATA_POINTS_THRESHOLD

        // 5. Score Trajectory
        val scoreTrajectory = if (!hasSufficientData) {
            "INSUFFICIENT_DATA"
        } else if (attempts.size >= 2) {
            val half = attempts.size / 2
            val firstHalfAvg = attempts.take(half).map { it.scorePercent }.average()
            val secondHalfAvg = attempts.drop(half).map { it.scorePercent }.average()
            val delta = secondHalfAvg - firstHalfAvg
            when {
                delta > 5.0 -> "IMPROVING"
                delta < -5.0 -> "DECLINING"
                else -> "STEADY"
            }
        } else {
            "INSUFFICIENT_DATA"
        }

        val dataSufficiencyNotice = if (!hasSufficientData) {
            "Insufficient historical data to compute reliable learning trends ($distinctActiveDays/$MINIMUM_DATA_POINTS_THRESHOLD active days recorded). Complete at least 3 study or assessment sessions to unlock predictive trend analytics."
        } else {
            "Reliable baseline established across $distinctActiveDays active study days ($totalQuestionsAnswered questions evaluated)."
        }

        // 6. Mastery Distribution
        val distribution = mutableMapOf<MasteryLevel, Int>()
        MasteryLevel.entries.forEach { distribution[it] = 0 }
        masteries.forEach { m ->
            distribution[m.masteryLevel] = (distribution[m.masteryLevel] ?: 0) + 1
        }

        // 7. Spaced Repetition Completion Rate & Overdue
        val overdueItems = srsItems.filter { it.dueEpochMs < currentEpochMs }
        val completedReviewsCount = srsItems.sumOf { it.reviewHistory.size }
        val totalReviewsDueOrCompleted = completedReviewsCount + overdueItems.size
        val revisionCompletionRate = if (totalReviewsDueOrCompleted > 0) {
            roundToSingleDecimal((completedReviewsCount.toDouble() / totalReviewsDueOrCompleted.toDouble()) * 100.0)
        } else {
            0.0
        }

        // 8. Daily Streaks
        val allActivityDays = (attempts.map { it.attemptEpochMs / MILLIS_PER_DAY } +
                completedTaskEpochs.map { it / MILLIS_PER_DAY })
            .distinct()
            .sortedDescending()

        val todayDayIndex = currentEpochMs / MILLIS_PER_DAY
        var currentStreak = 0
        var checkDay = todayDayIndex

        // Allow streak to count if active today or yesterday
        if (allActivityDays.isNotEmpty()) {
            if (allActivityDays.first() == todayDayIndex) {
                currentStreak++
                checkDay = todayDayIndex - 1
            } else if (allActivityDays.first() == todayDayIndex - 1) {
                currentStreak++
                checkDay = todayDayIndex - 2
            }
            while (allActivityDays.contains(checkDay)) {
                currentStreak++
                checkDay--
            }
        }

        // Calculate longest streak
        var longestStreak = 0
        var runningStreak = 0
        val sortedAscDays = allActivityDays.sorted()
        for (i in sortedAscDays.indices) {
            if (i == 0) {
                runningStreak = 1
            } else {
                if (sortedAscDays[i] == sortedAscDays[i - 1] + 1) {
                    runningStreak++
                } else if (sortedAscDays[i] != sortedAscDays[i - 1]) {
                    runningStreak = 1
                }
            }
            if (runningStreak > longestStreak) longestStreak = runningStreak
        }
        if (currentStreak > longestStreak) longestStreak = currentStreak

        // 9. Goal Completion Progress
        val totalTopics = masteries.size
        val masteredOrProficient = masteries.count {
            it.masteryLevel == MasteryLevel.MASTERED || it.masteryLevel == MasteryLevel.PROFICIENT
        }
        val goalProgressPercent = if (totalTopics > 0) {
            roundToSingleDecimal((masteredOrProficient.toDouble() / totalTopics.toDouble()) * 100.0)
        } else {
            0.0
        }

        return LearnerAnalyticsReport(
            trackId = trackId,
            userId = userId,
            dailyActivityMinutes = dailyActivityMinutes,
            weeklyActivityMinutes = weeklyActivityMinutes,
            totalQuestionsAnswered = totalQuestionsAnswered,
            overallAccuracyPercent = overallAccuracy,
            assessmentTrends = assessmentTrends,
            scoreTrajectory = scoreTrajectory,
            masteryDistribution = distribution,
            revisionCompletionRate = revisionCompletionRate,
            currentStreakDays = currentStreak,
            longestStreakDays = longestStreak,
            overdueTopicsCount = overdueItems.size,
            overdueTopicTitles = overdueItems.map { it.topicTitle },
            goalCompletionProgressPercent = goalProgressPercent,
            hasSufficientData = hasSufficientData,
            dataSufficiencyNotice = dataSufficiencyNotice
        )
    }

    private fun roundToSingleDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }
}


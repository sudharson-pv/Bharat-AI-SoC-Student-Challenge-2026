package com.learnmate.ai.domain.repetition

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Gate 6.6: Deterministic Spaced Repetition Engine.
 *
 * Implements an offline, deterministic revision scheduler based on the SuperMemo SM-2 interval
 * expansion model (Wozniak, 1990) and Ebbinghaus forgetting curve principles, parameterized for
 * modular curriculum topic retention.
 *
 * Algorithm Specifications:
 * 1. Initial Revision: Scheduled at 1 day (24 hours) after initial module completion.
 * 2. Performance Grade (q in 0..5) derived deterministically from review assessment score:
 *    - Score >= 90% -> Grade 5 (Perfect recall)
 *    - Score >= 80% -> Grade 4 (Hesitant correct recall)
 *    - Score >= 70% -> Grade 3 (Serious difficulty, but passing)
 *    - Score >= 50% -> Grade 2 (Incorrect, familiar)
 *    - Score >= 30% -> Grade 1 (Incorrect, vague)
 *    - Score < 30%  -> Grade 0 (Complete blackout)
 * 3. Interval Progression:
 *    - If q < 3 (Failed Review): Repetition resets to 0, Interval resets to 1 day,
 *      Ease Factor penalty applied (EF - 0.20).
 *    - If q >= 3 (Successful Review):
 *      - Repetition 1: I_1 = 1 day
 *      - Repetition 2: I_2 = 3 days
 *      - Repetition 3: I_3 = 7 days
 *      - Repetition n >= 4: I_n = round(I_{n-1} * EF)
 *      - EF' = clamp(1.3, 3.0, EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
 * 4. Overdue Detection: Due timestamp < Current timestamp.
 * 5. Manual Scheduling: Learner can override the next review date.
 *
 * Safeguard: Generative AI is NEVER used to calculate intervals or evaluate review performance.
 */

data class SpacedRepetitionConfig(
    val initialIntervalDays: Int = 1,
    val secondIntervalDays: Int = 3,
    val thirdIntervalDays: Int = 7,
    val defaultEaseFactor: Double = 2.5,
    val minEaseFactor: Double = 1.3,
    val maxEaseFactor: Double = 3.0,
    val passingScorePercent: Double = 70.0
)

data class ReviewLogEntry(
    val reviewEpochMs: Long,
    val scorePercent: Double,
    val performanceGrade: Int,
    val previousIntervalDays: Int,
    val newIntervalDays: Int,
    val easeFactor: Double,
    val wasOverdue: Boolean,
    val overdueDays: Int
)

enum class RevisionScheduleState {
    FUTURE,      // Due tomorrow or later
    DUE_TODAY,   // Due today for optimal retention (within 24 hours)
    OVERDUE,     // Past scheduled review deadline
    COMPLETED    // Fully retained / not currently scheduled
}

data class SpacedRepetitionItem(
    val repetitionId: String,
    val trackId: String,
    val userId: String,
    val nodeId: String,
    val topicTitle: String,
    val discipline: String,
    val repetitionNumber: Int,
    val intervalDays: Int,
    val easeFactor: Double,
    val lastReviewedEpochMs: Long,
    val dueEpochMs: Long,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val reviewHistory: List<ReviewLogEntry> = emptyList(),
    val scheduleState: RevisionScheduleState = if (isOverdue) RevisionScheduleState.OVERDUE else RevisionScheduleState.FUTURE,
    val schemaVersion: Int = 1
)

object SpacedRepetitionEngine {

    const val MILLIS_PER_DAY: Long = 86_400_000L

    val DEFAULT_CONFIG = SpacedRepetitionConfig()

    /**
     * Maps an assessment score percentage (0.0 - 100.0) to a deterministic SM-2 grade (0 - 5).
     */
    fun calculatePerformanceGrade(scorePercent: Double): Int {
        return when {
            scorePercent >= 90.0 -> 5
            scorePercent >= 80.0 -> 4
            scorePercent >= 70.0 -> 3
            scorePercent >= 50.0 -> 2
            scorePercent >= 30.0 -> 1
            else -> 0
        }
    }

    /**
     * Initializes a new spaced repetition schedule for a newly completed curriculum module.
     * The first review is deterministically scheduled for 24 hours (1 day) after completion.
     */
    fun scheduleInitialRevision(
        trackId: String,
        userId: String,
        nodeId: String,
        topicTitle: String,
        discipline: String,
        completionEpochMs: Long = System.currentTimeMillis(),
        config: SpacedRepetitionConfig = DEFAULT_CONFIG
    ): SpacedRepetitionItem {
        val dueEpochMs = completionEpochMs + (config.initialIntervalDays * MILLIS_PER_DAY)
        val scheduleState = if (config.initialIntervalDays <= 1) RevisionScheduleState.DUE_TODAY else RevisionScheduleState.FUTURE
        return SpacedRepetitionItem(
            repetitionId = "srs_${trackId}_${nodeId}",
            trackId = trackId,
            userId = userId,
            nodeId = nodeId,
            topicTitle = topicTitle,
            discipline = discipline,
            repetitionNumber = 0,
            intervalDays = config.initialIntervalDays,
            easeFactor = config.defaultEaseFactor,
            lastReviewedEpochMs = completionEpochMs,
            dueEpochMs = dueEpochMs,
            isOverdue = false,
            overdueDays = 0,
            reviewHistory = emptyList(),
            scheduleState = scheduleState,
            schemaVersion = 1
        )
    }

    /**
     * Computes the subsequent spaced repetition interval and updated ease factor following a review attempt.
     */
    fun calculateNextSchedule(
        item: SpacedRepetitionItem,
        reviewScorePercent: Double,
        reviewEpochMs: Long = System.currentTimeMillis(),
        config: SpacedRepetitionConfig = DEFAULT_CONFIG
    ): SpacedRepetitionItem {
        val grade = calculatePerformanceGrade(reviewScorePercent)
        val wasOverdue = reviewEpochMs > item.dueEpochMs
        val overdueDays = if (wasOverdue) {
            max(0, ((reviewEpochMs - item.dueEpochMs) / MILLIS_PER_DAY).toInt())
        } else {
            0
        }

        val nextRepetitionNumber: Int
        val nextIntervalDays: Int
        val nextEaseFactor: Double

        if (grade < 3) {
            // Review Failed: Reset repetition sequence to reinforce weak memory trace
            nextRepetitionNumber = 0
            nextIntervalDays = config.initialIntervalDays
            nextEaseFactor = max(config.minEaseFactor, roundToTwoDecimals(item.easeFactor - 0.20))
        } else {
            // Review Successful: Expand interval based on SM-2 progression
            nextRepetitionNumber = item.repetitionNumber + 1
            nextIntervalDays = when (nextRepetitionNumber) {
                1 -> config.secondIntervalDays
                2 -> config.thirdIntervalDays
                else -> {
                    val rawInterval = item.intervalDays * item.easeFactor
                    max(config.thirdIntervalDays + 1, round(rawInterval).toInt())
                }
            }

            // SM-2 Ease Factor adaptation formula
            val efDelta = 0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02)
            val updatedEf = item.easeFactor + efDelta
            nextEaseFactor = min(config.maxEaseFactor, max(config.minEaseFactor, roundToTwoDecimals(updatedEf)))
        }

        val nextDueEpochMs = reviewEpochMs + (nextIntervalDays * MILLIS_PER_DAY)
        val scheduleState = if (nextIntervalDays <= 1) RevisionScheduleState.DUE_TODAY else RevisionScheduleState.FUTURE

        val logEntry = ReviewLogEntry(
            reviewEpochMs = reviewEpochMs,
            scorePercent = reviewScorePercent,
            performanceGrade = grade,
            previousIntervalDays = item.intervalDays,
            newIntervalDays = nextIntervalDays,
            easeFactor = nextEaseFactor,
            wasOverdue = wasOverdue,
            overdueDays = overdueDays
        )

        return item.copy(
            repetitionNumber = nextRepetitionNumber,
            intervalDays = nextIntervalDays,
            easeFactor = nextEaseFactor,
            lastReviewedEpochMs = reviewEpochMs,
            dueEpochMs = nextDueEpochMs,
            isOverdue = false,
            overdueDays = 0,
            reviewHistory = item.reviewHistory + logEntry,
            scheduleState = scheduleState
        )
    }

    /**
     * Allows learner or tutor to manually schedule or reschedule a revision for a target timestamp.
     */
    fun scheduleManualRevision(
        item: SpacedRepetitionItem,
        targetEpochMs: Long,
        currentEpochMs: Long = System.currentTimeMillis()
    ): SpacedRepetitionItem {
        val daysFromNow = max(1, ((targetEpochMs - currentEpochMs) / MILLIS_PER_DAY).toInt())
        val isOverdue = currentEpochMs > targetEpochMs
        val overdueDays = if (isOverdue) max(0, ((currentEpochMs - targetEpochMs) / MILLIS_PER_DAY).toInt()) else 0
        val scheduleState = when {
            isOverdue -> RevisionScheduleState.OVERDUE
            targetEpochMs <= currentEpochMs + MILLIS_PER_DAY -> RevisionScheduleState.DUE_TODAY
            else -> RevisionScheduleState.FUTURE
        }

        return item.copy(
            dueEpochMs = targetEpochMs,
            intervalDays = daysFromNow,
            isOverdue = isOverdue,
            overdueDays = overdueDays,
            scheduleState = scheduleState
        )
    }

    /**
     * Evaluates current overdue status against a reference timestamp.
     */
    fun evaluateOverdueStatus(
        item: SpacedRepetitionItem,
        currentEpochMs: Long = System.currentTimeMillis()
    ): SpacedRepetitionItem {
        val isOverdue = currentEpochMs > item.dueEpochMs
        val overdueDays = if (isOverdue) {
            max(0, ((currentEpochMs - item.dueEpochMs) / MILLIS_PER_DAY).toInt())
        } else {
            0
        }
        val scheduleState = when {
            isOverdue -> RevisionScheduleState.OVERDUE
            item.dueEpochMs <= currentEpochMs + MILLIS_PER_DAY -> RevisionScheduleState.DUE_TODAY
            else -> RevisionScheduleState.FUTURE
        }
        return item.copy(
            isOverdue = isOverdue,
            overdueDays = overdueDays,
            scheduleState = scheduleState
        )
    }

    private fun roundToTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }
}


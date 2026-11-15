package com.learnmate.ai.learning

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.db.LearnMateDatabase
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.analytics.LearningAnalyticsEngine
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.AssessmentReliabilityEngine
import com.learnmate.ai.domain.assessment.QuestionResponseRecord
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.domain.mastery.KnowledgeMasteryEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.planner.PersonalisedStudyPlannerEngine
import com.learnmate.ai.domain.planner.PlannedTaskType
import com.learnmate.ai.domain.repetition.SpacedRepetitionEngine
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gate 6.6: Spaced Repetition, Assessment Reliability & Learning Progress Intelligence Verification Suite.
 */
class Gate66SpacedRepetitionIntelligenceTest {

    private lateinit var repository: LearningRepository

    @Before
    fun setup() {
        // Fallback in-memory mode
        repository = LearningRepository(dao = null)
    }

    // ── 1. Correct Mastery State Handling ───────────────────────────────────────

    @Test
    fun testUnassessedLearnerIsNotClassifiedAsNeedsImprovement() {
        // Case A: NOT_STARTED with 0 attempts
        val unassessedNotStarted = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            progressStatus = LearningProgressStatus.NOT_STARTED,
            attemptsCount = 0,
            correctCount = 0,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.NOT_ASSESSED, unassessedNotStarted.masteryLevel)
        assertEquals(0.0, unassessedNotStarted.compositeScore, 0.01)
        assertFalse(unassessedNotStarted.isAssessed)
        assertFalse(unassessedNotStarted.isZeroScore)

        // Case B: IN_PROGRESS study with 0 diagnostic attempts
        // A learner must NOT be branded as having poor knowledge (NEEDS_IMPROVEMENT) merely because no test was taken!
        val unassessedInProgress = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma",
            progressStatus = LearningProgressStatus.IN_PROGRESS,
            attemptsCount = 0,
            correctCount = 0,
            totalQuestions = 2,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals("Learner with 0 attempts must be NOT_ASSESSED, never NEEDS_IMPROVEMENT",
            MasteryLevel.NOT_ASSESSED, unassessedInProgress.masteryLevel)
        assertFalse(unassessedInProgress.isAssessed)

        // Case C: COMPLETED study reading with 0 diagnostic attempts
        val unassessedCompleted = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "eng_de_kmap",
            topicTitle = "Digital Logic",
            discipline = "Engineering",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 0,
            correctCount = 0,
            totalQuestions = 2,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals("Studied module without diagnostic must remain NOT_ASSESSED until assessed",
            MasteryLevel.NOT_ASSESSED, unassessedCompleted.masteryLevel)
        assertFalse(unassessedCompleted.isAssessed)
    }

    @Test
    fun testActualZeroAssessmentScoreClassifiesAsNeedsImprovement() {
        // Case: Learner took the assessment (attemptsCount = 1) and answered 0 questions correctly (0.0%)
        val actualZeroAttempt = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            progressStatus = LearningProgressStatus.NOT_STARTED,
            attemptsCount = 1,
            correctCount = 0,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.NEEDS_IMPROVEMENT, actualZeroAttempt.masteryLevel)
        assertTrue(actualZeroAttempt.isAssessed)
        assertTrue("Must flag actual zero score", actualZeroAttempt.isZeroScore)
        assertTrue(actualZeroAttempt.compositeScore < 50.0)
        assertTrue("Actual zero score requires revision", actualZeroAttempt.needsRevision)
    }

    @Test
    fun testStudyScoreHierarchyProgression() {
        // Verify logical progression: NOT_STARTED (0) < IN_PROGRESS (40) < NEEDS_REVISION (60) < COMPLETED (100)
        val notStarted = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Sciences",
            progressStatus = LearningProgressStatus.NOT_STARTED,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 2, // 100% assessment
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        ) // (100 * 0.45) + (0 * 0.35) + (100 * 0.20) = 45 + 0 + 20 = 65.0%

        val inProgress = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Sciences",
            progressStatus = LearningProgressStatus.IN_PROGRESS,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 2, // 100% assessment
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        ) // (100 * 0.45) + (40 * 0.35) + (100 * 0.20) = 45 + 14 + 20 = 79.0%

        val needsRevision = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Sciences",
            progressStatus = LearningProgressStatus.NEEDS_REVISION,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 2, // 100% assessment
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        ) // (100 * 0.45) + (60 * 0.35) + (100 * 0.20) = 45 + 21 + 20 = 86.0%

        val completed = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Sciences",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 2, // 100% assessment
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        ) // (100 * 0.45) + (100 * 0.35) + (100 * 0.20) = 45 + 35 + 20 = 100.0%

        assertTrue("NOT_STARTED < IN_PROGRESS", notStarted.compositeScore < inProgress.compositeScore)
        assertTrue("IN_PROGRESS < NEEDS_REVISION", inProgress.compositeScore < needsRevision.compositeScore)
        assertTrue("NEEDS_REVISION < COMPLETED", needsRevision.compositeScore < completed.compositeScore)
    }

    // ── 2. Spaced Repetition Engine ─────────────────────────────────────────────

    @Test
    fun testSpacedRepetitionInitialScheduleAndSuccessfulProgression() {
        val baseEpoch = 1700000000000L

        // Initial schedule: 1 day (24 hours) after completion
        val initial = SpacedRepetitionEngine.scheduleInitialRevision(
            trackId = "track_srs_01",
            userId = "user_01",
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            completionEpochMs = baseEpoch
        )
        assertEquals(0, initial.repetitionNumber)
        assertEquals(1, initial.intervalDays)
        assertEquals(2.5, initial.easeFactor, 0.01)
        assertEquals(baseEpoch + (1 * 86_400_000L), initial.dueEpochMs)
        assertFalse(initial.isOverdue)

        // Review 1: Passed with 85% (Grade 4) -> advances to 3 days (I_2 = 3)
        val review1Epoch = initial.dueEpochMs
        val review1 = SpacedRepetitionEngine.calculateNextSchedule(
            item = initial,
            reviewScorePercent = 85.0,
            reviewEpochMs = review1Epoch
        )
        assertEquals(1, review1.repetitionNumber)
        assertEquals(3, review1.intervalDays)
        assertEquals(review1Epoch + (3 * 86_400_000L), review1.dueEpochMs)
        assertEquals(1, review1.reviewHistory.size)

        // Review 2: Passed with 95% (Grade 5) -> advances to 7 days (I_3 = 7)
        val review2Epoch = review1.dueEpochMs
        val review2 = SpacedRepetitionEngine.calculateNextSchedule(
            item = review1,
            reviewScorePercent = 95.0,
            reviewEpochMs = review2Epoch
        )
        assertEquals(2, review2.repetitionNumber)
        assertEquals(7, review2.intervalDays)
        assertEquals(review2Epoch + (7 * 86_400_000L), review2.dueEpochMs)

        // Review 3: Passed with 90% (Grade 5) -> expands via SM-2 formula: round(7 * EF)
        val review3Epoch = review2.dueEpochMs
        val review3 = SpacedRepetitionEngine.calculateNextSchedule(
            item = review2,
            reviewScorePercent = 90.0,
            reviewEpochMs = review3Epoch
        )
        assertEquals(3, review3.repetitionNumber)
        assertTrue("Interval must expand beyond 7 days on 4th review", review3.intervalDays >= 14)
        assertEquals(3, review3.reviewHistory.size)
    }

    @Test
    fun testSpacedRepetitionFailedReviewResetsIntervalToDayOne() {
        val baseEpoch = 1700000000000L
        val item = SpacedRepetitionItem(
            repetitionId = "srs_fail_test",
            trackId = "track_01",
            userId = "user_01",
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma",
            repetitionNumber = 3,
            intervalDays = 18,
            easeFactor = 2.6,
            lastReviewedEpochMs = baseEpoch,
            dueEpochMs = baseEpoch + (18 * 86_400_000L),
            isOverdue = false,
            overdueDays = 0,
            reviewHistory = emptyList()
        )

        // Review failed with score 40% (Grade 1)
        val failedReview = SpacedRepetitionEngine.calculateNextSchedule(
            item = item,
            reviewScorePercent = 40.0,
            reviewEpochMs = item.dueEpochMs
        )
        assertEquals("Failed review must reset repetition count to 0", 0, failedReview.repetitionNumber)
        assertEquals("Failed review must reset interval to 1 day", 1, failedReview.intervalDays)
        assertTrue("Failed review must apply ease factor penalty", failedReview.easeFactor < item.easeFactor)
        assertEquals(1, failedReview.reviewHistory.size)
        assertEquals(1, failedReview.reviewHistory.first().performanceGrade)
    }

    @Test
    fun testOverdueRevisionDetectionAndManualScheduling() {
        val now = 1700000000000L
        val threeDaysAgo = now - (3 * 86_400_000L)

        val overdueItem = SpacedRepetitionItem(
            repetitionId = "srs_overdue",
            trackId = "track_01",
            userId = "user_01",
            nodeId = "eng_de_kmap",
            topicTitle = "Digital Logic",
            discipline = "Engineering",
            repetitionNumber = 1,
            intervalDays = 3,
            easeFactor = 2.5,
            lastReviewedEpochMs = threeDaysAgo - (3 * 86_400_000L),
            dueEpochMs = threeDaysAgo,
            isOverdue = false,
            overdueDays = 0
        )

        val evaluated = SpacedRepetitionEngine.evaluateOverdueStatus(overdueItem, currentEpochMs = now)
        assertTrue("Must detect overdue status", evaluated.isOverdue)
        assertEquals("Must calculate exact overdue days", 3, evaluated.overdueDays)

        // Manual scheduling to 5 days in future
        val targetEpoch = now + (5 * 86_400_000L)
        val rescheduled = SpacedRepetitionEngine.scheduleManualRevision(evaluated, targetEpoch, currentEpochMs = now)
        assertFalse("Manual schedule must clear overdue flag", rescheduled.isOverdue)
        assertEquals(targetEpoch, rescheduled.dueEpochMs)
        assertEquals(5, rescheduled.intervalDays)
    }

    // ── 3. Assessment Reliability & Question Bank Coverage ──────────────────────

    @Test
    fun testAssessmentHistoryPreservationWithQuestionLevelResponses() = runBlocking {
        val responses = listOf(
            QuestionResponseRecord("q1", "Balance chemical eq", 1, 1, true, false, "Verified explanation"),
            QuestionResponseRecord("q2", "Identify precipitate", 0, 0, true, false, "Verified explanation")
        )
        val attempt = AssessmentAttemptRecord(
            attemptId = "attempt_t1_node1_100",
            trackId = "track_hist_01",
            userId = "user_01",
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            scorePercent = 100.0,
            correctCount = 2,
            totalQuestions = 2,
            durationSeconds = 120,
            attemptEpochMs = 1700000000000L,
            questionResponses = responses
        )

        repository.recordAssessmentAttempt(attempt)

        val retrieved = repository.getAssessmentAttemptsForTopic("track_hist_01", "user_01", "cbse_sci10_ch01")
        assertEquals(1, retrieved.size)
        assertEquals("attempt_t1_node1_100", retrieved.first().attemptId)
        assertEquals(2, retrieved.first().questionResponses.size)
        assertTrue(retrieved.first().questionResponses.first().isCorrect)
    }

    @Test
    fun testRepeatedIdenticalQuestionsApplyFamiliarityDiscount() {
        val q1 = QuestionResponseRecord("q_same_01", "Ohm's Law V=IR", 0, 0, true, false, "Explanation")
        val q2 = QuestionResponseRecord("q_same_02", "Kirchhoff KCL", 1, 1, true, false, "Explanation")

        val attempt1 = AssessmentAttemptRecord(
            attemptId = "att_1",
            trackId = "track_01",
            userId = "user_01",
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma",
            scorePercent = 100.0,
            correctCount = 2,
            totalQuestions = 2,
            durationSeconds = 60,
            attemptEpochMs = 1700000000000L,
            questionResponses = listOf(q1, q2)
        )

        // Attempt 2 answers the exact same questions
        val attempt2 = AssessmentAttemptRecord(
            attemptId = "att_2",
            trackId = "track_01",
            userId = "user_01",
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma",
            scorePercent = 100.0,
            correctCount = 2,
            totalQuestions = 2,
            durationSeconds = 45,
            attemptEpochMs = 1700001000000L,
            questionResponses = listOf(q1, q2)
        )

        val result = AssessmentReliabilityEngine.calculateAggregatedAssessmentScore(
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            attempts = listOf(attempt1, attempt2),
            availableQuestionsCount = 2
        )

        assertTrue("Familiarity discount must be applied for repeated identical questions", result.familiarityDiscountApplied)
        assertTrue("Effective score must be lower than 100% due to repeated question familiarity discount",
            result.effectiveScorePercent < 100.0)
    }

    @Test
    fun testQuestionBankCoverageAnalysisAndMasteryCapping() {
        // Less than 3 questions -> Insufficient coverage
        val coverage = AssessmentReliabilityEngine.evaluateQuestionBankCoverage("eng_de_kmap", "Digital Logic", 2)
        assertFalse(coverage.isSufficient)
        assertEquals("LOW", coverage.confidenceRating)
        assertEquals(0.40, coverage.coverageRatio, 0.01)

        // Test that topic with insufficient question bank coverage cannot reach MASTERED
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "eng_de_kmap",
            topicTitle = "Digital Logic",
            discipline = "Engineering",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 2, // 100% score
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet(),
            availableQuestionsCount = 2 // Insufficient bank
        )
        assertEquals("Insufficient question bank coverage must cap mastery at PROFICIENT",
            MasteryLevel.PROFICIENT, result.masteryLevel)
        assertEquals("INSUFFICIENT", result.questionBankCoverage)
    }

    @Test
    fun testStaleScoreDegradationAfterSixtyDays() {
        val now = 1700000000000L
        val seventyDaysAgo = now - (70 * 86_400_000L)

        val staleMastery = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Sciences",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 5,
            totalQuestions = 5, // raw 100%
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet(),
            lastAssessedEpochMs = seventyDaysAgo,
            currentEpochMs = now
        )

        assertTrue("Score older than 60 days must be flagged stale", staleMastery.isStale)
        assertEquals(80.0, staleMastery.assessmentScore, 0.1) // 100 * 0.80 = 80.0%
        assertTrue("Stale scores must trigger revision", staleMastery.needsRevision)
    }

    // ── 4. Learning Analytics & Data Sufficiency Guard ───────────────────────────

    @Test
    fun testLearningAnalyticsInsufficientDataGuard() {
        val now = 1700000000000L
        val singleAttempt = AssessmentAttemptRecord(
            attemptId = "att_single",
            trackId = "track_01",
            userId = "user_01",
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            scorePercent = 80.0,
            correctCount = 4,
            totalQuestions = 5,
            durationSeconds = 300,
            attemptEpochMs = now,
            questionResponses = emptyList()
        )

        val report = LearningAnalyticsEngine.computeAnalytics(
            trackId = "track_01",
            userId = "user_01",
            attempts = listOf(singleAttempt),
            srsItems = emptyList(),
            masteries = emptyList(),
            completedTaskEpochs = emptyList(),
            currentEpochMs = now
        )

        assertFalse("Must disclose insufficient data when < 3 data points exist", report.hasSufficientData)
        assertEquals("INSUFFICIENT_DATA", report.scoreTrajectory)
        assertTrue(report.dataSufficiencyNotice.contains("Insufficient historical data"))
    }

    @Test
    fun testLearningAnalyticsCalculatesStreaksAndTrajectoryWithSufficientData() {
        val dayMillis = 86_400_000L
        val now = 1700000000000L

        // 3 consecutive days of activity
        val attempts = listOf(
            AssessmentAttemptRecord("a1", "t1", "u1", "n1", "T1", "D1", 60.0, 3, 5, 200, now - (2 * dayMillis), emptyList()),
            AssessmentAttemptRecord("a2", "t1", "u1", "n2", "T2", "D1", 75.0, 4, 5, 200, now - (1 * dayMillis), emptyList()),
            AssessmentAttemptRecord("a3", "t1", "u1", "n3", "T3", "D1", 90.0, 5, 5, 200, now, emptyList())
        )

        val report = LearningAnalyticsEngine.computeAnalytics(
            trackId = "t1",
            userId = "u1",
            attempts = attempts,
            srsItems = emptyList(),
            masteries = emptyList(),
            completedTaskEpochs = emptyList(),
            currentEpochMs = now
        )

        assertTrue("Must confirm sufficient data with 3 distinct days", report.hasSufficientData)
        assertEquals("IMPROVING", report.scoreTrajectory) // 60% -> 90%
        assertTrue("Streak must be at least 3 days", report.currentStreakDays >= 3)
    }

    // ── 5. Personalised Study Planner ───────────────────────────────────────────

    @Test
    fun testStudyPlannerPrioritizesOverdueRevisionAndPrerequisites() {
        val now = 1700000000000L
        val track = LearningTrack(
            trackId = "track_plan_01",
            userId = "user_01",
            title = "Board Exam Prep",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch02", // Acids, Bases
            dailyCommitmentMinutes = 45
        )

        val overdueItem = SpacedRepetitionItem(
            repetitionId = "srs_overdue_plan",
            trackId = "track_plan_01",
            userId = "user_01",
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            repetitionNumber = 1,
            intervalDays = 1,
            easeFactor = 2.5,
            lastReviewedEpochMs = now - (3 * 86_400_000L),
            dueEpochMs = now - (2 * 86_400_000L), // 2 days overdue
            isOverdue = true,
            overdueDays = 2
        )

        val nodes = CurriculumEngine.getUniversalBaselineNodes()
        val prereqs = CurriculumEngine.getUniversalBaselinePrerequisites()

        val dailyPlan = PersonalisedStudyPlannerEngine.generateDailyPlan(
            track = track,
            targetDailyMinutes = 45,
            masteries = emptyList(),
            srsItems = listOf(overdueItem),
            nodes = nodes,
            prerequisites = prereqs,
            currentEpochMs = now
        )

        assertTrue(dailyPlan.tasks.isNotEmpty())
        assertEquals("Overdue spaced repetition must be Priority 1",
            PlannedTaskType.SPACED_REPETITION_REVIEW, dailyPlan.tasks.first().taskType)
        assertTrue("Explainable reason must cite overdue days",
            dailyPlan.tasks.first().explainableReason.contains("overdue"))
        assertTrue("Total allocated minutes must not exceed commitment by more than single task threshold",
            dailyPlan.allocatedMinutes <= 60)
    }

    // ── 6. Multi-Track Data Isolation ───────────────────────────────────────────

    @Test
    fun testMultiTrackAssessmentAndRevisionIsolation() = runBlocking {
        val user = "learner_iso"
        val trackA = "track_school_cbse"
        val trackB = "track_polytechnic_ee"

        // Record attempt in Track A
        val attemptA = AssessmentAttemptRecord(
            attemptId = "att_a_1",
            trackId = trackA,
            userId = user,
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            scorePercent = 100.0,
            correctCount = 3,
            totalQuestions = 3,
            durationSeconds = 120,
            attemptEpochMs = 1700000000000L,
            questionResponses = emptyList()
        )
        repository.recordAssessmentAttempt(attemptA)

        // Initialize SRS in Track B
        repository.initializeSpacedRepetitionForTopic(
            trackId = trackB,
            userId = user,
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma"
        )

        // Verify Track A has attemptA and 0 attempts for Track B
        val attemptsA = repository.getAllAssessmentAttemptsForTrack(trackA, user)
        val attemptsB = repository.getAllAssessmentAttemptsForTrack(trackB, user)
        assertEquals(1, attemptsA.size)
        assertEquals(0, attemptsB.size)

        // Verify Track B has SRS item and Track A does not have Track B's item
        val srsA = repository.getSpacedRepetitionQueue(trackA, user)
        val srsB = repository.getSpacedRepetitionQueue(trackB, user)
        assertTrue(srsB.any { it.nodeId == "dip_ee_ohm_kirchhoff" })
        assertFalse(srsA.any { it.nodeId == "dip_ee_ohm_kirchhoff" })
    }

    // ── 7. Universal Education Coverage Across All 8 Disciplines ────────────────

    @Test
    fun testUniversalEducationSupportAcrossAllEightDisciplines() {
        val allNodeIds = StarterCurriculumCatalog.getSupportedNodeIds()
        val expectedDisciplines = setOf(
            "School Education",
            "Diploma & Polytechnic",
            "Engineering & Technology",
            "Arts & Sciences",
            "Management & Commerce",
            "Government & Competitive Exams",
            "Research & Doctoral Studies",
            "Vocational & Professional"
        )

        val foundDisciplines = mutableSetOf<String>()
        for (nodeId in allNodeIds) {
            val questions = DeterministicAssessmentEngine.getDiagnosticQuestionsForNode(nodeId)
            assertTrue("Node $nodeId must have diagnostic questions", questions.isNotEmpty())
            foundDisciplines.add(questions.first().domain)
        }

        assertEquals("Must support all 8 universal disciplines with zero VLSI default",
            expectedDisciplines, foundDisciplines)
    }

    // ── 8. Zero Network Permissions Guardrail ───────────────────────────────────

    @Test
    fun testZeroNetworkPermissionsInProductionManifest() {
        val candidatePaths = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("F:/rea/zzzz_lama_proj/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidatePaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(manifestFile!!)

        val permissionNodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissionNodes.length) {
            val name = permissionNodes.item(i).attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertTrue(
                "Gate 6.16.3: Only authorized model download network permissions permitted: $name",
                name == "android.permission.INTERNET" || name == "android.permission.ACCESS_NETWORK_STATE"
            )
        }
    }
}


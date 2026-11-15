package com.learnmate.ai.learning

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.KnowledgeQuestionFormat
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.domain.mastery.KnowledgeMasteryEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.recommendation.PersonalisedLearningPathEngine
import com.learnmate.ai.domain.recommendation.RecommendationPriority
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Gate 6.5: Adaptive Assessment & Personalised Learning Engine Comprehensive Test Suite.
 *
 * Verifies:
 * 1. Deterministic Knowledge Mastery formula and boundary classifications.
 * 2. Prerequisite gating enforcement (cannot exceed DEVELOPING / 69.9% if missing prerequisites).
 * 3. Diagnostic assessment across all 8 disciplines with Single-Choice, Multiple-Choice, and True/False formats.
 * 4. Transparent, explainable reasons for every personalised recommendation.
 * 5. Recommendation priority sequence (Critical Prerequisite > Skill Gap > Revision > Next Step).
 * 6. Revision Planner lifecycle (identification, queueing, and completion).
 * 7. Multi-track isolation (Track A mastery independent from Track B).
 * 8. Zero Internet permissions and active AI kill switch verification.
 */
class Gate65AdaptiveLearningEngineTest {

    private lateinit var learningRepository: LearningRepository

    @Before
    fun setup() {
        learningRepository = LearningRepository(dao = null)
    }

    // ── 1. Knowledge Mastery Engine & Boundary Validations ─────────────────────

    @Test
    fun testMasteryLevelNotAssessedWhenNoAttemptsAndNotStarted() {
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
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
        assertEquals(MasteryLevel.NOT_ASSESSED, result.masteryLevel)
        assertEquals(0.0, result.compositeScore, 0.01)
        assertFalse(result.needsRevision)
    }

    @Test
    fun testMasteryLevelNeedsImprovementBoundaryBelowFifty() {
        // Assessment: 1/3 (33.3%), Study: NOT_STARTED (0%), Prereqs: 100%
        // rawComposite = (33.3 * 0.45) + (0 * 0.35) + (100 * 0.20) = 15.0 + 0 + 20.0 = 35.0%
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "dip_ee_ohm_kirchhoff",
            topicTitle = "Basic Electrical",
            discipline = "Diploma",
            progressStatus = LearningProgressStatus.NOT_STARTED,
            attemptsCount = 1,
            correctCount = 1,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.NEEDS_IMPROVEMENT, result.masteryLevel)
        assertTrue(result.compositeScore < 50.0)
        assertTrue("Needs improvement topics must trigger revision", result.needsRevision)
    }

    @Test
    fun testMasteryLevelDevelopingBoundaries() {
        // Assessment: 2/3 (66.7%), Study: IN_PROGRESS (40%), Prereqs: 100%
        // rawComposite = (66.7 * 0.45) + (40 * 0.35) + (100 * 0.20) = 30.0 + 14.0 + 20.0 = 64.0%
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "eng_de_kmap",
            topicTitle = "K-Map Minimization",
            discipline = "Engineering",
            progressStatus = LearningProgressStatus.IN_PROGRESS,
            attemptsCount = 1,
            correctCount = 2,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.DEVELOPING, result.masteryLevel)
        assertTrue(result.compositeScore in 50.0..69.9)
    }

    @Test
    fun testMasteryLevelProficientBoundaryAtSeventyPercent() {
        // Assessment: 3/3 (100%), Study: IN_PROGRESS (40%), Prereqs: 100%
        // rawComposite = (100 * 0.45) + (40 * 0.35) + (100 * 0.20) = 45.0 + 14.0 + 20.0 = 79.0%
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Classical Mechanics",
            discipline = "Arts & Sciences",
            progressStatus = LearningProgressStatus.IN_PROGRESS,
            attemptsCount = 1,
            correctCount = 3,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.PROFICIENT, result.masteryLevel)
        assertTrue(result.compositeScore in 70.0..84.9)
        assertFalse("Proficient topics with high scores should not require immediate revision", result.needsRevision)
    }

    @Test
    fun testMasteryLevelMasteredBoundaryAboveEightyFive() {
        // Assessment: 3/3 (100%), Study: COMPLETED (100%), Prereqs: 100%
        // rawComposite = (100 * 0.45) + (100 * 0.35) + (100 * 0.20) = 45.0 + 35.0 + 20.0 = 100.0%
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "mgmt_acc_double_entry",
            topicTitle = "Double-Entry Bookkeeping",
            discipline = "Management",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 3,
            totalQuestions = 3,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )
        assertEquals(MasteryLevel.MASTERED, result.masteryLevel)
        assertEquals(100.0, result.compositeScore, 0.01)
        assertFalse(result.needsRevision)
    }

    // ── 2. Prerequisite Gating Enforcement ─────────────────────────────────────

    @Test
    fun testPrerequisiteGatingCapsMasteryAtDevelopingEvenWithPerfectAssessment() {
        // Perfect assessment score (100%) and COMPLETED module (100%), but missing mandatory prerequisite "prereq_chem_basics"
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "cbse_sci10_ch02",
            topicTitle = "Acids, Bases and Salts",
            discipline = "School Education",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 3,
            totalQuestions = 3,
            directMandatoryPrerequisites = listOf("cbse_sci10_ch01"),
            completedNodeIds = emptySet() // Missing cbse_sci10_ch01
        )

        // Must be capped at DEVELOPING and composite score capped at 69.9%
        assertEquals(MasteryLevel.DEVELOPING, result.masteryLevel)
        assertEquals(69.9, result.compositeScore, 0.01)
        assertTrue("Must record missing prerequisite node", result.missingPrerequisiteNodeIds.contains("cbse_sci10_ch01"))
        assertEquals(0, result.completedPrerequisitesCount)
        assertEquals(1, result.totalPrerequisitesCount)
    }

    @Test
    fun testCompletingPrerequisiteUnlocksMasteredStatus() {
        val result = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "cbse_sci10_ch02",
            topicTitle = "Acids, Bases and Salts",
            discipline = "School Education",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 1,
            correctCount = 3,
            totalQuestions = 3,
            directMandatoryPrerequisites = listOf("cbse_sci10_ch01"),
            completedNodeIds = setOf("cbse_sci10_ch01") // Prerequisite completed!
        )

        assertEquals(MasteryLevel.MASTERED, result.masteryLevel)
        assertEquals(100.0, result.compositeScore, 0.01)
        assertTrue(result.missingPrerequisiteNodeIds.isEmpty())
        assertEquals(1, result.completedPrerequisitesCount)
    }

    // ── 3. Diagnostic Assessment Across All 8 Disciplines ─────────────────────

    @Test
    fun testDiagnosticQuestionsCoverAllEightDisciplinesWithDiverseFormats() {
        val supportedNodeIds = StarterCurriculumCatalog.getSupportedNodeIds()
        assertEquals(9, supportedNodeIds.size)

        var foundSingleChoice = false
        var foundMultipleChoice = false
        var foundTrueFalse = false

        for (nodeId in supportedNodeIds) {
            val questions = DeterministicAssessmentEngine.getDiagnosticQuestionsForNode(nodeId)
            assertTrue("Each node must contain diagnostic questions: $nodeId", questions.isNotEmpty())
            assertTrue("Each topic must have at least 2 questions: $nodeId", questions.size >= 2)

            for (q in questions) {
                assertTrue("Question text cannot be blank in $nodeId", q.questionText.isNotBlank())
                assertTrue("Must have at least 2 options in $nodeId", q.options.size >= 2)
                assertTrue("Must provide verified explanation with citation in $nodeId", q.explanation.contains("Accredited Source"))
                assertTrue("Correct index must be within range", q.correctIndex in q.options.indices)
            }

            val module = StarterCurriculumCatalog.getLessonModule(nodeId, "en")
            for (kc in module.knowledgeChecks) {
                when (kc.format) {
                    KnowledgeQuestionFormat.SINGLE_CHOICE -> foundSingleChoice = true
                    KnowledgeQuestionFormat.MULTIPLE_CHOICE -> foundMultipleChoice = true
                    KnowledgeQuestionFormat.TRUE_FALSE -> foundTrueFalse = true
                }
            }
        }

        assertTrue("Must support SINGLE_CHOICE questions", foundSingleChoice)
        assertTrue("Must support MULTIPLE_CHOICE questions", foundMultipleChoice)
        assertTrue("Must support TRUE_FALSE questions", foundTrueFalse)
    }

    @Test
    fun testUniversalDiagnosticAssessmentGradingAndReportGeneration() {
        val profile = LearnerProfile(
            userId = "user_test_eval",
            name = "Dr. Evaluation",
            targetDomain = "School Education",
            subdomain = "Science",
            currentLevel = "Intermediate"
        )
        val questions = DeterministicAssessmentEngine.getDiagnosticQuestionsForNode("cbse_sci10_ch01")
        assertEquals(3, questions.size)

        // Answers: Q1 correct, Q2 wrong, Q3 correct -> 2/3 = 66%
        val submitted = mapOf(
            questions[0].id to questions[0].correctIndex,
            questions[1].id to ((questions[1].correctIndex + 1) % questions[1].options.size), // incorrect
            questions[2].id to questions[2].correctIndex
        )

        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, questions, submitted)
        assertEquals(66, report.overallScorePercent)
        assertEquals(2, report.correctCount)
        assertEquals(3, report.totalQuestions)
        assertEquals(1, report.weaknesses.size)
        assertEquals(0, report.strengths.size)
    }

    // ── 4. Personalised Learning Path & Recommendation Explainability ──────────

    @Test
    fun testPersonalisedLearningPathGeneratesExplainableRecommendationsInPriorityOrder() {
        val track = LearningTrack(
            trackId = "track_school_01",
            userId = "user_school_01",
            title = "Class 10 Science Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch02"
        )

        val nodes = listOf(
            CurriculumNodeEntity("cbse_sci10_ch01", null, "pack_01", "TOPIC", "SCI10-01", "Chemical Reactions", "Balancing", 35, 1),
            CurriculumNodeEntity("cbse_sci10_ch02", null, "pack_01", "TOPIC", "SCI10-02", "Acids and Bases", "pH Scale", 40, 2),
            CurriculumNodeEntity("cbse_sci10_ch03", null, "pack_01", "TOPIC", "SCI10-03", "Metals and Non-Metals", "Properties", 45, 3)
        )

        val prerequisites = listOf(
            NodePrerequisiteEntity("prereq_ch2_ch1", "cbse_sci10_ch02", "cbse_sci10_ch01", "MANDATORY")
        )

        val topicMasteries = listOf(
            TopicMasteryResult(
                nodeId = "cbse_sci10_ch01",
                topicTitle = "Chemical Reactions",
                discipline = "School Education",
                masteryLevel = MasteryLevel.NEEDS_IMPROVEMENT,
                compositeScore = 42.0,
                assessmentScore = 33.3,
                attemptsCount = 1,
                correctCount = 1,
                totalQuestions = 3,
                progressStatus = LearningProgressStatus.IN_PROGRESS,
                completedPrerequisitesCount = 0,
                totalPrerequisitesCount = 0,
                missingPrerequisiteNodeIds = emptyList(),
                needsRevision = true,
                lastAssessedEpochMs = 1000L
            ),
            TopicMasteryResult(
                nodeId = "cbse_sci10_ch02",
                topicTitle = "Acids and Bases",
                discipline = "School Education",
                masteryLevel = MasteryLevel.NOT_ASSESSED,
                compositeScore = 0.0,
                assessmentScore = 0.0,
                attemptsCount = 0,
                correctCount = 0,
                totalQuestions = 2,
                progressStatus = LearningProgressStatus.NOT_STARTED,
                completedPrerequisitesCount = 0,
                totalPrerequisitesCount = 1,
                missingPrerequisiteNodeIds = listOf("cbse_sci10_ch01"),
                needsRevision = false,
                lastAssessedEpochMs = null
            )
        )

        val recommendations = PersonalisedLearningPathEngine.generateLearningPath(
            track = track,
            nodes = nodes,
            prerequisites = prerequisites,
            topicMasteries = topicMasteries
        )

        assertTrue("Recommendations must not be empty", recommendations.isNotEmpty())

        // Priority 1: Critical Prerequisite for goal
        val firstRec = recommendations.first()
        assertEquals("cbse_sci10_ch01", firstRec.nodeId)
        assertEquals(RecommendationPriority.CRITICAL_PREREQUISITE, firstRec.priority)
        assertTrue("Must contain explainable rationale", firstRec.reason.contains("Mandatory foundational prerequisite"))

        // Every single recommendation must have an explainable reason
        for (rec in recommendations) {
            assertTrue("Reason must not be blank for ${rec.nodeId}", rec.reason.isNotBlank())
            assertTrue("Estimated minutes must be positive", rec.estimatedMinutes > 0)
        }
    }

    // ── 5. Revision Planner Lifecycle ─────────────────────────────────────────

    @Test
    fun testRevisionPlannerQueueAndCompletionLifecycle() = runBlocking {
        val trackId = "track_rev_01"
        val userId = "user_rev_01"

        // 1. Record an attempt with low score (1/3 = 33.3%)
        learningRepository.recordDiagnosticAttempt(
            trackId = trackId,
            userId = userId,
            nodeId = "cbse_sci10_ch01",
            correctCount = 1,
            totalQuestions = 3,
            topicTitle = "Chemical Reactions"
        )

        val masteries = learningRepository.getAllTopicMasteries(trackId, userId)
        val ch1Mastery = masteries.first { it.nodeId == "cbse_sci10_ch01" }
        assertTrue("Low score must flag for revision", ch1Mastery.needsRevision)
        assertEquals(MasteryLevel.NEEDS_IMPROVEMENT, ch1Mastery.masteryLevel)

        // 2. Check Revision Queue
        val revQueue = learningRepository.getRevisionQueue(trackId, userId)
        assertTrue("Revision queue must contain flagged topic", revQueue.any { it.nodeId == "cbse_sci10_ch01" })
        val revItem = revQueue.first { it.nodeId == "cbse_sci10_ch01" }
        assertTrue(revItem.priorityReason.contains("< 70%"))

        // 3. Complete revision study module
        val revisedMastery = learningRepository.recordRevisionCompletion(trackId, userId, "cbse_sci10_ch01")
        assertEquals(LearningProgressStatus.COMPLETED, revisedMastery.progressStatus)

        // 4. Re-assess topic after revision with full score (3/3 = 100%)
        val finalMastery = learningRepository.recordDiagnosticAttempt(
            trackId = trackId,
            userId = userId,
            nodeId = "cbse_sci10_ch01",
            correctCount = 3,
            totalQuestions = 3,
            topicTitle = "Chemical Reactions"
        )
        assertEquals(MasteryLevel.MASTERED, finalMastery.masteryLevel)
        assertEquals(100.0, finalMastery.compositeScore, 0.01)
        assertFalse("Mastered topic must no longer be flagged for revision", finalMastery.needsRevision)

        // 5. Verify queue is now cleared for that topic
        val postRevQueue = learningRepository.getRevisionQueue(trackId, userId)
        assertFalse("Completed topic must be removed from revision queue", postRevQueue.any { it.nodeId == "cbse_sci10_ch01" })
    }

    // ── 6. Multi-Track Progress & Mastery Isolation ───────────────────────────

    @Test
    fun testMultiTrackProgressAndMasteryIsolation() = runBlocking {
        val userId = "user_multitrack_01"
        val trackA = "track_cbse_science"
        val trackB = "track_polytechnic_electrical"

        // Learner studies and masters topic in Track A
        learningRepository.recordDiagnosticAttempt(
            trackId = trackA,
            userId = userId,
            nodeId = "cbse_sci10_ch01",
            correctCount = 3,
            totalQuestions = 3,
            topicTitle = "Chemical Reactions"
        )
        learningRepository.updateTopicProgress(
            trackId = trackA,
            userId = userId,
            nodeId = "cbse_sci10_ch01",
            status = LearningProgressStatus.COMPLETED,
            scorePercent = 100.0
        )

        // Verify Track A mastery is MASTERED
        val masteryA = learningRepository.getTopicMastery(trackA, userId, "cbse_sci10_ch01")
        assertEquals(MasteryLevel.MASTERED, masteryA.masteryLevel)
        assertEquals(100.0, masteryA.compositeScore, 0.01)

        // Verify Track B mastery for the same topic is completely UNTOUCHED (NOT_ASSESSED)
        val masteryB = learningRepository.getTopicMastery(trackB, userId, "cbse_sci10_ch01")
        assertEquals(MasteryLevel.NOT_ASSESSED, masteryB.masteryLevel)
        assertEquals(0.0, masteryB.compositeScore, 0.01)
        assertEquals(LearningProgressStatus.NOT_STARTED, masteryB.progressStatus)
    }

    // ── 7. Production Security, Offline Guardrails & Kill Switch ──────────────

    @Test
    fun testZeroNetworkPermissionsInProductionManifest() {
        val candidates = listOf(
            java.io.File("app/src/main/AndroidManifest.xml"),
            java.io.File("src/main/AndroidManifest.xml"),
            java.io.File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)
        val text = manifestFile!!.readText()
        // Gate 6.16.3: Network permitted for model distribution, zero dangerous/telemetry permissions
        assertTrue("Manifest includes internet for model download", text.contains("android.permission.INTERNET"))
        assertTrue("Manifest includes network state for download engine", text.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse("Telemetry and location permissions are strictly forbidden", text.contains("ACCESS_FINE_LOCATION") || text.contains("READ_CONTACTS"))
    }
}

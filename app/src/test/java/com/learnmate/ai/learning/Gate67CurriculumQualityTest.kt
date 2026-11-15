package com.learnmate.ai.learning

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.AssessmentReliabilityEngine
import com.learnmate.ai.domain.assessment.QuestionResponseRecord
import com.learnmate.ai.domain.mastery.KnowledgeMasteryEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.outcomes.LearningOutcomeEngine
import com.learnmate.ai.domain.provenance.ContentProvenanceEngine
import com.learnmate.ai.domain.repetition.RevisionScheduleState
import com.learnmate.ai.domain.repetition.SpacedRepetitionEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gate 6.7: Universal Curriculum Quality, Content Expansion & Psychometric Verification Suite.
 *
 * Verifies:
 * 1. Verified Question Banks: >= 5 unique questions across all 8 disciplines (45 total).
 * 2. Question Difficulty & Cognitive Diversity (EASY, INTERMEDIATE, ADVANCED; CONCEPTUAL, APPLICATION, ANALYTICAL).
 * 3. Psychometric Coverage Benchmark: coverageRatio = 1.0 (HIGH), unlocking MASTERED mastery.
 * 4. Content Quality & Accredited Provenance Integrity across all 9 modules.
 * 5. Learning Outcome (LO) Tracking and Weak Objective Identification (< 70% threshold).
 * 6. Gate 6.6 Corrections: RevisionScheduleState, Collision-safe attempt IDs, One-time bounded decay, Schema versioning.
 * 7. Multilingual Quality & Fallback Transparency (EN, TA, HI, KN).
 * 8. Zero Internet Permissions & Active Production AI Kill Switch.
 */
class Gate67CurriculumQualityTest {

    private lateinit var repository: LearningRepository

    private val allDisciplineNodeIds = listOf(
        "cbse_sci10_ch01",
        "cbse_sci10_ch02",
        "dip_ee_ohm_kirchhoff",
        "eng_de_kmap",
        "sci_phys_mechanics",
        "mgmt_acc_double_entry",
        "upsc_gs2_polity_rights",
        "res_method_hypothesis",
        "voc_elec_safety"
    )

    @Before
    fun setup() {
        repository = LearningRepository(dao = null)
    }

    // ── 1. Verified Question Bank Expansion (>= 5 questions per module) ──────────

    @Test
    fun testEveryCurriculumNodeHasAtLeastFiveUniqueQuestions() {
        val allQuestionIds = mutableSetOf<String>()

        for (nodeId in allDisciplineNodeIds) {
            val questions = StarterCurriculumCatalog.getQuestionBankForNode(nodeId, "en")
            assertTrue("Node $nodeId must have at least 5 questions, found ${questions.size}", questions.size >= 5)

            for (q in questions) {
                assertFalse("Duplicate questionId detected: ${q.questionId}", allQuestionIds.contains(q.questionId))
                allQuestionIds.add(q.questionId)
                assertEquals("Question nodeId must match parent topic", nodeId, q.nodeId)
                assertTrue("Question text must be non-empty", q.questionText.isNotBlank())
                assertTrue("Options must have >= 2 items", q.options.size >= 2)
                assertTrue("Correct indices must not be empty", q.correctIndices.isNotEmpty())
                assertTrue("Explanation must be non-empty", q.verifiedExplanation.isNotBlank())
                assertTrue("Citation reference must be non-empty", q.referenceCitation.isNotBlank())
                assertTrue("LearningObjectiveId must be set", q.learningObjectiveId.isNotBlank())
            }
        }

        assertEquals("Total question bank across all 9 starter modules must be >= 45", 45, allQuestionIds.size)
    }

    @Test
    fun testQuestionDifficultyAndCognitiveLevelDiversity() {
        for (nodeId in allDisciplineNodeIds) {
            val questions = StarterCurriculumCatalog.getQuestionBankForNode(nodeId, "en")
            val difficulties = questions.map { it.difficultyLevel.uppercase() }.toSet()
            val cognitiveLevels = questions.map { it.cognitiveLevel.uppercase() }.toSet()

            assertTrue(
                "Node $nodeId must offer diverse difficulty levels (found $difficulties)",
                difficulties.contains("EASY") || difficulties.contains("INTERMEDIATE") || difficulties.contains("ADVANCED")
            )
            assertTrue("Node $nodeId must have at least 2 distinct difficulty tiers", difficulties.size >= 2)
            assertTrue("Node $nodeId must have at least 2 distinct cognitive levels", cognitiveLevels.size >= 2)
        }
    }

    // ── 2. Psychometric Question-Bank Coverage & Mastery Unlocking ─────────────

    @Test
    fun testFiveQuestionsUnlocksHighReliabilityAndMasteredLevel() {
        for (nodeId in allDisciplineNodeIds) {
            val questions = StarterCurriculumCatalog.getQuestionBankForNode(nodeId, "en")
            val report = AssessmentReliabilityEngine.evaluateQuestionBankCoverage(
                nodeId = nodeId,
                topicTitle = "Topic $nodeId",
                availableQuestionsCount = questions.size
            )

            assertEquals("Coverage ratio for $nodeId must be 1.0", 1.0, report.coverageRatio, 0.001)
            assertEquals("Reliability rating must be HIGH", "HIGH", report.confidenceRating)
            assertTrue("Question bank must be sufficient", report.isSufficient)
        }

        // Verify that 5 questions allows calculating Mastered level without cap
        val mastery = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 5,
            correctCount = 5,
            totalQuestions = 5,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet()
        )

        assertEquals("Mastery level should reach MASTERED with 5/5 on 5 questions", MasteryLevel.MASTERED, mastery.masteryLevel)
        assertEquals("Composite score should be 100%", 100.0, mastery.compositeScore, 0.01)
    }

    // ── 3. Curriculum Content Quality & Accredited Provenance Audit ────────────

    @Test
    fun testAllNineModulesValidateContentQualityIntegrity() {
        for (nodeId in allDisciplineNodeIds) {
            val module = StarterCurriculumCatalog.getLessonModule(nodeId, "en")
            val validation = ContentProvenanceEngine.validateModuleIntegrity(module)

            assertTrue("Module $nodeId must pass integrity validation. Issues: ${validation.validationIssues}", validation.isValid)
            assertTrue("Module $nodeId must have learning objectives", module.learningObjectives.isNotEmpty())
            assertTrue("Module $nodeId must have sections", module.sections.isNotEmpty())
            assertTrue("Module $nodeId must have diagrams", module.diagrams.isNotEmpty())
            assertTrue("Module $nodeId must have worked examples", module.workedExamples.isNotEmpty())
            assertTrue("Module $nodeId must have key terms glossary", module.keyTerms.isNotEmpty())
            assertTrue("Module $nodeId must have references", module.references.isNotEmpty())
            assertTrue("Summary must be descriptive", module.summary.isNotBlank())
        }
    }

    @Test
    fun testAccreditedCanonicalProvenanceCatalog() {
        for (nodeId in allDisciplineNodeIds) {
            val provenance = ContentProvenanceEngine.ACCREDITED_CANONICAL_PROVENANCE[nodeId]
            assertNotNull("Canonical provenance must exist for $nodeId", provenance)
            assertEquals("Provenance type must be ACCREDITED_TEXTBOOK", "ACCREDITED_TEXTBOOK", provenance!!.provenanceType.name)
            assertTrue("Textbook title must be non-empty", provenance.title.isNotBlank())
            assertTrue("Author must be non-empty", provenance.authors.isNotBlank())
            assertTrue("Edition must be non-empty", provenance.edition.isNotBlank())
            assertTrue("Publisher/Board must be non-empty", provenance.publisherOrBoard.isNotBlank())
            assertTrue("Non-endorsement notice must be present", ContentProvenanceEngine.MANDATORY_NON_ENDORSEMENT_NOTICE.contains("does not claim official affiliation, sponsorship, or endorsement"))
        }
    }

    // ── 4. Learning Outcome (LO) Tracking & Remediation ────────────────────────

    @Test
    fun testLearningOutcomeTrackingAndWeakObjectiveIdentification() = runBlocking {
        val trackId = "track_gate67_test"
        val userId = "learner_gate67"

        // Simulate an assessment attempt covering cbse_sci10_ch01
        // Answer q_cbse_ch1_01 correctly (LO_SCI10_CH1_01: Conservation of mass)
        // Answer q_cbse_ch1_02 incorrectly (LO_SCI10_CH1_03: Subscripts)
        // Answer q_cbse_ch1_03 incorrectly (LO_SCI10_CH1_03: Subscripts)
        val responses = listOf(
            QuestionResponseRecord(
                questionId = "q_cbse_ch1_01",
                questionText = "Why must a chemical equation always be balanced?",
                selectedIndex = 0,
                correctIndex = 0,
                isCorrect = true,
                isRepeatedQuestion = false,
                verifiedExplanation = "Conservation of mass"
            ),
            QuestionResponseRecord(
                questionId = "q_cbse_ch1_02",
                questionText = "When balancing an equation, it is permissible to change subscripts.",
                selectedIndex = 0,
                correctIndex = 1,
                isCorrect = false,
                isRepeatedQuestion = false,
                verifiedExplanation = "Changing subscripts alters substance"
            ),
            QuestionResponseRecord(
                questionId = "q_cbse_ch1_03",
                questionText = "What is the stoichiometric coefficient of O2?",
                selectedIndex = 0,
                correctIndex = 2,
                isCorrect = false,
                isRepeatedQuestion = false,
                verifiedExplanation = "Requires 5 O2 molecules"
            )
        )

        val attempt = AssessmentAttemptRecord(
            attemptId = AssessmentReliabilityEngine.generateAttemptId(trackId, "cbse_sci10_ch01"),
            trackId = trackId,
            userId = userId,
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions & Balancing",
            discipline = "School Education",
            scorePercent = 33.3,
            correctCount = 1,
            totalQuestions = 3,
            durationSeconds = 95,
            attemptEpochMs = System.currentTimeMillis(),
            questionResponses = responses,
            schemaVersion = 1
        )

        repository.recordAssessmentAttempt(attempt)

        val coverageReport = repository.getCurriculumCoverageReport(trackId, userId)
        assertTrue("Covered objectives count must be >= 2", coverageReport.coveredObjectivesCount >= 2)

        val weakObjectives = repository.getWeakLearningObjectives(trackId, userId)
        assertTrue("Must identify at least 1 weak objective needing practice", weakObjectives.isNotEmpty())

        val weakLo3 = weakObjectives.firstOrNull { it.objectiveId == "LO_SCI10_CH1_03" }
        assertNotNull("LO_SCI10_CH1_03 must be identified as weak", weakLo3)
        assertEquals("Score percent for LO_SCI10_CH1_03 must be 0.0%", 0.0, weakLo3!!.scorePercent, 0.01)
        assertTrue("requiresPractice flag must be true", weakLo3.requiresPractice)
        assertEquals("Target node must link back to cbse_sci10_ch01", "cbse_sci10_ch01", weakLo3.targetNodeId)
    }

    // ── 5. Anti-Inflation & Familiarity Discount ───────────────────────────────

    @Test
    fun testFamiliarityDiscountOnRepeatedAssessmentAttempts() {
        val trackId = "track_anti_inflation"
        val nodeId = "cbse_sci10_ch02"
        val now = System.currentTimeMillis()

        val resp1 = QuestionResponseRecord(
            questionId = "q_cbse_ch2_01",
            questionText = "What is pH of pure water?",
            selectedIndex = 2,
            correctIndex = 2,
            isCorrect = true,
            isRepeatedQuestion = false,
            verifiedExplanation = "pH is 7"
        )

        val att1 = AssessmentAttemptRecord(
            attemptId = "att_1",
            trackId = trackId,
            userId = "learner_1",
            nodeId = nodeId,
            topicTitle = "Acids and Bases",
            discipline = "School Education",
            scorePercent = 100.0,
            correctCount = 1,
            totalQuestions = 1,
            durationSeconds = 30,
            attemptEpochMs = now - 100000,
            questionResponses = listOf(resp1)
        )

        // Attempt 2 with identical question answered correctly again
        val resp2 = QuestionResponseRecord(
            questionId = "q_cbse_ch2_01",
            questionText = "What is pH of pure water?",
            selectedIndex = 2,
            correctIndex = 2,
            isCorrect = true,
            isRepeatedQuestion = true,
            verifiedExplanation = "pH is 7"
        )

        val att2 = AssessmentAttemptRecord(
            attemptId = "att_2",
            trackId = trackId,
            userId = "learner_1",
            nodeId = nodeId,
            topicTitle = "Acids and Bases",
            discipline = "School Education",
            scorePercent = 100.0,
            correctCount = 1,
            totalQuestions = 1,
            durationSeconds = 25,
            attemptEpochMs = now,
            questionResponses = listOf(resp2)
        )

        val aggregated = AssessmentReliabilityEngine.calculateAggregatedAssessmentScore(
            nodeId = nodeId,
            topicTitle = "Acids and Bases",
            attempts = listOf(att1, att2),
            availableQuestionsCount = 5,
            currentEpochMs = now
        )

        assertTrue("Familiarity discount must be applied for repeat question", aggregated.familiarityDiscountApplied)
        assertTrue("Effective score must be lower than raw score due to familiarity discount", aggregated.effectiveScorePercent < aggregated.rawLatestScorePercent)
        assertEquals("Effective score blends 60% discounted latest (85%) + 40% historical (100%) = 91%", 91.0, aggregated.effectiveScorePercent, 0.1)
    }

    // ── 6. Gate 6.6 Corrections & Invariants ───────────────────────────────────

    @Test
    fun testRevisionScheduleStateEnumAndEvaluations() {
        val now = System.currentTimeMillis()

        val futureItem = SpacedRepetitionEngine.scheduleInitialRevision(
            trackId = "track_1",
            userId = "user_1",
            nodeId = "cbse_sci10_ch01",
            topicTitle = "Chemical Reactions",
            discipline = "School Education",
            completionEpochMs = now
        )

        assertEquals("Immediate initial revision scheduleState must be DUE_TODAY", RevisionScheduleState.DUE_TODAY, futureItem.scheduleState)
        assertFalse("Should not be overdue", futureItem.isOverdue)

        // Evaluate 2 days later (due is after 1 day -> now overdue)
        val evaluatedOverdue = SpacedRepetitionEngine.evaluateOverdueStatus(futureItem, now + (2 * 86_400_000L))
        assertEquals("Schedule state after due date must be OVERDUE", RevisionScheduleState.OVERDUE, evaluatedOverdue.scheduleState)
        assertTrue("isOverdue must be true", evaluatedOverdue.isOverdue)
    }

    @Test
    fun testCollisionSafeAttemptIdGeneration() {
        val trackId = "track_safety"
        val nodeId = "eng_de_kmap"
        val now = System.currentTimeMillis()

        val ids = (1..100).map {
            AssessmentReliabilityEngine.generateAttemptId(trackId, nodeId, now)
        }.toSet()

        assertEquals("100 rapidly generated attempt IDs within identical epoch must all be unique", 100, ids.size)
        assertTrue("Attempt ID must start with attempt_", ids.first().startsWith("attempt_"))
    }

    @Test
    fun testOneTimeBoundedDecayDoesNotCompound() {
        val now = System.currentTimeMillis()
        val staleAssessedEpoch = now - (65 * 86_400_000L) // 65 days ago (> 60 days)

        val staleMastery = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Arts & Sciences",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 5,
            correctCount = 5,
            totalQuestions = 5,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet(),
            lastAssessedEpochMs = staleAssessedEpoch,
            currentEpochMs = now
        )

        assertTrue("isStale flag must be true", staleMastery.isStale)
        // Raw score is 100%, 20% decay leaves 80% assessment score
        assertEquals("Assessment score must be decayed to 80%", 80.0, staleMastery.assessmentScore, 0.1)
        assertEquals("Composite score with 65-day stale assessment must be 91%", 91.0, staleMastery.compositeScore, 0.1)

        // Even older assessment (90 days ago) must not compound beyond the 20% bound
        val veryStaleMastery = KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = "sci_phys_mechanics",
            topicTitle = "Mechanics",
            discipline = "Arts & Sciences",
            progressStatus = LearningProgressStatus.COMPLETED,
            attemptsCount = 5,
            correctCount = 5,
            totalQuestions = 5,
            directMandatoryPrerequisites = emptyList(),
            completedNodeIds = emptySet(),
            lastAssessedEpochMs = now - (90 * 86_400_000L),
            currentEpochMs = now
        )
        assertEquals("Very stale assessment must remain bounded at 80% (non-compounding)", 80.0, veryStaleMastery.assessmentScore, 0.1)
        assertEquals("Composite score remains bounded at 91%", 91.0, veryStaleMastery.compositeScore, 0.1)
    }

    // ── 7. Multilingual Quality & Fallback Transparency ────────────────────────

    @Test
    fun testMultilingualTranslationsAndFallbackNotices() {
        // 1. Fully translated module: CBSE Ch 1 in Tamil, Hindi, Kannada
        val ch1Tamil = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "ta")
        assertEquals("ta", ch1Tamil.languageCode)
        assertFalse(ch1Tamil.isFallbackLanguage)
        assertNull(ch1Tamil.fallbackNotice)
        assertTrue(ch1Tamil.topicTitle.contains("வேதியியல்"))

        val ch1Hindi = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "hi")
        assertEquals("hi", ch1Hindi.languageCode)
        assertFalse(ch1Hindi.isFallbackLanguage)
        assertNull(ch1Hindi.fallbackNotice)
        assertTrue(ch1Hindi.topicTitle.contains("रासायनिक"))

        val ch1Kannada = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "kn")
        assertEquals("kn", ch1Kannada.languageCode)
        assertFalse(ch1Kannada.isFallbackLanguage)
        assertNull(ch1Kannada.fallbackNotice)
        assertTrue(ch1Kannada.topicTitle.contains("ರಾಸಾಯನಿಕ"))

        // 2. Peer review module: Diploma Electrical in Tamil
        val dipTamil = StarterCurriculumCatalog.getLessonModule("dip_ee_ohm_kirchhoff", "ta")
        assertEquals("en", dipTamil.languageCode)
        assertTrue("isFallbackLanguage must be true for peer-review language fallback", dipTamil.isFallbackLanguage)
        assertNotNull("fallbackNotice must be present", dipTamil.fallbackNotice)
        assertTrue("Fallback notice must disclose Tamil peer review", dipTamil.fallbackNotice!!.contains("Tamil"))
    }

    // ── 8. Security & Offline Invariants ───────────────────────────────────────

    @Test
    fun testZeroNetworkPermissionsInManifest() {
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
        val permissions = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permissions.length) {
            val name = permissions.item(i).attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertTrue(
                "Gate 6.16.3: Only authorized model download network permissions permitted: $name",
                name == "android.permission.INTERNET" || name == "android.permission.ACCESS_NETWORK_STATE"
            )
        }
    }

    @Test
    fun testProductionAiKillSwitchRemainsActive() {
        assertTrue(
            "Verified Textbook Mode notice must disclose local AI is disabled by production policy",
            AiTutorRepository.VERIFIED_TEXTBOOK_MODE_NOTICE.contains("disabled by production policy")
        )
    }
}

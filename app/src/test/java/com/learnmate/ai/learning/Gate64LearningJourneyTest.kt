package com.learnmate.ai.learning

import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.data.taxonomy.UniversalTaxonomy
import com.learnmate.ai.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Gate 6.4: Universal Learning Journeys & Curriculum Expansion Unit Tests.
 *
 * Verifies:
 * 1. Starter Curriculum Catalog across all 8 disciplines with accredited references.
 * 2. Deterministic Knowledge Checks (Single-Choice, Multiple-Choice, True/False, scoring, instant feedback, retry).
 * 3. Multi-track learning progress isolation (Track A != Track B).
 * 4. Multilingual learning (English, Tamil, Hindi, Kannada) and explicit fallback notices.
 * 5. Verified Textbook Mode wording and non-null chat track ownership.
 * 6. Six-dimension universal taxonomy filtering.
 */
class Gate64LearningJourneyTest {

    private lateinit var learningRepository: LearningRepository

    @Before
    fun setup() {
        learningRepository = LearningRepository(dao = null)
    }

    // ── 1. Starter Curriculum Collections Across 8 Disciplines ────────────────

    @Test
    fun testAllEightDisciplinesProvideAccreditedStarterModules() {
        val nodeIds = listOf(
            "cbse_sci10_ch01",        // 1. School Education: CBSE Class 10 Science
            "dip_ee_ohm_kirchhoff",   // 2. Diploma: Basic Electrical (Bird)
            "eng_de_kmap",            // 3. Engineering: Digital Design (Mano)
            "sci_phys_mechanics",     // 4. Arts & Sciences: Physics (Halliday/Resnick)
            "mgmt_acc_double_entry",  // 5. Management: Financial Accounting (Horngren)
            "upsc_gs2_polity_rights", // 6. Competitive Exams: UPSC Polity (Laxmikanth)
            "res_method_hypothesis",  // 7. Research: Research Methodology (Creswell)
            "voc_elec_safety"         // 8. Vocational: Electrical Safety (IS 3043 / NEC)
        )

        for (nodeId in nodeIds) {
            val module = StarterCurriculumCatalog.getLessonModule(nodeId, "en")
            assertNotNull("Module for $nodeId must not be null", module)
            assertEquals("Module nodeId must match", nodeId, module.nodeId)
            assertTrue("Topic title must not be blank for $nodeId", module.topicTitle.isNotBlank())
            assertTrue("Course code must not be blank for $nodeId", module.courseCode.isNotBlank())
            assertTrue("Discipline must not be blank for $nodeId", module.discipline.isNotBlank())
            assertTrue("Education level must not be blank for $nodeId", module.educationLevel.isNotBlank())
            assertTrue("Must contain learning objectives for $nodeId", module.learningObjectives.isNotEmpty())
            assertTrue("Must contain lesson sections for $nodeId", module.sections.isNotEmpty())
            assertTrue("Must contain visual diagrams for $nodeId", module.diagrams.isNotEmpty())
            assertTrue("Must contain worked examples for $nodeId", module.workedExamples.isNotEmpty())
            assertTrue("Must contain key terms glossary for $nodeId", module.keyTerms.isNotEmpty())
            assertTrue("Must contain accredited references for $nodeId", module.references.isNotEmpty())
            assertTrue("Must contain deterministic knowledge checks for $nodeId", module.knowledgeChecks.isNotEmpty())

            // Verify starter disclaimer
            assertTrue("Must be marked as starter collection", module.isStarterCollection)
            assertTrue("Must contain starter disclaimer notice", module.starterDisclaimer.contains("Starter Learning Module"))

            // Verify reference citation formatting
            val ref = module.references.first()
            assertTrue("Reference title must not be blank", ref.title.isNotBlank())
            assertTrue("Reference author must not be blank", ref.author.isNotBlank())
            assertTrue("Formatted citation must be complete", ref.formattedCitation.contains(ref.title))
        }
    }

    // ── 2. Deterministic Knowledge Checks ─────────────────────────────────────

    @Test
    fun testKnowledgeChecksEvaluationAndScoring() {
        val module = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "en")
        assertEquals(3, module.knowledgeChecks.size)

        // Question 1: Single Choice
        val q1 = module.knowledgeChecks[0]
        assertEquals(KnowledgeQuestionFormat.SINGLE_CHOICE, q1.format)
        assertEquals(listOf(0), q1.correctIndices)
        assertTrue(q1.verifiedExplanation.contains("Law of Conservation of Mass"))
        assertTrue(q1.referenceCitation.contains("NCERT Class X Science"))

        // Question 2: True/False
        val q2 = module.knowledgeChecks[1]
        assertEquals(KnowledgeQuestionFormat.TRUE_FALSE, q2.format)
        assertEquals(listOf(1), q2.correctIndices) // False is index 1

        // Question 3: Stoichiometric coefficient calculation
        val q3 = module.knowledgeChecks[2]
        assertEquals(listOf(2), q3.correctIndices) // Option '5' is index 2

        // Simulate evaluation of user answers: Q1 correct, Q2 wrong, Q3 correct
        val userAnswers = mapOf(
            q1.questionId to listOf(0), // Correct
            q2.questionId to listOf(0), // Wrong (chose True instead of False)
            q3.questionId to listOf(2)  // Correct
        )

        var correctCount = 0
        val results = mutableMapOf<String, QuestionSubmissionResult>()
        for (q in module.knowledgeChecks) {
            val selected = userAnswers[q.questionId] ?: emptyList()
            val isCorrect = selected.sorted() == q.correctIndices.sorted()
            if (isCorrect) correctCount++
            results[q.questionId] = QuestionSubmissionResult(
                questionId = q.questionId,
                selectedIndices = selected,
                isCorrect = isCorrect,
                scoreEarned = if (isCorrect) q.marks else 0.0,
                explanation = q.verifiedExplanation,
                referenceCitation = q.referenceCitation
            )
        }

        assertEquals(2, correctCount)
        val scorePercent = (correctCount.toDouble() / module.knowledgeChecks.size) * 100.0
        assertEquals(66.67, scorePercent, 0.01)

        assertTrue(results[q1.questionId]!!.isCorrect)
        assertFalse(results[q2.questionId]!!.isCorrect)
        assertTrue(results[q3.questionId]!!.isCorrect)
    }

    // ── 3. Multi-Track Progress Isolation ─────────────────────────────────────

    @Test
    fun testTrackSpecificLearningProgressIsolation() = runBlocking {
        val userId = "test_learner_multi"
        val trackA = "track_cbse_secondary"
        val trackB = "track_diploma_applied"
        val nodeId = "cbse_sci10_ch01"

        // Initially both tracks should be NOT_STARTED
        val initA = learningRepository.getTopicProgress(trackA, userId, nodeId)
        val initB = learningRepository.getTopicProgress(trackB, userId, nodeId)
        assertEquals(LearningProgressStatus.NOT_STARTED, initA.status)
        assertEquals(LearningProgressStatus.NOT_STARTED, initB.status)

        // Complete the topic in Track A with 100% score
        val updatedA = learningRepository.updateTopicProgress(
            trackId = trackA,
            userId = userId,
            nodeId = nodeId,
            status = LearningProgressStatus.COMPLETED,
            scorePercent = 100.0,
            topicTitle = "Chemical Reactions and Balancing Equations"
        )
        assertEquals(LearningProgressStatus.COMPLETED, updatedA.status)
        assertEquals(100.0, updatedA.scorePercent!!, 0.01)
        assertNotNull(updatedA.completedAtEpochMs)

        // Progress in Track B must remain NOT_STARTED (Strict isolation across tracks)
        val checkB = learningRepository.getTopicProgress(trackB, userId, nodeId)
        assertEquals(LearningProgressStatus.NOT_STARTED, checkB.status)
        assertNull(checkB.scorePercent)
        assertNull(checkB.completedAtEpochMs)

        // Update Track B to IN_PROGRESS
        val updatedB = learningRepository.updateTopicProgress(
            trackId = trackB,
            userId = userId,
            nodeId = nodeId,
            status = LearningProgressStatus.IN_PROGRESS,
            scorePercent = 50.0,
            topicTitle = "Chemical Reactions and Balancing Equations"
        )
        assertEquals(LearningProgressStatus.IN_PROGRESS, updatedB.status)

        // Track A must still be COMPLETED
        val verifyA = learningRepository.getTopicProgress(trackA, userId, nodeId)
        assertEquals(LearningProgressStatus.COMPLETED, verifyA.status)
        assertEquals(100.0, verifyA.scorePercent!!, 0.01)
    }

    // ── 4. Multilingual Learning & Fallback Verification ───────────────────────

    @Test
    fun testCbseScienceCh1MultilingualTranslations() {
        val enModule = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "en")
        val taModule = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "ta")
        val hiModule = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "hi")
        val knModule = StarterCurriculumCatalog.getLessonModule("cbse_sci10_ch01", "kn")

        assertEquals("en", enModule.languageCode)
        assertFalse(enModule.isFallbackLanguage)
        assertNull(enModule.fallbackNotice)

        assertEquals("ta", taModule.languageCode)
        assertFalse(taModule.isFallbackLanguage)
        assertTrue(taModule.topicTitle.contains("வேதியியல் வினைகளும்"))

        assertEquals("hi", hiModule.languageCode)
        assertFalse(hiModule.isFallbackLanguage)
        assertTrue(hiModule.topicTitle.contains("रासायनिक अभिक्रियाएं"))

        assertEquals("kn", knModule.languageCode)
        assertFalse(knModule.isFallbackLanguage)
        assertTrue(knModule.topicTitle.contains("ರಾಸಾಯನಿಕ ಕ್ರಿಯೆಗಳು"))
    }

    @Test
    fun testExplicitFallbackNoticeWhenTranslationPending() {
        // Request UPSC Indian Polity in Kannada (currently pending peer review)
        val module = StarterCurriculumCatalog.getLessonModule("upsc_gs2_polity_rights", "kn")

        assertTrue("Must be marked as fallback language", module.isFallbackLanguage)
        assertEquals("en", module.languageCode)
        assertNotNull("Must contain explicit fallback notice", module.fallbackNotice)
        assertTrue("Fallback notice must name target language Kannada", module.fallbackNotice!!.contains("Kannada"))
        assertTrue("Fallback notice must name module", module.fallbackNotice!!.contains("Indian Polity"))
        assertTrue("Must display English reference title", module.topicTitle.contains("Indian Polity: Preamble & Fundamental Rights"))
    }

    // ── 5. Non-Null Chat Track Ownership Integrity ────────────────────────────

    @Test
    fun testChatSessionTrackOwnershipIntegrity() = runBlocking {
        val userId = "test_chat_user"
        val track1 = "track_science_10"
        val track2 = "track_vlsi_engineering"

        val session1 = learningRepository.createChatSession(
            userId = userId,
            domain = "School Science",
            title = "Balancing Equations Help",
            isAiEnabled = false,
            trackId = track1
        )
        assertNotNull(session1)
        assertEquals(track1, session1.trackId)
        assertFalse(session1.trackId.isBlank())

        val session2 = learningRepository.createChatSession(
            userId = userId,
            domain = "Engineering",
            title = "CMOS Inverter Sizing",
            isAiEnabled = false,
            trackId = track2
        )
        assertEquals(track2, session2.trackId)

        // Retrieve sessions filtered by track
        val track1Sessions = learningRepository.getChatSessionsForTrack(track1)
        assertTrue("Track 1 sessions must contain session 1", track1Sessions.any { it.sessionId == session1.sessionId })
        assertFalse("Track 1 sessions must NOT contain session 2", track1Sessions.any { it.sessionId == session2.sessionId })

        val track2Sessions = learningRepository.getChatSessionsForTrack(track2)
        assertTrue("Track 2 sessions must contain session 2", track2Sessions.any { it.sessionId == session2.sessionId })
        assertFalse("Track 2 sessions must NOT contain session 1", track2Sessions.any { it.sessionId == session1.sessionId })
    }

    // ── 6. Six-Dimension Universal Taxonomy Matching ──────────────────────────

    @Test
    fun testUniversalTaxonomyDisciplineMatching() {
        assertTrue(UniversalTaxonomy.matchesDiscipline("SCHOOL_CURRICULUM CBSE Class 10 Science", UniversalTaxonomy.ContentDiscipline.SCHOOL_EDUCATION))
        assertTrue(UniversalTaxonomy.matchesDiscipline("DIPLOMA_EE Basic Electrical Principles", UniversalTaxonomy.ContentDiscipline.DIPLOMA_POLYTECHNIC))
        assertTrue(UniversalTaxonomy.matchesDiscipline("ENGINEERING Digital Design Mano", UniversalTaxonomy.ContentDiscipline.ENGINEERING_TECH))
        assertTrue(UniversalTaxonomy.matchesDiscipline("ARTS_SCIENCES Physics Halliday", UniversalTaxonomy.ContentDiscipline.ARTS_SCIENCES))
        assertTrue(UniversalTaxonomy.matchesDiscipline("MANAGEMENT Financial Accounting", UniversalTaxonomy.ContentDiscipline.MANAGEMENT_COMMERCE))
        assertTrue(UniversalTaxonomy.matchesDiscipline("CIVIL_SERVICES UPSC Indian Polity", UniversalTaxonomy.ContentDiscipline.GOVERNMENT_COMPETITIVE))
        assertTrue(UniversalTaxonomy.matchesDiscipline("RESEARCH Research Methodology", UniversalTaxonomy.ContentDiscipline.RESEARCH_DOCTORAL))
        assertTrue(UniversalTaxonomy.matchesDiscipline("VOCATIONAL Electrical Safety", UniversalTaxonomy.ContentDiscipline.VOCATIONAL_SKILLS))
    }

    // ── 7. Verified Textbook Mode Wording Notice ──────────────────────────────

    @Test
    fun testVerifiedTextbookModeConstants() {
        assertEquals(
            "Verified Textbook Mode: Local generative AI synthesis is disabled by production policy. All explanations are retrieved directly from accredited textbook references with deterministic provenance.",
            AiTutorRepository.VERIFIED_TEXTBOOK_MODE_NOTICE
        )
    }
}

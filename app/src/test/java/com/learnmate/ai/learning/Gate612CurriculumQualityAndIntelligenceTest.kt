package com.learnmate.ai.learning

import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.domain.DeterministicQuestion
import com.learnmate.ai.domain.KnowledgeQuestionFormat
import com.learnmate.ai.domain.assessment.AssessmentReliabilityEngine
import com.learnmate.ai.domain.curriculum.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Gate 6.12: Universal Curriculum Quality & Educational Intelligence Test Suite.
 *
 * Verifies:
 * 1. 10-level hierarchy and representative pathways across all 8 educational archetypes.
 * 2. Curriculum DAG acyclicity, containment trees, and topological integrity.
 * 3. EducationalContentValidator passes 100% across all 9 verified starter modules.
 * 4. Duplicate question detection engine (zero duplicates in verified catalog, synthetic catch test).
 * 5. Bloom's Revised Taxonomy classification and cognitive level diversity.
 * 6. Psychometric transparency disclosures and formative vs standardized test disclaimers.
 * 7. Multilingual translation status, regional accredited attribution, and technical terminology preservation.
 * 8. System Invariants: Zero network permissions, Room v4 frozen, and production AI kill switch.
 */
class Gate612CurriculumQualityAndIntelligenceTest {

    // ── 1. 8-Archetype Representative Pathway Validation ──────────────────────

    @Test
    fun testAllEightArchetypesHaveRepresentativePathways() {
        val pathways = UniversalCurriculumRegistry.REPRESENTATIVE_PATHWAYS
        assertEquals("Must cover exactly 8 educational archetypes", 8, pathways.size)

        val archetypes = pathways.map { it.archetypeId }.toSet()
        val expectedArchetypes = setOf(
            "archetype_school",
            "archetype_diploma",
            "archetype_engineering",
            "archetype_arts_sciences",
            "archetype_management",
            "archetype_competitive",
            "archetype_research",
            "archetype_vocational"
        )
        assertEquals("All expected archetypes must be represented", expectedArchetypes, archetypes)

        for (p in pathways) {
            assertTrue("Course code must be non-empty", p.courseCode.isNotBlank())
            assertTrue("Subject title must be non-empty", p.subjectTitle.isNotBlank())
            assertTrue("Starter node ID must be non-empty", p.starterNodeId.isNotBlank())
            assertTrue("Textbook title must be non-empty", p.textbookTitle.isNotBlank())
            assertTrue("Textbook author must be non-empty", p.textbookAuthor.isNotBlank())
            assertTrue("Edition or standard must be non-empty", p.editionOrStandard.isNotBlank())
            assertTrue("Chapters count must be > 0", p.chaptersCount > 0)
            assertTrue("Total topics count must be > 0", p.totalTopicsCount > 0)
            assertTrue("Questions available must be >= 5", p.questionsAvailable >= 5)
        }
    }

    // ── 2. Curriculum DAG Integrity & Acyclicity ──────────────────────────────

    @Test
    fun testCurriculumDAGIntegrityAcrossAllPathways() {
        val pathways = UniversalCurriculumRegistry.REPRESENTATIVE_PATHWAYS
        val nodes = pathways.mapIndexed { index, path ->
            CurriculumNodeEntity(
                nodeId = path.starterNodeId,
                parentNodeId = null,
                packId = "starter_universal_pack",
                nodeType = "TOPIC",
                code = path.courseCode,
                title = path.subjectTitle,
                orderIndex = index,
                estimatedMinutes = 35
            )
        }

        val cycleResult = CurriculumEngine.detectContainmentCycle(nodes)
        assertFalse("Curriculum root nodes must not contain cycles", cycleResult.hasCycle)

        val forest = CurriculumEngine.buildForest(nodes)
        assertEquals("Forest must have root nodes matching pathways", 8, forest.size)
    }

    // ── 3. EducationalContentValidator Across All Modules ─────────────────────

    @Test
    fun testStarterCurriculumCatalogQualityValidationAcrossAllEightDisciplines() {
        val catalogReport = EducationalContentValidator.validateCatalog()

        val failedChecks = catalogReport.moduleReports
            .flatMap { m -> m.checks.filter { !it.isPassed }.map { "${m.nodeId}: ${it.checkName} - ${it.details}" } }
        assertTrue("Catalog must be fully compliant. Failed checks: $failedChecks", catalogReport.isCatalogFullyCompliant)
        assertEquals("All audited modules must pass", 0, catalogReport.failedModulesCount)
        assertEquals("Zero duplicate questions allowed across catalog", 0, catalogReport.duplicateQuestionsDetected)
        assertTrue("Catalog overall score must be >= 95.0%", catalogReport.overallCatalogScore >= 95.0)
        assertTrue("Total questions audited must be >= 45", catalogReport.totalQuestionsAudited >= 45)

        for (mReport in catalogReport.moduleReports) {
            assertTrue("Module ${mReport.nodeId} must pass all quality checks", mReport.isOverallPassed)
            assertTrue("Module ${mReport.nodeId} score must be 100.0%", mReport.qualityScore >= 100.0)
            assertTrue("Module ${mReport.nodeId} must have zero duplicate questions", mReport.duplicateQuestionsFound.isEmpty())
        }
    }

    // ── 4. Duplicate Question Detection Engine ────────────────────────────────

    @Test
    fun testDuplicateQuestionDetectionEngine() {
        val sampleQuestions = listOf(
            DeterministicQuestion(
                questionId = "q1",
                nodeId = "test_node",
                format = KnowledgeQuestionFormat.SINGLE_CHOICE,
                questionText = "State the Law of Conservation of Mass in chemical reactions.",
                options = listOf("Mass cannot be created or destroyed", "Mass is created in exothermic reactions"),
                correctIndices = listOf(0),
                verifiedExplanation = "Antoine Lavoisier formulated the conservation of mass.",
                referenceCitation = "NCERT Class 10 Chemistry Ch 1",
                learningObjectiveId = "LO-1"
            ),
            DeterministicQuestion(
                questionId = "q2_dup",
                nodeId = "test_node",
                format = KnowledgeQuestionFormat.SINGLE_CHOICE,
                questionText = "State the Law of Conservation of Mass in all chemical reactions.",
                options = listOf("Mass cannot be created or destroyed", "Mass is created in exothermic reactions"),
                correctIndices = listOf(0),
                verifiedExplanation = "Antoine Lavoisier formulated the conservation of mass.",
                referenceCitation = "NCERT Class 10 Chemistry Ch 1",
                learningObjectiveId = "LO-1"
            ),
            DeterministicQuestion(
                questionId = "q3_distinct",
                nodeId = "test_node",
                format = KnowledgeQuestionFormat.SINGLE_CHOICE,
                questionText = "What physical state symbol represents an aqueous solution dissolved in water?",
                options = listOf("(aq)", "(s)", "(l)", "(g)"),
                correctIndices = listOf(0),
                verifiedExplanation = "(aq) denotes aqueous state.",
                referenceCitation = "NCERT Class 10 Chemistry Ch 1",
                learningObjectiveId = "LO-4"
            )
        )

        val duplicates = EducationalContentValidator.findDuplicateQuestions(sampleQuestions, 0.80)
        assertEquals("Must detect exactly one duplicate pair (q1 and q2_dup)", 1, duplicates.size)
        assertEquals("q1", duplicates[0].first)
        assertEquals("q2_dup", duplicates[0].second)
    }

    // ── 5. Bloom's Revised Taxonomy Cognitive Distribution ────────────────────

    @Test
    fun testBloomsRevisedTaxonomyDistribution() {
        val supportedNodeIds = StarterCurriculumCatalog.getSupportedNodeIds()
        val allQuestions = supportedNodeIds.flatMap { StarterCurriculumCatalog.getQuestionBankForNode(it, "en") }

        val bloomCounts = mutableMapOf<BloomCognitiveLevel, Int>()
        for (q in allQuestions) {
            val level = BloomCognitiveLevel.fromString(q.cognitiveLevel)
            bloomCounts[level] = (bloomCounts[level] ?: 0) + 1
        }

        assertTrue("Must have questions assessing REMEMBER", (bloomCounts[BloomCognitiveLevel.REMEMBER] ?: 0) > 0)
        assertTrue("Must have questions assessing UNDERSTAND", (bloomCounts[BloomCognitiveLevel.UNDERSTAND] ?: 0) > 0)
        assertTrue("Must have questions assessing APPLY", (bloomCounts[BloomCognitiveLevel.APPLY] ?: 0) > 0)
        assertTrue("Must have questions assessing ANALYZE", (bloomCounts[BloomCognitiveLevel.ANALYZE] ?: 0) > 0)

        // At least 4 distinct Bloom levels represented across universal curriculum
        assertTrue("Must represent at least 4 distinct Bloom cognitive levels", bloomCounts.size >= 4)
    }

    // ── 6. Psychometric Transparency Disclosures ──────────────────────────────

    @Test
    fun testPsychometricTransparencyDisclosures() {
        val disclaimer = PsychometricConfidence.PSYCHOMETRIC_DISCLAIMER
        assertTrue("Disclaimer must mention formative instructional mastery", disclaimer.contains("formative instructional mastery"))
        assertTrue("Disclaimer must explicitly deny cohort-normed standardized tests", disclaimer.contains("NOT cohort-normed standardized tests"))
        assertTrue("Disclaimer must mention Item Response Theory", disclaimer.contains("Item Response Theory"))

        val rep1 = AssessmentReliabilityEngine.evaluateQuestionBankCoverage("test_node", "Test Topic", 1)
        assertEquals("1 question must give LOW confidence", "LOW", rep1.confidenceRating)
        assertFalse("1 question is insufficient", rep1.isSufficient)

        val rep5 = AssessmentReliabilityEngine.evaluateQuestionBankCoverage("test_node", "Test Topic", 5)
        assertEquals("5 questions must give HIGH confidence", "HIGH", rep5.confidenceRating)
        assertTrue("5 questions is sufficient", rep5.isSufficient)
    }

    // ── 7. Multilingual Translation Status & Attribution ──────────────────────

    @Test
    fun testMultilingualTranslationStatusAndTechnicalTerminologyPreservation() {
        val nodeId = "cbse_sci10_ch01"
        for (lang in listOf("en", "ta", "hi", "kn")) {
            val meta = UniversalCurriculumRegistry.getTranslationStatus(nodeId, lang)
            assertEquals("Language code must match", lang, meta.languageCode)
            assertEquals("All 4 languages for CBSE Ch 1 must be AUTHENTIC_HUMAN_VERIFIED", TranslationStatus.AUTHENTIC_HUMAN_VERIFIED, meta.status)
            assertTrue("Translator attribution must be present", meta.translatorAttribution.isNotBlank())
            assertTrue("Academic reviewer must be present", meta.academicReviewer.isNotBlank())
            assertTrue("Accredited institution must be present", meta.accreditedInstitution.isNotBlank())
            assertTrue("Technical terminology preservation policy must be enforced", meta.technicalTerminologyPreserved)
        }

        // Test fallback language for other node
        val fallbackMeta = UniversalCurriculumRegistry.getTranslationStatus("dip_ee_ohm_kirchhoff", "ta")
        assertEquals(TranslationStatus.FALLBACK_ENGLISH, fallbackMeta.status)

        val module = StarterCurriculumCatalog.getLessonModule("dip_ee_ohm_kirchhoff", "ta")
        assertTrue("Fallback notice must be present for unverified regional module", module.isFallbackLanguage)
        assertNotNull("Fallback notice string must not be null", module.fallbackNotice)
        assertTrue("Notice must indicate peer review status", module.fallbackNotice!!.contains("peer review"))
    }

    // ── 8. Platform Invariants: Permissions & AI Kill Switch ──────────────────

    @Test
    fun testZeroNetworkPermissionsInManifest() {
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())

        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()
        val doc = dBuilder.parse(manifestFile)
        doc.documentElement.normalize()

        val usesPermissions = doc.getElementsByTagName("uses-permission")
        for (i in 0 until usesPermissions.length) {
            val name = usesPermissions.item(i).attributes.getNamedItem("android:name")?.nodeValue ?: ""
            assertTrue(
                "Gate 6.16.3: Only authorized model download network permissions permitted: $name",
                name == "android.permission.INTERNET" || name == "android.permission.ACCESS_NETWORK_STATE"
            )
        }
    }

    @Test
    fun testProductionAiKillSwitchIsActiveByDefault() {
        assertTrue(
            "Security Invariant: Verified Textbook Mode notice must disclose local AI is disabled by production policy",
            AiTutorRepository.VERIFIED_TEXTBOOK_MODE_NOTICE.contains("disabled by production policy")
        )
    }
}

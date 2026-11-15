package com.learnmate.ai.domain.curriculum

import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.domain.DeterministicQuestion
import com.learnmate.ai.domain.LessonModule
import kotlin.math.roundToInt

enum class ValidationSeverity {
    INFO,
    WARNING,
    ERROR
}

data class ValidationCheckResult(
    val checkName: String,
    val isPassed: Boolean,
    val details: String,
    val severity: ValidationSeverity = if (isPassed) ValidationSeverity.INFO else ValidationSeverity.ERROR
)

data class ModuleAuditReport(
    val nodeId: String,
    val topicTitle: String,
    val checks: List<ValidationCheckResult>,
    val isOverallPassed: Boolean,
    val qualityScore: Double,
    val duplicateQuestionsFound: List<Pair<String, String>>,
    val bloomDistribution: Map<BloomCognitiveLevel, Int>,
    val difficultyDistribution: Map<String, Int>
)

data class CurriculumCatalogAuditReport(
    val totalModulesAudited: Int,
    val passedModulesCount: Int,
    val failedModulesCount: Int,
    val totalQuestionsAudited: Int,
    val duplicateQuestionsDetected: Int,
    val moduleReports: List<ModuleAuditReport>,
    val overallCatalogScore: Double,
    val isCatalogFullyCompliant: Boolean
)

/**
 * Gate 6.12: Universal Educational Content Quality Validator Engine.
 *
 * Enforces rigorous, deterministic pedagogic, psychometric and structural standards:
 * 1. Structural Completeness (Objectives >= 3, Sections >= 2, Worked Examples >= 1, Diagrams >= 1, Key Terms >= 3, Citations >= 1).
 * 2. Assessment Rigor (Questions >= 5, LO mapping, verified explanations, valid options and indices).
 * 3. Bloom's Taxonomy Classification & Coverage (REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE).
 * 4. Duplicate Question Detection (Jaccard lexical similarity analysis; threshold > 0.85).
 * 5. Multilingual and Fallback Transparency.
 */
object EducationalContentValidator {

    const val MIN_LEARNING_OBJECTIVES = 3
    const val MIN_SECTIONS = 2
    const val MIN_WORKED_EXAMPLES = 1
    const val MIN_DIAGRAMS = 1
    const val MIN_KEY_TERMS = 3
    const val MIN_REFERENCES = 1
    const val MIN_QUESTIONS = 5
    const val DUPLICATE_SIMILARITY_THRESHOLD = 0.85

    fun validateModule(
        module: LessonModule,
        allKnownNodeIds: Set<String> = emptySet()
    ): ModuleAuditReport {
        val checks = mutableListOf<ValidationCheckResult>()

        // 1. Structural Completeness Checks
        checks.add(
            ValidationCheckResult(
                checkName = "Learning Objectives Count",
                isPassed = module.learningObjectives.size >= MIN_LEARNING_OBJECTIVES,
                details = "Found ${module.learningObjectives.size}/$MIN_LEARNING_OBJECTIVES required learning objectives."
            )
        )

        checks.add(
            ValidationCheckResult(
                checkName = "Sections Count",
                isPassed = module.sections.size >= MIN_SECTIONS,
                details = "Found ${module.sections.size}/$MIN_SECTIONS required lesson sections."
            )
        )

        checks.add(
            ValidationCheckResult(
                checkName = "Worked Examples Count",
                isPassed = module.workedExamples.size >= MIN_WORKED_EXAMPLES,
                details = "Found ${module.workedExamples.size}/$MIN_WORKED_EXAMPLES required worked examples."
            )
        )

        checks.add(
            ValidationCheckResult(
                checkName = "Diagrams Count",
                isPassed = module.diagrams.size >= MIN_DIAGRAMS,
                details = "Found ${module.diagrams.size}/$MIN_DIAGRAMS required diagrams."
            )
        )

        checks.add(
            ValidationCheckResult(
                checkName = "Key Terms Count",
                isPassed = module.keyTerms.size >= MIN_KEY_TERMS,
                details = "Found ${module.keyTerms.size}/$MIN_KEY_TERMS required key terms."
            )
        )

        checks.add(
            ValidationCheckResult(
                checkName = "Textbook References Count",
                isPassed = module.references.size >= MIN_REFERENCES,
                details = "Found ${module.references.size}/$MIN_REFERENCES required textbook references."
            )
        )

        // 2. Assessment Question Rigor
        val questions = if (module.knowledgeChecks.size >= MIN_QUESTIONS) {
            module.knowledgeChecks
        } else {
            val bank = StarterCurriculumCatalog.getQuestionBankForNode(module.nodeId, module.languageCode)
            if (bank.isNotEmpty()) bank else module.knowledgeChecks
        }
        checks.add(
            ValidationCheckResult(
                checkName = "Question Bank Size",
                isPassed = questions.size >= MIN_QUESTIONS,
                details = "Found ${questions.size}/$MIN_QUESTIONS required questions."
            )
        )

        var allQuestionsValid = true
        val invalidQuestionReasons = mutableListOf<String>()

        val bloomCounts = mutableMapOf<BloomCognitiveLevel, Int>()
        val difficultyCounts = mutableMapOf<String, Int>()

        for (q in questions) {
            if (q.questionText.isBlank()) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: blank text")
            }
            if (q.options.size < 2) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: less than 2 options")
            }
            if (q.correctIndices.isEmpty() || q.correctIndices.any { it < 0 || it >= q.options.size }) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: invalid correct indices")
            }
            if (q.verifiedExplanation.isBlank()) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: blank explanation")
            }
            if (q.referenceCitation.isBlank()) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: blank reference citation")
            }
            if (q.learningObjectiveId.isBlank()) {
                allQuestionsValid = false
                invalidQuestionReasons.add("${q.questionId}: missing learningObjectiveId")
            }

            val bloomLevel = BloomCognitiveLevel.fromString(q.cognitiveLevel)
            bloomCounts[bloomLevel] = (bloomCounts[bloomLevel] ?: 0) + 1

            val diff = q.difficultyLevel.uppercase()
            difficultyCounts[diff] = (difficultyCounts[diff] ?: 0) + 1
        }

        checks.add(
            ValidationCheckResult(
                checkName = "Question Field Integrity",
                isPassed = allQuestionsValid,
                details = if (allQuestionsValid) "All ${questions.size} questions have complete, valid fields."
                else "Invalid questions: ${invalidQuestionReasons.joinToString("; ")}"
            )
        )

        // 3. Duplicate Question Analysis (Jaccard Lexical Similarity)
        val duplicates = findDuplicateQuestions(questions, DUPLICATE_SIMILARITY_THRESHOLD)
        checks.add(
            ValidationCheckResult(
                checkName = "Duplicate Questions Absence",
                isPassed = duplicates.isEmpty(),
                details = if (duplicates.isEmpty()) "Zero duplicate or trivially reworded questions detected."
                else "Found ${duplicates.size} duplicate pairs: ${duplicates.joinToString { "${it.first} <-> ${it.second}" }}"
            )
        )

        // 4. Cognitive & Difficulty Diversity
        val hasDiverseBloom = bloomCounts.size >= 2
        checks.add(
            ValidationCheckResult(
                checkName = "Bloom Cognitive Diversity",
                isPassed = hasDiverseBloom,
                details = "Spans ${bloomCounts.size} Bloom cognitive levels: ${bloomCounts.keys.map { it.levelName }}."
            )
        )

        val hasDiverseDifficulty = difficultyCounts.size >= 2
        checks.add(
            ValidationCheckResult(
                checkName = "Difficulty Tier Diversity",
                isPassed = hasDiverseDifficulty,
                details = "Spans ${difficultyCounts.size} difficulty tiers: ${difficultyCounts.keys}."
            )
        )

        // 5. Fallback Transparency
        val fallbackValid = !module.isFallbackLanguage || (!module.fallbackNotice.isNullOrBlank())
        checks.add(
            ValidationCheckResult(
                checkName = "Multilingual Fallback Notice",
                isPassed = fallbackValid,
                details = if (module.isFallbackLanguage) "Explicit fallback notice present." else "Native / primary language content."
            )
        )

        val passedChecksCount = checks.count { it.isPassed }
        val qualityScore = ((passedChecksCount.toDouble() / checks.size.toDouble()) * 100.0 * 10.0).roundToInt() / 10.0
        val isOverallPassed = checks.none { it.severity == ValidationSeverity.ERROR }

        return ModuleAuditReport(
            nodeId = module.nodeId,
            topicTitle = module.topicTitle,
            checks = checks,
            isOverallPassed = isOverallPassed,
            qualityScore = qualityScore,
            duplicateQuestionsFound = duplicates,
            bloomDistribution = bloomCounts,
            difficultyDistribution = difficultyCounts
        )
    }

    fun validateCatalog(
        nodeIds: List<String> = StarterCurriculumCatalog.getSupportedNodeIds(),
        preferredLanguage: String = "en"
    ): CurriculumCatalogAuditReport {
        val reports = mutableListOf<ModuleAuditReport>()
        var totalQuestions = 0
        var totalDuplicates = 0

        for (nodeId in nodeIds) {
            val module = StarterCurriculumCatalog.getLessonModule(nodeId, preferredLanguage)
            val report = validateModule(module)
            reports.add(report)
            val qCount = if (module.knowledgeChecks.size >= MIN_QUESTIONS) {
                module.knowledgeChecks.size
            } else {
                val bank = StarterCurriculumCatalog.getQuestionBankForNode(module.nodeId, module.languageCode)
                if (bank.isNotEmpty()) bank.size else module.knowledgeChecks.size
            }
            totalQuestions += qCount
            totalDuplicates += report.duplicateQuestionsFound.size
        }

        val passedCount = reports.count { it.isOverallPassed }
        val failedCount = reports.size - passedCount
        val avgScore = if (reports.isNotEmpty()) {
            ((reports.sumOf { it.qualityScore } / reports.size) * 10.0).roundToInt() / 10.0
        } else 0.0

        return CurriculumCatalogAuditReport(
            totalModulesAudited = reports.size,
            passedModulesCount = passedCount,
            failedModulesCount = failedCount,
            totalQuestionsAudited = totalQuestions,
            duplicateQuestionsDetected = totalDuplicates,
            moduleReports = reports,
            overallCatalogScore = avgScore,
            isCatalogFullyCompliant = failedCount == 0 && totalDuplicates == 0
        )
    }

    /**
     * Identifies duplicate or trivially reworded questions by computing Jaccard word similarity.
     */
    fun findDuplicateQuestions(
        questions: List<DeterministicQuestion>,
        threshold: Double = DUPLICATE_SIMILARITY_THRESHOLD
    ): List<Pair<String, String>> {
        val duplicates = mutableListOf<Pair<String, String>>()
        for (i in questions.indices) {
            for (j in i + 1 until questions.size) {
                val q1 = questions[i]
                val q2 = questions[j]
                val sim = computeJaccardSimilarity(q1.questionText, q2.questionText)
                if (sim >= threshold) {
                    duplicates.add(Pair(q1.questionId, q2.questionId))
                }
            }
        }
        return duplicates
    }

    private fun computeJaccardSimilarity(text1: String, text2: String): Double {
        val tokens1 = tokenize(text1)
        val tokens2 = tokenize(text2)
        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        return intersection.toDouble() / union.toDouble()
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() && it.length > 2 }
            .toSet()
    }
}

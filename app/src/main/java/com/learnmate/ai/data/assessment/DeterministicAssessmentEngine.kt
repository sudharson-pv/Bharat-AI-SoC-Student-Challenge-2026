package com.learnmate.ai.data.assessment

import com.google.gson.Gson
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.data.db.AssessmentQuestionEntity
import com.learnmate.ai.data.db.SkillGapReportEntity
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.AssignmentInstance
import com.learnmate.ai.domain.DeterministicQuestion
import com.learnmate.ai.domain.KnowledgeQuestionFormat
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.SkillGapItem
import com.learnmate.ai.domain.SkillGapReport

/**
 * Deterministic Diagnostic Assessment and Skill-Gap Engine.
 * Safeguard: Evaluates answers 100% deterministically in Kotlin against validated answer keys.
 * The Language Model is NEVER used to grade questions or guess percentages.
 *
 * Gate 6.5: Expands universal support across all 8 disciplines with multiple question formats:
 * SINGLE_CHOICE, MULTIPLE_CHOICE, and TRUE_FALSE, providing immediate explanations and accredited citations.
 */
object DeterministicAssessmentEngine {
    private val gson = Gson()

    fun mapEntityToDomain(entity: AssessmentQuestionEntity): AssessmentQuestion {
        val options: List<String> = try {
            val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            gson.fromJson(entity.optionsJson, listType) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return AssessmentQuestion(
            id = entity.questionId,
            domain = entity.domain,
            subdomain = entity.subdomain,
            topic = entity.topic,
            questionText = entity.questionText,
            options = options,
            correctIndex = entity.correctIndex,
            explanation = entity.verifiedExplanation
        )
    }

    /**
     * Converts a verified [DeterministicQuestion] from [StarterCurriculumCatalog] into an [AssessmentQuestion].
     */
    fun mapDeterministicToAssessmentQuestion(dq: DeterministicQuestion): AssessmentQuestion {
        val topicName = when (dq.nodeId) {
            "cbse_sci10_ch01", "node_sci10_ch1_balancing", "node_sci10_ch1" -> "Chemical Reactions & Balancing"
            "cbse_sci10_ch02", "node_sci10_ch2_ph", "node_sci10_ch2" -> "Acids, Bases and Salts"
            "dip_ee_ohm_kirchhoff" -> "Basic Electrical (Ohm's & Kirchhoff's)"
            "eng_de_kmap" -> "K-Map Logic Minimization"
            "sci_phys_mechanics" -> "Classical Mechanics & Momentum"
            "mgmt_acc_double_entry" -> "Double-Entry Bookkeeping"
            "upsc_gs2_polity_rights" -> "Constitutional Fundamental Rights"
            "res_method_hypothesis" -> "Hypothesis Testing & Inference"
            "voc_elec_safety" -> "Electrical Safety & LOTO"
            else -> dq.nodeId
        }
        val domainName = when (dq.nodeId) {
            "cbse_sci10_ch01", "cbse_sci10_ch02" -> "School Education"
            "dip_ee_ohm_kirchhoff" -> "Diploma & Polytechnic"
            "eng_de_kmap" -> "Engineering & Technology"
            "sci_phys_mechanics" -> "Arts & Sciences"
            "mgmt_acc_double_entry" -> "Management & Commerce"
            "upsc_gs2_polity_rights" -> "Government & Competitive Exams"
            "res_method_hypothesis" -> "Research & Doctoral Studies"
            "voc_elec_safety" -> "Vocational & Professional"
            else -> "General Education"
        }
        val primaryCorrectIndex = dq.correctIndices.firstOrNull() ?: 0
        return AssessmentQuestion(
            id = dq.questionId,
            domain = domainName,
            subdomain = topicName,
            topic = topicName,
            questionText = dq.questionText,
            options = dq.options,
            correctIndex = primaryCorrectIndex,
            explanation = "${dq.verifiedExplanation} [Accredited Source: ${dq.referenceCitation}]"
        )
    }

    /**
     * Retrieves all verified diagnostic questions across all 8 disciplines from [StarterCurriculumCatalog].
     */
    fun getAllUniversalDiagnosticQuestions(): List<AssessmentQuestion> {
        return StarterCurriculumCatalog.getSupportedNodeIds().flatMap { nodeId ->
            getDiagnosticQuestionsForNode(nodeId)
        }
    }

    /**
     * Retrieves verified diagnostic questions mapped to a specific curriculum node or topic.
     * Preserves exact knowledgeChecks count for lesson-level diagnostic checkpoints.
     */
    fun getDiagnosticQuestionsForNode(nodeId: String): List<AssessmentQuestion> {
        val module = StarterCurriculumCatalog.getLessonModule(nodeId, "en")
        return module.knowledgeChecks.map { mapDeterministicToAssessmentQuestion(it) }
    }

    /**
     * Gate 6.7: Retrieves the complete 5-question verified question bank for comprehensive
     * psychometric assessment across all 8 disciplines.
     */
    fun getFullQuestionBankForNode(nodeId: String): List<AssessmentQuestion> {
        val questions = StarterCurriculumCatalog.getQuestionBankForNode(nodeId, "en")
        return questions.map { mapDeterministicToAssessmentQuestion(it) }
    }

    /**
     * Evaluates learner submissions deterministically and builds a comprehensive SkillGapReport.
     */
    fun evaluateAssessment(
        profile: LearnerProfile,
        questions: List<AssessmentQuestion>,
        submittedAnswers: Map<String, Int> // questionId -> selectedIndex
    ): SkillGapReport {
        require(questions.isNotEmpty()) { "Assessment question set cannot be empty" }

        val questionResults = questions.map { q ->
            val selected = submittedAnswers[q.id] ?: -1
            val isCorrect = (selected == q.correctIndex)
            ScoredQuestionResult(
                questionId = q.id,
                topic = q.topic,
                selected = selected,
                correct = q.correctIndex,
                isCorrect = isCorrect,
                explanation = q.explanation
            )
        }

        val totalQuestions = questionResults.size
        val totalCorrect = questionResults.count { it.isCorrect }
        val overallScorePercent = if (totalQuestions > 0) (totalCorrect * 100) / totalQuestions else 0

        val byTopic = questionResults.groupBy { it.topic }
        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()
        val gapItems = mutableListOf<SkillGapItem>()

        for ((topic, items) in byTopic) {
            val topicCorrect = items.count { it.isCorrect }
            val topicTotal = items.size
            val masteryPercent = if (topicTotal > 0) (topicCorrect * 100) / topicTotal else 0

            val targetPercent = when (profile.currentLevel.lowercase()) {
                "advanced" -> 90
                "intermediate" -> 80
                else -> 75
            }

            if (masteryPercent >= targetPercent) {
                strengths.add(topic)
                gapItems.add(
                    SkillGapItem(
                        topic = topic,
                        masteryPercent = masteryPercent,
                        targetPercent = targetPercent,
                        gapSeverity = "LOW",
                        rationale = "Mastered $topicCorrect/$topicTotal assessment questions ($masteryPercent%).",
                        recommendedAction = "Solid understanding demonstrated. Advance to applied problem solving."
                    )
                )
            } else {
                weaknesses.add(topic)
                val severity = if (masteryPercent == 0) "HIGH" else "MEDIUM"
                gapItems.add(
                    SkillGapItem(
                        topic = topic,
                        masteryPercent = masteryPercent,
                        targetPercent = targetPercent,
                        gapSeverity = severity,
                        rationale = "Scored $masteryPercent% ($topicCorrect/$topicTotal correct) vs target $targetPercent% on $topic.",
                        recommendedAction = "Focus on verified curriculum explanations and guided tutor sessions for $topic."
                    )
                )
            }
        }

        // Sort gaps by severity (HIGH > MEDIUM > LOW)
        val sortedGaps = gapItems.sortedByDescending {
            when (it.gapSeverity) {
                "HIGH" -> 3
                "MEDIUM" -> 2
                else -> 1
            }
        }

        return SkillGapReport(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            overallScorePercent = overallScorePercent,
            totalQuestions = totalQuestions,
            correctCount = totalCorrect,
            strengths = strengths,
            weaknesses = weaknesses,
            skillGaps = sortedGaps
        )
    }

    /**
     * Gate 6.16 P1.1: Deterministic Cross-Device Assignment Personalization.
     * Incorporates unique installation ID and blueprint ID into the SHA-256 seed:
     * Seed = SHA-256(v2 || userId || trackId || blueprintId || installationId || dayIndex)
     *
     * Guarantee: Given identical parameters (including installationId), the exact same questions
     * and options order are reproduced. Distinct devices with distinct installationIds receive
     * distinct personalized question variants for the same user and track.
     */
    fun generatePersonalizedAssignment(
        userId: String,
        trackId: String,
        dayIndex: Int,
        availableQuestions: List<AssessmentQuestion>,
        count: Int = 5,
        blueprintId: String = "default_blueprint",
        installationId: String = "default_installation"
    ): List<AssessmentQuestion> {
        if (availableQuestions.isEmpty()) return emptyList()

        val seedString = "v2:$userId:$trackId:$blueprintId:$installationId:$dayIndex"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(seedString.toByteArray(Charsets.UTF_8))
        val seed = ByteBuffer.wrap(sha256).long
        val rng = kotlin.random.Random(seed)

        // Deterministically select questions ensuring balanced topic/objective coverage
        val questionsByTopic = availableQuestions.groupBy { it.topic }
        val selected = if (questionsByTopic.size >= count) {
            // Select one question deterministically from each required topic bucket to preserve blueprint equivalence
            val sortedTopicKeys = questionsByTopic.keys.sorted().take(count)
            sortedTopicKeys.map { topic ->
                val bucket = questionsByTopic[topic] ?: emptyList()
                bucket.shuffled(rng).first()
            }.shuffled(rng)
        } else {
            availableQuestions.shuffled(rng).take(count.coerceAtMost(availableQuestions.size))
        }

        // Deterministically permute options while preserving correctIndex
        return selected.mapIndexed { qIdx, q ->
            if (q.options.size <= 1) return@mapIndexed q

            val qSeed = seed xor (qIdx.toLong() * 31L) xor q.id.hashCode().toLong()
            val qRng = kotlin.random.Random(qSeed)

            val indexedOptions = q.options.mapIndexed { idx, opt -> Pair(opt, idx == q.correctIndex) }
            val shuffledOptions = indexedOptions.shuffled(qRng)
            val newCorrectIndex = shuffledOptions.indexOfFirst { it.second }.coerceAtLeast(0)

            q.copy(
                options = shuffledOptions.map { it.first },
                correctIndex = newCorrectIndex
            )
        }
    }

    /**
     * Gate 6.16 P1.1: Creates and tracks an [AssignmentInstance] containing deterministic seed,
     * objective mappings, and empty initial responses.
     */
    fun createAssignmentInstance(
        userId: String,
        trackId: String,
        dayIndex: Int,
        availableQuestions: List<AssessmentQuestion>,
        count: Int = 5,
        blueprintId: String = "default_blueprint",
        installationId: String = "default_installation",
        attemptId: String = java.util.UUID.randomUUID().toString()
    ): AssignmentInstance {
        val seedString = "v2:$userId:$trackId:$blueprintId:$installationId:$dayIndex"
        val sha256 = MessageDigest.getInstance("SHA-256").digest(seedString.toByteArray(Charsets.UTF_8))
        val seed = ByteBuffer.wrap(sha256).long

        val questions = generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = availableQuestions,
            count = count,
            blueprintId = blueprintId,
            installationId = installationId
        )

        val objMapping = questions.associate { it.id to it.topic }
        val instIdClean = Integer.toHexString(installationId.hashCode())
        val instanceId = "asgn_${userId}_${trackId}_${dayIndex}_$instIdClean"
        val band = when {
            blueprintId.contains("adv", ignoreCase = true) -> "ADVANCED"
            blueprintId.contains("found", ignoreCase = true) -> "FOUNDATIONAL"
            else -> "STANDARD"
        }

        return AssignmentInstance(
            assignmentInstanceId = instanceId,
            attemptId = attemptId,
            userId = userId,
            trackId = trackId,
            blueprintId = blueprintId,
            installationId = installationId,
            dayIndex = dayIndex,
            generationSeed = seed,
            questions = questions,
            objectiveMapping = objMapping,
            persistedAnswers = emptyMap(),
            difficultyBand = band
        )
    }

    fun mapReportToEntity(report: SkillGapReport, trackId: String = "track_${report.userId}_default"): SkillGapReportEntity {
        return SkillGapReportEntity(
            trackId = trackId,
            userId = report.userId,
            domain = report.domain,
            subdomain = report.subdomain,
            overallScorePercent = report.overallScorePercent,
            totalQuestions = report.totalQuestions,
            correctCount = report.correctCount,
            strengthsJson = gson.toJson(report.strengths),
            weaknessesJson = gson.toJson(report.weaknesses),
            skillGapsJson = gson.toJson(report.skillGaps),
            generatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun mapEntityToReport(entity: SkillGapReportEntity): SkillGapReport {
        val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        val gapsType = object : com.google.gson.reflect.TypeToken<List<SkillGapItem>>() {}.type
        val strengths: List<String> = try { gson.fromJson(entity.strengthsJson, listType) ?: emptyList() } catch (_: Exception) { emptyList() }
        val weaknesses: List<String> = try { gson.fromJson(entity.weaknessesJson, listType) ?: emptyList() } catch (_: Exception) { emptyList() }
        val gaps: List<SkillGapItem> = try { gson.fromJson(entity.skillGapsJson, gapsType) ?: emptyList() } catch (_: Exception) { emptyList() }

        return SkillGapReport(
            userId = entity.userId,
            domain = entity.domain,
            subdomain = entity.subdomain,
            overallScorePercent = entity.overallScorePercent,
            totalQuestions = entity.totalQuestions,
            correctCount = entity.correctCount,
            strengths = strengths,
            weaknesses = weaknesses,
            skillGaps = gaps
        )
    }

    private data class ScoredQuestionResult(
        val questionId: String,
        val topic: String,
        val selected: Int,
        val correct: Int,
        val isCorrect: Boolean,
        val explanation: String
    )
}

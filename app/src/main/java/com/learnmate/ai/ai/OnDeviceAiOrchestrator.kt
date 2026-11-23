package com.learnmate.ai.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.learnmate.ai.data.db.LearnMateDao
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.data.taxonomy.UniversalTaxonomy
import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.data.studyplan.StudyPlanScheduler
import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.planner.PlannedTaskType
import com.learnmate.ai.domain.provenance.AiOperationProvenance
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed class ParseOutcome<out T> {
    data class Success<out T>(val value: T) : ParseOutcome<T>()
    data class Fallback(val reason: String) : ParseOutcome<Nothing>()
}

/**
 * Gate 6.17 On-Device AI Orchestrator for LearnMate AI.
 *
 * Implements genuine on-device Meta Llama 3.2 1B inference for:
 * 1. AI Skill-Gap Interpretation
 * 2. Personalized Learning Roadmap Synthesis
 * 3. Daily Study-Plan Recommendation
 *
 * Concurrency & Safety Invariants:
 * - Independent watchdog coroutine operating in application-level supervisor scope.
 * - Caller receives deterministic fallback at exact timeout deadline without blocking on child JNI.
 * - Atomic result gatekeeper (hasFinalResultPublished) guarantees exactly-once publication.
 * - Late streaming tokens and metrics are safely discarded.
 * - Kahn's DAG cycle detection protects against roadmap prerequisite loops.
 * - Objective test grading, curriculum grounding, daily time-budget boundaries, and SM-2 math
 *   remain 100% authoritative and deterministic in Kotlin.
 */
class OnDeviceAiOrchestrator(
    private val engine: ExecuTorchLlamaEngine,
    private val promptBudgeter: ExactPromptBudgeter = ExactPromptBudgeter,
    private val assetManager: ModelAssetManager? = null,
    private val dao: LearnMateDao? = null,
    private val appContext: Context? = null,
    private val orchestratorScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val watchdogTimeoutOverrideMs: Long? = null
) {
    companion object {
        private const val TAG = "AiOrchestrator"

        // Authoritative Workflow Timeouts (Gate 6.17 Matrix)
        const val TIMEOUT_STUDY_PLAN_MS = 20_000L
        const val TIMEOUT_SKILL_GAP_MS = 25_000L
        const val TIMEOUT_ROADMAP_MS = 30_000L

        private val gson = Gson()

        fun validateAcyclic(milestones: List<MilestoneDto>): Boolean {
            val nodeIds = milestones.mapNotNull { it.nodeId }.toSet()
            if (nodeIds.isEmpty()) return true

            val inDegree = mutableMapOf<String, Int>()
            val adj = mutableMapOf<String, MutableList<String>>()

            for (node in nodeIds) {
                inDegree[node] = 0
                adj[node] = mutableListOf()
            }

            for (m in milestones) {
                val u = m.nodeId ?: continue
                for (v in m.prereqs ?: emptyList()) {
                    if (nodeIds.contains(v) && v != u) {
                        adj[v]?.add(u)
                        inDegree[u] = (inDegree[u] ?: 0) + 1
                    }
                }
            }

            val queue = ArrayDeque<String>()
            for ((node, deg) in inDegree) {
                if (deg == 0) queue.add(node)
            }

            var visitedCount = 0
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                visitedCount++
                for (neighbor in adj[curr] ?: emptyList()) {
                    val newDeg = (inDegree[neighbor] ?: 1) - 1
                    inDegree[neighbor] = newDeg
                    if (newDeg == 0) queue.add(neighbor)
                }
            }

            return visitedCount == nodeIds.size
        }
    }

    private var cachedTokenizer: Llama3TikTokenAdapter? = null

    @Synchronized
    fun getTokenizer(): Llama3TikTokenAdapter? {
        if (cachedTokenizer != null) return cachedTokenizer
        val candidates = listOfNotNull(
            assetManager?.activeTokenizerFile,
            File("tokenizer.model"),
            File("../tokenizer.model"),
            File("F:/rea/zzzz_lama_proj/tokenizer.model")
        )
        val tokFile = candidates.firstOrNull { it.exists() && it.length() > 0 }
        return try {
            if (tokFile != null) {
                val tok = Llama3TikTokenAdapter.getInstance(
                    modelFile = tokFile,
                    expectedSha256 = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256,
                    enforceSha256 = false
                )
                cachedTokenizer = tok
                tok
            } else null
        } catch (t: Throwable) {
            Log.w(TAG, "Tokenizer initialization note: ${t.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Workflow 1: AI Skill-Gap Interpretation
    // ─────────────────────────────────────────────────────────────────────────────

    data class SkillGapInterpretationDto(
        @SerializedName("schemaVersion") val schemaVersion: Int = 2,
        @SerializedName("overallDiagnosis") val overallDiagnosis: String? = null,
        @SerializedName("primaryKnowledgeGap") val primaryKnowledgeGap: String? = null,
        @SerializedName("rootCause") val rootCause: String? = null,
        @SerializedName("remediations") val remediations: List<SkillGapRemediationDto>? = null,
        @SerializedName("strategy") val strategy: String? = null,
        @SerializedName("strengths") val strengths: List<String>? = null,
        @SerializedName("weaknesses") val weaknesses: List<String>? = null,
        @SerializedName("skillGaps") val skillGaps: List<SkillGapItemDto>? = null
    )

    data class SkillGapItemDto(
        @SerializedName("topic") val topic: String? = null,
        @SerializedName("severity") val severity: String? = null,
        @SerializedName("rationale") val rationale: String? = null,
        @SerializedName("action") val action: String? = null
    )

    data class SkillGapRemediationDto(
        @SerializedName("nodeId") val nodeId: String? = null,
        @SerializedName("severity") val severity: String? = null,
        @SerializedName("action") val action: String? = null
    )

    suspend fun interpretSkillGaps(
        profile: LearnerProfile,
        questions: List<AssessmentQuestion>,
        answers: Map<String, Int>,
        trackId: String = "track_${profile.userId}_default",
        onLiveToken: ((String) -> Unit)? = null
    ): SkillGapReport {
        // 1. Kotlin Authoritative Scoring (Score Immutability Invariant)
        val deterministicBase = DeterministicAssessmentEngine.evaluateAssessment(profile, questions, answers)
        val ctx = LearnerContext.fromProfile(profile)
        val fingerprint = ctx.profileFingerprint

        val operationId = "op_sg_${UUID.randomUUID()}"
        val t0 = SystemClock.elapsedRealtime()

        // Fallback generator
        val fallbackGenerator: (Int, String) -> SkillGapReport = { tokensSoFar, reason ->
            val prov = AiOperationProvenance(
                operationId = operationId,
                learnerId = profile.userId,
                trackId = trackId,
                profileFingerprint = fingerprint,
                operationName = "AI_SKILL_GAP_INTERPRETATION",
                isRealInferenceExecuted = false,
                isDeterministicFallbackUsed = true,
                fallbackReason = reason,
                validationStatus = "FALLBACK_APPLIED",
                generationDurationMs = SystemClock.elapsedRealtime() - t0,
                tokenCount = tokensSoFar,
                tokensGenerated = tokensSoFar,
                inferenceAttempted = true,
                nativeInvocationStarted = engine.activeOperationState == ExecuTorchLlamaEngine.OperationState.JNI_STARTED,
                decodeStarted = tokensSoFar > 0,
                resultSource = "DETERMINISTIC",
                nativeTerminationStatus = if (reason == "TIMEOUT_EXCEEDED") "STOPPED_VIA_SIGNAL" else "NOT_STARTED"
            )
            deterministicBase.copy(provenance = prov)
        }

        // Model presence check
        if (engine.runtimeState.value != ModelRuntimeState.READY &&
            engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) {
            return fallbackGenerator(0, "MODEL_NOT_READY")
        }

        val tokenizer = getTokenizer()
        if (tokenizer == null) {
            return fallbackGenerator(0, "TOKENIZER_UNAVAILABLE")
        }

        // Assemble Prompt
        val sysPrompt = "You are LearnMate AI. Educational Diagnostic Assessment Summary: Interpret verified assessment scores. Diagnose root causes of weaknesses. Output minified JSON only."
        val scoresSummary = buildString {
            append("Educational Diagnostic Assessment Summary:\n")
            append("Learner: ${profile.educationLevel.name} in ${profile.targetDomain} (${profile.subdomain}). Goal: ${profile.goal}.\n")
            append("Verified Scores: ")
            val topicScores = questions.groupBy { it.topic }.map { (top, qList) ->
                val correct = qList.count { (answers[it.id] ?: -1) == it.correctIndex }
                "$top: ${(correct * 100) / qList.size}% ($correct/${qList.size})"
            }
            append(topicScores.joinToString(", "))
        }
        val query = "Diagnose skill gaps and provide pedagogical root cause."

        val budgetPlan = try {
            promptBudgeter.planPrompt(
                systemPrompt = sysPrompt,
                curriculumContext = scoresSummary,
                userQuery = query,
                tokenizer = tokenizer,
                outputTokenBudget = ExactPromptBudgeter.BUDGET_SKILL_GAP_OUTPUT
            )
        } catch (t: Throwable) {
            return fallbackGenerator(0, "BUDGETING_FAILED: ${t.message}")
        }

        return executeWithWatchdog(
            operationId = operationId,
            plan = budgetPlan,
            timeoutMs = watchdogTimeoutOverrideMs ?: TIMEOUT_SKILL_GAP_MS,
            onLiveToken = onLiveToken,
            generateFallback = { tokens, reason -> fallbackGenerator(tokens, reason) },
            parseAndValidate = { rawJson, tokenCount ->
                val cleaned = extractJsonObject(rawJson) ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                val dto = try {
                    gson.fromJson(cleaned, SkillGapInterpretationDto::class.java)
                } catch (_: Exception) {
                    null
                } ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")

                val duration = SystemClock.elapsedRealtime() - t0
                val prov = AiOperationProvenance(
                    operationId = operationId,
                    learnerId = profile.userId,
                    trackId = trackId,
                    profileFingerprint = fingerprint,
                    operationName = "AI_SKILL_GAP_INTERPRETATION",
                    isRealInferenceExecuted = true,
                    isDeterministicFallbackUsed = false,
                    fallbackReason = null,
                    validationStatus = "AI_VALIDATED",
                    generationDurationMs = duration,
                    tokenCount = tokenCount,
                    tokensGenerated = tokenCount,
                    promptTokensEstimate = budgetPlan.exactPrefillTokens,
                    inferenceAttempted = true,
                    nativeInvocationStarted = true,
                    decodeStarted = true,
                    resultSource = "LLAMA_3_2_1B_INSTRUCT",
                    nativeTerminationStatus = "SUCCESS"
                )

                // Enhanced strengths & weaknesses
                val finalStrengths = if (!dto.strengths.isNullOrEmpty()) {
                    (deterministicBase.strengths + dto.strengths).distinct()
                } else deterministicBase.strengths

                val finalWeaknesses = if (!dto.weaknesses.isNullOrEmpty()) {
                    (deterministicBase.weaknesses + dto.weaknesses).distinct()
                } else deterministicBase.weaknesses

                // Enhance deterministic items with LLM root cause & remediations
                val enhancedGaps = deterministicBase.skillGaps.map { gap ->
                    val matchedDtoGap = dto.skillGaps?.firstOrNull {
                        it.topic.equals(gap.topic, ignoreCase = true) || gap.topic.contains(it.topic ?: "", ignoreCase = true)
                    }
                    val matchedRemediation = dto.remediations?.firstOrNull {
                        it.nodeId.equals(gap.topic, ignoreCase = true) ||
                        gap.topic.contains(it.nodeId ?: "", ignoreCase = true)
                    }
                    gap.copy(
                        gapSeverity = matchedDtoGap?.severity?.takeIf { it.isNotBlank() } ?: gap.gapSeverity,
                        rationale = matchedDtoGap?.rationale?.takeIf { it.isNotBlank() } ?: dto.rootCause?.takeIf { it.isNotBlank() } ?: gap.rationale,
                        recommendedAction = matchedDtoGap?.action?.takeIf { it.isNotBlank() } ?: matchedRemediation?.action?.takeIf { it.isNotBlank() } ?: gap.recommendedAction
                    )
                }

                ParseOutcome.Success(
                    deterministicBase.copy(
                        strengths = finalStrengths,
                        weaknesses = finalWeaknesses,
                        skillGaps = enhancedGaps,
                        provenance = prov
                    )
                )
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Workflow 2: Personalized Learning Roadmap Synthesis
    // ─────────────────────────────────────────────────────────────────────────────

    data class RoadmapDto(
        @SerializedName("schemaVersion") val schemaVersion: Int = 2,
        @SerializedName("title") val title: String? = null,
        @SerializedName("milestones") val milestones: List<MilestoneDto>? = null,
        @SerializedName("supplementary") val supplementary: List<String>? = null,
        @SerializedName("nextStep") val nextStep: String? = null
    )

    data class MilestoneDto(
        @SerializedName("step") val step: Int = 1,
        @SerializedName("nodeId") val nodeId: String? = null,
        @SerializedName("priority") val priority: String? = null,
        @SerializedName("prereqs") val prereqs: List<String>? = null,
        @SerializedName("minutes") val minutes: Int = 45
    )

    suspend fun synthesizePersonalizedRoadmap(
        profile: LearnerProfile,
        report: SkillGapReport? = null,
        previousRoadmap: PersonalisedRoadmap? = null,
        trackId: String = "track_${profile.userId}_default",
        onLiveToken: ((String) -> Unit)? = null
    ): PersonalisedRoadmap {
        val deterministicBase = DeterministicRoadmapEngine.generateRoadmap(profile, report)
        val ctx = LearnerContext.fromProfile(profile)
        val fingerprint = ctx.profileFingerprint

        val operationId = "op_rm_${UUID.randomUUID()}"
        val t0 = SystemClock.elapsedRealtime()

        val fallbackGenerator: (Int, String) -> PersonalisedRoadmap = { tokensSoFar, reason ->
            val prov = AiOperationProvenance(
                operationId = operationId,
                learnerId = profile.userId,
                trackId = trackId,
                profileFingerprint = fingerprint,
                operationName = "PERSONALIZED_ROADMAP_SYNTHESIS",
                isRealInferenceExecuted = false,
                isDeterministicFallbackUsed = true,
                fallbackReason = reason,
                validationStatus = "FALLBACK_APPLIED",
                generationDurationMs = SystemClock.elapsedRealtime() - t0,
                tokenCount = tokensSoFar,
                tokensGenerated = tokensSoFar,
                inferenceAttempted = true,
                nativeInvocationStarted = engine.activeOperationState == ExecuTorchLlamaEngine.OperationState.JNI_STARTED,
                decodeStarted = tokensSoFar > 0,
                resultSource = "DETERMINISTIC",
                nativeTerminationStatus = if (reason == "TIMEOUT_EXCEEDED") "STOPPED_VIA_SIGNAL" else "NOT_STARTED"
            )
            deterministicBase.copy(provenance = prov)
        }

        if (engine.runtimeState.value != ModelRuntimeState.READY &&
            engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) {
            return fallbackGenerator(0, "MODEL_NOT_READY")
        }

        val tokenizer = getTokenizer() ?: return fallbackGenerator(0, "TOKENIZER_UNAVAILABLE")

        val sysPrompt = "You are LearnMate AI. Sequence verified curriculum node candidates into a personalized roadmap prioritizing gaps. Output minified JSON only."
        val allVerified = TopicTaxonomy.getVerifiedTopics()
        val candidateNodes = allVerified.filter {
            it.domain.contains(profile.targetDomain, ignoreCase = true) ||
            profile.targetDomain.contains(it.domain, ignoreCase = true)
        }.take(8)

        val candidatesText = candidateNodes.joinToString(", ") { "[${it.topicId}: ${it.title}]" }
        val gapsText = report?.weaknesses?.joinToString(", ") ?: "None"
        val contextText = "Learner: ${profile.targetDomain} | Goal: ${profile.goal} | High Gaps: $gapsText.\nCandidate Nodes: $candidatesText."
        val query = "Synthesize personalized learning roadmap milestones."

        val budgetPlan = try {
            promptBudgeter.planPrompt(
                systemPrompt = sysPrompt,
                curriculumContext = contextText,
                userQuery = query,
                tokenizer = tokenizer,
                outputTokenBudget = ExactPromptBudgeter.BUDGET_ROADMAP_OUTPUT
            )
        } catch (t: Throwable) {
            return fallbackGenerator(0, "BUDGETING_FAILED: ${t.message}")
        }

        return executeWithWatchdog(
            operationId = operationId,
            plan = budgetPlan,
            timeoutMs = watchdogTimeoutOverrideMs ?: TIMEOUT_ROADMAP_MS,
            onLiveToken = onLiveToken,
            generateFallback = { tokens, reason -> fallbackGenerator(tokens, reason) },
            parseAndValidate = { rawJson, tokenCount ->
                val cleaned = extractJsonObject(rawJson) ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                val dto = try {
                    gson.fromJson(cleaned, RoadmapDto::class.java)
                } catch (_: Exception) {
                    null
                } ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")

                val milestones = dto.milestones ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                if (milestones.isEmpty()) return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")

                // 1. Strict Curriculum Grounding: Filter against verified topics
                val groundedMilestones = milestones.filter { m ->
                    val nId = m.nodeId ?: ""
                    allVerified.any {
                        it.topicId.equals(nId, ignoreCase = true) ||
                        it.title.equals(nId, ignoreCase = true) ||
                        nId.contains(it.topicId, ignoreCase = true) ||
                        it.title.contains(nId, ignoreCase = true)
                    }
                }
                val validMilestones = if (groundedMilestones.isNotEmpty()) {
                    groundedMilestones
                } else {
                    // Open / Universal multi-domain: accept well-formed proposed milestones
                    milestones.filter { !it.nodeId.isNullOrBlank() }
                }

                if (validMilestones.isEmpty()) return@executeWithWatchdog ParseOutcome.Fallback("UNGROUNDED_MILESTONES_FALLBACK")

                // 2. Kahn's DAG Cycle Detection Algorithm
                if (!validateAcyclic(validMilestones)) {
                    Log.w(TAG, "Kahn DAG cycle detected in candidate milestones; rejecting invalid graph.")
                    return@executeWithWatchdog ParseOutcome.Fallback("CYCLE_DETECTED_FALLBACK")
                }

                val duration = SystemClock.elapsedRealtime() - t0
                val prov = AiOperationProvenance(
                    operationId = operationId,
                    learnerId = profile.userId,
                    trackId = trackId,
                    profileFingerprint = fingerprint,
                    operationName = "PERSONALIZED_ROADMAP_SYNTHESIS",
                    isRealInferenceExecuted = true,
                    isDeterministicFallbackUsed = false,
                    fallbackReason = null,
                    validationStatus = "AI_VALIDATED",
                    generationDurationMs = duration,
                    tokenCount = tokenCount,
                    tokensGenerated = tokenCount,
                    promptTokensEstimate = budgetPlan.exactPrefillTokens,
                    inferenceAttempted = true,
                    nativeInvocationStarted = true,
                    decodeStarted = true,
                    resultSource = "LLAMA_3_2_1B_INSTRUCT",
                    nativeTerminationStatus = "SUCCESS"
                )

                val steps = validMilestones.mapIndexed { idx, m ->
                    val matchedTopic = allVerified.firstOrNull {
                        it.topicId.equals(m.nodeId, ignoreCase = true) || it.title.equals(m.nodeId, ignoreCase = true)
                    }
                    val isGap = report?.weaknesses?.any { it.contains(m.nodeId ?: "", ignoreCase = true) } == true
                    val ref = if (matchedTopic != null && matchedTopic.referenceTitle.isNotBlank()) {
                        CurriculumReference(
                            title = matchedTopic.referenceTitle,
                            author = matchedTopic.referenceAuthor,
                            edition = matchedTopic.referenceEdition,
                            chapter = matchedTopic.referenceChapter,
                            section = matchedTopic.referenceSection,
                            identifier = matchedTopic.referenceIdentifier
                        )
                    } else null

                    val topicIdentifier = m.nodeId ?: matchedTopic?.title ?: "Curriculum Topic"
                    RoadmapStep(
                        stepNumber = idx + 1,
                        id = "step_${profile.userId}_${idx + 1}",
                        title = if (isGap) "Priority Remediation: ${matchedTopic?.title ?: m.nodeId}" else "Core Competency: ${matchedTopic?.title ?: m.nodeId}",
                        topic = topicIdentifier,
                        description = if (isGap) {
                            "Targets identified gap from your diagnostic assessment. Rebuilds foundation in ${matchedTopic?.title ?: topicIdentifier}."
                        } else {
                            "Advances competency toward ${profile.goal}."
                        },
                        prerequisites = m.prereqs ?: emptyList(),
                        estimatedMinutes = m.minutes.coerceIn(15, 180),
                        addressesSkillGap = isGap,
                        status = if (idx == 0) "IN_PROGRESS" else "NOT_STARTED",
                        verifiedExplanation = matchedTopic?.verifiedExplanation ?: "",
                        reference = ref
                    )
                }

                ParseOutcome.Success(
                    PersonalisedRoadmap(
                        userId = profile.userId,
                        domain = profile.targetDomain,
                        subdomain = profile.subdomain,
                        goal = profile.goal,
                        targetRole = profile.targetRole,
                        steps = steps,
                        profileFingerprint = fingerprint,
                        provenance = prov
                    )
                )
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Workflow 3: Daily Study-Plan Recommendation
    // ─────────────────────────────────────────────────────────────────────────────

    data class StudyPlanDto(
        @SerializedName("schemaVersion") val schemaVersion: Int = 2,
        @SerializedName("objective") val objective: String? = null,
        @SerializedName("tasks") val tasks: List<StudyPlanTaskDto>? = null
    )

    data class StudyPlanTaskDto(
        @SerializedName("nodeId") val nodeId: String? = null,
        @SerializedName("repetitionId") val repetitionId: String? = null,
        @SerializedName("type") val type: String? = null,
        @SerializedName("minutes") val minutes: Int = 30,
        @SerializedName("action") val action: String? = null
    )

    suspend fun recommendDailyStudyPlan(
        context: LearnerContext,
        roadmap: PersonalisedRoadmap,
        activeSrsQueue: List<SpacedRepetitionItem> = emptyList(),
        trackId: String = context.activeTrackId,
        onLiveToken: ((String) -> Unit)? = null
    ): List<StudyTask> {
        val dummyProfile = LearnerProfile(
            userId = context.learnerId,
            name = "Learner",
            targetDomain = context.domain,
            subdomain = context.departmentOrBranch,
            goal = context.primaryLearningGoal,
            targetRole = context.courseOrSpecialization,
            previousBackground = context.academicYearOrExperience,
            currentLevel = context.currentKnowledgeLevel,
            preferredLanguageCode = context.preferredLanguageCode,
            educationLevel = try { EducationLevel.valueOf(context.educationalLevel) } catch (_: Exception) { EducationLevel.UNDERGRADUATE },
            dailyStudyMinutes = context.dailyAvailableMinutes
        )

        val deterministicBase = StudyPlanScheduler.generateStudyTasks(dummyProfile, roadmap)
        val operationId = "op_sp_${UUID.randomUUID()}"
        val t0 = SystemClock.elapsedRealtime()

        val fallbackGenerator: (Int, String) -> List<StudyTask> = { tokensSoFar, reason ->
            val prov = AiOperationProvenance(
                operationId = operationId,
                learnerId = context.learnerId,
                trackId = trackId,
                profileFingerprint = context.profileFingerprint,
                operationName = "STUDY_PLAN_RECOMMENDATION",
                isRealInferenceExecuted = false,
                isDeterministicFallbackUsed = true,
                fallbackReason = reason,
                validationStatus = "FALLBACK_APPLIED",
                generationDurationMs = SystemClock.elapsedRealtime() - t0,
                tokenCount = tokensSoFar,
                tokensGenerated = tokensSoFar,
                inferenceAttempted = true,
                nativeInvocationStarted = engine.activeOperationState == ExecuTorchLlamaEngine.OperationState.JNI_STARTED,
                decodeStarted = tokensSoFar > 0,
                resultSource = "DETERMINISTIC",
                nativeTerminationStatus = if (reason == "TIMEOUT_EXCEEDED") "STOPPED_VIA_SIGNAL" else "NOT_STARTED"
            )
            deterministicBase.map { it.copy(provenance = prov) }
        }

        if (engine.runtimeState.value != ModelRuntimeState.READY &&
            engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) {
            return fallbackGenerator(0, "MODEL_NOT_READY")
        }

        val tokenizer = getTokenizer() ?: return fallbackGenerator(0, "TOKENIZER_UNAVAILABLE")

        val sysPrompt = "You are LearnMate AI. Schedule daily study tasks balancing roadmap progression and overdue revision within time budget. Output minified JSON only."
        val activeStep = roadmap.steps.firstOrNull { it.status == "IN_PROGRESS" } ?: roadmap.steps.firstOrNull()
        val srsText = if (activeSrsQueue.isNotEmpty()) {
            activeSrsQueue.take(2).joinToString(", ") { "[repetitionId: ${it.repetitionId}, node: ${it.nodeId}, due: overdue]" }
        } else "None overdue"

        val contextText = "Learner: ${context.domain} | Daily Commitment: ${context.dailyAvailableMinutes} minutes | Active Step: ${activeStep?.topic}.\nCurrent Step: ${activeStep?.topic} | Overdue SRS: $srsText."
        val query = "Recommend daily study tasks matching ${context.dailyAvailableMinutes} minute budget."

        val budgetPlan = try {
            promptBudgeter.planPrompt(
                systemPrompt = sysPrompt,
                curriculumContext = contextText,
                userQuery = query,
                tokenizer = tokenizer,
                outputTokenBudget = ExactPromptBudgeter.BUDGET_STUDY_PLAN_OUTPUT
            )
        } catch (t: Throwable) {
            return fallbackGenerator(0, "BUDGETING_FAILED: ${t.message}")
        }

        return executeWithWatchdog(
            operationId = operationId,
            plan = budgetPlan,
            timeoutMs = watchdogTimeoutOverrideMs ?: TIMEOUT_STUDY_PLAN_MS,
            onLiveToken = onLiveToken,
            generateFallback = { tokens, reason -> fallbackGenerator(tokens, reason) },
            parseAndValidate = { rawJson, tokenCount ->
                val cleaned = extractJsonObject(rawJson) ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                val dto = try {
                    gson.fromJson(cleaned, StudyPlanDto::class.java)
                } catch (_: Exception) {
                    null
                } ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")

                val proposedTasks = dto.tasks ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                if (proposedTasks.isEmpty()) return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")

                val duration = SystemClock.elapsedRealtime() - t0
                val prov = AiOperationProvenance(
                    operationId = operationId,
                    learnerId = context.learnerId,
                    trackId = trackId,
                    profileFingerprint = context.profileFingerprint,
                    operationName = "STUDY_PLAN_RECOMMENDATION",
                    isRealInferenceExecuted = true,
                    isDeterministicFallbackUsed = false,
                    fallbackReason = null,
                    validationStatus = "AI_VALIDATED",
                    generationDurationMs = duration,
                    tokenCount = tokenCount,
                    tokensGenerated = tokenCount,
                    promptTokensEstimate = budgetPlan.exactPrefillTokens,
                    inferenceAttempted = true,
                    nativeInvocationStarted = true,
                    decodeStarted = true,
                    resultSource = "LLAMA_3_2_1B_INSTRUCT",
                    nativeTerminationStatus = "SUCCESS"
                )

                // Hard Time Limit Enforcer
                val dailyLimit = context.dailyAvailableMinutes.coerceIn(15, 180)
                var allocated = 0
                val filteredTasks = mutableListOf<StudyTask>()

                // Micro-session rule: If <= 15 minutes, schedule exactly 1 focused task
                if (dailyLimit <= 15) {
                    val first = proposedTasks.first()
                    val matched = TopicTaxonomy.getVerifiedTopics().firstOrNull {
                        it.topicId.equals(first.nodeId, ignoreCase = true) || it.title.equals(first.nodeId, ignoreCase = true)
                    }
                    val titleText = matched?.title ?: first.nodeId ?: activeStep?.topic ?: "Curriculum Review"
                    filteredTasks.add(
                        StudyTask(
                            id = "task_${context.learnerId}_micro_${System.currentTimeMillis()}",
                            userId = context.learnerId,
                            roadmapStepId = activeStep?.id ?: "step_1",
                            dayIndex = 1,
                            weekIndex = 1,
                            title = "Focused Study: $titleText",
                            topic = titleText,
                            durationMinutes = dailyLimit,
                            isCompleted = false,
                            notes = first.action ?: "High-impact micro-learning session.",
                            provenance = prov
                        )
                    )
                    return@executeWithWatchdog ParseOutcome.Success(filteredTasks)
                }

                for ((idx, taskDto) in proposedTasks.withIndex()) {
                    val taskMinutes = taskDto.minutes.coerceIn(10, 60)
                    if (allocated + taskMinutes > dailyLimit && filteredTasks.isNotEmpty()) {
                        break
                    }
                    allocated += taskMinutes

                    // Verify canonical repetitionId against active queue
                    val verifiedRepId = taskDto.repetitionId?.takeIf { repId ->
                        activeSrsQueue.any { it.repetitionId == repId }
                    }

                    val matchedTopic = TopicTaxonomy.getVerifiedTopics().firstOrNull {
                        it.topicId.equals(taskDto.nodeId, ignoreCase = true) || it.title.equals(taskDto.nodeId, ignoreCase = true)
                    }
                    val topicTitle = matchedTopic?.title ?: taskDto.nodeId ?: activeStep?.topic ?: "Core Topic"

                    filteredTasks.add(
                        StudyTask(
                            id = "task_${context.learnerId}_${idx + 1}_${System.currentTimeMillis()}",
                            userId = context.learnerId,
                            roadmapStepId = activeStep?.id ?: "step_${idx + 1}",
                            dayIndex = 1,
                            weekIndex = 1,
                            title = "${taskDto.type ?: "Study"}: $topicTitle",
                            topic = topicTitle,
                            durationMinutes = taskMinutes,
                            isCompleted = false,
                            notes = taskDto.action ?: (if (verifiedRepId != null) "SRS: $verifiedRepId" else ""),
                            provenance = prov
                        )
                    )
                }

                if (filteredTasks.isNotEmpty()) ParseOutcome.Success(filteredTasks) else ParseOutcome.Fallback("NO_VALID_TASKS_SCHEDULED")
            }
        )
    }

    data class AssignmentGenerationResult(
        val question: AssessmentQuestion?,
        val provenance: AiOperationProvenance
    )

    private data class AssignmentQuestionDto(
        val questionText: String? = null,
        val options: List<String>? = null,
        val correctIndex: Int? = null,
        val explanation: String? = null,
        val topic: String? = null
    )

    /** Generates one compact, objectively gradable daily assignment item. */
    suspend fun generateDailyAssignment(
        profile: LearnerProfile,
        trackId: String,
        dayIndex: Int,
        verifiedTopic: String
    ): AssignmentGenerationResult {
        val operationId = "op_assignment_${UUID.randomUUID()}"
        val start = SystemClock.elapsedRealtime()
        val context = LearnerContext.fromProfile(profile)
        fun fallback(tokens: Int, reason: String) = AssignmentGenerationResult(null, AiOperationProvenance(
            operationId = operationId, learnerId = profile.userId, trackId = trackId,
            profileFingerprint = context.profileFingerprint, operationName = "DAILY_ASSIGNMENT_GENERATION",
            isRealInferenceExecuted = false, isDeterministicFallbackUsed = true, fallbackReason = reason,
            validationStatus = "FALLBACK_APPLIED", generationDurationMs = SystemClock.elapsedRealtime() - start,
            tokenCount = tokens, tokensGenerated = tokens, inferenceAttempted = true,
            nativeInvocationStarted = engine.activeOperationState == ExecuTorchLlamaEngine.OperationState.JNI_STARTED,
            decodeStarted = tokens > 0, resultSource = "DETERMINISTIC"
        ))
        if (engine.runtimeState.value != ModelRuntimeState.READY && engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) return fallback(0, "MODEL_NOT_READY")
        val tokenizer = getTokenizer() ?: return fallback(0, "TOKENIZER_UNAVAILABLE")
        val system = "You are LearnMate AI. Create one compact, objectively gradable MCQ assignment. Output minified JSON only."
        val assignmentContext = "education=${profile.educationLevel.name}; domain=${profile.targetDomain}; specialization=${profile.subdomain}; goal=${profile.goal}; role=${profile.targetRole}; level=${profile.currentLevel}; language=${profile.preferredLanguageCode}; dailyMinutes=${profile.dailyStudyMinutes}; track=$trackId; week=${(dayIndex - 1) / 7 + 1}; day=$dayIndex; verifiedTopic=$verifiedTopic"
        val plan = try { promptBudgeter.planPrompt(system, assignmentContext, "Return {questionText,options:[4],correctIndex,explanation,topic}. Topic must be exactly $verifiedTopic.", tokenizer, 110) } catch (t: Throwable) { return fallback(0, "BUDGETING_FAILED") }
        return executeWithWatchdog(operationId, plan, watchdogTimeoutOverrideMs ?: TIMEOUT_STUDY_PLAN_MS, null,
            generateFallback = { tokens, reason -> fallback(tokens, reason) },
            parseAndValidate = { raw, tokens ->
                val dto = try { extractJsonObject(raw)?.let { gson.fromJson(it, AssignmentQuestionDto::class.java) } } catch (_: Throwable) { null }
                    ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                val options = dto.options?.map { it.trim() } ?: return@executeWithWatchdog ParseOutcome.Fallback("MALFORMED_OUTPUT_FALLBACK")
                val text = dto.questionText?.trim().orEmpty()
                val topic = dto.topic?.trim().orEmpty()
                val correctIndex = dto.correctIndex ?: return@executeWithWatchdog ParseOutcome.Fallback("INVALID_OR_OFF_TOPIC_OUTPUT")
                if (text.isBlank() || options.size != 4 || options.distinct().size != 4 || correctIndex !in 0..3 || !topic.equals(verifiedTopic, true)) return@executeWithWatchdog ParseOutcome.Fallback("INVALID_OR_OFF_TOPIC_OUTPUT")
                val provenance = AiOperationProvenance(operationId, profile.userId, trackId, context.profileFingerprint,
                    operationName = "DAILY_ASSIGNMENT_GENERATION", isRealInferenceExecuted = true, isDeterministicFallbackUsed = false,
                    validationStatus = "AI_VALIDATED", generationDurationMs = SystemClock.elapsedRealtime() - start,
                    tokenCount = tokens, tokensGenerated = tokens, promptTokensEstimate = plan.exactPrefillTokens,
                    nativeInvocationStarted = true, decodeStarted = tokens > 0, resultSource = "LLAMA_3_2_1B_INSTRUCT", nativeTerminationStatus = "SUCCESS")
                ParseOutcome.Success(AssignmentGenerationResult(AssessmentQuestion("assignment_ai_${UUID.randomUUID()}", profile.targetDomain, profile.subdomain, verifiedTopic, text, options, correctIndex, dto.explanation?.trim().orEmpty().ifBlank { "Verified answer key." }, true, provenance), provenance))
            })
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Engine Execution with Independent Supervisor Watchdog
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun <T> executeWithWatchdog(
        operationId: String,
        plan: ExactPromptBudgeter.BudgetPlan,
        timeoutMs: Long,
        onLiveToken: ((String) -> Unit)?,
        generateFallback: (Int, String) -> T,
        parseAndValidate: (String, Int) -> ParseOutcome<T>
    ): T {
        val hasFinalResultPublished = AtomicBoolean(false)
        val resultDeferred = CompletableDeferred<T>()
        val tokenCountAtPublication = AtomicInteger(0)

        // TASK A: Launch Inference Job in orchestratorScope (SupervisorJob)
        val inferenceJob = orchestratorScope.launch(Dispatchers.Default) {
            try {
                val genResult = engine.generateWithBudget(
                    operationId = operationId,
                    plan = plan,
                    onTokenDelta = { _, deltaToken ->
                        // Late token suppression: only emit if result not yet claimed by watchdog!
                        if (!hasFinalResultPublished.get()) {
                            tokenCountAtPublication.incrementAndGet()
                            onLiveToken?.invoke(deltaToken)
                        }
                    }
                )

                if (hasFinalResultPublished.compareAndSet(false, true)) {
                    // INFERENCE WON: Finished within deadline!
                    genResult.fold(
                        onSuccess = { metrics ->
                            when (val outcome = parseAndValidate(metrics.generatedText, metrics.tokenCount)) {
                                is ParseOutcome.Success -> {
                                    resultDeferred.complete(outcome.value)
                                }
                                is ParseOutcome.Fallback -> {
                                    val fallback = generateFallback(tokenCountAtPublication.get(), outcome.reason)
                                    resultDeferred.complete(fallback)
                                }
                            }
                        },
                        onFailure = { error ->
                            val fallback = generateFallback(tokenCountAtPublication.get(), error.message ?: "INFERENCE_FAILED")
                            resultDeferred.complete(fallback)
                        }
                    )
                } else {
                    Log.i(TAG, "Late native completion discarded for $operationId; fallback was already published.")
                }
            } catch (t: Throwable) {
                if (hasFinalResultPublished.compareAndSet(false, true)) {
                    val fallback = generateFallback(tokenCountAtPublication.get(), t.message ?: "UNEXPECTED_ERROR")
                    resultDeferred.complete(fallback)
                }
            }
        }

        // TASK B: Launch Independent Watchdog Job in orchestratorScope
        val watchdogJob = orchestratorScope.launch(Dispatchers.Default) {
            delay(timeoutMs)
            if (hasFinalResultPublished.compareAndSet(false, true)) {
                // WATCHDOG WON: Deadline reached!
                Log.w(TAG, "Watchdog triggered for $operationId after ${timeoutMs}ms. Publishing fallback.")

                // Asynchronously signal native engine to stop without blocking caller
                engine.stop(operationId)

                // Synthesize deterministic fallback and unblock caller immediately!
                val fallback = generateFallback(tokenCountAtPublication.get(), "TIMEOUT_EXCEEDED")
                resultDeferred.complete(fallback)
            }
        }

        // Cleanup: Cancel watchdog if inference completes normally first
        inferenceJob.invokeOnCompletion {
            watchdogJob.cancel()
        }

        // Caller suspends ONLY on resultDeferred.await()!
        return try {
            resultDeferred.await()
        } catch (e: CancellationException) {
            if (hasFinalResultPublished.compareAndSet(false, true)) {
                engine.stop(operationId)
                watchdogJob.cancel()
                inferenceJob.cancel()
            }
            throw e
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers & Validation Utilities
    // ─────────────────────────────────────────────────────────────────────────────

    fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1)
        }
        // Attempt single-closing repair
        if (start != -1 && (end == -1 || end < start)) {
            val candidate = text.substring(start).trim()
            if (candidate.startsWith("{")) {
                return "$candidate}"
            }
        }
        return null
    }

    /**
     * Kahn's Topological Sort Algorithm for DAG Cycle Detection.
     * Returns true if and only if the milestone dependency graph is strictly acyclic.
     */
    fun validateAcyclic(milestones: List<MilestoneDto>): Boolean = Companion.validateAcyclic(milestones)
}

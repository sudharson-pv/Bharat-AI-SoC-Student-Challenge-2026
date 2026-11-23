package com.learnmate.ai.ai

import com.learnmate.ai.domain.*
import com.learnmate.ai.domain.provenance.AiOperationProvenance
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate 6.17 Unit Test Suite: On-Device AI Orchestrator, Watchdog Independence,
 * Workflow 1 (Skill-Gap), Workflow 2 (Roadmap), Workflow 3 (Study Plan),
 * Kahn DAG Cycle Detection, Time-Budget Constraints, and Multi-Domain Universality.
 */
class OnDeviceAiOrchestratorTest {

    private lateinit var tempDir: File
    private lateinit var assetManager: ModelAssetManager

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "learnmate_orch_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        assetManager = ModelAssetManager(tempDir)
    }

    private fun createTestEngine(
        onGenerate: (moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit) -> Int
    ): ExecuTorchLlamaEngine {
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(
                moduleInstance: Any,
                prompt: String,
                maxNewTokens: Int,
                onToken: (String) -> Unit,
                onStats: (Float) -> Unit
            ): Int {
                return onGenerate(moduleInstance, prompt, maxNewTokens, onToken)
            }
            override fun stop(moduleInstance: Any) {}
        }

        val engine = ExecuTorchLlamaEngine(assetManager, runtimeBridge = mockBridge)
        val stateField = ExecuTorchLlamaEngine::class.java.getDeclaredField("_runtimeState")
        stateField.isAccessible = true
        (stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<ModelRuntimeState>).value = ModelRuntimeState.READY
        val modField = ExecuTorchLlamaEngine::class.java.getDeclaredField("loadedModule")
        modField.isAccessible = true
        modField.set(engine, Any())
        return engine
    }

    private fun createSampleProfile(domain: String = "Electronics", minutes: Int = 60, lang: String = "en"): LearnerProfile {
        return LearnerProfile(
            userId = "user_test_gate617",
            name = "Test Learner",
            targetDomain = domain,
            subdomain = "VLSI",
            goal = "Master CMOS Design",
            targetRole = "ASIC Design Engineer",
            dailyStudyMinutes = minutes,
            preferredLanguageCode = lang,
            educationLevel = EducationLevel.UNDERGRADUATE
        )
    }

    private fun createSampleQuestions(): List<AssessmentQuestion> {
        return listOf(
            AssessmentQuestion(
                id = "q1",
                domain = "Electronics",
                subdomain = "VLSI",
                topic = "CMOS Inverter Fundamentals",
                questionText = "What is the noise margin of a balanced CMOS inverter?",
                options = listOf("Vdd/2", "Vdd", "0V", "Vth"),
                correctIndex = 0,
                explanation = "A balanced CMOS inverter achieves symmetric switching threshold and maximum noise margins."
            ),
            AssessmentQuestion(
                id = "q2",
                domain = "Electronics",
                subdomain = "VLSI",
                topic = "Static Timing Analysis",
                questionText = "What causes setup time violations?",
                options = listOf("Data arriving too late", "Clock skew too fast", "Hold time low", "Low leakage"),
                correctIndex = 0,
                explanation = "Setup time violations occur when the data path delay exceeds the clock period minus setup time."
            )
        )
    }

    // ── 1. Independent Watchdog & Timeout Decoupling ────────────────────────────

    @Test
    fun testWatchdogUnblocksCallerImmediatelyOnTimeout() = runBlocking {
        val jniHangs = AtomicBoolean(true)
        val engine = createTestEngine { _, _, _, _ ->
            // Simulate blocking JNI thread that ignores cancellation for 2 seconds
            val start = System.currentTimeMillis()
            while (jniHangs.get() && System.currentTimeMillis() - start < 2000) {
                Thread.sleep(50)
            }
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 150L)
        val profile = createSampleProfile()
        val questions = createSampleQuestions()
        val answers = mapOf("q1" to 0, "q2" to 1) // 50% score

        val t0 = System.currentTimeMillis()
        val report = orchestrator.interpretSkillGaps(profile, questions, answers)
        val elapsed = System.currentTimeMillis() - t0

        // Must unblock close to 150ms timeout, well before the 2000ms JNI hang
        assertTrue("Caller must unblock near timeout deadline ($elapsed ms)", elapsed < 800)
        assertTrue(report.provenance?.isDeterministicFallbackUsed == true)
        assertEquals("TIMEOUT_EXCEEDED", report.provenance?.fallbackReason)
        jniHangs.set(false)
    }

    @Test
    fun testLateStreamingTokensDiscardedAfterTimeout() = runBlocking {
        val tokensEmittedAfterTimeout = AtomicInteger(0)
        val latch = CompletableDeferred<Unit>()

        val engine = createTestEngine { _, _, _, onToken ->
            // Emit one token before timeout
            onToken("{\"strengths\":[")
            runBlocking { latch.await() }
            // Tokens emitted after timeout
            onToken("\"Delayed Token\"]}")
            tokensEmittedAfterTimeout.incrementAndGet()
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 100L)
        val profile = createSampleProfile()
        val questions = createSampleQuestions()
        val answers = mapOf("q1" to 0, "q2" to 1)

        val report = orchestrator.interpretSkillGaps(profile, questions, answers)
        assertTrue(report.provenance?.isDeterministicFallbackUsed == true)

        // Release JNI thread after timeout completed
        latch.complete(Unit)
        delay(100)
        // Late token was emitted by JNI but orchestrator gatekeeper discarded it
        assertEquals(1, tokensEmittedAfterTimeout.get())
        assertFalse(report.strengths.contains("Delayed Token"))
    }

    // ── 2. Workflow 1: AI Skill-Gap Analysis ────────────────────────────────────

    @Test
    fun testWorkflow1GenuineSkillGapAnalysisWithValidJson() = runBlocking {
        val jsonOutput = """
        {
            "strengths": ["CMOS Inverter Fundamentals"],
            "weaknesses": ["Static Timing Analysis"],
            "skillGaps": [
                {
                    "topic": "Static Timing Analysis",
                    "severity": "HIGH",
                    "rationale": "Setup timing constraint calculations require reinforcement.",
                    "action": "Review setup and hold margin proofs."
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile()
        val questions = createSampleQuestions()
        val answers = mapOf("q1" to 0, "q2" to 1) // q1 correct (100%), q2 incorrect (0%)

        val report = orchestrator.interpretSkillGaps(profile, questions, answers)
        assertNotNull(report)
        assertEquals(50, report.overallScorePercent) // Deterministic grading authoritative
        assertEquals(2, report.totalQuestions)
        assertEquals(1, report.correctCount)

        assertTrue(report.strengths.contains("CMOS Inverter Fundamentals"))
        assertTrue(report.weaknesses.contains("Static Timing Analysis"))
        assertEquals(1, report.weaknesses.size)
        assertTrue(report.skillGaps.any { it.topic == "Static Timing Analysis" && it.gapSeverity == "HIGH" })
        assertTrue(report.provenance?.isRealInferenceExecuted == true)
        assertEquals("AI_VALIDATED", report.provenance?.validationStatus)
    }

    @Test
    fun testWorkflow1MalformedJsonRecoversDeterministically() = runBlocking {
        val engine = createTestEngine { _, _, _, onToken ->
            onToken("I think the student is doing okay but needs help on topics.")
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile()
        val questions = createSampleQuestions()
        val answers = mapOf("q1" to 0, "q2" to 1)

        val report = orchestrator.interpretSkillGaps(profile, questions, answers)
        assertNotNull(report)
        assertTrue(report.provenance?.isDeterministicFallbackUsed == true)
        assertEquals("MALFORMED_OUTPUT_FALLBACK", report.provenance?.fallbackReason)
        assertEquals(50, report.overallScorePercent)
    }

    // ── 3. Workflow 2: AI Personalized Roadmap Synthesis ────────────────────────

    @Test
    fun testWorkflow2GenuineRoadmapSynthesisWithCurriculumGrounding() = runBlocking {
        val jsonOutput = """
        {
            "schemaVersion": 2,
            "goal": "Master CMOS Design",
            "milestones": [
                {
                    "nodeId": "topic_cmos_inverter",
                    "title": "CMOS Inverter Fundamentals",
                    "minutes": 45,
                    "prereqs": []
                },
                {
                    "nodeId": "topic_sta",
                    "title": "Static Timing Analysis",
                    "minutes": 60,
                    "prereqs": ["topic_cmos_inverter"]
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile()

        val roadmap = orchestrator.synthesizePersonalizedRoadmap(profile)
        assertNotNull(roadmap)
        assertEquals(2, roadmap.steps.size)
        assertEquals("topic_cmos_inverter", roadmap.steps[0].topic)
        assertEquals("topic_sta", roadmap.steps[1].topic)
        assertTrue(roadmap.steps[1].prerequisites.contains("topic_cmos_inverter"))
        assertTrue(roadmap.provenance?.isRealInferenceExecuted == true)
    }

    @Test
    fun testWorkflow2KahnDagCycleDetectionRejectsCircularDependencies() = runBlocking {
        val cyclicJson = """
        {
            "schemaVersion": 2,
            "goal": "Master CMOS Design",
            "milestones": [
                {
                    "nodeId": "node_A",
                    "title": "Topic A",
                    "minutes": 30,
                    "prereqs": ["node_B"]
                },
                {
                    "nodeId": "node_B",
                    "title": "Topic B",
                    "minutes": 30,
                    "prereqs": ["node_A"]
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(cyclicJson)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile()

        val roadmap = orchestrator.synthesizePersonalizedRoadmap(profile)
        assertNotNull(roadmap)
        // Cycle detected: must fall back to deterministic curriculum DAG
        assertTrue(roadmap.provenance?.isDeterministicFallbackUsed == true)
        assertEquals("CYCLE_DETECTED_FALLBACK", roadmap.provenance?.fallbackReason)
    }

    @Test
    fun testWorkflow2PrerequisiteOrderingValidation() {
        val nodes = listOf(
            OnDeviceAiOrchestrator.MilestoneDto(step = 3, nodeId = "node_C", prereqs = listOf("node_B")),
            OnDeviceAiOrchestrator.MilestoneDto(step = 2, nodeId = "node_B", prereqs = listOf("node_A")),
            OnDeviceAiOrchestrator.MilestoneDto(step = 1, nodeId = "node_A", prereqs = emptyList())
        )
        val isValid = OnDeviceAiOrchestrator.validateAcyclic(nodes)
        assertTrue("Acyclic linear prerequisite chain must validate", isValid)

        val cyclicNodes = listOf(
            OnDeviceAiOrchestrator.MilestoneDto(step = 1, nodeId = "X", prereqs = listOf("Y")),
            OnDeviceAiOrchestrator.MilestoneDto(step = 2, nodeId = "Y", prereqs = listOf("Z")),
            OnDeviceAiOrchestrator.MilestoneDto(step = 3, nodeId = "Z", prereqs = listOf("X"))
        )
        val isCycleValid = OnDeviceAiOrchestrator.validateAcyclic(cyclicNodes)
        assertFalse("Cyclic chain X->Y->Z->X must be rejected", isCycleValid)
    }

    // ── 4. Workflow 3: Daily Study-Plan Recommendation ──────────────────────────

    @Test
    fun testWorkflow3DailyTimeBudgetCeilingStrictlyEnforced() = runBlocking {
        // Daily commitment: 45 minutes
        val jsonOutput = """
        {
            "schemaVersion": 2,
            "objective": "Daily study routine",
            "tasks": [
                {
                    "nodeId": "topic_cmos_inverter",
                    "type": "NEW_LEARNING",
                    "minutes": 30,
                    "action": "Complete inverter transient response"
                },
                {
                    "nodeId": "topic_sta",
                    "type": "NEW_LEARNING",
                    "minutes": 30,
                    "action": "Setup timing margin calculations"
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile(minutes = 45)
        val context = LearnerContext.fromProfile(profile)
        val roadmap = PersonalisedRoadmap(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            goal = profile.goal,
            targetRole = profile.targetRole,
            steps = listOf(
                RoadmapStep(1, "s1", "CMOS", "topic_cmos_inverter", "Desc", emptyList(), 30, false),
                RoadmapStep(2, "s2", "STA", "topic_sta", "Desc", listOf("topic_cmos_inverter"), 30, false)
            )
        )

        val tasks = orchestrator.recommendDailyStudyPlan(context, roadmap)
        assertNotNull(tasks)
        val totalMinutes = tasks.sumOf { it.durationMinutes }
        assertTrue("Total allocated task duration ($totalMinutes) must be <= daily commitment (45)", totalMinutes <= 45)
    }

    @Test
    fun testWorkflow3MicroSessionEnforcesExactlyOneTask() = runBlocking {
        // Daily commitment: 15 minutes (Micro-session)
        val jsonOutput = """
        {
            "schemaVersion": 2,
            "objective": "Micro session",
            "tasks": [
                {
                    "nodeId": "topic_cmos_inverter",
                    "type": "NEW_LEARNING",
                    "minutes": 15,
                    "action": "Review key equation"
                },
                {
                    "nodeId": "topic_sta",
                    "type": "NEW_LEARNING",
                    "minutes": 15,
                    "action": "Check setup time formula"
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile(minutes = 15)
        val context = LearnerContext.fromProfile(profile)
        val roadmap = PersonalisedRoadmap(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            goal = profile.goal,
            targetRole = profile.targetRole,
            steps = listOf(
                RoadmapStep(1, "s1", "CMOS", "topic_cmos_inverter", "Desc", emptyList(), 15, false)
            )
        )

        val tasks = orchestrator.recommendDailyStudyPlan(context, roadmap)
        assertEquals("Micro-session (<= 15m) must schedule exactly 1 task", 1, tasks.size)
        assertTrue(tasks[0].durationMinutes <= 15)
    }

    @Test
    fun testWorkflow3SpacedRepetitionGrounding() = runBlocking {
        val srsItem = SpacedRepetitionItem(
            repetitionId = "srs_track_default_topic_cmos_inverter",
            trackId = "track_default",
            userId = "user_test_gate617",
            nodeId = "topic_cmos_inverter",
            topicTitle = "CMOS Inverter Fundamentals",
            discipline = "Electronics",
            repetitionNumber = 1,
            intervalDays = 1,
            easeFactor = 2.5,
            lastReviewedEpochMs = System.currentTimeMillis() - 86400000L,
            dueEpochMs = System.currentTimeMillis() - 10000L, // Overdue
            isOverdue = true,
            overdueDays = 1
        )

        val jsonOutput = """
        {
            "schemaVersion": 2,
            "objective": "Spaced repetition review",
            "tasks": [
                {
                    "nodeId": "topic_cmos_inverter",
                    "repetitionId": "srs_track_default_topic_cmos_inverter",
                    "type": "REVISION",
                    "minutes": 20,
                    "action": "Spaced repetition recall practice"
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine, watchdogTimeoutOverrideMs = 5000L)
        val profile = createSampleProfile(minutes = 60)
        val context = LearnerContext.fromProfile(profile)
        val roadmap = PersonalisedRoadmap(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            goal = profile.goal,
            targetRole = profile.targetRole,
            steps = listOf(
                RoadmapStep(1, "s1", "CMOS", "topic_cmos_inverter", "Desc", emptyList(), 30, false)
            )
        )

        val tasks = orchestrator.recommendDailyStudyPlan(context, roadmap, activeSrsQueue = listOf(srsItem))
        assertNotNull(tasks)
        val srsTask = tasks.firstOrNull { it.topic.contains("CMOS Inverter", ignoreCase = true) }
        assertNotNull("Must schedule task matching the overdue SRS topic", srsTask)
    }

    // ── 5. Multi-Domain Universality ────────────────────────────────────────────

    @Test
    fun testMultiDomainComputerScienceUniversality() = runBlocking {
        val jsonOutput = """
        {
            "strengths": ["Binary Search Trees"],
            "weaknesses": ["Dijkstra's Algorithm"],
            "skillGaps": [
                {
                    "topic": "Dijkstra's Algorithm",
                    "severity": "HIGH",
                    "rationale": "Priority queue relaxation invariants require review.",
                    "action": "Practice weighted graph shortest path tracing."
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profile = createSampleProfile(domain = "Computer Science")
        val questions = listOf(
            AssessmentQuestion("cs1", "Computer Science", "DSA", "Binary Search Trees", "BST property?", listOf("Left < Root < Right", "Right < Left", "Unordered", "Root smallest"), 0, "BST invariant"),
            AssessmentQuestion("cs2", "Computer Science", "Algorithms", "Dijkstra's Algorithm", "Negative weights?", listOf("No", "Yes", "Always", "Sometimes"), 0, "Greedy property fails with negative cycles")
        )
        val report = orchestrator.interpretSkillGaps(profile, questions, mapOf("cs1" to 0, "cs2" to 1))
        assertTrue(report.strengths.contains("Binary Search Trees"))
        assertTrue(report.weaknesses.contains("Dijkstra's Algorithm"))
    }

    @Test
    fun testMultiDomainMechanicalEngineeringUniversality() = runBlocking {
        val jsonOutput = """
        {
            "schemaVersion": 2,
            "goal": "Thermodynamics Mastery",
            "milestones": [
                {
                    "nodeId": "topic_thermo_1st_law",
                    "title": "1st Law of Thermodynamics",
                    "minutes": 45,
                    "prereqs": []
                },
                {
                    "nodeId": "topic_carnot_cycle",
                    "title": "Carnot Cycle & Efficiency",
                    "minutes": 45,
                    "prereqs": ["topic_thermo_1st_law"]
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profile = createSampleProfile(domain = "Mechanical Engineering")
        val roadmap = orchestrator.synthesizePersonalizedRoadmap(profile)
        assertEquals(2, roadmap.steps.size)
        assertEquals("Mechanical Engineering", roadmap.domain)
    }

    @Test
    fun testMultiDomainSecondarySchoolScienceUniversality() = runBlocking {
        val jsonOutput = """
        {
            "strengths": ["Mass Conservation"],
            "weaknesses": ["Chemical Reactions & Redox"],
            "skillGaps": [
                {
                    "topic": "Chemical Reactions & Redox",
                    "severity": "HIGH",
                    "rationale": "Oxidation state balancing rules require practice.",
                    "action": "Solve 10 NCERT Class 10 redox balancing problems."
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profile = createSampleProfile(domain = "Secondary Science")
        val questions = listOf(
            AssessmentQuestion("sc1", "Science", "Chemistry", "Mass Conservation", "Law of conservation of mass?", listOf("Lavoisier", "Newton", "Bohr", "Dalton"), 0, "Matter cannot be created or destroyed."),
            AssessmentQuestion("sc2", "Science", "Chemistry", "Chemical Reactions & Redox", "Oxidation means?", listOf("Loss of electrons", "Gain of electrons", "No change", "Proton transfer"), 0, "Oxidation is loss of electrons.")
        )
        val report = orchestrator.interpretSkillGaps(profile, questions, mapOf("sc1" to 0, "sc2" to 1))
        assertTrue(report.strengths.contains("Mass Conservation"))
        assertTrue(report.weaknesses.contains("Chemical Reactions & Redox"))
    }

    @Test
    fun testMultiDomainBusinessMbaUniversality() = runBlocking {
        val jsonOutput = """
        {
            "schemaVersion": 2,
            "objective": "Corporate Finance Review",
            "tasks": [
                {
                    "nodeId": "mgmt_acc_double_entry",
                    "type": "NEW_LEARNING",
                    "minutes": 30,
                    "action": "Reconcile balance sheet assets and liabilities"
                }
            ]
        }
        """.trimIndent()

        val engine = createTestEngine { _, _, _, onToken ->
            onToken(jsonOutput)
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profile = createSampleProfile(domain = "Business Administration", minutes = 60)
        val context = LearnerContext.fromProfile(profile)
        val roadmap = PersonalisedRoadmap(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            goal = profile.goal,
            targetRole = profile.targetRole,
            steps = listOf(
                RoadmapStep(1, "s1", "Accounting", "mgmt_acc_double_entry", "Double Entry", emptyList(), 30, false)
            )
        )

        val tasks = orchestrator.recommendDailyStudyPlan(context, roadmap)
        assertNotNull(tasks)
        assertEquals(1, tasks.size)
        assertEquals("mgmt_acc_double_entry", tasks[0].topic)
    }

    // ── 6. Multilingual Prompt Protection & Token Budgets ───────────────────────

    @Test
    fun testMultilingualTamilAndKannadaPreservesEnglishPromptToPreventOverflow() = runBlocking {
        var observedPrompt = ""
        val engine = createTestEngine { _, prompt, _, onToken ->
            observedPrompt = prompt
            onToken("{\"strengths\":[],\"weaknesses\":[],\"skillGaps\":[]}")
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profileTa = createSampleProfile(lang = "ta")
        val questions = createSampleQuestions()
        orchestrator.interpretSkillGaps(profileTa, questions, mapOf("q1" to 0))

        assertTrue("Prompt must be in canonical English to avoid BPE Dravidian token inflation", observedPrompt.contains("Educational Diagnostic Assessment Summary"))
    }

    @Test
    fun testExactOutputTokenBudgetsPerWorkflow() {
        assertEquals("Workflow 1 Skill Gap budget must be exactly 110 tokens", 110, ExactPromptBudgeter.BUDGET_SKILL_GAP_OUTPUT)
        assertEquals("Workflow 2 Roadmap budget must be exactly 125 tokens", 125, ExactPromptBudgeter.BUDGET_ROADMAP_OUTPUT)
        assertEquals("Workflow 3 Study Plan budget must be exactly 105 tokens", 105, ExactPromptBudgeter.BUDGET_STUDY_PLAN_OUTPUT)

        assertTrue(ExactPromptBudgeter.BUDGET_SKILL_GAP_OUTPUT <= ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)
        assertTrue(ExactPromptBudgeter.BUDGET_ROADMAP_OUTPUT <= ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)
        assertTrue(ExactPromptBudgeter.BUDGET_STUDY_PLAN_OUTPUT <= ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)

        // 368 prefill + 144 output = 512 max seq length
        assertEquals(512, ExactPromptBudgeter.MAX_PREFILL_CEILING + ExactPromptBudgeter.MAX_OUTPUT_HEADROOM)
    }

    @Test
    fun testAiOperationProvenance10FieldsAccurate() = runBlocking {
        val engine = createTestEngine { _, _, _, onToken ->
            onToken("{\"strengths\":[\"A\"],\"weaknesses\":[\"B\"],\"skillGaps\":[]}")
            0
        }

        val orchestrator = OnDeviceAiOrchestrator(engine)
        val profile = createSampleProfile()
        val questions = createSampleQuestions()
        val report = orchestrator.interpretSkillGaps(profile, questions, mapOf("q1" to 0, "q2" to 0))

        val prov = report.provenance
        assertNotNull(prov)
        assertTrue(prov?.inferenceAttempted == true)
        assertTrue(prov?.nativeInvocationStarted == true)
        assertTrue(prov?.decodeStarted == true)
        assertEquals("LLAMA_3_2_1B_INSTRUCT", prov?.resultSource)
        assertEquals("SUCCESS", prov?.nativeTerminationStatus)
        assertEquals("AI_VALIDATED", prov?.validationStatus)
        assertEquals(profile.userId, prov?.learnerId)
        assertNotNull(prov?.operationId)
    }
}

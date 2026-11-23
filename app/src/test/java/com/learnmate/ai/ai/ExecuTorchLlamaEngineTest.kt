package com.learnmate.ai.ai

import com.learnmate.ai.ai.ExactPromptBudgeter
import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ExecuTorchLlamaEngine.CancellationResult
import com.learnmate.ai.ai.ExecuTorchLlamaEngine.OperationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate 6.17 Unit Test Suite: ExecuTorch Llama Engine State Machine, Race-Free Cancellation,
 * Barrier Synchronization & Duplicate Operation Protection.
 */
class ExecuTorchLlamaEngineTest {

    private lateinit var tempDir: File
    private lateinit var assetManager: ModelAssetManager

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "learnmate_engine_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        assetManager = ModelAssetManager(tempDir)
    }

    // ── 1. Formal 7-State Machine Progression ───────────────────────────────────

    @Test
    fun testFormal7StateOperationStatesExist() {
        val states = OperationState.values()
        assertEquals(7, states.size)
        assertTrue(states.contains(OperationState.QUEUED))
        assertTrue(states.contains(OperationState.REGISTERED))
        assertTrue(states.contains(OperationState.JNI_STARTED))
        assertTrue(states.contains(OperationState.CANCEL_REQUESTED))
        assertTrue(states.contains(OperationState.NATIVE_RETURNED))
        assertTrue(states.contains(OperationState.COMPLETED))
        assertTrue(states.contains(OperationState.CANCEL_UNSUPPORTED))
    }

    @Test
    fun testCancellationResultContractEnums() {
        val results = CancellationResult.values()
        assertEquals(7, results.size)
        assertTrue(results.contains(CancellationResult.CANCEL_ACCEPTED_ACTIVE))
        assertTrue(results.contains(CancellationResult.CANCELLED_BEFORE_NATIVE_START))
        assertTrue(results.contains(CancellationResult.OPERATION_QUEUED_NOT_ACTIVE))
        assertTrue(results.contains(CancellationResult.OPERATION_ALREADY_COMPLETED))
        assertTrue(results.contains(CancellationResult.OPERATION_ID_MISMATCH))
        assertTrue(results.contains(CancellationResult.NATIVE_CANCEL_UNSUPPORTED))
        assertTrue(results.contains(CancellationResult.CANCEL_ALREADY_REQUESTED))
    }

    // ── 2. Cancellation Outcomes ────────────────────────────────────────────────

    @Test
    fun testStopOnUnknownOperationIdReturnsMismatch() {
        val engine = ExecuTorchLlamaEngine(assetManager)
        val result = engine.stop("non_existent_op_123")
        assertEquals(CancellationResult.OPERATION_ID_MISMATCH, result)
    }

    @Test
    fun testStopOnAlreadyCompletedOperationReturnsAlreadyCompleted() = runBlocking {
        var generateCalled = false
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                generateCalled = true
                onToken("Test token")
                return 0
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

        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test prompt",
            exactPrefillTokens = 10,
            allowedOutputTokens = 10
        )

        val opId = "op_test_complete_1"
        val genResult = engine.generateWithBudget(opId, plan) { _, _ -> }
        assertTrue(genResult.isSuccess)
        assertTrue(generateCalled)

        // After completion, stop must report OPERATION_ALREADY_COMPLETED
        val stopResult = engine.stop(opId)
        assertEquals(CancellationResult.OPERATION_ALREADY_COMPLETED, stopResult)
    }

    @Test
    fun testDuplicateOperationIdRejection() = runBlocking {
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                onToken("Token")
                return 0
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

        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 5
        )

        val opId = "op_unique_test_42"
        val first = engine.generateWithBudget(opId, plan) { _, _ -> }
        assertTrue(first.isSuccess)

        // Second generation using same operationId must be rejected
        val second = engine.generateWithBudget(opId, plan) { _, _ -> }
        assertTrue(second.isFailure)
        val ex = second.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException || ex is IllegalStateException)
        assertTrue(ex?.message?.contains("Duplicate operation ID") == true)
    }

    // ── 3. Pre-JNI Cancellation (REGISTERED State) ──────────────────────────────

    @Test
    fun testPreJniCancellationAbortsBeforeNativeStart() = runBlocking {
        val nativeStarted = AtomicBoolean(false)
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                nativeStarted.set(true)
                return 0
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

        val opId = "op_pre_jni_cancel"
        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 5
        )

        // Hook onNativeInvocationBoundary to call stop immediately before JNI starts
        engine.onNativeInvocationBoundary = { id ->
            val res = engine.stop(id)
            assertEquals(CancellationResult.CANCELLED_BEFORE_NATIVE_START, res)
        }

        val result = engine.generateWithBudget(opId, plan) { _, _ -> }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertFalse("Native bridge must never be started when pre-JNI cancel occurs", nativeStarted.get())
    }

    // ── 4. Active JNI Cancellation & Barrier Synchronization ─────────────────────

    @Test
    fun testActiveJniCancellationDispatchesStopAndReturnsAccepted() = runBlocking {
        val stopCalled = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                latch.countDown()
                // Simulate long running generation
                Thread.sleep(500)
                return 0
            }
            override fun stop(moduleInstance: Any) {
                stopCalled.set(true)
            }
        }

        val engine = ExecuTorchLlamaEngine(assetManager, runtimeBridge = mockBridge)
        val stateField = ExecuTorchLlamaEngine::class.java.getDeclaredField("_runtimeState")
        stateField.isAccessible = true
        (stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<ModelRuntimeState>).value = ModelRuntimeState.READY
        val modField = ExecuTorchLlamaEngine::class.java.getDeclaredField("loadedModule")
        modField.isAccessible = true
        modField.set(engine, Any())

        val opId = "op_active_cancel_test"
        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 5
        )

        val job = launch(Dispatchers.Default) {
            engine.generateWithBudget(opId, plan) { _, _ -> }
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        // Now JNI has started
        assertEquals(OperationState.JNI_STARTED, engine.activeOperationState)

        val cancelResult = engine.stop(opId)
        assertEquals(CancellationResult.CANCEL_ACCEPTED_ACTIVE, cancelResult)

        // Calling stop again should return CANCEL_ALREADY_REQUESTED
        val repeatCancel = engine.stop(opId)
        assertEquals(CancellationResult.CANCEL_ALREADY_REQUESTED, repeatCancel)

        job.join()
        assertTrue(stopCalled.get())
    }

    @Test
    fun testPendingStopBarrierProtectsSubsequentOperation() = runBlocking {
        val stopExecuted = AtomicBoolean(false)
        val secondOpStarted = AtomicBoolean(false)
        val genCount = AtomicInteger(0)

        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                val idx = genCount.incrementAndGet()
                if (idx == 1) {
                    Thread.sleep(150)
                } else {
                    secondOpStarted.set(true)
                    onToken("tokenB")
                }
                return 0
            }
            override fun stop(moduleInstance: Any) {
                Thread.sleep(100) // Delay in stop dispatch
                stopExecuted.set(true)
            }
        }

        val engine = ExecuTorchLlamaEngine(assetManager, runtimeBridge = mockBridge)
        val stateField = ExecuTorchLlamaEngine::class.java.getDeclaredField("_runtimeState")
        stateField.isAccessible = true
        (stateField.get(engine) as kotlinx.coroutines.flow.MutableStateFlow<ModelRuntimeState>).value = ModelRuntimeState.READY
        val modField = ExecuTorchLlamaEngine::class.java.getDeclaredField("loadedModule")
        modField.isAccessible = true
        modField.set(engine, Any())

        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 5
        )

        val opA = "op_A_barrier"
        val opB = "op_B_barrier"

        val jobA = launch(Dispatchers.Default) {
            engine.generateWithBudget(opA, plan) { _, _ -> }
        }

        while (engine.activeOperationState != OperationState.JNI_STARTED) {
            delay(10)
        }
        engine.stop(opA)

        val jobB = launch(Dispatchers.Default) {
            jobA.join()
            // When jobA finishes, stop must have fully returned
            assertTrue("Stop on Op A must complete before Op B can proceed", stopExecuted.get())
            val resB = engine.generateWithBudget(opB, plan) { _, _ -> }
            assertTrue(resB.isSuccess)
        }

        jobB.join()
        assertTrue(secondOpStarted.get())
    }

    // ── 5. History Bounding & Token Limits ──────────────────────────────────────

    @Test
    fun testCompletedOperationsLruHistoryBoundedTo100() = runBlocking {
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                onToken("Token")
                return 0
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

        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 5
        )

        for (i in 1..110) {
            val res = engine.generateWithBudget("op_batch_$i", plan) { _, _ -> }
            assertTrue(res.isSuccess)
        }

        val mapField = ExecuTorchLlamaEngine::class.java.getDeclaredField("completedOperations")
        mapField.isAccessible = true
        val map = mapField.get(engine) as Map<*, *>
        assertTrue("Completed operations map must be bounded to at most 100 entries", map.size <= 100)
    }

    @Test
    fun testSpecialTokensTrimmedFromGenerationOutput() = runBlocking {
        val mockBridge = object : ExecuTorchLlamaEngine.NativeLlmBridge {
            override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any = Any()
            override fun generate(moduleInstance: Any, prompt: String, maxNewTokens: Int, onToken: (String) -> Unit, onStats: (Float) -> Unit): Int {
                onToken("Hello world")
                onToken("<|eot_id|>")
                return 0
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

        val plan = ExactPromptBudgeter.BudgetPlan(
            formattedPrompt = "Test",
            exactPrefillTokens = 5,
            allowedOutputTokens = 10
        )

        val result = engine.generateWithBudget("op_tokens_test", plan) { _, _ -> }
        assertTrue(result.isSuccess)
        val metrics = result.getOrNull()
        assertNotNull(metrics)
        assertEquals("Hello world", metrics?.generatedText)
        assertFalse(metrics?.generatedText?.contains("<|eot_id|>") == true)
    }
}

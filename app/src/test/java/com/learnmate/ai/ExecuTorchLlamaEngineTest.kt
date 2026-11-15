package com.learnmate.ai

import com.learnmate.ai.ai.ActiveModelManifest
import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.ai.ModelRuntimeState
import com.learnmate.ai.ai.PromptBudgeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ExecuTorchLlamaEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var assetManager: ModelAssetManager

    @Before
    fun setUp() {
        appFilesDir = tempFolder.newFolder("app_files")
        assetManager = ModelAssetManager(appFilesDir)
    }

    class FakeLlmBridge(
        var loadShouldFail: Boolean = false,
        var generateDelayMs: Long = 10L,
        var tokensToEmit: List<String> = listOf("Hello", ", ", "world", "!")
    ) : ExecuTorchLlamaEngine.NativeLlmBridge {

        val concurrentCallCounter = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)
        var stopCallCount = 0

        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
            if (loadShouldFail) {
                throw IllegalStateException("Simulated native load failure")
            }
            return "FakeNativeModuleInstance"
        }

        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            val current = concurrentCallCounter.incrementAndGet()
            maxObservedConcurrency.updateAndGet { maxOf(it, current) }

            try {
                if (generateDelayMs > 0) {
                    Thread.sleep(generateDelayMs)
                }
                for (token in tokensToEmit) {
                    onToken(token)
                }
                onStats(15.5f)
                return 0
            } finally {
                concurrentCallCounter.decrementAndGet()
            }
        }

        override fun stop(moduleInstance: Any) {
            stopCallCount++
        }
    }

    @Test
    fun testPromptBudgeter_enforcesBounds() {
        val prompt = "What is a PN junction diode?"
        val plan = PromptBudgeter.planPromptBudget(prompt, requestedMaxOutput = 256)

        assertTrue(plan.estimatedPromptTokens in 5..20)
        assertTrue(plan.allowedOutputTokens <= ActiveModelManifest.MAX_SEQ_LENGTH)
        assertEquals(prompt, plan.formattedPrompt)
    }

    @Test
    fun testPromptBudgeter_indicScriptEstimation() {
        val indicPrompt = "ஒரு குறைக்கடத்தி என்றால் என்ன?"
        val estimated = PromptBudgeter.estimateTokens(indicPrompt)

        // Tamil multibyte chars should receive conservative token budgeting
        assertTrue(estimated > 10)
    }

    @Test
    fun testModelAssetManager_detectsMissingModel() {
        assertFalse(assetManager.isValidInstalledModel())
        runBlocking {
            val res = assetManager.ensureModelProvisioned()
            assertEquals(ModelRuntimeState.MODEL_MISSING, res.state)
            assertNotNull(res.errorMessage)
        }
    }

    @Test
    fun testExecuTorchLlamaEngine_loadSuccess() = runBlocking {
        val fakeBridge = FakeLlmBridge()
        val engine = ExecuTorchLlamaEngine(assetManager, fakeBridge)

        val res = engine.loadModel("dummy/path.pte", "dummy/tok.model")
        assertTrue(res.isSuccess)
        assertEquals(ModelRuntimeState.READY, engine.runtimeState.value)
        assertNotNull(engine.lastLoadTimeMs)
    }

    @Test
    fun testExecuTorchLlamaEngine_loadFailure() = runBlocking {
        val fakeBridge = FakeLlmBridge(loadShouldFail = true)
        val engine = ExecuTorchLlamaEngine(assetManager, fakeBridge)

        val res = engine.loadModel("dummy/path.pte", "dummy/tok.model")
        assertTrue(res.isFailure)
        assertEquals(ModelRuntimeState.MODEL_ERROR, engine.runtimeState.value)
        assertNotNull(engine.lastError.value)
    }

    @Test
    fun testExecuTorchLlamaEngine_generateStreamsTokens() = runBlocking {
        val fakeBridge = FakeLlmBridge(tokensToEmit = listOf("On-Device", " AI", " learning"))
        val engine = ExecuTorchLlamaEngine(assetManager, fakeBridge)

        engine.loadModel("dummy/path.pte", "dummy/tok.model")

        val accumulated = StringBuilder()
        val res = engine.generate("Explain physics") { full, delta ->
            accumulated.clear().append(full)
        }

        assertTrue(res.isSuccess)
        val metrics = res.getOrNull()!!
        assertEquals("On-Device AI learning", metrics.generatedText)
        assertEquals(3, metrics.tokenCount)
        assertTrue(metrics.tokensPerSecond > 0.0)
        assertEquals(ModelRuntimeState.READY, engine.runtimeState.value)
    }

    @Test
    fun testExecuTorchLlamaEngine_mutexEnforcesSingleThreadedSerialization() = runBlocking(Dispatchers.Default) {
        val fakeBridge = FakeLlmBridge(generateDelayMs = 40L)
        val engine = ExecuTorchLlamaEngine(assetManager, fakeBridge)

        engine.loadModel("dummy/path.pte", "dummy/tok.model")

        // Launch 4 concurrent requests
        val jobs = (1..4).map { i ->
            async {
                engine.generate("Prompt $i") { _, _ -> }
            }
        }
        val results = jobs.awaitAll()

        assertTrue(results.all { it.isSuccess })
        // Mutex ensures that maximum concurrency inside generate() is strictly 1
        assertEquals(1, fakeBridge.maxObservedConcurrency.get())
    }

    @Test
    fun testExecuTorchLlamaEngine_handlesEosTokenProperly() = runBlocking {
        val fakeBridge = FakeLlmBridge(tokensToEmit = listOf("Concept", " explained.", "<|eot_id|>", "extra junk"))
        val engine = ExecuTorchLlamaEngine(assetManager, fakeBridge)

        engine.loadModel("dummy/path.pte", "dummy/tok.model")

        val res = engine.generate("Explain diode") { _, _ -> }
        assertTrue(res.isSuccess)
        val text = res.getOrNull()!!.generatedText
        assertEquals("Concept explained.", text)
        assertFalse(text.contains("extra junk"))
    }
}


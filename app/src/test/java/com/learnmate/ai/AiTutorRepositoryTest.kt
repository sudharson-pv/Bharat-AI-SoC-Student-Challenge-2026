package com.learnmate.ai

import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
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

class AiTutorRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appFilesDir: File
    private lateinit var assetManager: ModelAssetManager
    private lateinit var learningRepository: LearningRepository

    class TestLlmBridge(
        var generateCalledCount: AtomicInteger = AtomicInteger(0),
        var shouldThrow: Boolean = false
    ) : ExecuTorchLlamaEngine.NativeLlmBridge {

        override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
            return "TestNativeModule"
        }

        override fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int {
            generateCalledCount.incrementAndGet()
            if (shouldThrow) {
                throw IllegalStateException("Simulated native JNI failure")
            }
            onToken("A p-n junction diode operates by carrier diffusion.")
            onStats(12.0f)
            return 0
        }

        override fun stop(moduleInstance: Any) {}
    }

    @Before
    fun setUp() {
        appFilesDir = tempFolder.newFolder("app_files")
        assetManager = ModelAssetManager(appFilesDir)
        learningRepository = LearningRepository()
    }

    @Test
    fun testEducationalSafeguard_aiDisabledServesVerifiedCurriculumWithZeroLlmCalls() = runBlocking {
        val testBridge = TestLlmBridge()
        val engine = ExecuTorchLlamaEngine(assetManager, testBridge)
        val aiRepo = AiTutorRepository(engine, assetManager, learningRepository)

        // Disable AI explicitly
        aiRepo.setAiEnabled(false)
        assertFalse(aiRepo.isAiEnabled.value)

        val session = learningRepository.createChatSession("user_1", "Electronics and VLSI", "Diode Study", false)

        var streamedText = ""
        val result = aiRepo.askTutor(
            sessionId = session.sessionId,
            userId = "user_1",
            userQuestion = "Explain PN junction diode",
            currentTopic = "PN Junction Physics & Diodes"
        ) { fullText, _ ->
            streamedText = fullText
        }

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()!!

        // Educational Safeguards verification
        assertFalse("Message must NOT be flagged as AI generated", msg.isAiGenerated)
        assertEquals("Zero LLM generate calls must occur when AI is disabled", 0, testBridge.generateCalledCount.get())
        assertTrue("Must contain verified curriculum reference header", msg.text.contains("Verified Curriculum Reference"))
        assertTrue("Must explain p-n junction verified physics", msg.text.contains("space-charge depletion region"))
        assertTrue("Must include educational disclaimer", msg.text.contains(AiTutorRepository.DISCLAIMER))
    }

    @Test
    fun testEducationalSafeguard_aiEnabledInvokesEngineAndAppendsDisclaimer() = runBlocking {
        val testBridge = TestLlmBridge()
        val engine = ExecuTorchLlamaEngine(assetManager, testBridge)
        engine.loadModel("dummy/model.pte", "dummy/tok.model")

        val aiRepo = AiTutorRepository(engine, assetManager, learningRepository)
        aiRepo.setAiEnabled(true)
        assertTrue(aiRepo.isAiEnabled.value)

        val session = learningRepository.createChatSession("user_2", "Electronics and VLSI", "Active Chat", true)

        val result = aiRepo.askTutor(
            sessionId = session.sessionId,
            userId = "user_2",
            userQuestion = "What is a diode?"
        ) { _, _ -> }

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()!!

        assertTrue("Message must be truthfully flagged as AI generated", msg.isAiGenerated)
        assertEquals("LLM generate must be called exactly once", 1, testBridge.generateCalledCount.get())
        assertTrue(msg.text.contains("A p-n junction diode operates by carrier diffusion"))
        assertTrue("Must include educational disclaimer", msg.text.contains(AiTutorRepository.DISCLAIMER))
    }

    @Test
    fun testEducationalSafeguard_fallbackToCurriculumOnEngineFailure() = runBlocking {
        val testBridge = TestLlmBridge(shouldThrow = true)
        val engine = ExecuTorchLlamaEngine(assetManager, testBridge)
        engine.loadModel("dummy/model.pte", "dummy/tok.model")

        val aiRepo = AiTutorRepository(engine, assetManager, learningRepository)
        aiRepo.setAiEnabled(true)

        val session = learningRepository.createChatSession("user_3", "Electronics and VLSI", "Failure Test", true)

        val result = aiRepo.askTutor(
            sessionId = session.sessionId,
            userId = "user_3",
            userQuestion = "Explain diode physics"
        ) { _, _ -> }

        assertTrue(result.isSuccess)
        val msg = result.getOrNull()!!

        assertFalse("Fallback message must not be flagged as AI generated", msg.isAiGenerated)
        assertTrue("Must indicate offline curriculum fallback", msg.text.contains("Offline Curriculum Fallback"))
        assertTrue("Must cite the model error note", msg.text.contains("Model notice"))
    }
}


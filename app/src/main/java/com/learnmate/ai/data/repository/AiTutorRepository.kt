package com.learnmate.ai.data.repository

import android.util.Log
import com.learnmate.ai.ai.ExecuTorchLlamaEngine
import com.learnmate.ai.ai.ModelAssetManager
import com.learnmate.ai.ai.ModelRuntimeState
import com.learnmate.ai.ai.PromptBudgeter
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.ChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * AI Tutor Repository integrating ExecuTorchLlamaEngine through clean application boundaries.
 * Keeps all native JNI execution, threading, and model memory away from UI ViewModels.
 *
 * Educational Safeguards:
 * 1. Supports architecture-level kill switch ([isAiEnabled]): when false, serves verified textbook curriculum.
 * 2. Connects [PromptBudgeter] to conversation history to enforce 512-token sequence limits.
 * 3. Transparently flags model responses as [isAiGenerated = true] vs verified curriculum [isAiGenerated = false].
 */
class AiTutorRepository(
    val engine: ExecuTorchLlamaEngine,
    val assetManager: ModelAssetManager,
    private val learningRepository: LearningRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "AiTutorRepository"
        const val DISCLAIMER = "Notice: AI-generated guidance is intended for conceptual study and should be cross-referenced with verified curriculum materials."
        const val VERIFIED_TEXTBOOK_MODE_NOTICE = "Verified Textbook Mode: Local generative AI synthesis is disabled by production policy. All explanations are retrieved directly from accredited textbook references with deterministic provenance."
    }

    // Educational Safeguard: Production AI kill switch active by default.
    // The learning platform operates with 100% verified textbook fallback until native acceptance tests pass.
    private val _isAiEnabled = MutableStateFlow(false)
    val isAiEnabled: StateFlow<Boolean> = _isAiEnabled.asStateFlow()

    val runtimeState: StateFlow<ModelRuntimeState> = engine.runtimeState
    val lastError: StateFlow<String?> = engine.lastError

    fun setAiEnabled(enabled: Boolean) {
        _isAiEnabled.value = enabled
        Log.i(TAG, "AI Tutor enabled state set to: $enabled")
        if (enabled && engine.runtimeState.value != ModelRuntimeState.READY && engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) {
            CoroutineScope(ioDispatcher).launch {
                initializeModelAsync()
            }
        }
    }

    /**
     * Initializes model assets and loads ExecuTorch engine in the background.
     */
    suspend fun initializeModelAsync(): Result<Unit> = withContext(ioDispatcher) {
        try {
            val provResult = assetManager.ensureModelProvisioned()
            if (provResult.state == ModelRuntimeState.MODEL_READY) {
                engine.loadModel(
                    modelPath = provResult.modelFile.absolutePath,
                    tokenizerPath = provResult.tokenizerFile.absolutePath,
                    temperature = 0.7f
                )
            } else {
                Result.failure(IllegalStateException(provResult.errorMessage ?: "Model provisioning incomplete"))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Background model initialization failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    /**
     * Submits a question to the AI tutor with token budgeting and educational fallback.
     */
    suspend fun askTutor(
        sessionId: String,
        userId: String,
        userQuestion: String,
        currentTopic: String = "",
        onTokenDelta: (fullText: String, delta: String) -> Unit
    ): Result<ChatMessage> = withContext(ioDispatcher) {
        val userMsgId = "msg_${UUID.randomUUID()}"
        val userMessage = ChatMessage(
            messageId = userMsgId,
            sessionId = sessionId,
            userId = userId,
            senderRole = "USER",
            text = userQuestion,
            isAiGenerated = false,
            timestampEpochMs = System.currentTimeMillis()
        )
        learningRepository.saveChatMessage(userMessage)

        // ── Safeguard: If AI is disabled, serve verified curriculum facts ─────────
        if (!_isAiEnabled.value) {
            val verifiedExplanation = resolveVerifiedCurriculum(userQuestion, currentTopic)
            val tutorText = "[Verified Textbook Mode — Verified Curriculum Reference — AI Guidance Disabled]\n\n$verifiedExplanation\n\n$VERIFIED_TEXTBOOK_MODE_NOTICE\n\n$DISCLAIMER"

            onTokenDelta(tutorText, tutorText)

            val tutorMsg = ChatMessage(
                messageId = "msg_${UUID.randomUUID()}",
                sessionId = sessionId,
                userId = userId,
                senderRole = "TUTOR",
                text = tutorText,
                isAiGenerated = false,
                timestampEpochMs = System.currentTimeMillis()
            )
            learningRepository.saveChatMessage(tutorMsg)
            return@withContext Result.success(tutorMsg)
        }

        // ── If AI is enabled: Enforce Prompt Budgeting with conversation history ─
        if (engine.runtimeState.value != ModelRuntimeState.READY && engine.runtimeState.value != ModelRuntimeState.MODEL_LOADED) {
            Log.i(TAG, "Model not loaded yet. Attempting on-demand initializeModelAsync()...")
            val initRes = initializeModelAsync()
            if (initRes.isFailure) {
                val err = initRes.exceptionOrNull()?.message ?: "Model load failed"
                Log.e(TAG, "On-demand model initialization failed: $err")
                val fallback = "[Offline Curriculum Fallback]\n\n" +
                    resolveVerifiedCurriculum(userQuestion, currentTopic) +
                    "\n\n(Model notice: On-device inference error: $err)"

                onTokenDelta(fallback, fallback)

                val errorMsg = ChatMessage(
                    messageId = "msg_${UUID.randomUUID()}",
                    sessionId = sessionId,
                    userId = userId,
                    senderRole = "TUTOR",
                    text = fallback,
                    isAiGenerated = false,
                    timestampEpochMs = System.currentTimeMillis()
                )
                learningRepository.saveChatMessage(errorMsg)
                return@withContext Result.success(errorMsg)
            }
        }

        val pastMessages = learningRepository.getMessagesForSession(sessionId)
            .filter { it.messageId != userMsgId } // exclude just-saved message
            .takeLast(4) // Retain up to 4 recent turns for sliding-window memory

        val formattedPrompt = buildBudgetedPrompt(pastMessages, userQuestion, currentTopic)

        val genResult = engine.generate(
            prompt = formattedPrompt,
            maxNewTokens = 192,
            onTokenDelta = onTokenDelta
        )

        return@withContext if (genResult.isSuccess) {
            val metrics = genResult.getOrNull()!!
            val rawText = metrics.generatedText
            val cleanedText = if (!rawText.contains(DISCLAIMER)) {
                "$rawText\n\n$DISCLAIMER"
            } else {
                rawText
            }

            val tutorMsg = ChatMessage(
                messageId = "msg_${UUID.randomUUID()}",
                sessionId = sessionId,
                userId = userId,
                senderRole = "TUTOR",
                text = cleanedText,
                isAiGenerated = true, // Transparent provenance
                promptTokensEstimate = metrics.promptTokensEstimate,
                generatedTokens = metrics.tokenCount,
                ttftMs = metrics.ttftMs,
                tokensPerSec = metrics.tokensPerSecond,
                timestampEpochMs = System.currentTimeMillis()
            )
            learningRepository.saveChatMessage(tutorMsg)
            Result.success(tutorMsg)
        } else {
            val err = genResult.exceptionOrNull()?.message ?: "Inference failed"
            // Fallback to verified curriculum on model error
            val fallback = "[Offline Curriculum Fallback]\n\n" +
                resolveVerifiedCurriculum(userQuestion, currentTopic) +
                "\n\n(Model notice: On-device inference error: $err)"

            onTokenDelta(fallback, fallback)

            val errorMsg = ChatMessage(
                messageId = "msg_${UUID.randomUUID()}",
                sessionId = sessionId,
                userId = userId,
                senderRole = "TUTOR",
                text = fallback,
                isAiGenerated = false,
                timestampEpochMs = System.currentTimeMillis()
            )
            learningRepository.saveChatMessage(errorMsg)
            Result.success(errorMsg)
        }
    }

    private fun buildBudgetedPrompt(
        history: List<ChatMessage>,
        newQuestion: String,
        currentTopic: String
    ): String {
        val topicContext = if (currentTopic.isNotBlank()) "Topic: $currentTopic. " else ""
        val systemInstruction = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n" +
            "You are LearnMate, a concise, accurate on-device educational tutor. ${topicContext}" +
            "Explain concepts clearly in 2 to 3 sentences.<|eot_id|>"

        val historyBuilder = java.lang.StringBuilder()
        for (m in history) {
            val role = if (m.senderRole == "USER") "user" else "assistant"
            historyBuilder.append("<|start_header_id|>$role<|end_header_id|>\n\n")
            val cleanHistoryText = m.text.substringBefore("\n\n*This explanation is generated").take(200)
            historyBuilder.append(cleanHistoryText)
            historyBuilder.append("<|eot_id|>")
        }

        val userInstruction = "<|start_header_id|>user<|end_header_id|>\n\n$newQuestion<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"

        var fullPrompt = "$systemInstruction$historyBuilder$userInstruction"

        // Enforce max sequence headroom
        if (PromptBudgeter.estimateTokens(fullPrompt) > (PromptBudgeter.MAX_SEQ_LENGTH - 64)) {
            // Trim history completely if budget is tight
            fullPrompt = "$systemInstruction$userInstruction"
        }
        return fullPrompt
    }

    private fun resolveVerifiedCurriculum(question: String, topicHint: String): String {
        val q = question.lowercase()

        // 1. School Education: CBSE Class 10 Science (NCERT)
        if (q.contains("chemical reaction") || q.contains("balancing") || q.contains("reactant") || q.contains("conservation of mass")) {
            return "According to the Law of Conservation of Mass, matter can neither be created nor destroyed in a chemical reaction. The total mass of elements present in the products must equal the total mass present in the reactants. Chemical equations are balanced by equating atom counts of each element on both sides without altering chemical formulas.\n\n[Verified Reference: Science Class X, NCERT (Rationalised 2023-24), Ch. 1: Chemical Reactions and Equations, Sec. 1.1, ISBN 978-81-7450-492-0]"
        }
        if (q.contains("acid") || q.contains("base") || q.contains("ph scale") || q.contains("salt") || q.contains("alkali")) {
            return "Acids produce hydrogen ions (H+(aq)) in aqueous solution, while bases generate hydroxide ions (OH-(aq)). The pH scale measures hydrogen ion concentration, ranging from 0 (strongly acidic) to 14 (strongly alkaline), with 7 denoting neutral water at 25 deg C.\n\n[Verified Reference: Science Class X, NCERT (Rationalised 2023-24), Ch. 2: Acids, Bases and Salts, Sec. 2.1-2.3, ISBN 978-81-7450-492-0]"
        }

        // 2. Diploma / Polytechnic: Basic Electrical & Applied Math
        if (q.contains("ohm's law") || q.contains("ohms law") || q.contains("kirchhoff") || q.contains("kcl") || q.contains("kvl")) {
            return "Ohm's law states that electric current through a conductor is directly proportional to voltage across it at constant temperature (V = I * R). Kirchhoff's Current Law (KCL) dictates that the algebraic sum of currents entering any node equals zero. Kirchhoff's Voltage Law (KVL) dictates that the algebraic sum of all potential differences around any closed circuit loop equals zero.\n\n[Verified Reference: Electrical and Electronic Principles and Technology by John Bird (6th Edition), Ch. 2: Circuit Theorems, Sec. 2.1-2.4, ISBN 978-1-138-67352-6]"
        }
        if (q.contains("matrix") || q.contains("determinant") || q.contains("cramer")) {
            return "A matrix is a rectangular array of numbers arranged in rows and columns. Determinants exist only for square matrices and scale volume transformations. Linear systems A*X = B can be solved via Cramer's Rule or Gaussian elimination provided det(A) != 0.\n\n[Verified Reference: Higher Engineering Mathematics by B.S. Grewal (44th Edition), Ch. 2: Linear Algebra, Sec. 2.1-2.8, ISBN 978-81-933284-9-1]"
        }

        // 3. Engineering & Technology: Digital Electronics & CS
        if (q.contains("karnaugh") || q.contains("k-map") || q.contains("kmap") || q.contains("boolean algebra") || q.contains("logic gate")) {
            return "Karnaugh Maps (K-Maps) provide a systematic pictorial method for minimizing Boolean expressions into minimal Sum of Products (SOP) or Product of Sums (POS). Cells are arranged in Gray code order so adjacent cells differ by exactly one binary variable, enabling elimination of redundant terms.\n\n[Verified Reference: Digital Design: With an Introduction to the Verilog HDL by M. Morris Mano and Michael D. Ciletti (6th Edition), Ch. 3: Gate-Level Minimization, Sec. 3.1-3.4, ISBN 978-0-13-454989-7]"
        }
        if (q.contains("big-o") || q.contains("complexity") || q.contains("binary search") || q.contains("algorithm")) {
            return "Asymptotic notation describes algorithm running time growth as input size n approaches infinity. Big-O O(g(n)) gives the asymptotic upper bound. Binary search operates on sorted arrays in O(log n) time by halving the search space at each iteration, compared to O(n) for linear scan.\n\n[Verified Reference: Introduction to Algorithms (CLRS) by Thomas H. Cormen et al. (3rd Edition), Ch. 3: Growth of Functions & Ch. 2, Sec. 2.3, ISBN 978-0-262-03384-8]"
        }

        // 4. Arts & Sciences: Physics (Halliday/Resnick)
        if (q.contains("newton") || q.contains("momentum") || q.contains("inertia") || q.contains("force")) {
            return "Newton's first law defines inertial reference frames where an isolated body maintains uniform velocity. The second law relates net external force to rate of change of linear momentum (F_net = dp/dt = m*a). The third law states forces occur in equal and opposite interaction pairs.\n\n[Verified Reference: Fundamentals of Physics by David Halliday, Robert Resnick, and Jearl Walker (10th Edition), Ch. 5: Force and Motion & Ch. 9: Center of Mass and Linear Momentum, ISBN 978-1-118-23072-5]"
        }

        // 5. Management & Commerce: Financial Accounting (Horngren)
        if (q.contains("accounting equation") || q.contains("double-entry") || q.contains("debit") || q.contains("credit") || q.contains("balance sheet")) {
            return "The fundamental accounting equation states: Assets = Liabilities + Owner Equity. Every financial transaction has dual economic effects recorded via debits (left side, increases assets/expenses) and credits (right side, increases liabilities/equity/revenue) to preserve balance sheet equilibrium.\n\n[Verified Reference: Introduction to Financial Accounting by Charles T. Horngren et al. (11th Edition), Ch. 2: Measuring Income to Assess Performance & The Balance Sheet, ISBN 978-0-13-325103-6]"
        }

        // 6. Competitive Examinations: UPSC Indian Polity (Laxmikanth)
        if (q.contains("preamble") || q.contains("fundamental rights") || q.contains("article 21") || q.contains("article 32") || q.contains("constitution")) {
            return "The Preamble embodies the basic philosophy and values of the Indian Constitution, declaring India a Sovereign, Socialist, Secular, Democratic Republic. Fundamental Rights under Part III (Articles 12-35) guarantee equality, liberty, and protection against discrimination, enforceable directly through the Supreme Court under Article 32.\n\n[Verified Reference: Indian Polity for Civil Services by M. Laxmikanth (6th Edition), Ch. 4: Preamble of the Constitution & Ch. 7: Fundamental Rights, McGraw Hill, ISBN 978-93-89538-47-2]"
        }

        // 7. Research Methodology: Hypotheses & Literature Review (Creswell)
        if (q.contains("hypothesis") || q.contains("null hypothesis") || q.contains("literature review") || q.contains("research methodology")) {
            return "A research hypothesis is a formal tentative statement predicting the relationship between two or more variables. The null hypothesis (H0) posits no statistically significant difference or effect, while the alternative hypothesis (H1) reflects the directional or non-directional expectation tested against sample data.\n\n[Verified Reference: Research Design: Qualitative, Quantitative, and Mixed Methods Approaches by John W. Creswell and J. David Creswell (5th Edition), Ch. 7: Research Questions and Hypotheses, SAGE Publications, ISBN 978-1-5063-8670-6]"
        }

        // 8. Vocational: Electrical Safety & Earthing (IS 3043 / NEC)
        if (q.contains("electrical safety") || q.contains("earthing") || q.contains("grounding") || q.contains("loto") || q.contains("lockout")) {
            return "Electrical safety requires de-energization and Lockout/Tagout (LOTO) verification prior to servicing. Equipment earthing provides a low-impedance path to ground so that protective overcurrent or residual current devices (RCD) trip promptly upon fault condition, preventing lethal touch potentials.\n\n[Verified Reference: IS 3043:2018 Code of Practice for Earthing, Bureau of Indian Standards & NFPA 70E Standard for Electrical Safety in the Workplace, Sec. 120-130]"
        }

        // Fallback to legacy TopicTaxonomy if matched
        val topics = TopicTaxonomy.getVerifiedTopics()
        val matchedByTopic = if (topicHint.isNotBlank()) {
            topics.firstOrNull {
                it.title.contains(topicHint, ignoreCase = true) ||
                    topicHint.contains(it.title, ignoreCase = true)
            }
        } else null

        val topic = matchedByTopic ?: topics.firstOrNull { t ->
            question.contains("pn junction", ignoreCase = true) && t.title.contains("PN Junction", ignoreCase = true) ||
                question.contains("diode", ignoreCase = true) && t.title.contains("PN Junction", ignoreCase = true) ||
                question.contains("cmos", ignoreCase = true) && t.title.contains("CMOS", ignoreCase = true) ||
                question.contains("inverter", ignoreCase = true) && t.title.contains("CMOS", ignoreCase = true) ||
                question.contains("timing", ignoreCase = true) && t.title.contains("Static Timing", ignoreCase = true) ||
                question.contains("setup", ignoreCase = true) && t.title.contains("Static Timing", ignoreCase = true) ||
                question.contains("hold time", ignoreCase = true) && t.title.contains("Static Timing", ignoreCase = true) ||
                question.contains("quantiz", ignoreCase = true) && t.title.contains("Quantization", ignoreCase = true) ||
                question.contains("persona", ignoreCase = true) && t.title.contains("Personas", ignoreCase = true) ||
                question.contains("journey map", ignoreCase = true) && t.title.contains("Personas", ignoreCase = true) ||
                question.contains("wcag", ignoreCase = true) && t.title.contains("WCAG", ignoreCase = true) ||
                question.contains("contrast", ignoreCase = true) && t.title.contains("WCAG", ignoreCase = true) ||
                question.contains("token", ignoreCase = true) && t.title.contains("Tokens", ignoreCase = true) ||
                question.contains("hooke", ignoreCase = true) && t.title.contains("Hooke", ignoreCase = true) ||
                question.contains("stress", ignoreCase = true) && t.title.contains("Stress", ignoreCase = true) ||
                question.contains("strain", ignoreCase = true) && t.title.contains("Stress", ignoreCase = true) ||
                question.contains("thermodynamic", ignoreCase = true) && t.title.contains("Thermodynamics", ignoreCase = true) ||
                question.contains("entropy", ignoreCase = true) && t.title.contains("Thermodynamics", ignoreCase = true) ||
                question.contains("conduction", ignoreCase = true) && t.title.contains("Conduction", ignoreCase = true) ||
                question.contains("heat exchanger", ignoreCase = true) && t.title.contains("Heat Exchangers", ignoreCase = true)
        }

        if (topic != null) {
            val citation = if (topic.referenceTitle.isNotBlank()) {
                "\n\n[Verified Reference: ${topic.referenceTitle} by ${topic.referenceAuthor} (${topic.referenceEdition}), Ch. ${topic.referenceChapter}, Sec. ${topic.referenceSection}, ${topic.referenceIdentifier}]"
            } else ""
            return "${topic.verifiedExplanation}$citation"
        }

        return "[Notice: This topic is not covered in the verified on-device curriculum syllabus. Please consult standard accredited textbooks or connect with an instructor for this subject area.]"
    }
}


package com.learnmate.ai.ai

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-Device ExecuTorch 1.4.0 Llama Engine (Gate 2 Inference Spike).
 * - Executes model loading and token generation on a dedicated single-thread dispatcher.
 * - Guarded by [engineMutex] ensuring strictly serialized native execution.
 * - Bridges to org.pytorch.executorch.extension.llm.LlmModule and LlmCallback.
 * - Never fabricates fake responses or simulated streaming.
 */
class ExecuTorchLlamaEngine(
    private val assetManager: ModelAssetManager,
    private val runtimeBridge: NativeLlmBridge = ReflectionExecuTorchLlmBridge(),
    private val inferenceDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "ExecuTorch-Inference-Thread").apply { isDaemon = true }
        }.asCoroutineDispatcher()
) {
    companion object {
        private const val TAG = "ExecuTorchEngine"
    }

    enum class OperationState {
        QUEUED,
        REGISTERED,
        JNI_STARTED,
        CANCEL_REQUESTED,
        NATIVE_RETURNED,
        COMPLETED,
        CANCEL_UNSUPPORTED
    }

    enum class CancellationResult {
        CANCEL_ACCEPTED_ACTIVE,
        CANCELLED_BEFORE_NATIVE_START,
        OPERATION_QUEUED_NOT_ACTIVE,
        OPERATION_ALREADY_COMPLETED,
        OPERATION_ID_MISMATCH,
        NATIVE_CANCEL_UNSUPPORTED,
        CANCEL_ALREADY_REQUESTED
    }

    interface NativeLlmBridge {
        fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any
        fun generate(
            moduleInstance: Any,
            prompt: String,
            maxNewTokens: Int,
            onToken: (String) -> Unit,
            onStats: (Float) -> Unit
        ): Int
        fun stop(moduleInstance: Any)
    }

    data class GenerationMetrics(
        val generatedText: String,
        val tokenCount: Int,
        val ttftMs: Long,
        val totalElapsedMs: Long,
        val tokensPerSecond: Double,
        val promptTokensEstimate: Int,
        val allowedOutputTokens: Int
    )

    private val engineMutex = Mutex()
    private val cancellationLock = Object()
    private val nativeControlDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "ExecuTorch-Control-Thread").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    var activeOperationId: String? = null
        private set
    var activeOperationState: OperationState = OperationState.COMPLETED
        private set
    private var activeGeneration: Long = 0L
    private var isStopDispatchInProgress: Boolean = false
    private var stopDispatchTargetGeneration: Long = -1L
    private var pendingStopBarrierDeferred: CompletableDeferred<Unit>? = null

    private val queuedOperations = mutableSetOf<String>()
    private val cancelledQueuedOperations = mutableSetOf<String>()
    private val completedOperations = object : LinkedHashMap<String, Long>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 100
    }

    @Volatile var onNativeInvocationBoundary: ((String) -> Unit)? = null
    @Volatile var onStopDispatched: ((String) -> Unit)? = null
    @Volatile var onStopReturned: ((String, Boolean) -> Unit)? = null

    private var loadedModule: Any? = null
    var loadedModelPath: String? = null
        private set
    private var activeCancellation = AtomicBoolean(false)

    private val _runtimeState = MutableStateFlow(ModelRuntimeState.MODEL_MISSING)
    val runtimeState: StateFlow<ModelRuntimeState> = _runtimeState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    var lastLoadTimeMs: Long? = null
        private set

    @Volatile
    var lastTtftMs: Long? = null
        private set

    @Volatile
    var lastGeneratedTokens: Int? = null
        private set

    @Volatile
    var lastDecodeTokensPerSec: Double? = null
        private set

    suspend fun refreshModelPresenceState(): ModelRuntimeState = withContext(Dispatchers.IO) {
        if (loadedModule != null && _runtimeState.value == ModelRuntimeState.READY) {
            return@withContext ModelRuntimeState.READY
        }
        val isVerified = assetManager.isCryptographicallyVerifiedModel()
        val nextState = if (isVerified) {
            ModelRuntimeState.VERIFIED
        } else {
            val validSize = assetManager.isValidInstalledModel()
            if (validSize) ModelRuntimeState.DOWNLOADED else ModelRuntimeState.NOT_INSTALLED
        }
        _runtimeState.value = nextState
        if (nextState == ModelRuntimeState.NOT_INSTALLED) {
            _lastError.value = "Quantized model (${ActiveModelManifest.MODEL_FILENAME}) not found in ${assetManager.modelsDirectory.absolutePath}."
        } else {
            _lastError.value = null
        }
        nextState
    }

    suspend fun loadModel(
        modelPath: String = assetManager.activeModelFile.absolutePath,
        tokenizerPath: String = assetManager.activeTokenizerFile.absolutePath,
        temperature: Float = 0.8f,
        runSmokeTest: Boolean = false
    ): Result<Unit> = engineMutex.withLock {
        withContext(inferenceDispatcher) {
            if (loadedModule != null && loadedModelPath == modelPath && _runtimeState.value == ModelRuntimeState.READY) {
                return@withContext Result.success(Unit)
            }

            _runtimeState.value = ModelRuntimeState.INITIALIZING
            _lastError.value = null

            // Security Gate 6.16.5 & 6.16.6: Verify asset integrity and format before native memory mapping
            val modelFile = java.io.File(modelPath)
            val tokFile = java.io.File(tokenizerPath)
            if (assetManager.isGgufFile(modelFile)) {
                val err = "Format Rejection: GGUF files cannot be loaded through the ExecuTorch PTE loader. Use PyTorch export to generate .pte."
                Log.e(TAG, err)
                _lastError.value = err
                _runtimeState.value = ModelRuntimeState.INITIALIZATION_FAILED
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            val expectedEntry = ModelCatalog.ALL_ENTRIES.firstOrNull { it.modelFileName == modelFile.name } ?: ModelCatalog.ENTRY_LLAMA_3_2_1B
            if (runtimeBridge is ReflectionExecuTorchLlmBridge) {
                if (!modelFile.exists() || modelFile.length() != expectedEntry.modelSizeBytes ||
                    !tokFile.exists() || tokFile.length() != expectedEntry.tokenizerSizeBytes) {
                    val err = "Security Pre-Condition Failed: Cannot load unverified or truncated model assets into ExecuTorch JNI (model=${if (modelFile.exists()) modelFile.length() else 0}, tok=${if (tokFile.exists()) tokFile.length() else 0})"
                    Log.e(TAG, err)
                    _lastError.value = err
                    _runtimeState.value = ModelRuntimeState.INITIALIZATION_FAILED
                    return@withContext Result.failure(IllegalStateException(err))
                }
            }

            Log.i(TAG, "Loading ExecuTorch LlmModule: model=$modelPath, tok=$tokenizerPath")
            val t0 = SystemClock.elapsedRealtime()

            val instance = try {
                val mod = runtimeBridge.createAndLoadModule(
                    modelPath = modelPath,
                    tokenizerPath = tokenizerPath,
                    temperature = temperature
                )
                val loadElapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                loadedModule = mod
                loadedModelPath = modelPath
                lastLoadTimeMs = loadElapsed
                Log.i(TAG, "LlmModule loaded successfully into native memory in ${loadElapsed}ms")
                mod
            } catch (t: Throwable) {
                loadedModule = null
                loadedModelPath = null
                val actual = if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
                val msg = "Native ExecuTorch LlmModule load failed: ${actual.javaClass.simpleName}: ${actual.message}"
                Log.e(TAG, msg, actual)
                _lastError.value = msg
                _runtimeState.value = ModelRuntimeState.INITIALIZATION_FAILED
                return@withContext Result.failure(IllegalStateException(msg, actual))
            }

            if (runSmokeTest) {
                Log.i(TAG, "Executing post-load verification smoke test...")
                val smokePrompt = "Explain what a CMOS inverter is in simple terms."
                val budget = PromptBudgeter.planPromptBudget(smokePrompt, requestedMaxOutput = 32)
                val smokeResult = executeGenerationInternal(
                    module = instance,
                    formattedPrompt = budget.formattedPrompt,
                    allowedOutputTokens = budget.allowedOutputTokens,
                    promptTokensEstimate = budget.estimatedPromptTokens,
                    onTokenDelta = { _, _ -> }
                )
                if (smokeResult.isSuccess && (smokeResult.getOrNull()?.tokenCount ?: 0) > 0) {
                    _runtimeState.value = ModelRuntimeState.READY
                    _lastError.value = null
                    Log.i(TAG, "Smoke test succeeded: generated ${smokeResult.getOrNull()?.tokenCount} tokens")
                    Result.success(Unit)
                } else {
                    val cause = smokeResult.exceptionOrNull()
                    val actual = if (cause is java.lang.reflect.InvocationTargetException) cause.targetException ?: cause else cause
                    val err = "Inference smoke test failed: ${actual?.message ?: "0 tokens produced"}"
                    Log.e(TAG, err, actual)
                    _lastError.value = err
                    _runtimeState.value = ModelRuntimeState.INFERENCE_FAILED
                    Result.failure(IllegalStateException(err, actual))
                }
            } else {
                _runtimeState.value = ModelRuntimeState.READY
                Result.success(Unit)
            }
        }
    }

    /**
     * Master Phase 4: Safely switches active model with pre-flight verification and automatic rollback.
     * Prevents leaving the application without a working model if target model initialization fails.
     */
    suspend fun switchModel(
        targetEntry: ModelCatalogEntry,
        temperature: Float = 0.8f
    ): Result<Unit> = engineMutex.withLock {
        withContext(inferenceDispatcher) {
            Log.i(TAG, "Initiating safe model switch to: ${targetEntry.displayName} (${targetEntry.modelId})")

            val targetModelFile = assetManager.getModelFile(targetEntry.modelId)
            val targetTokFile = assetManager.getTokenizerFile(targetEntry.modelId)

            // Step 1: Pre-flight validation prior to touching the currently loaded active model
            if (assetManager.isGgufFile(targetModelFile)) {
                val err = "Format Rejection: GGUF format not supported by ExecuTorch PTE loader"
                Log.e(TAG, err)
                _lastError.value = err
                return@withContext Result.failure(IllegalArgumentException(err))
            }
            if (!targetModelFile.exists() || !targetModelFile.isFile || targetModelFile.length() != targetEntry.modelSizeBytes) {
                val err = "Target model verification failed: File missing or size mismatch (${targetModelFile.length()} vs expected ${targetEntry.modelSizeBytes})"
                Log.e(TAG, err)
                _lastError.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }
            if (!targetTokFile.exists() || !targetTokFile.isFile || targetTokFile.length() != targetEntry.tokenizerSizeBytes) {
                val err = "Target tokenizer verification failed: File missing or size mismatch"
                Log.e(TAG, err)
                _lastError.value = err
                return@withContext Result.failure(IllegalStateException(err))
            }

            // Step 2: Preserve previous active references for reliable rollback
            val previousModule = loadedModule
            val previousModelPath = loadedModelPath
            val previousEntry = ModelCatalog.ALL_ENTRIES.firstOrNull { it.modelFileName == File(previousModelPath ?: "").name }
                ?: ModelCatalog.ENTRY_LLAMA_3_2_1B
            val previousTokFile = assetManager.getTokenizerFile(previousEntry.modelId)

            // Step 3: Unload previous active model
            if (previousModule != null) {
                try {
                    runtimeBridge.stop(previousModule)
                } catch (e: Exception) {
                    Log.w(TAG, "Notice during previous module stop: ${e.message}")
                }
                loadedModule = null
                loadedModelPath = null
            }
            _runtimeState.value = ModelRuntimeState.LOADING_MODEL

            // Step 4: Attempt native loading of target model
            val t0 = SystemClock.elapsedRealtime()
            try {
                val instance = runtimeBridge.createAndLoadModule(
                    modelPath = targetModelFile.absolutePath,
                    tokenizerPath = targetTokFile.absolutePath,
                    temperature = temperature
                )
                val loadElapsed = (SystemClock.elapsedRealtime() - t0).coerceAtLeast(1L)
                loadedModule = instance
                loadedModelPath = targetModelFile.absolutePath
                lastLoadTimeMs = loadElapsed
                _runtimeState.value = ModelRuntimeState.MODEL_LOADED
                _runtimeState.value = ModelRuntimeState.READY
                _lastError.value = null
                Log.i(TAG, "Switched successfully to ${targetEntry.displayName} in ${loadElapsed}ms")
                Result.success(Unit)
            } catch (t: Throwable) {
                val targetError = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "Failed to load target model ${targetEntry.displayName}: $targetError. Initiating automatic rollback.", t)

                // Step 5: Rollback Recovery Path to previous working model
                if (previousModelPath != null && File(previousModelPath).exists() && previousTokFile.exists()) {
                    try {
                        Log.i(TAG, "Executing rollback to previous model: $previousModelPath")
                        val rollbackInstance = runtimeBridge.createAndLoadModule(
                            modelPath = previousModelPath,
                            tokenizerPath = previousTokFile.absolutePath,
                            temperature = temperature
                        )
                        loadedModule = rollbackInstance
                        loadedModelPath = previousModelPath
                        _runtimeState.value = ModelRuntimeState.READY
                        val rollbackMsg = "Failed to switch to ${targetEntry.displayName} ($targetError). Successfully rolled back to previous active model."
                        _lastError.value = rollbackMsg
                        Log.i(TAG, rollbackMsg)
                        return@withContext Result.failure(IllegalStateException(rollbackMsg, t))
                    } catch (rollbackError: Throwable) {
                        Log.e(TAG, "Fatal: Rollback to previous model also failed: ${rollbackError.message}", rollbackError)
                    }
                }

                loadedModule = null
                loadedModelPath = null
                val fatalMsg = "Failed to switch to ${targetEntry.displayName}: $targetError, and rollback was unavailable."
                Log.e(TAG, fatalMsg)
                _lastError.value = fatalMsg
                _runtimeState.value = ModelRuntimeState.MODEL_ERROR
                Result.failure(IllegalStateException(fatalMsg, t))
            }
        }
    }

    suspend fun generateWithBudget(
        operationId: String,
        plan: ExactPromptBudgeter.BudgetPlan,
        onTokenDelta: (fullAccumulatedText: String, deltaToken: String) -> Unit
    ): Result<GenerationMetrics> {
        return executeGeneration(
            operationId = operationId,
            formattedPrompt = plan.formattedPrompt,
            allowedOutputTokens = plan.allowedOutputTokens,
            promptTokensEstimate = plan.exactPrefillTokens,
            onTokenDelta = onTokenDelta
        )
    }

    suspend fun generateWithBudget(
        plan: ExactPromptBudgeter.BudgetPlan,
        onTokenDelta: (fullAccumulatedText: String, deltaToken: String) -> Unit
    ): Result<GenerationMetrics> = generateWithBudget(
        operationId = "op_${UUID.randomUUID()}",
        plan = plan,
        onTokenDelta = onTokenDelta
    )

    suspend fun generate(
        prompt: String,
        maxNewTokens: Int = ActiveModelManifest.MAX_NEW_TOKENS,
        onTokenDelta: (fullAccumulatedText: String, deltaToken: String) -> Unit
    ): Result<GenerationMetrics> {
        val budget = PromptBudgeter.planPromptBudget(
            prompt,
            maxNewTokens.coerceAtMost(ActiveModelManifest.MAX_NEW_TOKENS)
        )
        return executeGeneration(
            operationId = "op_${UUID.randomUUID()}",
            formattedPrompt = budget.formattedPrompt,
            allowedOutputTokens = budget.allowedOutputTokens,
            promptTokensEstimate = budget.estimatedPromptTokens,
            onTokenDelta = onTokenDelta
        )
    }

    suspend fun runSmokeTest(
        prompt: String = "Explain what a CMOS inverter is in simple terms."
    ): Result<GenerationMetrics> {
        val result = generate(prompt, maxNewTokens = 32) { _, _ -> }
        if (result.isSuccess && (result.getOrNull()?.tokenCount ?: 0) > 0) {
            _runtimeState.value = ModelRuntimeState.READY
            _lastError.value = null
        } else {
            _runtimeState.value = ModelRuntimeState.INFERENCE_FAILED
            if (_lastError.value == null) {
                _lastError.value = "Inference smoke test failed: Zero tokens produced."
            }
        }
        return result
    }

    private suspend fun executeGeneration(
        operationId: String,
        formattedPrompt: String,
        allowedOutputTokens: Int,
        promptTokensEstimate: Int,
        onTokenDelta: (fullAccumulatedText: String, deltaToken: String) -> Unit
    ): Result<GenerationMetrics> {
        // Enforce operation ID uniqueness across active, queued, and recently completed cache
        synchronized(cancellationLock) {
            if (operationId == activeOperationId || queuedOperations.contains(operationId) || completedOperations.containsKey(operationId)) {
                return Result.failure(IllegalArgumentException("Duplicate operation ID rejected: $operationId"))
            }
            queuedOperations.add(operationId)
        }

        return try {
            engineMutex.withLock {
                var currentOpGeneration: Long
                var shouldAbortPreJni = false

                synchronized(cancellationLock) {
                    queuedOperations.remove(operationId)
                    if (cancelledQueuedOperations.remove(operationId)) {
                        completedOperations[operationId] = SystemClock.elapsedRealtime()
                        return@withLock Result.failure(
                            CancellationException("Operation $operationId was cancelled while queued awaiting mutex")
                        )
                    }
                    activeGeneration++
                    currentOpGeneration = activeGeneration
                    activeOperationId = operationId
                    activeOperationState = OperationState.REGISTERED
                }

                try {
                    val module = withContext(inferenceDispatcher) { loadedModule }
                    if (module == null) {
                        return@withLock Result.failure(
                            IllegalStateException("Model not loaded. Call loadModel() first.")
                        )
                    }

                    // Native invocation boundary check
                    onNativeInvocationBoundary?.invoke(operationId)

                    synchronized(cancellationLock) {
                        if (activeOperationState == OperationState.CANCEL_REQUESTED) {
                            shouldAbortPreJni = true
                        } else {
                            activeOperationState = OperationState.JNI_STARTED
                        }
                    }

                    if (shouldAbortPreJni) {
                        return@withLock Result.failure(
                            CancellationException("Operation $operationId aborted prior to native JNI start (CANCELLED_BEFORE_NATIVE_START)")
                        )
                    }

                    withContext(inferenceDispatcher) {
                        var genRes = executeGenerationInternal(
                            module = module,
                            formattedPrompt = formattedPrompt,
                            allowedOutputTokens = allowedOutputTokens,
                            promptTokensEstimate = promptTokensEstimate,
                            onTokenDelta = onTokenDelta
                        )
                        if (genRes.isFailure) {
                            val err = genRes.exceptionOrNull()?.message ?: ""
                            if (err.contains("0x12") || err.contains("Invalid argument") || err.contains("start_pos") || err.contains("cache")) {
                                Log.w(TAG, "Native KV cache limit reached ($err). Resetting module and retrying generation...")
                                try {
                                    runtimeBridge.stop(module)
                                } catch (_: Exception) {}
                                val modelPath = loadedModelPath ?: assetManager.activeModelFile.absolutePath
                                val tokPath = assetManager.activeTokenizerFile.absolutePath
                                try {
                                    val freshModule = runtimeBridge.createAndLoadModule(modelPath, tokPath, 0.7f)
                                    loadedModule = freshModule
                                    genRes = executeGenerationInternal(
                                        module = freshModule,
                                        formattedPrompt = formattedPrompt,
                                        allowedOutputTokens = allowedOutputTokens,
                                        promptTokensEstimate = promptTokensEstimate,
                                        onTokenDelta = onTokenDelta
                                    )
                                } catch (reloadErr: Throwable) {
                                    Log.e(TAG, "Module reload recovery failed: ${reloadErr.message}", reloadErr)
                                }
                            }
                        }
                        genRes
                    }
                } finally {
                    val barrierToAwait = synchronized(cancellationLock) {
                        activeOperationState = OperationState.NATIVE_RETURNED
                        if (isStopDispatchInProgress && stopDispatchTargetGeneration == currentOpGeneration) {
                            pendingStopBarrierDeferred
                        } else null
                    }

                    // Suspend cooperatively until any in-flight native stop dispatch targeted at this generation completes
                    barrierToAwait?.await()

                    synchronized(cancellationLock) {
                        activeOperationState = OperationState.COMPLETED
                        activeOperationId = null
                        completedOperations[operationId] = SystemClock.elapsedRealtime()
                    }
                }
            }
        } finally {
            synchronized(cancellationLock) {
                queuedOperations.remove(operationId)
                cancelledQueuedOperations.remove(operationId)
            }
        }
    }

    private fun executeGenerationInternal(
        module: Any,
        formattedPrompt: String,
        allowedOutputTokens: Int,
        promptTokensEstimate: Int,
        onTokenDelta: (fullAccumulatedText: String, deltaToken: String) -> Unit
    ): Result<GenerationMetrics> {
        // Defense in depth: callers of generateWithBudget can construct a plan
        // directly, so enforce the authoritative cap at the native boundary too.
        val cappedOutputTokens = allowedOutputTokens.coerceIn(1, ActiveModelManifest.MAX_NEW_TOKENS)
        _runtimeState.value = ModelRuntimeState.GENERATING
        _lastError.value = null
        activeCancellation.set(false)

        val builder = StringBuilder()
        var tokenCallbacks = 0
        val startMs = SystemClock.elapsedRealtime()
        var firstTokenMs: Long? = null
        var reportedTps: Float? = null

        return try {
            Log.i(
                TAG,
                "Starting generation (promptTokens~=$promptTokensEstimate, allowedOutput=$allowedOutputTokens)"
            )

            val status = runtimeBridge.generate(
                moduleInstance = module,
                prompt = formattedPrompt,
                maxNewTokens = cappedOutputTokens,
                onToken = { rawToken ->
                    if (activeCancellation.get() || tokenCallbacks >= cappedOutputTokens) {
                        activeCancellation.set(true)
                        runtimeBridge.stop(module)
                        return@generate
                    }
                    if (rawToken.contains("<|eot_id|>") || rawToken.contains("<|end_of_text|>")) {
                        val cleaned = rawToken
                            .replace("<|eot_id|>", "")
                            .replace("<|end_of_text|>", "")
                        if (cleaned.isNotEmpty()) {
                            builder.append(cleaned)
                            onTokenDelta(builder.toString(), cleaned)
                        }
                        activeCancellation.set(true)
                        runtimeBridge.stop(module)
                        return@generate
                    }
                    if (rawToken.isNotEmpty()) {
                        if (firstTokenMs == null) {
                            firstTokenMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
                            Log.i(TAG, "First token received in ${firstTokenMs}ms")
                        }
                        tokenCallbacks += 1
                        builder.append(rawToken)
                        onTokenDelta(builder.toString(), rawToken)
                        if (tokenCallbacks >= cappedOutputTokens) {
                            activeCancellation.set(true)
                            runtimeBridge.stop(module)
                        }
                    }
                },
                onStats = { tps ->
                    if (tps > 0f) reportedTps = tps
                }
            )

            val totalMs = (SystemClock.elapsedRealtime() - startMs).coerceAtLeast(1L)
            val finalOutput = builder.toString().trim()

            if (finalOutput.isEmpty() && !activeCancellation.get()) {
                val err = "LlmModule.generate returned status=$status with 0 generated tokens."
                _lastError.value = err
                _runtimeState.value = ModelRuntimeState.INFERENCE_FAILED
                return Result.failure(IllegalStateException(err))
            }

            val ttft = firstTokenMs ?: totalMs
            val decodeWindowMs = (totalMs - ttft).coerceAtLeast(1L)
            val computedTps = reportedTps?.toDouble()
                ?: if (tokenCallbacks > 1) {
                    ((tokenCallbacks - 1) * 1000.0) / decodeWindowMs.toDouble()
                } else {
                    (tokenCallbacks * 1000.0) / totalMs.toDouble()
                }

            lastTtftMs = ttft
            lastGeneratedTokens = tokenCallbacks
            lastDecodeTokensPerSec = kotlin.math.round(computedTps * 100.0) / 100.0

            _runtimeState.value = ModelRuntimeState.READY

            Result.success(
                GenerationMetrics(
                    generatedText = finalOutput,
                    tokenCount = tokenCallbacks,
                    ttftMs = ttft,
                    totalElapsedMs = totalMs,
                    tokensPerSecond = lastDecodeTokensPerSec ?: computedTps,
                    promptTokensEstimate = promptTokensEstimate,
                    allowedOutputTokens = cappedOutputTokens
                )
            )
        } catch (t: Throwable) {
            val actual = if (t is java.lang.reflect.InvocationTargetException) t.targetException ?: t else t
            val err = "Native generation failed: ${actual.javaClass.simpleName}: ${actual.message}"
            Log.e(TAG, err, actual)
            _lastError.value = err
            _runtimeState.value = ModelRuntimeState.INFERENCE_FAILED
            Result.failure(IllegalStateException(err, actual))
        }
    }

    fun stop(targetOperationId: String): CancellationResult {
        var moduleToStop: Any? = null
        var barrierDeferred: CompletableDeferred<Unit>? = null

        synchronized(cancellationLock) {
            when {
                targetOperationId == activeOperationId -> {
                    when (activeOperationState) {
                        OperationState.REGISTERED -> {
                            activeOperationState = OperationState.CANCEL_REQUESTED
                            return CancellationResult.CANCELLED_BEFORE_NATIVE_START
                        }
                        OperationState.JNI_STARTED -> {
                            activeOperationState = OperationState.CANCEL_REQUESTED
                            isStopDispatchInProgress = true
                            stopDispatchTargetGeneration = activeGeneration
                            barrierDeferred = CompletableDeferred()
                            pendingStopBarrierDeferred = barrierDeferred
                            moduleToStop = loadedModule
                        }
                        OperationState.CANCEL_REQUESTED -> {
                            return CancellationResult.CANCEL_ALREADY_REQUESTED
                        }
                        OperationState.CANCEL_UNSUPPORTED -> {
                            return CancellationResult.NATIVE_CANCEL_UNSUPPORTED
                        }
                        else -> {
                            return CancellationResult.OPERATION_ALREADY_COMPLETED
                        }
                    }
                }
                queuedOperations.contains(targetOperationId) -> {
                    cancelledQueuedOperations.add(targetOperationId)
                    return CancellationResult.OPERATION_QUEUED_NOT_ACTIVE
                }
                completedOperations.containsKey(targetOperationId) -> {
                    return CancellationResult.OPERATION_ALREADY_COMPLETED
                }
                else -> {
                    return CancellationResult.OPERATION_ID_MISMATCH
                }
            }
        }

        if (moduleToStop != null && barrierDeferred != null) {
            onStopDispatched?.invoke(targetOperationId)
            activeCancellation.set(true)

            // Dispatch stop asynchronously to dedicated single-thread executor without waiting for JNI
            nativeControlDispatcher.asExecutor().execute {
                var success = false
                try {
                    runtimeBridge.stop(moduleToStop!!)
                    success = true
                } catch (t: Throwable) {
                    Log.w(TAG, "Native stop dispatch threw exception for $targetOperationId", t)
                    synchronized(cancellationLock) {
                        if (activeOperationId == targetOperationId) {
                            activeOperationState = OperationState.CANCEL_UNSUPPORTED
                        }
                    }
                } finally {
                    onStopReturned?.invoke(targetOperationId, success)
                    synchronized(cancellationLock) {
                        isStopDispatchInProgress = false
                        barrierDeferred.complete(Unit)
                    }
                }
            }
            return CancellationResult.CANCEL_ACCEPTED_ACTIVE
        }

        return CancellationResult.OPERATION_ALREADY_COMPLETED
    }

    fun stop() {
        activeCancellation.set(true)
        val currentId = synchronized(cancellationLock) { activeOperationId }
        if (currentId != null) {
            stop(currentId)
        } else {
            val module = loadedModule
            if (module != null) {
                nativeControlDispatcher.asExecutor().execute {
                    try {
                        runtimeBridge.stop(module)
                    } catch (_: Throwable) {}
                }
            }
        }
    }
}

/**
 * Default reflection-based bridge to org.pytorch.executorch.extension.llm.LlmModule and LlmCallback.
 * Enables both host JVM tests (with mock bridge) and real on-device JNI execution.
 */
class ReflectionExecuTorchLlmBridge : ExecuTorchLlamaEngine.NativeLlmBridge {

    companion object {
        private const val TAG = "ReflectionLlmBridge"
        private val nativeInitialized = AtomicBoolean(false)

        fun ensureNativeLibrariesLoaded() {
            if (nativeInitialized.get()) return
            try {
                val runtimeCls = Class.forName("org.pytorch.executorch.ExecuTorchRuntime")
                val getRuntimeMethod = runtimeCls.getMethod("getRuntime")
                getRuntimeMethod.invoke(null)
                nativeInitialized.set(true)
                Log.i(TAG, "ExecuTorchRuntime native loader initialized successfully")
                return
            } catch (_: Throwable) {
            }

            try {
                System.loadLibrary("executorch")
                nativeInitialized.set(true)
                Log.i(TAG, "libexecutorch.so loaded directly via System.loadLibrary")
            } catch (t: Throwable) {
                Log.w(TAG, "Native library pre-load note: ${t.message}")
            }
        }

        fun unwrap(t: Throwable): Throwable {
            var curr: Throwable = t
            while (curr is java.lang.reflect.InvocationTargetException && curr.targetException != null) {
                curr = curr.targetException
            }
            return curr
        }
    }

    override fun createAndLoadModule(modelPath: String, tokenizerPath: String, temperature: Float): Any {
        ensureNativeLibrariesLoaded()

        val llmModuleClass = try {
            Class.forName("org.pytorch.executorch.extension.llm.LlmModule")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException("ExecuTorch LlmModule not found on classpath. Ensure executorch-android AAR is bundled.", e)
        }

        val instance = try {
            val ctor3 = llmModuleClass.getConstructor(
                String::class.java,
                String::class.java,
                Float::class.javaPrimitiveType
            )
            ctor3.newInstance(modelPath, tokenizerPath, temperature)
        } catch (_: NoSuchMethodException) {
            try {
                val ctor2 = llmModuleClass.getConstructor(String::class.java, String::class.java)
                ctor2.newInstance(modelPath, tokenizerPath)
            } catch (t: Throwable) {
                throw unwrap(t)
            }
        } catch (t: Throwable) {
            throw unwrap(t)
        }

        try {
            val loadMethod = llmModuleClass.getMethod("load")
            val loadResult = loadMethod.invoke(instance)
            if (loadResult is Number && loadResult.toInt() != 0) {
                throw IllegalStateException("LlmModule.load() returned non-zero error code: ${loadResult.toInt()}")
            }
        } catch (_: NoSuchMethodException) {
            // Eager loading constructor
        } catch (t: Throwable) {
            throw unwrap(t)
        }
        return instance
    }

    override fun generate(
        moduleInstance: Any,
        prompt: String,
        maxNewTokens: Int,
        onToken: (String) -> Unit,
        onStats: (Float) -> Unit
    ): Int {
        val callbackClass = Class.forName("org.pytorch.executorch.extension.llm.LlmCallback")
        val callbackProxy = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass),
            InvocationHandler { _, method, args ->
                when (method.name) {
                    "onResult" -> {
                        val token = args?.firstOrNull() as? String ?: ""
                        onToken(token)
                        null
                    }
                    "onStats" -> {
                        val arg = args?.firstOrNull()
                        val tps = when (arg) {
                            is Number -> arg.toFloat()
                            is String -> {
                                Regex("""([0-9]+(?:\.[0-9]+)?)""").find(arg)?.value?.toFloatOrNull() ?: 0f
                            }
                            else -> 0f
                        }
                        if (tps > 0f) onStats(tps)
                        null
                    }
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> false
                    "toString" -> "ExecuTorchLlmCallbackProxy"
                    else -> null
                }
            }
        )

        val cls = moduleInstance.javaClass
        // In ExecuTorch C++, seq_len represents the TOTAL sequence window (prompt + max_new_tokens),
        // NOT max_new_tokens alone. resolve_max_new_tokens calculates (seq_len - num_prompt_tokens).
        // Enforce the 1000-token cap: allowedOutput = min(requestedOutput, 1000, actualModelSequenceLimit - actualPrefillTokens - requiredReservedTokens)
        val estimatedPrompt = PromptBudgeter.estimateTokens(prompt)
        val cappedNewTokens = minOf(
            maxNewTokens,
            ActiveModelManifest.MAX_NEW_TOKENS,
            (ActiveModelManifest.MAX_SEQ_LENGTH - estimatedPrompt - 32).coerceAtLeast(1)
        )
        val totalSeqWindow = (estimatedPrompt + cappedNewTokens).coerceIn(32, ActiveModelManifest.MAX_CONTEXT_LENGTH)
        return try {
            val m4 = cls.getMethod(
                "generate",
                String::class.java,
                Int::class.javaPrimitiveType,
                callbackClass,
                Boolean::class.javaPrimitiveType
            )
            (m4.invoke(moduleInstance, prompt, totalSeqWindow, callbackProxy, false) as? Int) ?: 0
        } catch (_: NoSuchMethodException) {
            try {
                val m3 = cls.getMethod(
                    "generate",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    callbackClass
                )
                (m3.invoke(moduleInstance, prompt, totalSeqWindow, callbackProxy) as? Int) ?: 0
            } catch (_: NoSuchMethodException) {
                try {
                    val m2 = cls.getMethod("generate", String::class.java, callbackClass)
                    (m2.invoke(moduleInstance, prompt, callbackProxy) as? Int) ?: 0
                } catch (t: Throwable) {
                    throw unwrap(t)
                }
            } catch (t: Throwable) {
                throw unwrap(t)
            }
        } catch (t: Throwable) {
            throw unwrap(t)
        }
    }

    override fun stop(moduleInstance: Any) {
        try {
            moduleInstance.javaClass.getMethod("stop").invoke(moduleInstance)
        } catch (_: Exception) {
        }
    }
}


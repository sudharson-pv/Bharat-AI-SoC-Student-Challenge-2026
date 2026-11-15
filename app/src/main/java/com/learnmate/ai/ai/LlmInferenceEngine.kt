package com.learnmate.ai.ai

import kotlinx.coroutines.flow.Flow

interface LlmInferenceEngine {
    val isModelLoaded: Boolean
    suspend fun generateTokenStream(prompt: String, maxTokens: Int): Flow<String>
    fun getTelemetrySnapshot(): LlmTelemetrySnapshot
}

data class LlmTelemetrySnapshot(
    val modelName: String = "Llama 3.2 1B Instruct (Pending Validation)",
    val quantization: String = "8da4w (Candidate)",
    val runtime: String = "ExecuTorch 1.4.0 + XNNPACK",
    val targetSoC: String = "MediaTek Dimensity 7020 (arm64-v8a)",
    val isVerifiedOnDevice: Boolean = false
)


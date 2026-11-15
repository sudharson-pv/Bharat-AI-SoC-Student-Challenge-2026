package com.learnmate.ai.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Isolated Gate 2 Inference Spike Activity.
 * Strictly decoupled from Gate 1 HomeScreen and MainViewModel.
 * Executes on-device inference using ExecuTorchLlamaEngine and logs live telemetry.
 */
class InferenceSpikeActivity : ComponentActivity() {

    companion object {
        const val TAG = "LearnMateSpike"
        const val TEST_PROMPT = "Explain what a PN junction diode is in 2 sentences."
    }

    private lateinit var assetManager: ModelAssetManager
    private lateinit var engine: ExecuTorchLlamaEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assetManager = ModelAssetManager(filesDir)
        engine = ExecuTorchLlamaEngine(assetManager)

        setContent {
            SpikeScreen(
                engine = engine,
                onRunBenchmark = { runLiveBenchmark() }
            )
        }

        // Auto-run benchmark on launch
        val autoRun = intent.getBooleanExtra("auto_run", true)
        if (autoRun) {
            runLiveBenchmark()
        }
    }

    private var isRunningBenchmark = mutableStateOf(false)
    private var liveStreamText = mutableStateOf("")
    private var benchmarkJsonResult = mutableStateOf<String?>(null)

    private fun runLiveBenchmark() {
        if (isRunningBenchmark.value) return
        isRunningBenchmark.value = true
        liveStreamText.value = ""
        benchmarkJsonResult.value = null

        val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
        coroutineScope.launch {
            Log.i(TAG, "=== STARTING GATE 2 EXECUTORCH 1.4.0 ON-DEVICE SPIKE ===")
            Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            Log.i(TAG, "Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            Log.i(TAG, "Android OS: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")

            val batteryInfo = getBatteryStatus()
            Log.i(TAG, "Initial Battery: level=${batteryInfo.level}%, temp=${batteryInfo.tempCelsius}°C")

            // 1. Asset Provisioning Check
            val provResult = assetManager.ensureModelProvisioned()
            Log.i(TAG, "Asset provisioned: state=${provResult.state}, model=${provResult.modelFile.absolutePath} (exists=${provResult.modelFile.exists()}, bytes=${provResult.modelFile.length()})")

            if (provResult.state == ModelRuntimeState.MODEL_MISSING || provResult.state == ModelRuntimeState.MODEL_ERROR) {
                val err = "Cannot proceed: ${provResult.errorMessage}"
                Log.e(TAG, err)
                isRunningBenchmark.value = false
                liveStreamText.value = "ERROR: $err"
                return@launch
            }

            // 2. Measure Cold Model Load
            val modelPath = provResult.modelFile.absolutePath
            val tokPath = provResult.tokenizerFile.absolutePath

            Log.i(TAG, "Executing cold loadModel()...")
            val t0Load = SystemClock.elapsedRealtime()
            val loadResult = engine.loadModel(modelPath, tokPath, temperature = 0.8f)
            val coldLoadElapsedMs = SystemClock.elapsedRealtime() - t0Load

            if (loadResult.isFailure) {
                val err = "Model load failed: ${loadResult.exceptionOrNull()?.message}"
                Log.e(TAG, err, loadResult.exceptionOrNull())
                isRunningBenchmark.value = false
                liveStreamText.value = "ERROR: $err"
                return@launch
            }
            Log.i(TAG, "Cold loadModel() SUCCEEDED in ${coldLoadElapsedMs}ms")

            // 3. Measure Prompt Generation
            Log.i(TAG, "Submitting prompt: \"$TEST_PROMPT\"")
            val t0Gen = SystemClock.elapsedRealtime()

            val genResult = engine.generate(TEST_PROMPT, maxNewTokens = 128) { fullText, delta ->
                liveStreamText.value = fullText
                Log.d(TAG, "Token delta: [$delta]")
            }

            val totalGenElapsedMs = SystemClock.elapsedRealtime() - t0Gen
            val memInfo = getMemoryFootprint()
            val endBattery = getBatteryStatus()

            val resultJson = JSONObject()
            resultJson.put("device_model", Build.MODEL)
            resultJson.put("manufacturer", Build.MANUFACTURER)
            resultJson.put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            resultJson.put("android_version", Build.VERSION.RELEASE)
            resultJson.put("cold_load_ms", coldLoadElapsedMs)
            resultJson.put("initial_battery_temp_c", batteryInfo.tempCelsius)
            resultJson.put("final_battery_temp_c", endBattery.tempCelsius)
            resultJson.put("initial_battery_pct", batteryInfo.level)
            resultJson.put("final_battery_pct", endBattery.level)
            resultJson.put("total_process_pss_kb", memInfo.totalPss)
            resultJson.put("native_heap_pss_kb", memInfo.nativePss)
            resultJson.put("dalvik_pss_kb", memInfo.dalvikPss)

            if (genResult.isSuccess) {
                val metrics = genResult.getOrNull()!!
                Log.i(TAG, "Generation SUCCEEDED!")
                Log.i(TAG, "Generated Text: \"${metrics.generatedText}\"")
                Log.i(TAG, "Tokens Generated: ${metrics.tokenCount}")
                Log.i(TAG, "Time To First Token (TTFT): ${metrics.ttftMs}ms")
                Log.i(TAG, "Decode Speed: ${metrics.tokensPerSecond} tokens/sec")
                Log.i(TAG, "Total Elapsed: ${totalGenElapsedMs}ms")
                Log.i(TAG, "Peak Memory Total PSS: ${memInfo.totalPss} KB (~${memInfo.totalPss / 1024} MB)")

                resultJson.put("success", true)
                resultJson.put("generated_text", metrics.generatedText)
                resultJson.put("token_count", metrics.tokenCount)
                resultJson.put("ttft_ms", metrics.ttftMs)
                resultJson.put("tokens_per_second", metrics.tokensPerSecond)
                resultJson.put("total_elapsed_ms", totalGenElapsedMs)
            } else {
                val err = genResult.exceptionOrNull()?.message ?: "Unknown generation error"
                Log.e(TAG, "Generation FAILED: $err")
                resultJson.put("success", false)
                resultJson.put("error", err)
            }

            val finalJsonStr = resultJson.toString(2)
            benchmarkJsonResult.value = finalJsonStr

            // Save report to disk
            withContext(Dispatchers.IO) {
                try {
                    File(filesDir, "gate2_spike_results.json").writeText(finalJsonStr)
                    Log.i(TAG, "Saved spike results to ${File(filesDir, "gate2_spike_results.json").absolutePath}")
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not write spike results file", t)
                }
            }

            Log.i(TAG, "=== GATE 2 SPIKE COMPLETED ===\n$finalJsonStr")
            isRunningBenchmark.value = false
        }
    }

    private data class BatterySnapshot(val level: Int, val tempCelsius: Float)

    private fun getBatteryStatus(): BatterySnapshot {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val bStatus = registerReceiver(null, ifilter)
        val level = bStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else level
        val tempRaw = bStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempRaw > 0) tempRaw / 10.0f else 0.0f
        return BatterySnapshot(pct, tempC)
    }

    private data class MemSnapshot(val totalPss: Int, val nativePss: Int, val dalvikPss: Int)

    private fun getMemoryFootprint(): MemSnapshot {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val pid = android.os.Process.myPid()
        val mems = am?.getProcessMemoryInfo(intArrayOf(pid))
        val info = mems?.firstOrNull() ?: Debug.MemoryInfo()
        return MemSnapshot(info.totalPss, info.nativePss, info.dalvikPss)
    }

    @Composable
    fun SpikeScreen(
        engine: ExecuTorchLlamaEngine,
        onRunBenchmark: () -> Unit
    ) {
        val runtimeState by engine.runtimeState.collectAsState()
        val streamText by liveStreamText
        val jsonResult by benchmarkJsonResult
        val isRunning by isRunningBenchmark

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF10172C))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Gate 2: ExecuTorch Inference Spike",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF35D9F5)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Moto G54 5G | Dimensity 7020 | Llama 3.2 1B (8da4w)",
                fontSize = 12.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2540)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Status: $runtimeState", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Engine: org.pytorch:executorch-android:1.4.0",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Text(
                        text = "Delegate: XNNPACK CPU (Arm64 NEON)",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRunBenchmark,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.height(16.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(" Running On-Device Inference...")
                } else {
                    Text("Re-Run Live Benchmark")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Live Streamed Tokens:",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFFA78BFA)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0F1D))
                    .padding(12.dp)
            ) {
                Text(
                    text = streamText.ifEmpty { "(Streaming tokens will appear here live...)" },
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }

            if (jsonResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Telemetry JSON Report:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF35D9F5)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0F1D))
                        .padding(12.dp)
                ) {
                    Text(
                        text = jsonResult ?: "",
                        color = Color(0xFF86EFAC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}


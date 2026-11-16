package com.learnmate.ai.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.learnmate.ai.ai.ActiveModelManifest
import com.learnmate.ai.ai.ModelRuntimeState
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDiagnosticsScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val runtimeState by learningViewModel.runtimeState.collectAsState()
    val engineError by learningViewModel.engineError.collectAsState()

    var isRunningSmokeTest by remember { mutableStateOf(false) }
    var smokeTestStatus by remember { mutableStateOf<String?>(null) }
    var calculatedModelHash by remember { mutableStateOf<String?>("Tap 'Verify Checksums' to calculate") }
    var calculatedTokenizerHash by remember { mutableStateOf<String?>("Tap 'Verify Checksums' to calculate") }
    var isCalculatingHash by remember { mutableStateOf(false) }

    // Hardware Details
    val am = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val memInfo = remember {
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        mi
    }
    val totalRamGb = remember { "%.2f".format(memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)) }
    val availRamGb = remember { "%.2f".format(memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)) }

    val freeStorageGb = remember {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            "%.2f".format((stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024.0 * 1024.0 * 1024.0))
        } catch (_: Exception) {
            "Unknown"
        }
    }

    val modelFile = learningViewModel.modelAssetManager.activeModelFile
    val tokFile = learningViewModel.modelAssetManager.activeTokenizerFile

    val nativeClassFound = remember {
        try {
            Class.forName("org.pytorch.executorch.extension.llm.LlmModule")
            true
        } catch (_: Throwable) {
            false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Model Diagnostics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                exportDiagnosticsReport(
                                    context = context,
                                    runtimeState = runtimeState,
                                    engineError = engineError,
                                    totalRamGb = totalRamGb,
                                    availRamGb = availRamGb,
                                    freeStorageGb = freeStorageGb,
                                    modelFile = modelFile,
                                    tokFile = tokFile,
                                    calculatedModelHash = calculatedModelHash,
                                    calculatedTokenizerHash = calculatedTokenizerHash,
                                    nativeClassFound = nativeClassFound,
                                    lastLoadTimeMs = learningViewModel.modelEngine.lastLoadTimeMs,
                                    lastTtftMs = learningViewModel.modelEngine.lastTtftMs,
                                    lastTps = learningViewModel.modelEngine.lastDecodeTokensPerSec,
                                    lastTokens = learningViewModel.modelEngine.lastGeneratedTokens
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Diagnostics Report"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Status Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (runtimeState) {
                        ModelRuntimeState.READY -> Color(0xFFE8F5E9)
                        ModelRuntimeState.VERIFIED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ModelRuntimeState.INITIALIZING, ModelRuntimeState.VERIFYING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED, ModelRuntimeState.DOWNLOAD_FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (runtimeState) {
                            ModelRuntimeState.READY -> Icons.Default.CheckCircle
                            ModelRuntimeState.VERIFIED -> Icons.Default.FileDownloadDone
                            ModelRuntimeState.INITIALIZING, ModelRuntimeState.VERIFYING -> Icons.Default.HourglassTop
                            ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED -> Icons.Default.Error
                            else -> Icons.Default.HelpOutline
                        },
                        contentDescription = null,
                        tint = when (runtimeState) {
                            ModelRuntimeState.READY -> Color(0xFF2E7D32)
                            ModelRuntimeState.VERIFIED -> MaterialTheme.colorScheme.primary
                            ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.outline
                        },
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Runtime State: ${runtimeState.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = when (runtimeState) {
                                ModelRuntimeState.READY -> "Active & verified. On-device inference operational."
                                ModelRuntimeState.VERIFIED -> "Installed on local storage. Awaiting initialization."
                                ModelRuntimeState.INITIALIZING -> "Initializing ExecuTorch runtime into memory..."
                                ModelRuntimeState.INITIALIZATION_FAILED -> "Failed to initialize native ExecuTorch module."
                                ModelRuntimeState.INFERENCE_FAILED -> "Module loaded, but smoke test inference failed."
                                ModelRuntimeState.NOT_INSTALLED -> "Model assets not found on device storage."
                                else -> "Lifecycle state: ${runtimeState.name}"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (engineError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Engine Diagnostic Trace", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = engineError!!,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Section 1: Device & Hardware Environment
            DiagnosticsSection(title = "1. Device & Hardware Environment") {
                DiagRow(label = "Device Model", value = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
                DiagRow(label = "Android Version", value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                DiagRow(label = "Supported ABIs", value = Build.SUPPORTED_ABIS.joinToString(", "))
                DiagRow(label = "Process 64-Bit Mode", value = if (Process.is64Bit()) "Yes (64-bit)" else "No (32-bit)")
                DiagRow(label = "Total Physical RAM", value = "$totalRamGb GB")
                DiagRow(label = "Available RAM", value = "$availRamGb GB")
                DiagRow(label = "Internal Storage Free", value = "$freeStorageGb GB")
            }

            // Section 2: Model Asset Storage & Cryptographic Integrity
            DiagnosticsSection(title = "2. Model Asset Storage & Integrity") {
                DiagRow(label = "Target Model ID", value = ActiveModelManifest.MODEL_ID)
                DiagRow(label = "Model File Path", value = modelFile.absolutePath)
                DiagRow(label = "Model File Exists", value = if (modelFile.exists()) "YES (${modelFile.length()} bytes)" else "NO (Missing)")
                DiagRow(label = "Expected Model Size", value = "${ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES} bytes (765.8 MB)")
                DiagRow(label = "Model SHA-256 (Expected)", value = ActiveModelManifest.EXPECTED_MODEL_SHA256.take(16) + "...")
                DiagRow(label = "Model SHA-256 (Actual)", value = calculatedModelHash ?: "Not calculated")

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                DiagRow(label = "Tokenizer File Path", value = tokFile.absolutePath)
                DiagRow(label = "Tokenizer File Exists", value = if (tokFile.exists()) "YES (${tokFile.length()} bytes)" else "NO (Missing)")
                DiagRow(label = "Expected Tokenizer Size", value = "${ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES} bytes (2.08 MB)")
                DiagRow(label = "Tokenizer SHA-256 (Expected)", value = ActiveModelManifest.EXPECTED_TOKENIZER_SHA256.take(16) + "...")
                DiagRow(label = "Tokenizer SHA-256 (Actual)", value = calculatedTokenizerHash ?: "Not calculated")

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isCalculatingHash = true
                        coroutineScope.launch(Dispatchers.IO) {
                            val mHash = if (modelFile.exists()) learningViewModel.modelAssetManager.computeFileSha256(modelFile) else "File missing"
                            val tHash = if (tokFile.exists()) learningViewModel.modelAssetManager.computeFileSha256(tokFile) else "File missing"
                            withContext(Dispatchers.Main) {
                                calculatedModelHash = mHash
                                calculatedTokenizerHash = tHash
                                isCalculatingHash = false
                                Toast.makeText(context, "SHA-256 verification complete", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isCalculatingHash && modelFile.exists(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCalculatingHash) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Computing SHA-256 Checksums...")
                    } else {
                        Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify Cryptographic Checksums")
                    }
                }
            }

            // Section 3: ExecuTorch Runtime & Native JNI
            DiagnosticsSection(title = "3. ExecuTorch Native Runtime") {
                DiagRow(label = "Runtime Version", value = ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION)
                DiagRow(label = "Target Backend", value = ActiveModelManifest.TARGET_BACKEND)
                DiagRow(label = "Quantization Scheme", value = ActiveModelManifest.QUANTIZATION_SCHEME)
                DiagRow(label = "Max Context Window", value = "${ActiveModelManifest.MAX_CONTEXT_LENGTH} tokens")
                DiagRow(label = "Max Sequence Length", value = "${ActiveModelManifest.MAX_SEQ_LENGTH} tokens")
                DiagRow(label = "Native LlmModule Class", value = if (nativeClassFound) "Bundled (org.pytorch.executorch.extension.llm.LlmModule)" else "MISSING FROM CLASSPATH")
                DiagRow(label = "libexecutorch.so ABI", value = if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) "arm64-v8a available" else "arm64-v8a not primary")
            }

            // Section 4: Inference Performance & Smoke Test
            DiagnosticsSection(title = "4. Inference Performance & Smoke Test") {
                DiagRow(label = "Last Load Time", value = "${learningViewModel.modelEngine.lastLoadTimeMs ?: "N/A"} ms")
                DiagRow(label = "Time-To-First-Token (TTFT)", value = "${learningViewModel.modelEngine.lastTtftMs ?: "N/A"} ms")
                DiagRow(label = "Tokens Per Second (TPS)", value = "${learningViewModel.modelEngine.lastDecodeTokensPerSec ?: "N/A"} tps")
                DiagRow(label = "Tokens Generated (Last Run)", value = "${learningViewModel.modelEngine.lastGeneratedTokens ?: "N/A"}")

                if (smokeTestStatus != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = smokeTestStatus!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isRunningSmokeTest = true
                            smokeTestStatus = "Initializing & executing smoke test..."
                            learningViewModel.initializeAndActivateModel { success, err ->
                                if (success) {
                                    learningViewModel.runModelSmokeTest { smokeSuccess, smokeMsg ->
                                        isRunningSmokeTest = false
                                        smokeTestStatus = smokeMsg ?: (if (smokeSuccess) "Smoke test succeeded" else "Smoke test failed")
                                    }
                                } else {
                                    isRunningSmokeTest = false
                                    smokeTestStatus = "Initialization failed: $err"
                                }
                            }
                        },
                        enabled = !isRunningSmokeTest && modelFile.exists(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRunningSmokeTest) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Smoke Test")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            learningViewModel.initializeAndActivateModel { ok, err ->
                                Toast.makeText(context, if (ok) "Model initialized" else "Error: $err", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !isRunningSmokeTest && modelFile.exists(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Re-Init Runtime")
                    }
                }
            }

            // Export Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        exportDiagnosticsReport(
                            context = context,
                            runtimeState = runtimeState,
                            engineError = engineError,
                            totalRamGb = totalRamGb,
                            availRamGb = availRamGb,
                            freeStorageGb = freeStorageGb,
                            modelFile = modelFile,
                            tokFile = tokFile,
                            calculatedModelHash = calculatedModelHash,
                            calculatedTokenizerHash = calculatedTokenizerHash,
                            nativeClassFound = nativeClassFound,
                            lastLoadTimeMs = learningViewModel.modelEngine.lastLoadTimeMs,
                            lastTtftMs = learningViewModel.modelEngine.lastTtftMs,
                            lastTps = learningViewModel.modelEngine.lastDecodeTokensPerSec,
                            lastTokens = learningViewModel.modelEngine.lastGeneratedTokens
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Diagnostics Markdown Report")
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.2f)
        )
    }
}

private suspend fun exportDiagnosticsReport(
    context: Context,
    runtimeState: ModelRuntimeState,
    engineError: String?,
    totalRamGb: String,
    availRamGb: String,
    freeStorageGb: String,
    modelFile: File,
    tokFile: File,
    calculatedModelHash: String?,
    calculatedTokenizerHash: String?,
    nativeClassFound: Boolean,
    lastLoadTimeMs: Long?,
    lastTtftMs: Long?,
    lastTps: Double?,
    lastTokens: Int?
) = withContext(Dispatchers.IO) {
    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val reportFile = File(context.cacheDir, "LearnMate_AI_Model_Diagnostics_$dateStr.md")

    val markdown = buildString {
        appendLine("# LearnMate AI — Mobile Model Diagnostics & Qualification Report")
        appendLine()
        appendLine("**Generated:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
        appendLine("**Package:** com.learnmate.ai")
        appendLine("**Target Model:** ${ActiveModelManifest.DISPLAY_NAME} (`${ActiveModelManifest.MODEL_ID}`)")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## 1. Hardware & Operating System Diagnostics")
        appendLine("- **Device Model:** ${Build.MANUFACTURER} ${Build.MODEL} (`${Build.DEVICE}`)")
        appendLine("- **Android OS:** Android ${Build.VERSION.RELEASE} (API Level ${Build.VERSION.SDK_INT})")
        appendLine("- **Supported ABIs:** ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("- **Process 64-bit Architecture:** ${if (Process.is64Bit()) "64-bit (arm64-v8a)" else "32-bit"}")
        appendLine("- **Total RAM:** $totalRamGb GB")
        appendLine("- **Available Free RAM:** $availRamGb GB")
        appendLine("- **Free Internal Storage:** $freeStorageGb GB")
        appendLine()
        appendLine("## 2. Model Asset Storage & Cryptographic Verification")
        appendLine("- **Model File Path:** `${modelFile.absolutePath}`")
        appendLine("- **Model File Exists:** ${modelFile.exists()}")
        appendLine("- **Model File Size:** ${if (modelFile.exists()) modelFile.length() else 0} bytes (Expected: ${ActiveModelManifest.EXPECTED_MODEL_SIZE_BYTES} bytes)")
        appendLine("- **Model Expected SHA-256:** `${ActiveModelManifest.EXPECTED_MODEL_SHA256}`")
        appendLine("- **Model Calculated SHA-256:** `${calculatedModelHash ?: "Not calculated"}`")
        appendLine("- **Tokenizer File Path:** `${tokFile.absolutePath}`")
        appendLine("- **Tokenizer File Exists:** ${tokFile.exists()}")
        appendLine("- **Tokenizer File Size:** ${if (tokFile.exists()) tokFile.length() else 0} bytes (Expected: ${ActiveModelManifest.EXPECTED_TOKENIZER_SIZE_BYTES} bytes)")
        appendLine("- **Tokenizer Expected SHA-256:** `${ActiveModelManifest.EXPECTED_TOKENIZER_SHA256}`")
        appendLine("- **Tokenizer Calculated SHA-256:** `${calculatedTokenizerHash ?: "Not calculated"}`")
        appendLine()
        appendLine("## 3. ExecuTorch Runtime & Native JNI Status")
        appendLine("- **ExecuTorch Version:** ${ActiveModelManifest.EXECUTORCH_RUNTIME_VERSION}")
        appendLine("- **Target Backend:** ${ActiveModelManifest.TARGET_BACKEND}")
        appendLine("- **Quantization:** ${ActiveModelManifest.QUANTIZATION_SCHEME}")
        appendLine("- **Max Sequence Length:** ${ActiveModelManifest.MAX_SEQ_LENGTH} tokens")
        appendLine("- **Native LlmModule Present:** $nativeClassFound")
        appendLine("- **Current Runtime State:** `${runtimeState.name}`")
        appendLine("- **Engine Last Error:** ${engineError ?: "None (Clean)"}")
        appendLine()
        appendLine("## 4. Inference Performance Metrics")
        appendLine("- **Last Load Time:** ${lastLoadTimeMs ?: "N/A"} ms")
        appendLine("- **Time-To-First-Token (TTFT):** ${lastTtftMs ?: "N/A"} ms")
        appendLine("- **Tokens Per Second (TPS):** ${lastTps ?: "N/A"} tps")
        appendLine("- **Last Tokens Generated:** ${lastTokens ?: "N/A"}")
        appendLine()
        appendLine("---")
        appendLine("*Report generated locally by LearnMate AI Mobile Diagnostic Engine.*")
    }

    reportFile.writeText(markdown)

    withContext(Dispatchers.Main) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", reportFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LearnMate AI Model Diagnostics Report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostics Report"))
        } catch (_: Exception) {
            Toast.makeText(context, "Report exported to: ${reportFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}

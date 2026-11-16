package com.learnmate.ai.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.ai.*
import com.learnmate.ai.data.AppPreferences
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gate 6.16.6: Multi-Model Catalog & Dynamic Model Management Screen.
 *
 * Implements:
 * 1. Multi-Llama catalog selection (1B Recommended, 3B High-Accuracy, 8B Flagship).
 * 2. Real device hardware evaluation (RAM, Storage, API Level).
 * 3. Distinct status per model: Installed, Active, Downloadable, or Incompatible.
 * 4. Model switching: Allows activating any verified installed model.
 * 5. Model deletion with confirmation dialog (safely protected for active model).
 * 6. Preserves all 11 lifecycle download states and 8-step verification pipeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    downloadEngine: ModelDownloadEngine,
    learningViewModel: com.learnmate.ai.viewmodel.LearningViewModel? = null,
    onNavigateToDiagnostics: () -> Unit = {},
    onModelReady: () -> Unit,
    onSkipOrContinue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val appPreferences = remember { AppPreferences(context) }
    val assetManager = remember { ModelAssetManager(context.filesDir) }

    var activeModelId by remember { mutableStateOf(appPreferences.getActiveModelId()) }
    val downloadSnapshot by downloadEngine.downloadFlow.collectAsState()
    val selectedCatalogEntry by downloadEngine.selectedEntry.collectAsState()
    val runtimeState by (learningViewModel?.runtimeState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(ModelRuntimeState.NOT_INSTALLED) }).collectAsState()
    val engineError by (learningViewModel?.engineError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsState()

    // Hardware diagnostics
    val totalRamBytes = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        if (mi.totalMem > 0) mi.totalMem else 8_000_000_000L
    }
    val totalRamGb = "%.1f".format(totalRamBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

    val availableStorageBytes = remember {
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            50_000_000_000L
        }
    }
    val availableStorageGb = "%.1f".format(availableStorageBytes.toDouble() / (1024.0 * 1024.0 * 1024.0))

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<ModelCatalogEntry?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var showCdnDialog by remember { mutableStateOf(false) }
    var cdnInputText by remember { mutableStateOf(appPreferences.getCustomModelCdnUrl() ?: "") }

    val totalSizeMb = selectedCatalogEntry.modelSizeBytes / (1024 * 1024)
    val downloadedMb = downloadSnapshot.downloadedBytes / (1024 * 1024)
    val speedMbps = "%.2f".format(downloadSnapshot.speedBytesPerSec.toDouble() / (1024.0 * 1024.0))

    val isCurrentSelectedInstalled = assetManager.isModelInstalled(selectedCatalogEntry.modelId)
    val isCurrentSelectedActive = (selectedCatalogEntry.modelId == activeModelId) && isCurrentSelectedInstalled && (runtimeState == ModelRuntimeState.READY)

    val hardwareCompat = selectedCatalogEntry.isCompatibleWithHardware(
        apiLevel = Build.VERSION.SDK_INT,
        totalRamBytes = totalRamBytes,
        availableStorageBytes = availableStorageBytes
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "AI Model Setup & Catalog",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToDiagnostics) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Open Model Diagnostics"
                        )
                    }
                    IconButton(onClick = { showCdnDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Configure Distribution CDN Endpoint"
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hardware Diagnostic Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Hardware: $totalRamGb GB RAM • $availableStorageGb GB Free",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        val recEntry = ModelCatalog.getRecommendedEntry(totalRamBytes)
                        Text(
                            text = "Recommended for this device: ${recEntry.displayName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Model Selection Catalog
            Text(
                text = "Select On-Device LLM",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ModelCatalog.ALL_ENTRIES.forEach { entry ->
                    val isSelected = entry.modelId == selectedCatalogEntry.modelId
                    val isInstalled = assetManager.isModelInstalled(entry.modelId)
                    val isCurrentTarget = (entry.modelId == activeModelId)
                    val isActive = isInstalled && isCurrentTarget && (runtimeState == ModelRuntimeState.READY)
                    val isInitializing = isInstalled && isCurrentTarget && (runtimeState == ModelRuntimeState.INITIALIZING)
                    val isFailed = isInstalled && isCurrentTarget && (runtimeState == ModelRuntimeState.INITIALIZATION_FAILED || runtimeState == ModelRuntimeState.INFERENCE_FAILED)
                    val entryCompat = entry.isCompatibleWithHardware(
                        apiLevel = Build.VERSION.SDK_INT,
                        totalRamBytes = totalRamBytes,
                        availableStorageBytes = availableStorageBytes
                    )

                    ModelOptionCard(
                        entry = entry,
                        isSelected = isSelected,
                        isInstalled = isInstalled,
                        isActive = isActive,
                        isInitializing = isInitializing,
                        isFailed = isFailed,
                        isCompatible = entryCompat.isCompatible,
                        incompatibilityReason = entryCompat.failureReason,
                        onClick = {
                            if (downloadSnapshot.state != ModelInstallationState.DOWNLOADING) {
                                downloadEngine.selectCatalogEntry(entry)
                            }
                        }
                    )
                }
            }

            // Action message alert if present
            if (actionMessage != null) {
                Text(
                    text = actionMessage!!,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Specification Matrix for Selected Model
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${selectedCatalogEntry.displayName} Specifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    SpecRow(label = "Format & Quantization", value = "${selectedCatalogEntry.modelFileName} (${selectedCatalogEntry.quantization})")
                    SpecRow(label = "Target Backend", value = selectedCatalogEntry.targetBackend)
                    SpecRow(label = "Model Size", value = "$totalSizeMb MB")
                    SpecRow(label = "Tokenizer Size", value = "${selectedCatalogEntry.tokenizerSizeBytes / 1024} KB")
                    SpecRow(label = "Minimum Device RAM", value = "%.1f GB".format(selectedCatalogEntry.minRamBytes / (1024.0 * 1024.0 * 1024.0)))
                    SpecRow(label = "Minimum Storage", value = "${selectedCatalogEntry.minStorageBytes / (1024 * 1024 * 1024)} GB")
                    SpecRow(label = "Download Source", value = "GitHub (models/)")
                    SpecRow(label = "License", value = selectedCatalogEntry.license)
                }
            }

            // Model Management & State Actions
            if (!hardwareCompat.isCompatible) {
                // Incompatible Hardware Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Model Incompatible With This Device",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = hardwareCompat.failureReason ?: "Insufficient hardware specifications to run this model.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Please select a compatible model such as Llama 3.2 1B or Llama 3.2 3B.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if (isCurrentSelectedInstalled) {
                // Model is installed on disk
                val isSelectedActive = (selectedCatalogEntry.modelId == activeModelId) && (runtimeState == ModelRuntimeState.READY)
                val isSelectedInitializing = (selectedCatalogEntry.modelId == activeModelId) && (runtimeState == ModelRuntimeState.INITIALIZING)
                val isSelectedFailed = (selectedCatalogEntry.modelId == activeModelId) && (runtimeState == ModelRuntimeState.INITIALIZATION_FAILED || runtimeState == ModelRuntimeState.INFERENCE_FAILED)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isSelectedActive -> Color(0xFFE8F5E9)
                            isSelectedFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            isSelectedInitializing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    isSelectedActive -> Icons.Default.CheckCircle
                                    isSelectedFailed -> Icons.Default.Error
                                    isSelectedInitializing -> Icons.Default.HourglassTop
                                    else -> Icons.Default.FileDownloadDone
                                },
                                contentDescription = null,
                                tint = when {
                                    isSelectedActive -> Color(0xFF2E7D32)
                                    isSelectedFailed -> MaterialTheme.colorScheme.error
                                    isSelectedInitializing -> Color(0xFFEF6C00)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isSelectedActive -> "Currently Active Model"
                                    isSelectedInitializing -> "Initializing ExecuTorch Runtime..."
                                    isSelectedFailed -> "Model Initialization / Inference Error"
                                    else -> "Model Installed (Inactive)"
                                },
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelectedActive -> Color(0xFF2E7D32)
                                    isSelectedFailed -> MaterialTheme.colorScheme.error
                                    isSelectedInitializing -> Color(0xFFEF6C00)
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                        }

                        Text(
                            text = when {
                                isSelectedActive -> "${selectedCatalogEntry.displayName} is loaded into memory, verified via smoke test, and powering on-device tutoring."
                                isSelectedInitializing -> "Loading weights into native ExecuTorch XNNPACK CPU memory..."
                                isSelectedFailed -> engineError ?: "Native initialization error occurred during model setup. Check diagnostics."
                                else -> "${selectedCatalogEntry.displayName} is installed on local storage. Tap below to initialize ExecuTorch and activate this model."
                            },
                            fontSize = 13.sp,
                            color = when {
                                isSelectedActive -> Color(0xFF1B5E20)
                                isSelectedFailed -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )

                        if (isSelectedActive) {
                            Button(
                                onClick = onModelReady,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Continue with ${selectedCatalogEntry.displayName}")
                            }
                            OutlinedButton(
                                onClick = onNavigateToDiagnostics,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Model Diagnostics")
                            }
                        } else if (isSelectedFailed) {
                            Button(
                                onClick = {
                                    learningViewModel?.initializeAndActivateModel { success, err ->
                                        if (success) {
                                            actionMessage = "Model initialized and verified successfully"
                                        } else {
                                            actionMessage = "Initialization error: $err"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry Initialization")
                            }
                            OutlinedButton(
                                onClick = onNavigateToDiagnostics,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("View Detailed Diagnostics")
                            }
                        } else if (isSelectedInitializing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        } else {
                            // Verified / Installed but not yet active
                            Button(
                                onClick = {
                                    appPreferences.setActiveModelId(selectedCatalogEntry.modelId)
                                    activeModelId = selectedCatalogEntry.modelId
                                    learningViewModel?.initializeAndActivateModel { success, err ->
                                        if (success) {
                                            actionMessage = "Switched to and activated ${selectedCatalogEntry.displayName}"
                                        } else {
                                            actionMessage = "Activation note: $err"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Initialize & Activate ${selectedCatalogEntry.displayName}")
                            }
                        }

                        // Delete option (only allowed for non-active models to prevent broken state)
                        if (!isCurrentSelectedActive) {
                            OutlinedButton(
                                onClick = {
                                    modelToDelete = selectedCatalogEntry
                                    showDeleteConfirmDialog = true
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete Model Assets")
                            }
                        }
                    }
                }
            } else {
                // Model is not yet installed: Dynamic State UI
                when (downloadSnapshot.state) {
                    ModelInstallationState.NOT_INSTALLED -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Model Not Installed", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "To enable personal tutoring with ${selectedCatalogEntry.displayName}, download model weights ($totalSizeMb MB). All inference runs strictly offline after installation.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (downloadSnapshot.isMobileData && !downloadSnapshot.mobileDataApproved) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Mobile Data Warning", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "You are connected to mobile data. Downloading $totalSizeMb MB may consume cellular data.",
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = {
                                            downloadEngine.approveMobileDataUsage()
                                            coroutineScope.launch { downloadEngine.downloadAndInstall() }
                                        }) {
                                            Text("Download over Mobile Data")
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            downloadEngine.downloadAndInstall()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download ${selectedCatalogEntry.displayName} ($totalSizeMb MB)")
                                }
                            }
                        }
                    }

                    ModelInstallationState.DOWNLOADED -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Assets Present — Verification Required", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Model assets are located on disk but have not completed cryptographic qualification. Verification is required before AI Ready is granted.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val ok = downloadEngine.verifyAndQualifyInstalledModel()
                                            if (ok) {
                                                appPreferences.setActiveModelId(selectedCatalogEntry.modelId)
                                                activeModelId = selectedCatalogEntry.modelId
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verify & Qualify AI Model")
                                }
                            }
                        }
                    }

                    ModelInstallationState.DOWNLOADING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(downloadSnapshot.currentStage, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            LinearProgressIndicator(
                                progress = { downloadSnapshot.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(10.dp),
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${downloadSnapshot.percent}% ($downloadedMb / $totalSizeMb MB)", fontSize = 12.sp)
                                Text("$speedMbps MB/s • ${downloadSnapshot.etaSeconds}s remaining", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { downloadEngine.pause() }) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause")
                                }
                                OutlinedButton(onClick = { downloadEngine.cancel() }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    ModelInstallationState.DOWNLOAD_INTERRUPTED -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PauseCircleFilled, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Download Interrupted", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                }
                                Text(
                                    text = downloadSnapshot.errorMessage ?: "Transfer paused at $downloadedMb MB of $totalSizeMb MB.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                LinearProgressIndicator(
                                    progress = { downloadSnapshot.percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        coroutineScope.launch { downloadEngine.downloadAndInstall() }
                                    }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resume Download")
                                    }
                                    OutlinedButton(onClick = { downloadEngine.cancel() }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }

                    ModelInstallationState.VERIFYING,
                    ModelInstallationState.VERIFIED,
                    ModelInstallationState.INSTALLING,
                    ModelInstallationState.RUNTIME_INITIALIZING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Text(
                                text = downloadSnapshot.currentStage,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    ModelInstallationState.AI_READY -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AI Model Ready", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "On-device AI ready — 8/8 verification checks passed for ${selectedCatalogEntry.displayName}.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onModelReady,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("Continue to LearnMate")
                                }
                            }
                        }
                    }

                    ModelInstallationState.FAILED -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val title = when (downloadSnapshot.failedStepNumber) {
                                        0 -> "Manifest Signature Invalid"
                                        1 -> "Model File Missing"
                                        2 -> "Model File Truncated"
                                        3 -> "Model Checksum Mismatch"
                                        4 -> "Tokenizer File Missing"
                                        5 -> "Tokenizer File Truncated"
                                        6 -> "Tokenizer Checksum Mismatch"
                                        7 -> "ExecuTorch Initialization Error"
                                        8 -> "Inference Smoke Test Failed"
                                        else -> "Installation Issue"
                                    }
                                    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = downloadSnapshot.errorMessage ?: "An unexpected error occurred during model qualification.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                downloadEngine.downloadAndInstall()
                                            }
                                        }
                                    ) {
                                        Text(downloadSnapshot.recoveryAction ?: "Retry Installation")
                                    }
                                    OutlinedButton(onClick = onSkipOrContinue) {
                                        Text("Textbook Mode")
                                    }
                                }
                            }
                        }
                    }

                    ModelInstallationState.INCOMPATIBLE -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Incompatible Hardware", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = downloadSnapshot.errorMessage ?: "This device does not meet the minimum requirements for on-device AI.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onSkipOrContinue,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Continue in Textbook Mode")
                                }
                            }
                        }
                    }
                }
            }

            // Fallback action
            OutlinedButton(
                onClick = onSkipOrContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip AI Setup (Textbook Mode)")
            }
        }
    }

    // Model Deletion Confirmation Dialog
    if (showDeleteConfirmDialog && modelToDelete != null) {
        val entry = modelToDelete!!
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                modelToDelete = null
            },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${entry.displayName}?") },
            text = {
                Text("Are you sure you want to delete ${entry.displayName} (${entry.modelSizeBytes / (1024 * 1024)} MB)? This will free up device storage. You can re-download it later.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = assetManager.deleteModel(entry.modelId, activeModelId)
                        showDeleteConfirmDialog = false
                        modelToDelete = null
                        if (deleted) {
                            actionMessage = "Deleted ${entry.displayName} from storage."
                            downloadEngine.refreshInstalledStatus()
                        } else {
                            actionMessage = "Could not delete ${entry.displayName}."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDeleteConfirmDialog = false
                    modelToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Configurable Production CDN / HTTPS Distribution Endpoint Dialog
    if (showCdnDialog) {
        AlertDialog(
            onDismissRequest = { showCdnDialog = false },
            icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Model Distribution CDN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Specify a custom HTTPS endpoint or GitHub tree URL (e.g., https://github.com/sudharson-pv/llama_model_download/tree/main/models) hosting Llama PTE weights and tokenizer models:",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = cdnInputText,
                        onValueChange = { cdnInputText = it },
                        label = { Text("HTTPS CDN or GitHub Tree URL") },
                        placeholder = { Text("https://github.com/sudharson-pv/llama_model_download/tree/main/models") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val trimmed = cdnInputText.trim().takeIf { it.isNotBlank() }
                    appPreferences.setCustomModelCdnUrl(trimmed)
                    downloadEngine.customCdnBaseUrl = trimmed
                    showCdnDialog = false
                    actionMessage = if (trimmed != null) "Custom CDN endpoint saved: $trimmed" else "Reset to default endpoint"
                }) {
                    Text("Save Endpoint")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    cdnInputText = ""
                    appPreferences.setCustomModelCdnUrl(null)
                    downloadEngine.customCdnBaseUrl = null
                    showCdnDialog = false
                    actionMessage = "Reset to default endpoint"
                }) {
                    Text("Reset Default")
                }
            }
        )
    }
}

@Composable
private fun ModelOptionCard(
    entry: ModelCatalogEntry,
    isSelected: Boolean,
    isInstalled: Boolean,
    isActive: Boolean,
    isInitializing: Boolean = false,
    isFailed: Boolean = false,
    isCompatible: Boolean,
    incompatibilityReason: String?,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isActive -> Color(0xFF2E7D32)
        isFailed -> MaterialTheme.colorScheme.error
        isInitializing -> Color(0xFFEF6C00)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        isActive -> Color(0xFFE8F5E9).copy(alpha = 0.5f)
        isFailed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = if (isSelected || isActive || isFailed) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = true, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (entry.isDefaultRecommended) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "RECOMMENDED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.modelSizeBytes / (1024 * 1024)} MB • ${entry.targetBackend} • Min ${(entry.minRamBytes / (1024L * 1024L * 1024L))}GB RAM",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isCompatible && incompatibilityReason != null) {
                    Text(
                        text = incompatibilityReason,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2E7D32)
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isInitializing) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEF6C00)
                    ) {
                        Text(
                            text = "INITIALIZING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isFailed) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = "FAILED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (isInstalled) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        Text(
                            text = "INSTALLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (!isCompatible) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Text(
                            text = "UNSUPPORTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

package com.learnmate.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.ai.ModelRuntimeState
import com.learnmate.ai.domain.lifecycle.BackupResult
import com.learnmate.ai.domain.lifecycle.RestoreResult
import com.learnmate.ai.localization.AppLanguage
import com.learnmate.ai.viewmodel.LearningViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    learningViewModel: LearningViewModel,
    currentLanguage: AppLanguage,
    isDarkTheme: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToModelDownload: () -> Unit = {},
    onNavigateToDiagnostics: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allProfiles by learningViewModel.allProfiles.collectAsState()
    val profileState by learningViewModel.profileState.collectAsState()
    val currentProfile = (profileState as? com.learnmate.ai.viewmodel.LearningUiState.Success)?.data

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDeleteProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_navigate_back)
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 0: Active Learner Profile & Multi-User Switcher
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACTIVE LEARNER PROFILE (MULTI-USER)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentProfile?.name ?: "No Profile Active",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${currentProfile?.targetDomain ?: "Universal Learning"} • ${currentProfile?.learnerCategory?.name ?: "COLLEGE_STUDENT"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (allProfiles.isNotEmpty()) {
                            Text(
                                text = "Switch Learner Profile (8 Archetypes):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                allProfiles.chunked(2).forEach { rowProfiles ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowProfiles.forEach { p ->
                                            val isSelected = (currentProfile?.userId == p.userId)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    learningViewModel.switchUser(p.userId)
                                                    Toast.makeText(context, "Switched to ${p.name}", Toast.LENGTH_SHORT).show()
                                                },
                                                label = { Text(p.name, fontSize = 11.sp) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 1: Language & Localization
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_language),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onNavigateToLanguage() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.current_language),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${currentLanguage.nativeName} (${currentLanguage.displayName})",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Section 1.5: On-Device AI Engine & Model Management (Unified Gate 6.16.5)
            val runtimeState by learningViewModel.runtimeState.collectAsState()
            val engineError by learningViewModel.engineError.collectAsState()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "On-Device AI Engine",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToModelDownload() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (runtimeState) {
                                    ModelRuntimeState.READY -> Icons.Default.Psychology
                                    ModelRuntimeState.VERIFIED -> Icons.Default.FileDownloadDone
                                    ModelRuntimeState.INITIALIZING -> Icons.Default.HourglassTop
                                    ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED -> Icons.Default.Error
                                    ModelRuntimeState.DOWNLOADING, ModelRuntimeState.VERIFYING -> Icons.Default.CloudDownload
                                    else -> Icons.Default.CloudDownload
                                },
                                contentDescription = null,
                                tint = when (runtimeState) {
                                    ModelRuntimeState.READY -> Color(0xFF2E7D32)
                                    ModelRuntimeState.VERIFIED -> MaterialTheme.colorScheme.primary
                                    ModelRuntimeState.INITIALIZING -> Color(0xFFEF6C00)
                                    ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED -> MaterialTheme.colorScheme.error
                                    ModelRuntimeState.DOWNLOADING, ModelRuntimeState.VERIFYING -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Meta Llama 3.2 1B Instruct",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (runtimeState) {
                                        ModelRuntimeState.READY -> "Installed & Active • Powered by on-device Llama 3.2 1B (XNNPACK CPU)"
                                        ModelRuntimeState.VERIFIED -> "Installed (Inactive) • Tap to initialize ExecuTorch runtime"
                                        ModelRuntimeState.INITIALIZING -> "Initializing ExecuTorch runtime and native JNI..."
                                        ModelRuntimeState.INITIALIZATION_FAILED -> "Initialization Failed: ${engineError ?: "Native runtime error"} • Tap for diagnostics"
                                        ModelRuntimeState.INFERENCE_FAILED -> "Inference Error: ${engineError ?: "Smoke test produced 0 tokens"} • Tap for diagnostics"
                                        ModelRuntimeState.DOWNLOADING -> "Downloading AI Model... • Tap to view progress"
                                        ModelRuntimeState.VERIFYING -> "Verifying cryptographic SHA-256 checksums..."
                                        else -> "Not Installed • Tap to download model for offline AI generation"
                                    },
                                    fontSize = 12.sp,
                                    color = when (runtimeState) {
                                        ModelRuntimeState.READY -> Color(0xFF2E7D32)
                                        ModelRuntimeState.VERIFIED -> MaterialTheme.colorScheme.primary
                                        ModelRuntimeState.INITIALIZING -> Color(0xFFEF6C00)
                                        ModelRuntimeState.INITIALIZATION_FAILED, ModelRuntimeState.INFERENCE_FAILED -> MaterialTheme.colorScheme.error
                                        ModelRuntimeState.DOWNLOADING, ModelRuntimeState.VERIFYING -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // Diagnostics Quick Link
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToDiagnostics() },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Model Diagnostics & Hardware Audit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Section 2: Appearance
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_theme),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = if (isDarkTheme) stringResource(R.string.theme_dark) else stringResource(R.string.theme_light),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isDarkTheme) "AI Dark UI" else "Curriculum Light UI",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        )
                    }
                }
            }

            // Section 3: Data Sovereignty, Backup & Privacy (Gate 6.11 UX Enhancement)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_data_lifecycle),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Backup Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBackupDialog = true }
                                .defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.backup_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.backup_desc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Restore Action
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRestoreDialog = true }
                                .defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.restore_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.restore_desc),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Data Portability & Export
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val exportFile = File(context.cacheDir, "learnmate_export_${System.currentTimeMillis()}.json")
                                    learningViewModel.exportLearnerDataJson(exportFile) { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Exported JSON (${res.getOrNull()?.length()} bytes)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Export failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_export_json), fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val exportFile = File(context.cacheDir, "learnmate_summary_${System.currentTimeMillis()}.md")
                                    learningViewModel.exportProgressSummaryMarkdown(exportFile) { res ->
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Exported Markdown Summary (${res.getOrNull()?.length()} bytes)", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Export failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_export_markdown), fontSize = 11.sp)
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Privacy Disclosures Action
                        TextButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_view_privacy_disclosure), fontSize = 13.sp)
                        }

                        // Local Erasure / Reset Action
                        TextButton(
                            onClick = { showDeleteProfileDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_delete_profile), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Section 4: Hardware Platform
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.section_device_info),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.competition_tag),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.target_device),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Text(
                            text = stringResource(R.string.target_soc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Text(
                            text = stringResource(R.string.runtime_status),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }

    // Dialog: Create Backup
    if (showBackupDialog) {
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(stringResource(R.string.backup_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_desc), fontSize = 13.sp)
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text(stringResource(R.string.restore_passphrase_prompt)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (passphrase.isNotBlank()) {
                            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
                            val backupFile = File(backupDir, "learnmate_backup_${System.currentTimeMillis()}.lmbackup")
                            learningViewModel.createBackup(passphrase.toCharArray(), backupFile) { result ->
                                when (result) {
                                    is BackupResult.Success -> {
                                        Toast.makeText(context, "Backup created: ${backupFile.name} (${result.fileSizeBytes} bytes)", Toast.LENGTH_LONG).show()
                                    }
                                    is BackupResult.Failure -> {
                                        Toast.makeText(context, "Backup failed: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            showBackupDialog = false
                        }
                    },
                    enabled = passphrase.isNotBlank(),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_create_backup))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBackupDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Dialog: Restore Backup
    if (showRestoreDialog) {
        var passphrase by remember { mutableStateOf("") }
        var confirmOverwrite by remember { mutableStateOf(false) }
        val backupDir = remember { File(context.filesDir, "backups") }
        val backupFiles = remember { backupDir.listFiles { f -> f.extension == "lmbackup" }?.toList() ?: emptyList() }
        var selectedFile by remember { mutableStateOf<File?>(backupFiles.firstOrNull()) }

        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.restore_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.restore_desc), fontSize = 13.sp)
                    if (backupFiles.isEmpty()) {
                        Text(
                            text = "No backup archives found in local storage. Create a backup first.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("Select Backup Archive:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        backupFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFile = file }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedFile == file, onClick = { selectedFile = file })
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(file.name, fontSize = 12.sp, maxLines = 1)
                            }
                        }

                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text(stringResource(R.string.restore_passphrase_prompt)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = confirmOverwrite, onCheckedChange = { confirmOverwrite = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.restore_overwrite_warning), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileToRestore = selectedFile
                        if (fileToRestore != null && passphrase.isNotBlank()) {
                            learningViewModel.restoreBackup(fileToRestore, passphrase.toCharArray(), confirmOverwrite) { result ->
                                when (result) {
                                    is RestoreResult.Success -> {
                                        Toast.makeText(context, "Restore successful: ${result.restoredTracksCount} tracks recovered", Toast.LENGTH_LONG).show()
                                    }
                                    is RestoreResult.ConfirmationRequired -> {
                                        Toast.makeText(context, "Confirmation required: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                    is RestoreResult.Failure -> {
                                        Toast.makeText(context, "Restore failed: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            showRestoreDialog = false
                        }
                    },
                    enabled = selectedFile != null && passphrase.isNotBlank() && confirmOverwrite,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_confirm_restore))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Dialog: Privacy Disclosures
    if (showPrivacyDialog) {
        val disclosureText = remember { learningViewModel.getPrivacyPolicyDisclosure() }
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(stringResource(R.string.privacy_disclosure_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = disclosureText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    // Dialog: Delete Profile
    if (showDeleteProfileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProfileDialog = false },
            title = { Text(stringResource(R.string.profile_reset_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Text(stringResource(R.string.profile_reset_desc), fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        learningViewModel.deleteProfile {
                            Toast.makeText(context, "Profile and local records purged.", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_delete_profile))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteProfileDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

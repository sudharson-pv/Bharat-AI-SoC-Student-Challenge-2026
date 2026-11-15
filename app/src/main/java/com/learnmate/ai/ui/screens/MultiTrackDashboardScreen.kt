package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTrackDashboardScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCurriculum: () -> Unit,
    onNavigateToRoadmap: () -> Unit,
    onNavigateToContentLibrary: () -> Unit = {}
) {
    val tracksState by learningViewModel.tracksState.collectAsState()
    val activeTrack by learningViewModel.activeTrack.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var trackToDelete by remember { mutableStateOf<LearningTrack?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.track_dashboard_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToContentLibrary,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = stringResource(R.string.content_packs_title)
                        )
                    }
                    IconButton(
                        onClick = onNavigateToCurriculum,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = stringResource(R.string.track_btn_browse_curriculum)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.track_btn_new)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.track_dashboard_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            when (val state = tracksState) {
                is LearningUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LearningUiState.Success -> {
                    val tracks = state.data
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(tracks, key = { it.trackId }) { track ->
                            TrackCard(
                                track = track,
                                isActive = track.trackId == activeTrack?.trackId,
                                onSwitch = {
                                    learningViewModel.switchActiveTrack(track.trackId)
                                },
                                onViewRoadmap = onNavigateToRoadmap,
                                onDelete = {
                                    trackToDelete = track
                                }
                            )
                        }
                    }
                }
                is LearningUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is LearningUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    trackToDelete?.let { track ->
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.delete_track_title),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            },
            text = {
                Text(text = stringResource(R.string.delete_track_confirm))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = track.trackId
                        trackToDelete = null
                        learningViewModel.deleteTrack(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_delete_track),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { trackToDelete = null },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showCreateDialog) {
        TrackCreationDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, mode, examName, commitment ->
                learningViewModel.createLearningTrack(
                    title = title,
                    learningMode = mode,
                    targetCurriculumNodeId = null,
                    dailyCommitmentMinutes = commitment,
                    targetExamName = examName.ifBlank { null }
                )
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun TrackCard(
    track: LearningTrack,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onViewRoadmap: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                )

                if (isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.track_active_badge),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Mode & Commitment Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = track.learningMode.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.track_commitment_format, track.dailyCommitmentMinutes),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (!track.targetExamName.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = track.targetExamName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.btn_delete_track),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isActive) {
                        TextButton(
                            onClick = onSwitch,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.track_btn_switch), fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onViewRoadmap,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.home_action_roadmap), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackCreationDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, mode: LearningMode, examName: String, commitment: Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(LearningMode.ACADEMIC) }
    var examName by remember { mutableStateOf("") }
    var commitmentMinutes by remember { mutableIntStateOf(60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.track_create_title),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.track_create_label_title)) },
                    placeholder = { Text("e.g. UPSC Civil Services Prelims") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.track_create_label_mode), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LearningMode.entries.take(2).forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.displayName.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LearningMode.entries.drop(2).forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.displayName.split(" ").first(), fontSize = 11.sp) }
                        )
                    }
                }

                if (selectedMode == LearningMode.COMPETITIVE_EXAM) {
                    OutlinedTextField(
                        value = examName,
                        onValueChange = { examName = it },
                        label = { Text(stringResource(R.string.track_create_label_exam)) },
                        placeholder = { Text("e.g. UPSC CSE, GATE, CAT, JEE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = stringResource(R.string.label_daily_commitment, commitmentMinutes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = commitmentMinutes.toFloat(),
                    onValueChange = { commitmentMinutes = it.toInt() },
                    valueRange = 15f..120f,
                    steps = 6
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, selectedMode, examName, commitmentMinutes)
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.track_create_btn_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}


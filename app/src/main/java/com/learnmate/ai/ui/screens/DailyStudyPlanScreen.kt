package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.learnmate.ai.R
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.AssignmentInstance
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.StudyTask
import com.learnmate.ai.domain.youtube.YouTubeVideoItem
import com.learnmate.ai.domain.youtube.VideoStudyNotes
import com.learnmate.ai.viewmodel.DayCheckpointResult
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyStudyPlanScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAssessment: () -> Unit
) {
    val tasksState by learningViewModel.studyTasksState.collectAsState()
    val unlockedDays by learningViewModel.unlockedDays.collectAsState()
    val checkpointResults by learningViewModel.checkpointResults.collectAsState()
    val completedAssignments by learningViewModel.completedAssignments.collectAsState()
    val profileState by learningViewModel.profileState.collectAsState()
    val roadmapState by learningViewModel.roadmapState.collectAsState()

    val currentProfile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.study_plan_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        when (val state = tasksState) {
            is LearningUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LearningUiState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToAssessment) {
                            Text(stringResource(R.string.home_btn_take_assessment))
                        }
                    }
                }
            }
            is LearningUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is LearningUiState.Success -> {
                DailyStudyPlanContent(
                    allTasks = state.data,
                    unlockedDays = unlockedDays,
                    checkpointResults = checkpointResults,
                    completedAssignments = completedAssignments,
                    currentProfile = currentProfile,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onToggleTask = { taskId, isCompleted ->
                        learningViewModel.toggleStudyTask(taskId, isCompleted)
                    },
                    learningViewModel = learningViewModel
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun DailyStudyPlanContent(
    allTasks: List<StudyTask>,
    unlockedDays: Set<Int>,
    checkpointResults: Map<Int, DayCheckpointResult>,
    completedAssignments: Map<Int, com.learnmate.ai.viewmodel.AssignmentResult>,
    currentProfile: LearnerProfile?,
    modifier: Modifier = Modifier,
    onToggleTask: (String, Boolean) -> Unit,
    learningViewModel: LearningViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val maxDayFromTasks = maxOf(1, allTasks.maxOfOrNull { it.dayIndex } ?: 1)
    val totalDays = maxOf(14, maxDayFromTasks)
    var selectedDay by remember { mutableIntStateOf(1) }

    // Dialog states
    var activeCheckpointQuestions by remember { mutableStateOf<List<AssessmentQuestion>?>(null) }
    var activeCheckpointTopic by remember { mutableStateOf("") }
    var activeCheckpointAttempt by remember { mutableIntStateOf(1) }
    var activeAssignment by remember { mutableStateOf<AssignmentInstance?>(null) }
    var activeVideoForNotes by remember { mutableStateOf<YouTubeVideoItem?>(null) }
    var isLoadingModal by remember { mutableStateOf(false) }

    // YouTube State
    val youtubeVideosState by learningViewModel.youtubeVideosState.collectAsState()
    val videoStudyNotesState by learningViewModel.videoStudyNotesState.collectAsState()
    val isLoadingVideoNotes by learningViewModel.isLoadingVideoNotes.collectAsState()

    val isDayUnlocked = selectedDay <= 1 || unlockedDays.contains(selectedDay)
    val dayTasks = allTasks.filter { it.dayIndex == selectedDay }
    val dayTopic = dayTasks.firstOrNull()?.topic ?: currentProfile?.subdomain?.takeIf { it.isNotBlank() } ?: currentProfile?.targetDomain ?: "Core Curriculum Concepts"
    val checkpointResult = checkpointResults[selectedDay]
    val assignmentResult = completedAssignments[selectedDay]

    LaunchedEffect(selectedDay, dayTopic) {
        if (dayTopic.isNotBlank()) {
            learningViewModel.loadYouTubeResourcesForTopic(currentProfile, dayTopic)
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Day selector row
        Text(
            text = "Study Days",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items((1..totalDays).toList()) { day ->
                val unlocked = day <= 1 || unlockedDays.contains(day)
                val passed = checkpointResults[day]?.isPassed == true
                val isSelected = selectedDay == day

                FilterChip(
                    selected = isSelected,
                    onClick = { selectedDay = day },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Day $day", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (passed) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Passed", modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32))
                            } else if (!unlocked) {
                                Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        if (!isDayUnlocked) {
            // Locked Day Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Day $selectedDay is Locked",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To unlock Day $selectedDay, complete all study tasks for Day ${selectedDay - 1} and pass the Day ${selectedDay - 1} Checkpoint Assessment with a score of 60% or higher.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { selectedDay = maxOf(1, selectedDay - 1) }) {
                        Text("Go to Day ${selectedDay - 1}")
                    }
                }
            }
        } else {
            // Unlocked Day Content
            val completedCount = dayTasks.count { it.isCompleted }
            val totalCount = dayTasks.size
            val progressFraction = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
            val progressPercent = (progressFraction * 100).toInt()

            // Daily Progress Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DAY $selectedDay FOCUS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dayTopic,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Text(
                            text = "$progressPercent%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$completedCount of $totalCount Tasks Completed",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Scheduled Tasks
            Text(
                text = "Scheduled Learning Lessons",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (dayTasks.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("No scheduled tasks for Day $selectedDay.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                dayTasks.forEach { task ->
                    Card(
                        onClick = { onToggleTask(task.id, !task.isCompleted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (task.isCompleted) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (task.isCompleted) 0.dp else 2.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (task.isCompleted) {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onToggleTask(task.id, it) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (task.isCompleted) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Day ${task.dayIndex}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "•  ${task.topic}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "•  ${task.durationMinutes}m",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // YouTube Video Lectures Section
            YouTubeVideoLecturesSection(
                dayTopic = dayTopic,
                youtubeVideosState = youtubeVideosState,
                onRefresh = {
                    learningViewModel.loadYouTubeResourcesForTopic(currentProfile, dayTopic)
                },
                onWatchVideo = { video ->
                    try {
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${video.id}"))
                        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(appIntent)
                    } catch (_: ActivityNotFoundException) {
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(video.playbackUrl))
                        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(webIntent)
                    }
                },
                onOpenNotes = { video ->
                    activeVideoForNotes = video
                    if (!videoStudyNotesState.containsKey(video.id)) {
                        learningViewModel.generateVideoNotes(video)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // AI Practical Assignment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Day $selectedDay AI Assignment",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (assignmentResult != null && assignmentResult.isSubmitted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F5E9)
                            ) {
                                Text(
                                    text = "Completed (${assignmentResult.scorePercent}%)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Practical problem-solving exercise on $dayTopic to validate real-world application.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (assignmentResult != null && assignmentResult.isSubmitted) {
                        Text(
                            text = assignmentResult.feedback,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    } else {
                        Button(
                            onClick = {
                                if (currentProfile != null) {
                                    coroutineScope.launch {
                                        isLoadingModal = true
                                        val asgn = learningViewModel.generateAssignmentForDay(currentProfile, selectedDay, dayTopic)
                                        activeAssignment = asgn
                                        isLoadingModal = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingModal
                        ) {
                            Text("Start Day $selectedDay Assignment")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day Checkpoint Assessment Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        checkpointResult?.isPassed == true -> Color(0xFFF1F8E9)
                        checkpointResult != null && !checkpointResult.isPassed -> Color(0xFFFFF3E0)
                        else -> MaterialTheme.colorScheme.surface
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    when {
                        checkpointResult?.isPassed == true -> Color(0xFF81C784)
                        checkpointResult != null && !checkpointResult.isPassed -> Color(0xFFFFB74D)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (checkpointResult?.isPassed == true) Icons.Default.CheckCircle else Icons.Default.Quiz,
                                contentDescription = null,
                                tint = if (checkpointResult?.isPassed == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Day $selectedDay Checkpoint (5 MCQs)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (checkpointResult != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (checkpointResult.isPassed) Color(0xFFC8E6C9) else Color(0xFFFFCC80)
                            ) {
                                Text(
                                    text = if (checkpointResult.isPassed) "Passed (${checkpointResult.scorePercent}%)" else "Score: ${checkpointResult.scorePercent}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (checkpointResult.isPassed) Color(0xFF1B5E20) else Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (checkpointResult == null) {
                        Text(
                            text = "Take this 5-question checkpoint assessment to verify mastery of $dayTopic. A score of 60% or higher is required to unlock Day ${selectedDay + 1}.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (currentProfile != null) {
                                    coroutineScope.launch {
                                        isLoadingModal = true
                                        val questions = learningViewModel.getCheckpointQuestions(currentProfile, selectedDay, dayTopic, 1)
                                        activeCheckpointQuestions = questions
                                        activeCheckpointTopic = dayTopic
                                        activeCheckpointAttempt = 1
                                        isLoadingModal = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoadingModal
                        ) {
                            Text("Take Day $selectedDay Checkpoint Assessment")
                        }
                    } else if (checkpointResult.isPassed) {
                        Text(
                            text = "🎉 Excellent! You scored ${checkpointResult.scorePercent}% (${checkpointResult.correctCount}/${checkpointResult.totalQuestions} correct). Day ${selectedDay + 1} is unlocked!",
                            fontSize = 13.sp,
                            color = Color(0xFF2E7D32),
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { selectedDay = minOf(totalDays, selectedDay + 1) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Proceed to Day ${selectedDay + 1}")
                        }
                    } else {
                        // Failed (< 60%)
                        Text(
                            text = "⚠️ Checkpoint not passed (${checkpointResult.scorePercent}%). Day ${selectedDay + 1} remains locked.",
                            fontSize = 13.sp,
                            color = Color(0xFFE65100),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!checkpointResult.remedialAdvice.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = checkpointResult.remedialAdvice,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                lineHeight = 17.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (currentProfile != null) {
                                    coroutineScope.launch {
                                        isLoadingModal = true
                                        val newAttempt = checkpointResult.attemptNumber + 1
                                        val questions = learningViewModel.getCheckpointQuestions(currentProfile, selectedDay, dayTopic, newAttempt)
                                        activeCheckpointQuestions = questions
                                        activeCheckpointTopic = dayTopic
                                        activeCheckpointAttempt = newAttempt
                                        isLoadingModal = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !isLoadingModal
                        ) {
                            Text("Retake Checkpoint Assessment")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Modal Dialog: Checkpoint Assessment
    activeCheckpointQuestions?.let { questions ->
        CheckpointAssessmentDialog(
            questions = questions,
            topic = activeCheckpointTopic,
            dayIndex = selectedDay,
            attemptNumber = activeCheckpointAttempt,
            onDismiss = { activeCheckpointQuestions = null },
            onSubmit = { answers ->
                if (currentProfile != null) {
                    learningViewModel.submitDayCheckpointAssessment(
                        profile = currentProfile,
                        dayIndex = selectedDay,
                        topic = activeCheckpointTopic,
                        questions = questions,
                        answers = answers,
                        attemptNumber = activeCheckpointAttempt
                    ) { _ ->
                        activeCheckpointQuestions = null
                    }
                } else {
                    activeCheckpointQuestions = null
                }
            }
        )
    }

    // Modal Dialog: Practical Assignment
    activeAssignment?.let { assignment ->
        AssignmentDialog(
            assignment = assignment,
            onDismiss = { activeAssignment = null },
            onSubmit = { answers ->
                if (currentProfile != null) {
                    learningViewModel.submitAssignment(
                        profile = currentProfile,
                        assignment = assignment,
                        answers = answers
                    ) { _ ->
                        activeAssignment = null
                    }
                } else {
                    activeAssignment = null
                }
            }
        )
    }

    // Modal Dialog: YouTube AI Study Notes & Check for Understanding
    activeVideoForNotes?.let { video ->
        val notes = videoStudyNotesState[video.id]
        VideoStudyNotesDialog(
            video = video,
            notes = notes,
            isLoading = isLoadingVideoNotes && notes == null,
            onDismiss = { activeVideoForNotes = null },
            onRegenerate = { transcript ->
                learningViewModel.generateVideoNotes(video, transcript)
            }
        )
    }
}

@Composable
private fun CheckpointAssessmentDialog(
    questions: List<AssessmentQuestion>,
    topic: String,
    dayIndex: Int,
    attemptNumber: Int,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, Int>) -> Unit
) {
    val userAnswers = remember { mutableStateMapOf<String, Int>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Day $dayIndex Checkpoint Assessment",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Topic: $topic • Attempt $attemptNumber",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Questions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    questions.forEachIndexed { qIdx, question ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Q${qIdx + 1}. ${question.questionText}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                question.options.forEachIndexed { optIdx, optionText ->
                                    val isSelected = userAnswers[question.id] == optIdx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = isSelected,
                                                onClick = { userAnswers[question.id] = optIdx }
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { userAnswers[question.id] = optIdx }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = optionText,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Submit button
                Button(
                    onClick = { onSubmit(userAnswers.toMap()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userAnswers.size == questions.size
                ) {
                    Text(
                        if (userAnswers.size == questions.size) "Submit Checkpoint Assessment"
                        else "Answer all ${questions.size} questions (${userAnswers.size}/${questions.size})"
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignmentDialog(
    assignment: AssignmentInstance,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, Int>) -> Unit
) {
    val userAnswers = remember { mutableStateMapOf<String, Int>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Day ${assignment.dayIndex} ${if (assignment.isAiGenerated) "AI Generated" else "Deterministic Fallback"} Assignment",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Band: ${assignment.difficultyBand} • ${assignment.questions.size} Tasks",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Questions
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    assignment.questions.forEachIndexed { qIdx, question ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Task ${qIdx + 1}: ${question.questionText}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                question.options.forEachIndexed { optIdx, optionText ->
                                    val isSelected = userAnswers[question.id] == optIdx
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = isSelected,
                                                onClick = { userAnswers[question.id] = optIdx }
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { userAnswers[question.id] = optIdx }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = optionText,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Submit button
                Button(
                    onClick = { onSubmit(userAnswers.toMap()) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userAnswers.size == assignment.questions.size
                ) {
                    Text(
                        if (userAnswers.size == assignment.questions.size) "Submit Assignment"
                        else "Complete all ${assignment.questions.size} tasks (${userAnswers.size}/${assignment.questions.size})"
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeVideoLecturesSection(
    dayTopic: String,
    youtubeVideosState: LearningUiState<List<YouTubeVideoItem>>,
    onRefresh: () -> Unit,
    onWatchVideo: (YouTubeVideoItem) -> Unit,
    onOpenNotes: (YouTubeVideoItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE53935)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "YouTube",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Recommended Video Lectures",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Curated academic resources for $dayTopic",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh video lectures",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (youtubeVideosState) {
                is LearningUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Searching verified academic lectures...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
                is LearningUiState.Empty -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                            Text(
                                text = "No external videos loaded. Use offline study materials.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                is LearningUiState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Video Recommendations (Offline Mode)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (youtubeVideosState.message.contains("quota", ignoreCase = true)) {
                                    "YouTube API quota limit reached. Using local curriculum materials and cached lectures."
                                } else {
                                    "Unable to connect to YouTube. Cached curriculum materials remain fully accessible offline."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                is LearningUiState.Success -> {
                    val videos = youtubeVideosState.data
                    if (videos.isEmpty()) {
                        Text(
                            text = "No video lectures available for this topic.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(videos) { video ->
                                YouTubeVideoCard(
                                    video = video,
                                    onWatch = { onWatchVideo(video) },
                                    onNotes = { onOpenNotes(video) }
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideoItem,
    onWatch: () -> Unit,
    onNotes: () -> Unit
) {
    Card(
        modifier = Modifier.width(260.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column {
            // Video Preview Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(0xFF212121)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = Color.White.copy(alpha = 0.85f)
                )

                if (video.durationFormatted.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = video.durationFormatted,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (video.isOfflineCached) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = "Offline Cached",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Info Body
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = video.channelTitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium
                )

                if (video.viewCount != null && video.viewCount > 0) {
                    val viewsFormatted = if (video.viewCount >= 1000000) {
                        String.format("%.1fM views", video.viewCount / 1000000.0)
                    } else if (video.viewCount >= 1000) {
                        String.format("%.1fK views", video.viewCount / 1000.0)
                    } else {
                        "${video.viewCount} views"
                    }
                    Text(
                        text = viewsFormatted,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onWatch,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Watch", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = onNotes,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("AI Notes", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoStudyNotesDialog(
    video: YouTubeVideoItem,
    notes: VideoStudyNotes?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRegenerate: (String) -> Unit
) {
    var customTranscript by remember { mutableStateOf("") }
    var showTranscriptInput by remember { mutableStateOf(false) }
    val selectedQuestionAnswers = remember { mutableStateMapOf<String, Int>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "AI Video Study Notes",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = video.title,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Provenance badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                        Text(
                            text = "Local On-Device Meta Llama 3.2 1B (ExecuTorch 1.4.0) • Offline",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Synthesizing lecture concepts with Llama 3.2 1B...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Running locally via XNNPACK CPU backend",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else if (notes != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Summary card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Core Concept Summary",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = notes.summary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Key Takeaways
                        if (notes.keyTakeaways.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Key Academic Takeaways",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    notes.keyTakeaways.forEach { takeaway ->
                                        Row(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(takeaway, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }

                        // Generated Check-for-Understanding Questions
                        if (notes.generatedQuestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Check for Understanding (Practice MCQs)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    notes.generatedQuestions.forEachIndexed { qIdx, question ->
                                        val answeredIdx = selectedQuestionAnswers[question.id]
                                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                            Text(
                                                text = "Q${qIdx + 1}: ${question.questionText}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            question.options.forEachIndexed { optIdx, optText ->
                                                val isSelected = answeredIdx == optIdx
                                                val isCorrect = optIdx == question.correctIndex
                                                val optionColor = when {
                                                    answeredIdx == null -> MaterialTheme.colorScheme.surface
                                                    isSelected && isCorrect -> Color(0xFFC8E6C9)
                                                    isSelected && !isCorrect -> Color(0xFFFFCDD2)
                                                    isCorrect -> Color(0xFFE8F5E9)
                                                    else -> MaterialTheme.colorScheme.surface
                                                }

                                                Surface(
                                                    onClick = { selectedQuestionAnswers[question.id] = optIdx },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = optionColor,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "${('A' + optIdx)}. $optText",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }

                                            if (answeredIdx != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = question.explanation,
                                                    fontSize = 11.sp,
                                                    color = if (answeredIdx == question.correctIndex) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Optional Transcript / Caption Input Area
                        if (showTranscriptInput) {
                            OutlinedTextField(
                                value = customTranscript,
                                onValueChange = { customTranscript = it },
                                label = { Text("Paste Lecture Transcript / Subtitles", fontSize = 11.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp)
                            )
                            Button(
                                onClick = {
                                    onRegenerate(customTranscript)
                                    showTranscriptInput = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = customTranscript.isNotBlank()
                            ) {
                                Text("Re-synthesize with Local Llama", fontSize = 12.sp)
                            }
                        } else {
                            TextButton(
                                onClick = { showTranscriptInput = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Provide Lecture Captions / Transcript", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = { onRegenerate("") }) {
                            Text("Generate Notes with Local Llama 3.2 1B")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
            }
        }
    }
}

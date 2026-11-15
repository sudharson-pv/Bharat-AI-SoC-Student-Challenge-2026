package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.data.content.StarterCurriculumCatalog
import com.learnmate.ai.domain.*
import com.learnmate.ai.viewmodel.LearningViewModel

/**
 * Gate 6.4: Universal Learning Module Screen.
 * Provides rich pedagogical study experience across all 8 disciplines with:
 * - Structured learning objectives
 * - Verified textbook explanations and subsections
 * - Interactive ASCII diagrams and schematics
 * - Worked step-by-step examples
 * - Key terms glossary
 * - Deterministic knowledge checks with instant feedback and score
 * - Independent track-scoped progress tracking (Not Started, In Progress, Completed, Needs Revision)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningModuleScreen(
    nodeId: String,
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTopic: (String) -> Unit = {}
) {
    val activeLesson by learningViewModel.activeLessonModule.collectAsState()
    val topicProgress by learningViewModel.activeTopicProgress.collectAsState()
    val knowledgeCheckState by learningViewModel.knowledgeCheckState.collectAsState()
    val activeTrack by learningViewModel.activeTrack.collectAsState()

    var selectedLanguage by remember { mutableStateOf("en") }
    var selectedAnswers by remember { mutableStateOf<Map<String, List<Int>>>(emptyMap()) }
    var showProgressMenu by remember { mutableStateOf(false) }

    LaunchedEffect(nodeId, selectedLanguage) {
        learningViewModel.loadLessonModule(nodeId, selectedLanguage)
        selectedAnswers = emptyMap()
    }

    val supportedNodes = remember { StarterCurriculumCatalog.getSupportedNodeIds() }
    val currentIndex = supportedNodes.indexOf(nodeId)
    val prevNodeId = if (currentIndex > 0) supportedNodes[currentIndex - 1] else null
    val nextNodeId = if (currentIndex != -1 && currentIndex < supportedNodes.size - 1) supportedNodes[currentIndex + 1] else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeLesson?.topicTitle ?: stringResource(R.string.learning_module_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${activeLesson?.courseCode ?: "LM-CURR"} • ${activeTrack?.title ?: "Active Track"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_navigate_back))
                    }
                },
                actions = {
                    // Status Action Chip / Menu
                    Box {
                        AssistChip(
                            onClick = { showProgressMenu = true },
                            label = {
                                Text(
                                    text = topicProgress?.status?.displayName ?: "Not Started",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                val icon = when (topicProgress?.status) {
                                    LearningProgressStatus.COMPLETED -> Icons.Default.CheckCircle
                                    LearningProgressStatus.IN_PROGRESS -> Icons.Default.Pending
                                    LearningProgressStatus.NEEDS_REVISION -> Icons.Default.Warning
                                    else -> Icons.Default.RadioButtonUnchecked
                                }
                                val tint = when (topicProgress?.status) {
                                    LearningProgressStatus.COMPLETED -> Color(0xFF2E7D32)
                                    LearningProgressStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primary
                                    LearningProgressStatus.NEEDS_REVISION -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.outline
                                }
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            }
                        )
                        DropdownMenu(
                            expanded = showProgressMenu,
                            onDismissRequest = { showProgressMenu = false }
                        ) {
                            LearningProgressStatus.entries.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status.displayName, fontSize = 13.sp) },
                                    onClick = {
                                        learningViewModel.updateTopicProgress(status)
                                        showProgressMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { prevNodeId?.let { onNavigateToTopic(it) } },
                        enabled = prevNodeId != null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.learning_module_btn_prev), fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val newStatus = if (topicProgress?.status == LearningProgressStatus.COMPLETED) {
                                LearningProgressStatus.IN_PROGRESS
                            } else {
                                LearningProgressStatus.COMPLETED
                            }
                            learningViewModel.updateTopicProgress(newStatus)
                        },
                        colors = if (topicProgress?.status == LearningProgressStatus.COMPLETED) {
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        } else ButtonDefaults.buttonColors()
                    ) {
                        Icon(
                            if (topicProgress?.status == LearningProgressStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (topicProgress?.status == LearningProgressStatus.COMPLETED) "Completed" else stringResource(R.string.learning_module_mark_completed),
                            fontSize = 12.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { nextNodeId?.let { onNavigateToTopic(it) } },
                        enabled = nextNodeId != null,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.learning_module_btn_next), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    ) { padding ->
        val lesson = activeLesson
        if (lesson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Language Selector Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Language:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf("en" to "English", "ta" to "தமிழ்", "hi" to "हिंदी", "kn" to "ಕನ್ನಡ").forEach { (code, label) ->
                        FilterChip(
                            selected = selectedLanguage == code,
                            onClick = { selectedLanguage = code },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // 2. Fallback Notice Banner (if any)
            if (lesson.isFallbackLanguage && lesson.fallbackNotice != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lesson.fallbackNotice,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // 3. Header Card: Discipline, Duration, Level
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text(lesson.discipline, fontSize = 11.sp, fontWeight = FontWeight.Medium) }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${lesson.estimatedMinutes} min", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(lesson.topicTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(lesson.educationLevel, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (lesson.isStarterCollection) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = lesson.starterDisclaimer,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Learning Objectives Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.learning_module_objectives), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        lesson.learningObjectives.forEachIndexed { index, objective ->
                            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Text("${index + 1}. ", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Text(objective, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }

            // 5. Prerequisites (if any)
            if (lesson.prerequisites.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Prerequisites: ${lesson.prerequisites.joinToString(", ")}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 6. Lesson Subsections
            items(lesson.sections, key = { it.heading }) { section ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(section.heading, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(section.content, fontSize = 13.sp, lineHeight = 20.sp)

                        section.formula?.let { formula ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = formula,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        section.keyTakeaway?.let { takeaway ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(takeaway, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }

            // 7. Visual Diagrams / Schematics (Monospace Preformatted)
            if (lesson.diagrams.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.learning_module_diagrams), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(lesson.diagrams, key = { it.title }) { diag ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schema, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(diag.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(diag.caption, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        Text(
                                            text = diag.asciiArt,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Worked Examples
            if (lesson.workedExamples.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.learning_module_worked_examples), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(lesson.workedExamples, key = { it.title }) { ex ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(ex.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Problem: ${ex.problemStatement}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Step-by-Step Solution:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(4.dp))
                            ex.steps.forEach { step ->
                                Text("• $step", fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ex.finalAnswer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 9. Key Terms & Definitions
            if (lesson.keyTerms.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.learning_module_glossary), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        lesson.keyTerms.forEach { kt ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(kt.term, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(kt.definition, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 10. Deterministic Knowledge Checks
            if (lesson.knowledgeChecks.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.learning_module_knowledge_check), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                knowledgeCheckState?.let { kcs ->
                                    if (kcs.isSubmitted) {
                                        Surface(
                                            color = if (kcs.scorePercent >= 60.0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Text(
                                                text = "${kcs.scorePercent.toInt()}% (${kcs.correctCount}/${kcs.totalQuestions})",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            lesson.knowledgeChecks.forEachIndexed { qIdx, question ->
                                val questionResult = knowledgeCheckState?.results?.get(question.questionId)
                                val currentSelections = selectedAnswers[question.questionId] ?: emptyList()

                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        when {
                                            questionResult?.isCorrect == true -> Color(0xFF2E7D32)
                                            questionResult?.isCorrect == false -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Q${qIdx + 1}. ${question.questionText}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                            Text(
                                                text = question.format.name.replace("_", " "),
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        question.options.forEachIndexed { optIdx, optionText ->
                                            val isSelected = currentSelections.contains(optIdx)
                                            val isCorrectOption = question.correctIndices.contains(optIdx)

                                            val optBgColor = when {
                                                knowledgeCheckState?.isSubmitted == true && isCorrectOption -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                                knowledgeCheckState?.isSubmitted == true && isSelected && !isCorrectOption -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                else -> Color.Transparent
                                            }

                                            Surface(
                                                color = optBgColor,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 2.dp)
                                                    .clickable(enabled = knowledgeCheckState?.isSubmitted != true) {
                                                        val updated = when (question.format) {
                                                            KnowledgeQuestionFormat.SINGLE_CHOICE, KnowledgeQuestionFormat.TRUE_FALSE -> listOf(optIdx)
                                                            KnowledgeQuestionFormat.MULTIPLE_CHOICE -> {
                                                                if (isSelected) currentSelections - optIdx else currentSelections + optIdx
                                                            }
                                                        }
                                                        selectedAnswers = selectedAnswers + (question.questionId to updated)
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (question.format == KnowledgeQuestionFormat.MULTIPLE_CHOICE) {
                                                        Checkbox(
                                                            checked = isSelected,
                                                            onCheckedChange = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    } else {
                                                        RadioButton(
                                                            selected = isSelected,
                                                            onClick = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(optionText, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        // Explanation after submission
                                        if (knowledgeCheckState?.isSubmitted == true && questionResult != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = if (questionResult.isCorrect) Color(0xFF2E7D32).copy(alpha = 0.08f) else MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            if (questionResult.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                            contentDescription = null,
                                                            tint = if (questionResult.isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            if (questionResult.isCorrect) "Correct!" else "Needs Review",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = if (questionResult.isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(questionResult.explanation, fontSize = 11.sp, lineHeight = 16.sp)
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text("Citation: ${questionResult.referenceCitation}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (knowledgeCheckState?.isSubmitted == true) {
                                    OutlinedButton(onClick = {
                                        learningViewModel.resetKnowledgeCheck()
                                        selectedAnswers = emptyMap()
                                    }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.learning_module_retry), fontSize = 12.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = { learningViewModel.submitKnowledgeCheck(selectedAnswers) },
                                        enabled = selectedAnswers.isNotEmpty()
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.learning_module_check_answer), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 11. Accredited Textbook References
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.learning_module_references), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        lesson.references.forEach { ref ->
                            Text(
                                text = "• ${ref.formattedCitation}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

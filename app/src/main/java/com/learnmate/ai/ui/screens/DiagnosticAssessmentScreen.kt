package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticAssessmentScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onAssessmentComplete: () -> Unit
) {
    val profileState by learningViewModel.profileState.collectAsState()
    val profile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data

    var questions by remember { mutableStateOf<List<AssessmentQuestion>>(emptyList()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    val userAnswers = remember { mutableStateMapOf<String, Int>() }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(profile) {
        if (profile != null) {
            questions = learningViewModel.getOrGenerateDiagnosticAssessment(profile)
        }
    }

    val currentQuestion = questions.getOrNull(currentIndex)
    val totalCount = questions.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.assessment_title),
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
        if (questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AI Diagnostic Assessment is tailoring 10 questions to your domain & goal...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (currentQuestion != null) {
            val progress = (currentIndex + 1).toFloat() / totalCount

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Counter & Domain Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.assessment_question_counter, currentIndex + 1, totalCount),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text(currentQuestion.topic, fontSize = 11.sp) }
                    )
                }

                // Question Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Question ${currentIndex + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = currentQuestion.questionText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Options List
                val selectedOption = userAnswers[currentQuestion.id]
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    currentQuestion.options.forEachIndexed { optIndex, optionText ->
                        val isChosen = selectedOption == optIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isChosen,
                                    onClick = { userAnswers[currentQuestion.id] = optIndex }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChosen) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                width = if (isChosen) 2.dp else 1.dp,
                                color = if (isChosen) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isChosen,
                                    onClick = { userAnswers[currentQuestion.id] = optIndex },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = optionText,
                                    fontSize = 14.sp,
                                    color = if (isChosen) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                // Immediate Feedback & Verified Explanation Card
                if (selectedOption != null) {
                    val isCorrect = selectedOption == currentQuestion.correctIndex
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isCorrect) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isCorrect) "Correct Answer!" else "Incorrect (Correct: Option ${currentQuestion.correctIndex + 1})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentQuestion.explanation,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.btn_prev_question))
                    }

                    if (currentIndex < totalCount - 1) {
                        Button(
                            onClick = { currentIndex++ },
                            enabled = userAnswers.containsKey(currentQuestion.id),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.btn_next_question))
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                if (profile != null && !isSubmitting) {
                                    isSubmitting = true
                                    learningViewModel.submitDiagnosticAssessment(
                                        profile = profile,
                                        questions = questions,
                                        answers = userAnswers.toMap()
                                    )
                                    onAssessmentComplete()
                                }
                            },
                            enabled = userAnswers.containsKey(currentQuestion.id) && !isSubmitting,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_submit_assessment))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}


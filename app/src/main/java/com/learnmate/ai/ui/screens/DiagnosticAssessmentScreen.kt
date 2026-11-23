package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import java.util.Random

enum class AssessmentFlowMode {
    TAKING,
    REVIEW,
    RESULTS
}

data class ShuffledOption(
    val id: String,
    val text: String,
    val originalIndex: Int
)

data class ShuffledQuestion(
    val question: AssessmentQuestion,
    val options: List<ShuffledOption>
)

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
    val userAnswers = remember { mutableStateMapOf<String, Int>() } // question.id -> originalIndex
    var flowMode by remember { mutableStateOf(AssessmentFlowMode.TAKING) }
    var showConfirmSubmitDialog by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.userId, profile?.targetDomain, profile?.subdomain, profile?.goal) {
        val targetDom = profile?.targetDomain.orEmpty()
        val normDom = TopicTaxonomy.normalizeDomain(targetDom)
        val subdomain = profile?.subdomain.orEmpty().trim()
        val needsRegeneration = profile != null && (
            questions.isEmpty() ||
            (subdomain.isNotBlank() && !questions.all { it.subdomain.equals(subdomain, ignoreCase = true) }) ||
            (subdomain.isBlank() && !questions.all {
                it.domain.equals(targetDom, ignoreCase = true) ||
                TopicTaxonomy.normalizeDomain(it.domain).equals(normDom, ignoreCase = true)
            })
        )
        if (needsRegeneration) {
            questions = learningViewModel.getOrGenerateDiagnosticAssessment(profile)
            userAnswers.clear()
            currentIndex = 0
            flowMode = AssessmentFlowMode.TAKING
        }
    }

    val assessmentSessionSeed = remember { java.util.UUID.randomUUID().mostSignificantBits }

    // Session-based randomized options per question (persisted stably for this active assessment session)
    val shuffledQuestions = remember(questions, assessmentSessionSeed) {
        questions.map { q ->
            val rng = Random(assessmentSessionSeed xor q.id.hashCode().toLong())
            val shuffled = q.options.mapIndexed { idx, optText ->
                ShuffledOption(
                    id = "opt_${idx}",
                    text = optText,
                    originalIndex = idx
                )
            }.shuffled(rng)
            ShuffledQuestion(question = q, options = shuffled)
        }
    }

    val totalCount = shuffledQuestions.size
    val currentShuffled = shuffledQuestions.getOrNull(currentIndex)
    val currentQuestion = currentShuffled?.question

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (flowMode) {
                            AssessmentFlowMode.TAKING -> stringResource(R.string.assessment_title)
                            AssessmentFlowMode.REVIEW -> "Review Answers"
                            AssessmentFlowMode.RESULTS -> "Diagnostic Results"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (flowMode) {
                            AssessmentFlowMode.TAKING -> onNavigateBack()
                            AssessmentFlowMode.REVIEW -> flowMode = AssessmentFlowMode.TAKING
                            AssessmentFlowMode.RESULTS -> onAssessmentComplete()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_navigate_back)
                        )
                    }
                },
                actions = {
                    if (flowMode == AssessmentFlowMode.TAKING && totalCount > 0) {
                        TextButton(onClick = { flowMode = AssessmentFlowMode.REVIEW }) {
                            Text(
                                text = "Review (${userAnswers.size}/$totalCount)",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (flowMode == AssessmentFlowMode.REVIEW && userAnswers.isNotEmpty() && !isSubmitting) {
                        TextButton(onClick = {
                            if (profile != null && !isSubmitting) {
                                isSubmitting = true
                                learningViewModel.submitDiagnosticAssessment(
                                    profile = profile,
                                    questions = questions,
                                    answers = userAnswers.toMap()
                                )
                                flowMode = AssessmentFlowMode.RESULTS
                            }
                        }) {
                            Text(
                                text = "Submit",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            when (flowMode) {
                AssessmentFlowMode.TAKING -> {
                    if (currentQuestion != null && currentShuffled != null) {
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isAi = currentQuestion.isAiGenerated
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isAi) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                        border = BorderStroke(1.dp, if (isAi) Color(0xFF4CAF50) else Color(0xFFFFB74D))
                                    ) {
                                        Text(
                                            text = if (isAi) "AI Generated & Validated" else "Deterministic Fallback",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isAi) Color(0xFF2E7D32) else Color(0xFFE65100),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(currentQuestion.topic, fontSize = 11.sp) }
                                    )
                                }
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

                            // Shuffled Options List (Zero immediate correctness feedback shown during testing)
                            val selectedOriginalIndex = userAnswers[currentQuestion.id]
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                currentShuffled.options.forEach { opt ->
                                    val isChosen = selectedOriginalIndex == opt.originalIndex
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = isChosen,
                                                onClick = { userAnswers[currentQuestion.id] = opt.originalIndex }
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
                                                onClick = { userAnswers[currentQuestion.id] = opt.originalIndex },
                                                colors = RadioButtonDefaults.colors(
                                                    selectedColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = opt.text,
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
                                        onClick = { flowMode = AssessmentFlowMode.REVIEW },
                                        enabled = userAnswers.containsKey(currentQuestion.id),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Review Answers")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }

                AssessmentFlowMode.REVIEW -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Assessment Summary",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${userAnswers.size} of $totalCount questions answered. Review your selections or tap any question to modify your answer before final submission.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Question Review List
                        shuffledQuestions.forEachIndexed { idx, sq ->
                            val q = sq.question
                            val answeredIndex = userAnswers[q.id]
                            val isAnswered = answeredIndex != null
                            val selectedText = if (isAnswered) {
                                q.options.getOrNull(answeredIndex!!) ?: "Selected"
                            } else "Not Answered"

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentIndex = idx
                                        flowMode = AssessmentFlowMode.TAKING
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isAnswered) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Question ${idx + 1}: ${q.topic}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = q.questionText,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isAnswered) "Answer: $selectedText" else "Status: Incomplete",
                                            fontSize = 12.sp,
                                            fontWeight = if (isAnswered) FontWeight.Normal else FontWeight.Medium,
                                            color = if (isAnswered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    TextButton(onClick = {
                                        currentIndex = idx
                                        flowMode = AssessmentFlowMode.TAKING
                                    }) {
                                        Text("Edit", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Submit Assessment Button
                        Button(
                            onClick = {
                                if (profile != null && !isSubmitting) {
                                    isSubmitting = true
                                    learningViewModel.submitDiagnosticAssessment(
                                        profile = profile,
                                        questions = questions,
                                        answers = userAnswers.toMap()
                                    )
                                    flowMode = AssessmentFlowMode.RESULTS
                                }
                            },
                            enabled = userAnswers.isNotEmpty() && !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Submit Diagnostic Assessment",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }

                AssessmentFlowMode.RESULTS -> {
                    val correctCount = questions.count { (userAnswers[it.id] ?: -1) == it.correctIndex }
                    val scorePercent = if (totalCount > 0) (correctCount * 100) / totalCount else 0

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Score Header Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (scorePercent >= 70) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (scorePercent >= 70) Color(0xFF81C784) else Color(0xFFFFB74D)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (scorePercent >= 70) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$scorePercent%",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (scorePercent >= 70) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                                Text(
                                    text = "$correctCount of $totalCount Correct",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = when {
                                        scorePercent >= 80 -> "Strong Domain Mastery Demonstrated. Advanced topics will be prioritized in your roadmap."
                                        scorePercent >= 50 -> "Developing Mastery. Core foundations identified; targeted gap remediation scheduled."
                                        else -> "Foundational Focus Required. Essential building blocks have been prioritized in your personalized roadmap."
                                    },
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "Question-by-Question Detailed Breakdown",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        // Breakdown of each question with explanations
                        questions.forEachIndexed { idx, q ->
                            val selectedIdx = userAnswers[q.id]
                            val isCorrect = selectedIdx == q.correctIndex
                            val selectedText = if (selectedIdx != null) {
                                q.options.getOrNull(selectedIdx) ?: "Option ${selectedIdx + 1}"
                            } else "None (Skipped)"
                            val correctText = q.options.getOrNull(q.correctIndex) ?: "Option ${q.correctIndex + 1}"

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCorrect) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color(0xFFE57373).copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Question ${idx + 1} • ${q.topic}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCorrect) "Correct" else "Incorrect",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = q.questionText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 20.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Your Answer: $selectedText",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )

                                    if (!isCorrect) {
                                        Text(
                                            text = "Correct Answer: $correctText",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ) {
                                        Text(
                                            text = "Explanation: ${q.explanation}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // CTA to Dashboard & Roadmap
                        Button(
                            onClick = onAssessmentComplete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "View Personalized Roadmap & Dashboard",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showConfirmSubmitDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSubmitDialog = false },
            title = {
                Text(
                    text = "Submit Diagnostic Assessment?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "You have answered ${userAnswers.size} of $totalCount questions. Once submitted, your answers will be graded by the AI engine and your personalized learning roadmap will be synthesized.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSubmitDialog = false
                        if (profile != null && !isSubmitting) {
                            isSubmitting = true
                            learningViewModel.submitDiagnosticAssessment(
                                profile = profile,
                                questions = questions,
                                answers = userAnswers.toMap()
                            )
                            flowMode = AssessmentFlowMode.RESULTS
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirm & Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSubmitDialog = false }) {
                    Text("Review Again")
                }
            }
        )
    }
}

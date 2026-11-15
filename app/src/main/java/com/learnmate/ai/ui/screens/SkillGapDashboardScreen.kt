    package com.learnmate.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
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
import com.learnmate.ai.domain.SkillGapReport
import com.learnmate.ai.domain.analytics.LearnerAnalyticsReport
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.planner.DailyStudyPlan
import com.learnmate.ai.domain.planner.PlannedStudyTask
import com.learnmate.ai.domain.planner.PlannedTaskType
import com.learnmate.ai.domain.recommendation.RecommendationPriority
import com.learnmate.ai.domain.recommendation.RecommendedTopicItem
import com.learnmate.ai.domain.recommendation.RevisionTopicItem
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillGapDashboardScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRoadmap: () -> Unit,
    onNavigateToStudyPlan: () -> Unit
) {
    val skillGapState by learningViewModel.skillGapState.collectAsState()
    val topicMasteries by learningViewModel.topicMasteries.collectAsState()
    val learningPath by learningViewModel.learningPath.collectAsState()
    val revisionQueue by learningViewModel.revisionQueue.collectAsState()
    val spacedRepetitionQueue by learningViewModel.spacedRepetitionQueue.collectAsState()
    val analyticsReport by learningViewModel.analyticsReport.collectAsState()
    val dailyStudyPlan by learningViewModel.dailyStudyPlan.collectAsState()
    val assessmentHistory by learningViewModel.assessmentHistory.collectAsState()
    val coverageReport by learningViewModel.coverageReport.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        learningViewModel.loadAdaptiveLearningEngine()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.skill_gap_title),
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
        val report = (skillGapState as? LearningUiState.Success<SkillGapReport>)?.data

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Mastery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        val overdueCount = spacedRepetitionQueue.count { it.isOverdue }
                        Text(
                            text = if (overdueCount > 0) "Spaced Rep ($overdueCount)" else "Spaced Rep",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Planner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("Analytics", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            when (selectedTabIndex) {
                0 -> SkillGapContent(
                    report = report,
                    topicMasteries = topicMasteries,
                    learningPath = learningPath,
                    revisionQueue = revisionQueue,
                    coverageReport = coverageReport,
                    modifier = Modifier.fillMaxSize(),
                    onCompleteRevision = { nodeId ->
                        learningViewModel.completeRevision(nodeId)
                    },
                    onNavigateToRoadmap = onNavigateToRoadmap,
                    onNavigateToStudyPlan = onNavigateToStudyPlan
                )
                1 -> SpacedRepetitionTabContent(
                    queue = spacedRepetitionQueue,
                    onReviewComplete = { nodeId, score ->
                        learningViewModel.recordSpacedRepetitionReview(nodeId, score)
                    },
                    onManualSchedule = { nodeId, days ->
                        learningViewModel.scheduleManualRevision(nodeId, days)
                    }
                )
                2 -> StudyPlannerTabContent(
                    plan = dailyStudyPlan,
                    onCompleteTask = { /* no-op */ }
                )
                3 -> AnalyticsTabContent(
                    report = analyticsReport,
                    assessmentHistory = assessmentHistory
                )
            }
        }
    }
}

@Composable
private fun SkillGapContent(
    report: SkillGapReport?,
    topicMasteries: List<TopicMasteryResult>,
    learningPath: List<RecommendedTopicItem>,
    revisionQueue: List<RevisionTopicItem>,
    coverageReport: com.learnmate.ai.domain.outcomes.CurriculumCoverageReport?,
    modifier: Modifier = Modifier,
    onCompleteRevision: (String) -> Unit,
    onNavigateToRoadmap: () -> Unit,
    onNavigateToStudyPlan: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overall Readiness Score Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Overall Curriculum Readiness",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                val avgScore = if (topicMasteries.isNotEmpty()) {
                    val assessedTopics = topicMasteries.filter { it.isAssessed }
                    if (assessedTopics.isNotEmpty()) assessedTopics.map { it.compositeScore }.average().toInt() else 0
                } else report?.overallScorePercent ?: 0

                Text(
                    text = "$avgScore%",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (avgScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                )
            }
        }

        // Gate 6.7: Learning Outcomes & Objective-Level Performance
        if (coverageReport != null && coverageReport.coveredObjectivesCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Learning Outcomes Mastery",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${coverageReport.coveredObjectivesCount}/${coverageReport.totalObjectivesCount} (${coverageReport.coveragePercent}%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (coverageReport.weakObjectivesCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${coverageReport.weakObjectivesCount} objectives require focused practice (< 70% threshold):",
                            fontSize = 12.sp,
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        coverageReport.objectivePerformances.filter { it.requiresPractice }.forEach { weakObj ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "• ${weakObj.statement}",
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${weakObj.scorePercent.toInt()}%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sample: ${weakObj.sampleSummary}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    if (weakObj.evidenceConfidence == "PRELIMINARY_EVIDENCE") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFFE0B2)
                                        ) {
                                            Text(
                                                text = "Preliminary (N < 2)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE65100),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "All assessed learning objectives currently meet or exceed the 70% mastery threshold.",
                            fontSize = 11.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = coverageReport.psychometricDisclaimer,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        lineHeight = 12.sp
                    )
                }
            }
        }

        // Topic-Level Knowledge Mastery Breakdown
        if (topicMasteries.isNotEmpty()) {
            Text(
                text = "Topic-Level Knowledge Mastery",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            topicMasteries.forEach { mastery ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mastery.topicTitle,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = mastery.discipline,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(mastery.masteryLevel.badgeColorHex)
                            ) {
                                Text(
                                    text = mastery.masteryLevel.displayName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (mastery.isAssessed) "Score: ${mastery.compositeScore}%" else "Not Assessed",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Prereqs: ${mastery.completedPrerequisitesCount}/${mastery.totalPrerequisitesCount}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        // Personalised Learning Path Recommendations
        if (learningPath.isNotEmpty()) {
            Text(
                text = "Recommended Next Steps",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            learningPath.take(5).forEach { rec ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rec.topicTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val badgeColor = when (rec.priority) {
                                RecommendationPriority.CRITICAL_PREREQUISITE -> Color(0xFFD32F2F)
                                RecommendationPriority.SKILL_GAP -> Color(0xFFF57C00)
                                RecommendationPriority.REVISION -> Color(0xFF7B1FA2)
                                RecommendationPriority.NEXT_CURRICULUM_STEP -> Color(0xFF1976D2)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = rec.priority.label,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rec.reason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // Action Buttons
        Button(
            onClick = onNavigateToRoadmap,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.btn_view_roadmap), fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = onNavigateToStudyPlan,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.home_action_study_plan), fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SpacedRepetitionTabContent(
    queue: List<SpacedRepetitionItem>,
    onReviewComplete: (String, Double) -> Unit,
    onManualSchedule: (String, Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Spaced Repetition Schedule (SM-2)",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        val overdueCount = queue.count { it.isOverdue }
        if (overdueCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$overdueCount topic(s) overdue for spaced review to prevent memory decay.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB71C1C)
                    )
                }
            }
        }

        if (queue.isEmpty()) {
            Text(
                text = "No spaced repetition items scheduled. Complete curriculum study modules to initiate spaced review sequences.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        } else {
            queue.forEach { srs ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (srs.isOverdue) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = srs.topicTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (srs.isOverdue) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            ) {
                                Text(
                                    text = if (srs.isOverdue) "OVERDUE ${srs.overdueDays}d" else "In ${srs.intervalDays}d",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "SM-2 Repetition: ${srs.repetitionNumber} | Interval: ${srs.intervalDays}d | Ease: ${srs.easeFactor}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onManualSchedule(srs.nodeId, 3) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("In 3 Days", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onReviewComplete(srs.nodeId, 85.0) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Review Now", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyPlannerTabContent(
    plan: DailyStudyPlan?,
    onCompleteTask: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Personalised Daily Study Plan",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (plan != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target Commitment: ${plan.targetDailyMinutes} mins",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Allocated: ${plan.allocatedMinutes} mins",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = plan.planSummary,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Text(
                text = "Scheduled Tasks (${plan.tasks.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            plan.tasks.forEach { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = task.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(task.taskType.badgeColorHex).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${task.taskType.displayName} (${task.durationMinutes}m)",
                                    color = Color(task.taskType.badgeColorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = task.explainableReason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        } else {
            Text("Loading daily study plan...", fontSize = 13.sp)
        }
    }
}

@Composable
private fun AnalyticsTabContent(
    report: LearnerAnalyticsReport?,
    assessmentHistory: List<AssessmentAttemptRecord>
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Learning Progress Intelligence",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        if (report != null) {
            // Data Sufficiency Notice Guard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (report.hasSufficientData) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (report.hasSufficientData) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (report.hasSufficientData) Color(0xFF2E7D32) else Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = report.dataSufficiencyNotice,
                        fontSize = 12.sp,
                        color = if (report.hasSufficientData) Color(0xFF1B5E20) else Color(0xFFBF360C)
                    )
                }
            }

            // Metrics Summary Grid
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Streak", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${report.currentStreakDays} Days", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Revision Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text("${report.revisionCompletionRate}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Score Trend", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(report.scoreTrajectory, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Assessment History
            Text(
                text = "Assessment Attempts History (${assessmentHistory.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (assessmentHistory.isEmpty()) {
                Text("No assessment attempts recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            } else {
                assessmentHistory.take(5).forEach { att ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(att.topicTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("Score: ${att.correctCount}/${att.totalQuestions} (${att.scorePercent}%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(if (att.isStale) "STALE" else "VERIFIED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (att.isStale) Color(0xFFD32F2F) else Color(0xFF388E3C))
                        }
                    }
                }
            }
        } else {
            Text("Loading analytics...", fontSize = 13.sp)
        }
    }
}

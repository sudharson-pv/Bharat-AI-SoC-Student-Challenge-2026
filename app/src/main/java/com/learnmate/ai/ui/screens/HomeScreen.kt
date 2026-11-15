package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.PersonalisedRoadmap
import com.learnmate.ai.domain.StudyTask
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    learningViewModel: LearningViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAssessment: () -> Unit,
    onNavigateToRoadmap: () -> Unit,
    onNavigateToStudyPlan: () -> Unit,
    onNavigateToSkillGap: () -> Unit,
    onNavigateToAiTutor: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMultiTrack: () -> Unit = {},
    onNavigateToCurriculum: () -> Unit = {},
    onNavigateToUniversalProfile: () -> Unit = {},
    onNavigateToContentLibrary: () -> Unit = {}
) {
    val profileState by learningViewModel.profileState.collectAsState()
    val roadmapState by learningViewModel.roadmapState.collectAsState()
    val tasksState by learningViewModel.studyTasksState.collectAsState()

    val profile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data
    val roadmap = (roadmapState as? LearningUiState.Success<PersonalisedRoadmap>)?.data
    val tasks = (tasksState as? LearningUiState.Success<List<StudyTask>>)?.data ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToLanguage) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = stringResource(R.string.nav_language)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Case 1: No profile configured yet -> Onboarding prompt
            if (profile == null || !profile.onboardingCompleted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.welcome_title),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.welcome_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToOnboarding,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.home_btn_start_onboarding))
                        }
                    }
                }
            } else {
                // Personalized Greeting Card
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
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = stringResource(R.string.home_greeting, profile.name),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    lineHeight = 26.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile.targetDomain,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            SuggestionChip(
                                onClick = onNavigateToUniversalProfile,
                                label = { Text("Profile", fontSize = 11.sp) },
                                modifier = Modifier.wrapContentWidth()
                            )
                        }

                        if (profile.goal.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${stringResource(R.string.home_goal_prefix)}${profile.goal}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.OfflinePin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.status_offline),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Multi-Track Status Card
                val activeTrack by learningViewModel.activeTrack.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.AltRoute,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeTrack?.title ?: "Multi-Track Learning Engine",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.track_active_badge),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = onNavigateToMultiTrack,
                                leadingIcon = {
                                    Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text(stringResource(R.string.track_dashboard_title), fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = onNavigateToCurriculum,
                                leadingIcon = {
                                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text(stringResource(R.string.track_btn_browse_curriculum), fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = onNavigateToContentLibrary,
                                leadingIcon = {
                                    Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text("Content Packs", fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = onNavigateToUniversalProfile,
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                label = { Text("Profile", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Assessment prompt if not completed
                if (!profile.assessmentCompleted) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.home_card_assessment_prompt_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.home_card_assessment_prompt_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToAssessment,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(stringResource(R.string.home_btn_take_assessment))
                            }
                        }
                    }
                }

                // Quick Navigation Hub
                Text(
                    text = stringResource(R.string.home_section_modules),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HubButton(
                        title = stringResource(R.string.home_action_roadmap),
                        icon = Icons.Default.Map,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToRoadmap
                    )
                    HubButton(
                        title = stringResource(R.string.home_action_study_plan),
                        icon = Icons.Default.CalendarToday,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToStudyPlan
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HubButton(
                        title = stringResource(R.string.home_action_skill_gap),
                        icon = Icons.Default.Analytics,
                        color = Color(0xFFF57C00),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToSkillGap
                    )
                    HubButton(
                        title = stringResource(R.string.home_action_tutor),
                        icon = Icons.Default.Psychology,
                        color = Color(0xFF7B1FA2),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAiTutor
                    )
                }

                // Today's Study Tasks Preview
                if (tasks.isNotEmpty()) {
                    val completed = tasks.count { it.isCompleted }
                    val fraction = completed.toFloat() / tasks.size

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_section_today_plan),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$completed/${tasks.size} Done",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .semantics {
                                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                                        contentDescription = "$completed of ${tasks.size} tasks completed"
                                    },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            tasks.take(3).forEach { task ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { learningViewModel.toggleStudyTask(task.id, !task.isCompleted) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.isCompleted,
                                        onCheckedChange = { learningViewModel.toggleStudyTask(task.id, it) },
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = task.title,
                                        fontSize = 13.sp,
                                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Roadmap Milestone Preview
                if (roadmap != null && roadmap.steps.isNotEmpty()) {
                    val activeStep = roadmap.steps.firstOrNull { it.status == "IN_PROGRESS" } ?: roadmap.steps.first()
                    Card(
                        onClick = onNavigateToRoadmap,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_section_active_roadmap),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                SuggestionChip(
                                    onClick = onNavigateToRoadmap,
                                    label = { Text("Step ${activeStep.stepNumber}", fontSize = 11.sp) }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = activeStep.topic ?: "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeStep.description ?: activeStep.title ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                maxLines = 2,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Competition Tag Footer
            Text(
                text = stringResource(R.string.competition_tag),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HubButton(
    title: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

package com.learnmate.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.localization.AppLanguage
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    learningViewModel: LearningViewModel,
    currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    onLanguageSelected: (AppLanguage) -> Unit = {},
    onNavigateBack: () -> Unit,
    onOnboardingComplete: () -> Unit
) {
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var selectedDomain by remember { mutableStateOf("Computer Science & AI") }
    var selectedSubdomain by remember { mutableStateOf("Data Structures & Algorithms") }
    var goal by remember { mutableStateOf("Build conceptual mastery and structured problem-solving skills") }
    var targetRole by remember { mutableStateOf("Software Engineer") }
    var selectedEducationLevel by remember { mutableStateOf(EducationLevel.UNDERGRADUATE) }
    var selectedCategory by remember { mutableStateOf(LearnerCategory.ENGINEERING_STUDENT) }
    var selectedAudience by remember { mutableStateOf(AudienceLevel.UNDERGRADUATE) }
    var selectedDailyMinutes by remember { mutableIntStateOf(60) }
    var selectedWeeklyDays by remember { mutableIntStateOf(5) }

    val universalDisciplines = remember {
        listOf(
            "Secondary School Science & Math",
            "Polytechnic Electrical Engg",
            "Computer Science & AI",
            "Electronics & VLSI",
            "Mechanical Engineering",
            "Civil & Structural Engineering",
            "Physics & Applied Math",
            "Chemistry & Materials Science",
            "Biology & Biotechnology",
            "Medicine & Healthcare",
            "Business & MBA Management",
            "Finance & Accounting",
            "UPSC Civil Services",
            "Law & Jurisprudence",
            "Research Methodology & Ph.D.",
            "Industrial Safety & ITI Trades",
            "Environmental Science",
            "Psychology & Cognitive Science",
            "Literature & Linguistics",
            "Architecture & Urban Planning",
            "Data Science & Statistics",
            "Agriculture & Agronomy"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.onboarding_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            }

            // Step 1: Language-First Selection (Gate 6.16 P2)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Choose Learning Language / भाषा / மொழி / ಭಾಷೆ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(AppLanguage.entries.toTypedArray()) { lang ->
                            val isSelected = (currentLanguage == lang)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onLanguageSelected(lang) },
                                label = {
                                    Column {
                                        Text(lang.nativeName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                                        Text(lang.displayName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Step 2: Access Mode (100% Offline-First Data Sovereignty)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Offline Data Sovereignty (No Account Required)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "LearnMate AI is 100% offline-first. All learner profiles, diagnostic assessments, learning roadmaps, and AI tutor models run locally on your device with complete privacy. No online login or cloud account is required.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Local Offline Profile Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Text(
                text = "3. Profile & Academic Focus",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_learner_name)) },
                placeholder = { Text("e.g. Aarav / Priya / Vikram") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            // Target Domain Selection (Free-text + 22 Universal Disciplines)
            Text(
                text = stringResource(R.string.label_target_domain),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = selectedDomain,
                onValueChange = { selectedDomain = it },
                label = { Text("Enter Target Learning Domain") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) }
            )

            Text(
                text = "Select from 20+ Universal Disciplines:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(universalDisciplines) { discipline ->
                    val isSelected = selectedDomain.equals(discipline, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedDomain = discipline },
                        label = { Text(discipline, fontSize = 11.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            // Subdomain / Focus Area
            OutlinedTextField(
                value = selectedSubdomain,
                onValueChange = { selectedSubdomain = it },
                label = { Text(stringResource(R.string.label_subdomain)) },
                placeholder = { Text("e.g. Applied Mechanics, Constitution, Data Structures") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Explore, contentDescription = null) }
            )

            // Goal
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = it },
                label = { Text(stringResource(R.string.label_goal)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) }
            )

            // Target Role / Academic Objective
            OutlinedTextField(
                value = targetRole,
                onValueChange = { targetRole = it },
                label = { Text(stringResource(R.string.label_target_role)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) }
            )

            // Learner Category Selection
            Text(
                text = stringResource(R.string.label_learner_category),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LearnerCategory.entries.chunked(2).forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowCats.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.displayName.split(" / ").first(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Audience Level
            Text(
                text = stringResource(R.string.label_education_level),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AudienceLevel.entries.forEach { level ->
                    val isLevelSelected = level == selectedAudience
                    FilterChip(
                        selected = isLevelSelected,
                        onClick = { selectedAudience = level },
                        label = { Text(level.displayName, fontSize = 12.sp) }
                    )
                }
            }

            // Daily Minutes
            Text(
                text = stringResource(R.string.label_daily_minutes),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(30, 45, 60, 90).forEach { mins ->
                    val isMinSelected = mins == selectedDailyMinutes
                    FilterChip(
                        selected = isMinSelected,
                        onClick = { selectedDailyMinutes = mins },
                        label = { Text("$mins mins", fontSize = 13.sp) }
                    )
                }
            }

            // Weekly Days
            Text(
                text = stringResource(R.string.label_weekly_days),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 6, 7).forEach { days ->
                    val isDaysSelected = days == selectedWeeklyDays
                    FilterChip(
                        selected = isDaysSelected,
                        onClick = { selectedWeeklyDays = days },
                        label = { Text("$days days/wk", fontSize = 13.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save and Continue
            Button(
                onClick = {
                    val finalName = name.ifBlank { "Learner" }
                    val profile = LearnerProfile(
                        userId = "default_learner",
                        name = finalName,
                        targetDomain = selectedDomain,
                        subdomain = selectedSubdomain,
                        goal = goal,
                        targetRole = targetRole,
                        educationLevel = selectedEducationLevel,
                        learnerCategory = selectedCategory,
                        audienceLevel = selectedAudience,
                        dailyStudyMinutes = selectedDailyMinutes,
                        weeklyStudyDays = selectedWeeklyDays,
                        onboardingCompleted = true,
                        assessmentCompleted = false
                    )
                    learningViewModel.createOrUpdateProfile(profile)
                    onOnboardingComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_start_assessment),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

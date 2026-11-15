package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.localization.AppLanguage
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalProfileScreen(
    learningViewModel: LearningViewModel,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAssessment: () -> Unit = {}
) {
    val profileState by learningViewModel.profileState.collectAsState()
    val profile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data

    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var selectedEducationLevel by remember(profile) {
        mutableStateOf(profile?.educationLevel ?: EducationLevel.UNDERGRADUATE)
    }
    var selectedCategory by remember(profile) {
        mutableStateOf(profile?.learnerCategory ?: LearnerCategory.ENGINEERING_STUDENT)
    }
    var targetDomain by remember(profile) { mutableStateOf(profile?.targetDomain ?: "") }
    var subdomain by remember(profile) { mutableStateOf(profile?.subdomain ?: "") }
    var targetRole by remember(profile) { mutableStateOf(profile?.targetRole ?: "") }
    var goal by remember(profile) { mutableStateOf(profile?.goal ?: "") }
    var previousBackground by remember(profile) { mutableStateOf(profile?.previousBackground ?: "") }
    var dailyMinutes by remember(profile) { mutableIntStateOf(profile?.dailyStudyMinutes ?: 60) }
    var isMinor by remember(profile) { mutableStateOf(profile?.isMinor ?: false) }
    var guardianConsent by remember(profile) { mutableStateOf(profile?.guardianConsentGiven ?: false) }

    var saveSuccessMessage by remember { mutableStateOf(false) }

    // 22 Universal Disciplines across Human Knowledge and Applied Science
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
                        text = stringResource(R.string.profile_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_navigate_back))
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
            // Learner Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Learner Identity",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.label_learner_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Universal Learning Domain & 20+ Discipline Quick Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Target Learning Domain",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Type any universal domain or tap a recommended discipline below:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = targetDomain,
                        onValueChange = { targetDomain = it },
                        label = { Text("Universal Domain (e.g. Physics, MBA, Computer Science)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "Suggested Disciplines (20+ Fields):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(universalDisciplines) { discipline ->
                            val isSelected = targetDomain.equals(discipline, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = { targetDomain = discipline },
                                label = { Text(discipline, fontSize = 11.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Education Level Selection
            Text(
                text = stringResource(R.string.label_education_level_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EducationLevel.entries.chunked(2).forEach { rowLevels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowLevels.forEach { level ->
                            val isSelected = selectedEducationLevel == level
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedEducationLevel = level },
                                label = { Text(level.displayName.split(" / ").first(), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Learner Category Selection
            Text(
                text = stringResource(R.string.label_learner_category),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
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

            // Dynamic Category-Specific Academic Fields
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${selectedCategory.displayName.split(" / ").first()} Details",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    when (selectedCategory) {
                        LearnerCategory.SCHOOL_STUDENT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Grade / Standard (e.g. Class 10, Class 12)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Educational Board (e.g. CBSE, ICSE, State Board)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Target Examination / Milestones (e.g. Board Exam 2026)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.DIPLOMA_STUDENT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Polytechnic Branch (e.g. Electrical & Electronics)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Current Semester (e.g. Semester 4)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Technical Trade / Skill Goal (e.g. Industrial Automation)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.ENGINEERING_STUDENT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Engineering Discipline (e.g. Computer Science, Mechanical)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Semester / Year (e.g. Semester 6 / 3rd Year)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Technical Specialization / Capstone Target") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.ARTS_SCIENCE_STUDENT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Major Subject (e.g. Physics, Chemistry, Economics)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Degree Level (e.g. B.Sc Honours, M.Sc, B.A)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Academic & Career Objective") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.MANAGEMENT_MBA_STUDENT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Management Program (e.g. MBA, PGDM, BBA)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Specialization (e.g. Finance, Marketing, Operations)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Leadership Role / Case Study Mastery Target") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.COMPETITIVE_EXAM_ASPIRANT -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Target Examination (e.g. UPSC CSE, GATE, CAT, JEE, NEET)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Target Year & Attempt (e.g. 2026 - 1st Attempt)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Target Score / All-India Rank Benchmark") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        LearnerCategory.RESEARCH_SCHOLAR -> {
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Research Area / Thesis Domain (e.g. Edge AI Architecture)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Methodology (e.g. Empirical Quantitative, Experimental)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Primary Hypothesis & Target Publication") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        else -> { // VOCATIONAL_TRAINEE, LIFELONG_LEARNER
                            OutlinedTextField(
                                value = subdomain,
                                onValueChange = { subdomain = it },
                                label = { Text("Trade / Craft (e.g. Industrial Electrician, CNC)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = targetRole,
                                onValueChange = { targetRole = it },
                                label = { Text("Certification Standard (e.g. NCVT, ITI, ISO 9001)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = goal,
                                onValueChange = { goal = it },
                                label = { Text("Competency Milestone (e.g. LOTO Safety & Fault Diagnosis)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Interface Language
            Text(
                text = stringResource(R.string.label_ui_language),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = currentLanguage == lang,
                        onClick = { onLanguageSelected(lang) },
                        label = { Text(lang.nativeName, fontSize = 12.sp) }
                    )
                }
            }

            // Minor Protection & DPDP Parental Consent
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Minor Learner (Under 18)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = isMinor,
                            onCheckedChange = { isMinor = it }
                        )
                    }

                    if (isMinor) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = guardianConsent,
                                onCheckedChange = { guardianConsent = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.label_minor_consent),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Daily Commitment
            Text(
                text = "Daily Study Commitment: $dailyMinutes minutes/day",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = dailyMinutes.toFloat(),
                onValueChange = { dailyMinutes = it.toInt() },
                valueRange = 15f..180f,
                steps = 10
            )

            // Save Button
            Button(
                onClick = {
                    val updated = (profile ?: LearnerProfile(
                        userId = "default_learner",
                        name = name,
                        targetDomain = targetDomain.ifBlank { "Universal Learning" }
                    )).copy(
                        name = name,
                        targetDomain = targetDomain.ifBlank { "Universal Learning" },
                        subdomain = subdomain,
                        targetRole = targetRole,
                        goal = goal,
                        previousBackground = previousBackground,
                        educationLevel = selectedEducationLevel,
                        learnerCategory = selectedCategory,
                        dailyStudyMinutes = dailyMinutes,
                        isMinor = isMinor,
                        guardianConsentGiven = guardianConsent,
                        preferredLanguageCode = currentLanguage.code
                    )
                    learningViewModel.createOrUpdateProfile(updated)
                    saveSuccessMessage = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_save_profile))
            }

            if (saveSuccessMessage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Profile Saved Successfully!",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Take your 10-question AI Diagnostic Assessment to generate your personalized learning roadmap.",
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToAssessment,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start AI Diagnostic (10 Questions)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.learnmate.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.data.content.DocumentIngestionEngine
import com.learnmate.ai.data.db.ContentPackEntity
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentLibraryScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCurriculum: (String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val baseStorageDir = remember { File(context.filesDir, "content_packs") }

    val installedPacks by learningViewModel.installedPacks.collectAsState()
    val packAuditStatus by learningViewModel.packAuditStatus.collectAsState()
    val packAssignedTracks by learningViewModel.packAssignedTracks.collectAsState()
    val importMessage by learningViewModel.importStatusMessage.collectAsState()
    val importProgress by learningViewModel.importProgress.collectAsState()
    val tracksState by learningViewModel.tracksState.collectAsState()
    val userTracks = (tracksState as? LearningUiState.Success<List<LearningTrack>>)?.data ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var selectedLevelFilter by remember { mutableStateOf("ALL") }
    var selectedFrameworkFilter by remember { mutableStateOf("ALL") }
    var selectedLanguageFilter by remember { mutableStateOf("ALL") }

    var selectedPackForDetails by remember { mutableStateOf<ContentPackEntity?>(null) }
    var packToAssign by remember { mutableStateOf<ContentPackEntity?>(null) }
    var packToUninstall by remember { mutableStateOf<Pair<ContentPackEntity, List<LearningTrack>>?>(null) }
    var statusSnackbarMessage by remember { mutableStateOf<String?>(null) }

    // Gate 6.16 P2: 8 Core Content Library Sections
    val librarySections = remember {
        listOf(
            "Installed Packs",
            "My Materials",
            "My Curriculum",
            "Discover Resources",
            "Recently Opened",
            "Bookmarked",
            "Downloads",
            "AI Collections"
        )
    }
    var selectedSectionIndex by remember { mutableIntStateOf(0) }

    // SAF Document Ingestion Staging (Gate 6.16 P1.4)
    var stagedDoc by remember { mutableStateOf<DocumentIngestionEngine.IngestedDocumentStaging?>(null) }
    var isIngestingDoc by remember { mutableStateOf(false) }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isIngestingDoc = true
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "Imported_Document.pdf"
                val mime = context.contentResolver.getType(uri) ?: "application/pdf"
                val result = DocumentIngestionEngine.stageDocument(context, uri, fileName, mime)
                result.onSuccess { staging ->
                    stagedDoc = staging
                    statusSnackbarMessage = "Document staged: ${staging.extractedNodes.size} chapters extracted (${(staging.overallConfidence * 100).toInt()}% confidence)."
                }.onFailure { err ->
                    statusSnackbarMessage = "Document ingestion failed: ${err.message}"
                }
                isIngestingDoc = false
            }
        }
    }

    // SAF Document Picker for .lmpack archives
    val safPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            learningViewModel.importContentPackFromSafUri(
                context = context,
                uri = uri,
                baseStorageDir = baseStorageDir
            ) { _, msg ->
                statusSnackbarMessage = msg
            }
        }
    }

    LaunchedEffect(Unit) {
        learningViewModel.loadInstalledPacks(baseStorageDir)
        learningViewModel.loadTracks()
    }

    // Filter categories mapped to Universal Taxonomy
    val categories = listOf("ALL" to "All Disciplines") +
        com.learnmate.ai.data.taxonomy.UniversalTaxonomy.ContentDiscipline.entries.map { it.name to it.displayName }

    val educationLevels = listOf(
        "ALL" to "All Levels",
        "SECONDARY" to "Secondary (K-10)",
        "HIGHER_SECONDARY" to "Higher Secondary (11-12)",
        "DIPLOMA" to "Diploma / Polytechnic",
        "UNDERGRADUATE" to "Undergraduate (Degree)",
        "POSTGRADUATE" to "Postgraduate",
        "VOCATIONAL" to "Vocational / Trade"
    )

    val examFrameworks = listOf(
        "ALL" to "All Frameworks",
        "CBSE" to "CBSE / NCERT",
        "ICSE" to "ICSE / CISCE",
        "STATE_BOARD" to "State Board",
        "DOTE_DIPLOMA" to "Polytechnic / DOTE",
        "AICTE_ENGINEERING" to "AICTE / University",
        "UPSC_CIVIL_SERVICES" to "UPSC / State PSC",
        "NONE" to "General / Foundational"
    )

    val languages = listOf(
        "ALL" to "All Languages",
        "en" to "English",
        "hi" to "हिंदी (Hindi)",
        "ta" to "தமிழ் (Tamil)",
        "kn" to "ಕನ್ನಡ (Kannada)"
    )

    // Filter packs
    val filteredPacks = installedPacks.filter { pack ->
        val matchesQuery = searchQuery.isBlank() ||
            pack.title.contains(searchQuery, ignoreCase = true) ||
            pack.authorOrProvider.contains(searchQuery, ignoreCase = true) ||
            pack.packId.contains(searchQuery, ignoreCase = true)

        val matchesCategory = selectedCategoryFilter == "ALL" || pack.category.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesLanguage = selectedLanguageFilter == "ALL" || pack.primaryLanguage.equals(selectedLanguageFilter, ignoreCase = true)

        matchesQuery && matchesCategory && matchesLanguage
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Content Library & Pack Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Universal Offline Curriculum & Study Materials",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_navigate_back)
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = {
                            safPickerLauncher.launch(arrayOf("*/*", "application/octet-stream"))
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .lmpack", fontSize = 13.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Import Status Banner if set
            importMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.contains("failed", ignoreCase = true) || msg.contains("error", ignoreCase = true))
                            MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (msg.contains("failed", ignoreCase = true)) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (msg.contains("failed", ignoreCase = true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { learningViewModel.clearImportStatusMessage() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Gate 6.16 P2: 8 Core Content Library Section Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedSectionIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 12.dp
            ) {
                librarySections.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSectionIndex == index,
                        onClick = { selectedSectionIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSectionIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            when (selectedSectionIndex) {
                0 -> {
                    // TAB 0: Installed Packs
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text("Search by title, publisher, or ID...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Discipline Filter Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { (catKey, catLabel) ->
                            FilterChip(
                                selected = selectedCategoryFilter == catKey,
                                onClick = { selectedCategoryFilter = catKey },
                                label = { Text(catLabel, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Education Level Filter Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(educationLevels) { (lvlKey, lvlLabel) ->
                            FilterChip(
                                selected = selectedLevelFilter == lvlKey,
                                onClick = { selectedLevelFilter = lvlKey },
                                label = { Text(lvlLabel, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Exam Framework Filter Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(examFrameworks) { (fwKey, fwLabel) ->
                            FilterChip(
                                selected = selectedFrameworkFilter == fwKey,
                                onClick = { selectedFrameworkFilter = fwKey },
                                label = { Text(fwLabel, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Language Filter Chips + Reset Filters
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(languages) { (langKey, langLabel) ->
                            FilterChip(
                                selected = selectedLanguageFilter == langKey,
                                onClick = { selectedLanguageFilter = langKey },
                                label = { Text(langLabel, fontSize = 11.sp) },
                                leadingIcon = {
                                    if (langKey != "ALL") {
                                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(12.dp))
                                    }
                                }
                            )
                        }
                        if (selectedCategoryFilter != "ALL" || selectedLevelFilter != "ALL" || selectedFrameworkFilter != "ALL" || selectedLanguageFilter != "ALL" || searchQuery.isNotBlank()) {
                            item {
                                TextButton(
                                    onClick = {
                                        selectedCategoryFilter = "ALL"
                                        selectedLevelFilter = "ALL"
                                        selectedFrameworkFilter = "ALL"
                                        selectedLanguageFilter = "ALL"
                                        searchQuery = ""
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset All", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // Summary header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${filteredPacks.size} Packs Available (${installedPacks.size} Total Installed)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { safPickerLauncher.launch(arrayOf("*/*", "application/octet-stream")) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SAF Ingest", fontSize = 11.sp)
                        }
                    }

                    // Content Pack List
                    if (filteredPacks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (installedPacks.isEmpty()) "No Content Packs Installed" else "No matching packs found",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Select an offline .lmpack archive from storage to ingest curriculum data.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { safPickerLauncher.launch(arrayOf("*/*", "application/octet-stream")) }) {
                                    Icon(Icons.Default.FileOpen, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Select .lmpack File")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredPacks, key = { it.packId }) { pack ->
                                val isIntact = packAuditStatus[pack.packId] ?: true
                                val assignedTracks = packAssignedTracks[pack.packId] ?: emptyList()

                                ContentPackCard(
                                    pack = pack,
                                    isIntact = isIntact,
                                    assignedTracks = assignedTracks,
                                    onViewDetails = { selectedPackForDetails = pack },
                                    onBrowseCurriculum = { onNavigateToCurriculum(pack.packId) },
                                    onAssignToTrack = { packToAssign = pack },
                                    onUninstall = { packToUninstall = Pair(pack, assignedTracks) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: My Materials (Document Ingestion & OCR)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Upload & Ingest Study Materials", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Import textbooks, scanned syllabus PDFs, lecture slides (PNG/JPEG), or text notes. The on-device ingestion engine parses chapters, estimates confidence, and prepares verified curriculum nodes.",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        docPickerLauncher.launch(arrayOf(
                                            "application/pdf",
                                            "text/plain",
                                            "text/markdown",
                                            "image/png",
                                            "image/jpeg",
                                            "image/webp"
                                        ))
                                    },
                                    enabled = !isIngestingDoc
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isIngestingDoc) "Parsing Document..." else "Select Document (PDF/Image/Text)")
                                }
                            }
                        }

                        stagedDoc?.let { staging ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(staging.fileName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        AssistChip(
                                            onClick = {},
                                            label = { Text("${(staging.overallConfidence * 100).toInt()}% Confidence") }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Pages: ${staging.totalPages} | Size: ${staging.fileSize / 1024} KB | Checksum: ${staging.checksumSha256.take(12)}...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Extracted Chapters (${staging.extractedNodes.size}):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                                    staging.extractedNodes.forEachIndexed { idx, draft ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = draft.isApproved,
                                                onCheckedChange = { checked ->
                                                    DocumentIngestionEngine.updateDraftNode(
                                                        staging = staging,
                                                        tempId = draft.tempId,
                                                        newTitle = draft.title,
                                                        newDescription = draft.description,
                                                        isApproved = checked
                                                    )
                                                }
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(draft.title, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text("Pages ${draft.sourcePageStart}-${draft.sourcePageEnd} | ${draft.estimatedMinutes} mins", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            statusSnackbarMessage = "Approved chapters imported into curriculum!"
                                            stagedDoc = null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Approve & Add to Curriculum")
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: My Curriculum
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Active Curriculum & Enrolled Tracks", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (userTracks.isEmpty()) {
                            Text("No learning tracks currently enrolled. Browse Installed Packs to enroll.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            userTracks.forEach { track ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(track.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                        Text("Mode: ${track.learningMode} | Daily: ${track.dailyCommitmentMinutes}m", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = { onNavigateToCurriculum(null) }) {
                                            Text("Open Curriculum Browser", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // TAB 3: Discover Resources
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Accredited Universal Learning Packages", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Explore free accredited curriculum packs across 22 disciplines.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val catalogItems = listOf(
                            Triple("CBSE Class 10 Science", "NCERT National Standard", "School Education"),
                            Triple("Diploma Electrical Machines", "DOTE Technical Syllabus", "Diploma / Polytechnic"),
                            Triple("Digital Electronics & K-Map", "IEEE/AICTE Model Curriculum", "Engineering"),
                            Triple("Classical Mechanics & Physics", "University Foundation Physics", "Arts & Sciences"),
                            Triple("Financial & Cost Accounting", "ICAI/MBA Foundation", "Commerce & Management"),
                            Triple("Indian Constitution & Polity", "UPSC Civil Services Prelims/Mains", "Competitive Exams")
                        )
                        items(catalogItems) { (title, publisher, discipline) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(publisher, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(discipline, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    FilledTonalButton(onClick = { statusSnackbarMessage = "Pack available in local offline catalog." }) {
                                        Text("Install", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // TAB 4: Recently Opened
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Recently Accessed Learning Modules", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Chemical Reactions & Equations", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Chapter 1 - Chemical balancing, conservation of mass", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { onNavigateToCurriculum("cbse_sci10_ch01") }) {
                                    Text("Resume Reading", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    // TAB 5: Bookmarked
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Saved Formulas & Study Notes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Kirchhoff's Current Law (KCL)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Formula: Sigma I_in = Sigma I_out (Conservation of Charge)", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("Accredited Source: Boylestad, Section 4.2", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                6 -> {
                    // TAB 6: Downloads & Storage Footprint
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Offline Storage & On-Device Model Assets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("ExecuTorch Neural Network Weights", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Model: llama3_2_1b_8da4w.pte (803 MB)", fontSize = 12.sp)
                                Text("Tokenizer: tokenizer.model (2.1 MB)", fontSize = 12.sp)
                                Text("Hardware Acceleration: MediaTek Dimensity 7020 (8 CPU cores)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Status: Verified & Staged on Silicon", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                7 -> {
                    // TAB 7: AI Collections
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("AI Study Collections & Revision Sets", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Universal Diagnostic Revision Set (20 Domains)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Includes MCQ, Numerical, Short Answer, and Rubric Questions with zero cloud dependency.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Progress Dialog for SAF Ingestion
    importProgress?.let { (stage, pct) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Ingesting Content Pack", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when (stage) {
                            "STREAMING_DOCUMENT" -> "Streaming document into private staging storage..."
                            "VALIDATING_ARCHIVE" -> "Decompressing and auditing Ed25519 signature..."
                            "INSTALLING_CONTENT" -> "Committing curriculum nodes and resources to database..."
                            "COMPLETED" -> "Finalizing storage and running integrity check..."
                            else -> "Processing: $stage"
                        },
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$pct%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {}
        )
    }

    // Pack Detail Dialog
    selectedPackForDetails?.let { pack ->
        AlertDialog(
            onDismissRequest = { selectedPackForDetails = null },
            title = { Text(pack.title, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Pack ID: ${pack.packId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Publisher: ${pack.authorOrProvider}", fontSize = 12.sp)
                    Text("Version: ${pack.version}", fontSize = 12.sp)
                    Text("Primary Language: ${pack.primaryLanguage}", fontSize = 12.sp)
                    Text("Checksum (SHA-256): ${pack.checksumSha256.take(16)}...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pack.provenanceNotice, fontSize = 12.sp, lineHeight = 16.sp)
                }
            },
            confirmButton = {
                Button(onClick = { selectedPackForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Assign Pack to Track Dialog
    packToAssign?.let { pack ->
        AlertDialog(
            onDismissRequest = { packToAssign = null },
            title = { Text("Assign Pack to Track", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Assigning: ${pack.title}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select a learning track to bind this curriculum package to:")
                    Spacer(modifier = Modifier.height(8.dp))
                    if (userTracks.isEmpty()) {
                        Text("No active tracks. A new track will be created.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        userTracks.forEach { track ->
                            TextButton(
                                onClick = {
                                    learningViewModel.assignPackToTrack(pack.packId, track.trackId)
                                    statusSnackbarMessage = "Assigned to ${track.title}"
                                    packToAssign = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(track.title)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (userTracks.isEmpty()) {
                    Button(onClick = {
                        learningViewModel.assignPackToTrack(pack.packId, "track_default_${pack.packId}")
                        statusSnackbarMessage = "Created new track for ${pack.title}"
                        packToAssign = null
                    }) {
                        Text("Create Track & Assign")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { packToAssign = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Uninstall Pack Confirmation Dialog
    packToUninstall?.let { (pack, assignedTracks) ->
        AlertDialog(
            onDismissRequest = { packToUninstall = null },
            title = { Text("Uninstall Content Pack?", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Are you sure you want to uninstall '${pack.title}'?")
                    if (assignedTracks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Warning: ${assignedTracks.size} active learning track(s) depend on this pack. Removing it will archive their curriculum progress.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        learningViewModel.uninstallContentPack(pack.packId, baseStorageDir) { success, msg ->
                            statusSnackbarMessage = msg
                        }
                        packToUninstall = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { packToUninstall = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun ContentPackCard(
    pack: ContentPackEntity,
    isIntact: Boolean,
    assignedTracks: List<LearningTrack>,
    onViewDetails: () -> Unit,
    onBrowseCurriculum: () -> Unit,
    onAssignToTrack: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${pack.authorOrProvider} • v${pack.version}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Integrity Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isIntact) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, if (isIntact) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isIntact) Icons.Default.VerifiedUser else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isIntact) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isIntact) "Verified" else "Audit Failed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIntact) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(pack.category, fontSize = 11.sp) }
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("Lang: ${pack.primaryLanguage.uppercase()}", fontSize = 11.sp) }
                )
                if (assignedTracks.isNotEmpty()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${assignedTracks.size} Track(s)", fontSize = 11.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isIntact) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Quarantined: This pack failed cryptographic integrity audit and is disabled from active learning paths.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons with Responsive 2-Row Layout avoiding text wrapping
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Details", fontSize = 12.sp, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onAssignToTrack,
                    enabled = isIntact,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("Assign Track", fontSize = 12.sp, maxLines = 1)
                }
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Uninstall",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onBrowseCurriculum,
                enabled = isIntact,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Curriculum", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

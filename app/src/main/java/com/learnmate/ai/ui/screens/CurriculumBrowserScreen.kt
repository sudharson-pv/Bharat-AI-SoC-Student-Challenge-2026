package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.CurriculumResourceEntity
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumBrowserScreen(
    learningViewModel: LearningViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTrackDashboard: () -> Unit,
    onNavigateToContentLibrary: () -> Unit = {},
    onNavigateToLearningModule: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allNodes by learningViewModel.curriculumNodes.collectAsState()
    val importMessage by learningViewModel.importStatusMessage.collectAsState()
    val installedPacks by learningViewModel.installedPacks.collectAsState()
    val activeTrack by learningViewModel.activeTrack.collectAsState()
    val profileState by learningViewModel.profileState.collectAsState()
    val preferredLanguage = (profileState as? com.learnmate.ai.viewmodel.LearningUiState.Success)?.data?.preferredLanguageCode ?: "en"

    var showPackManagerDialog by remember { mutableStateOf(false) }
    var viewingResource by remember { mutableStateOf<Pair<CurriculumNodeEntity, String>?>(null) }

    val handleViewResource: (CurriculumNodeEntity) -> Unit = { targetNode ->
        scope.launch {
            val resolution = learningViewModel.resolveResourceForNode(targetNode.nodeId, preferredLanguage)
            if (resolution.resource != null) {
                val res = resolution.resource
                val installedDir = File(context.filesDir, "content_packs/installed")
                var fileContent: String? = null
                if (installedDir.exists()) {
                    installedDir.listFiles()?.forEach { packDir ->
                        val resourcesDir = File(packDir, "resources")
                        if (resourcesDir.exists()) {
                            resourcesDir.listFiles()?.forEach { f ->
                                val cleanTargetId = targetNode.nodeId.replace("cbse_sci10_", "")
                                if (f.name.contains(cleanTargetId, ignoreCase = true)) {
                                    fileContent = f.readText()
                                }
                            }
                        }
                    }
                }

                val noticeHeader = if (resolution.isFallback && resolution.fallbackNotice != null) {
                    "⚠️ ${resolution.fallbackNotice}\n========================================\n\n"
                } else ""

                val textToDisplay = noticeHeader + (fileContent ?: """
                    ${res.verifiedExplanation}
                    
                    Reference: ${res.referenceTitle} (${res.referenceEdition})
                    Chapter: ${res.referenceChapter}, Section: ${res.referenceSection}
                    Identifier: ${res.referenceIdentifier}
                    Language: ${res.languageCode.uppercase()}
                    Core Keywords: ${res.coreKeywordsJson}
                """.trimIndent())
                viewingResource = Pair(targetNode, textToDisplay)
            } else {
                viewingResource = Pair(targetNode, "Description:\n${targetNode.description}\n\nEstimated Duration: ${targetNode.estimatedMinutes} minutes\n\nNo verified textbook citation available for this node.")
            }
        }
    }

    LaunchedEffect(Unit) {
        val baseDir = File(context.filesDir, "content_packs")
        learningViewModel.loadInstalledPacks(baseDir)
    }

    val disciplines = remember(allNodes) {
        allNodes.filter { it.parentNodeId == null }.sortedBy { it.orderIndex }
    }

    // Discipline resolution based strictly on active track assignment (zero arbitrary or VLSI defaults)
    var selectedDisciplineId by remember(disciplines, activeTrack) {
        val targetNodeId = activeTrack?.targetCurriculumNodeId
        val initialDiscipline = if (targetNodeId != null) {
            val node = allNodes.find { it.nodeId == targetNodeId }
            if (node != null) {
                if (node.parentNodeId == null) node.nodeId
                else allNodes.find { it.nodeId == node.parentNodeId }?.nodeId ?: node.nodeId
            } else null
        } else null
        mutableStateOf(initialDiscipline)
    }

    val selectedDiscipline = remember(disciplines, selectedDisciplineId) {
        disciplines.firstOrNull { it.nodeId == selectedDisciplineId }
    }

    val childNodes = remember(allNodes, selectedDisciplineId) {
        allNodes.filter { it.parentNodeId == selectedDisciplineId }
    }

    var showCreateDialogForNode by remember { mutableStateOf<CurriculumNodeEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.curriculum_browser_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToContentLibrary) {
                        Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = "Content Packs Library")
                    }
                    IconButton(onClick = { showPackManagerDialog = true }) {
                        Icon(imageVector = Icons.Default.Inventory2, contentDescription = "Manage Content Packs")
                    }
                    IconButton(onClick = onNavigateToTrackDashboard) {
                        Icon(imageVector = Icons.Default.ViewAgenda, contentDescription = "My Tracks")
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.curriculum_browser_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Discipline Selector Horizontal Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                items(disciplines) { discipline ->
                    val isSelected = discipline.nodeId == selectedDisciplineId
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedDisciplineId = discipline.nodeId },
                        label = {
                            Text(
                                text = discipline.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            // Selected Discipline Banner
            selectedDiscipline?.let { disc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = disc.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = disc.code,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (disc.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = disc.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Course & Topic Tree
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                if (selectedDisciplineId == null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val targetNodeId = activeTrack?.targetCurriculumNodeId
                                val isMissingPack = targetNodeId != null && allNodes.none { it.nodeId == targetNodeId }
                                val icon = if (isMissingPack) Icons.Default.Warning else Icons.Default.AltRoute
                                val title = when {
                                    allNodes.isEmpty() -> "No Curricula Installed"
                                    isMissingPack -> "Assigned Curriculum Not Found"
                                    else -> "No Curriculum Assigned"
                                }
                                val desc = when {
                                    allNodes.isEmpty() -> "LearnMate AI is an offline learning companion. Please import a content pack (.lmpack) to begin your curriculum."
                                    isMissingPack -> "The curriculum node '$targetNodeId' linked to active track '${activeTrack?.title}' is not installed in local storage."
                                    else -> "Active track '${activeTrack?.title ?: "Current Track"}' does not have a linked curriculum. Browse installed disciplines below or import a new content pack."
                                }

                                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = desc,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (disciplines.isNotEmpty()) {
                                        Button(onClick = { selectedDisciplineId = disciplines.first().nodeId }) {
                                            Text("Browse Curricula")
                                        }
                                    }
                                    OutlinedButton(onClick = onNavigateToContentLibrary) {
                                        Text("Content Packs")
                                    }
                                }
                            }
                        }
                    }
                } else if (childNodes.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Modular Curriculum Pack Installed", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Standard syllabus topics are ready for learning track enrollment.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = {
                                    showCreateDialogForNode = selectedDiscipline
                                }) {
                                    Text(stringResource(R.string.curriculum_btn_create_track))
                                }
                            }
                        }
                    }
                } else {
                    items(childNodes, key = { it.nodeId }) { node ->
                        CurriculumNodeCard(
                            node = node,
                            allNodes = allNodes,
                            onStartTrack = {
                                showCreateDialogForNode = node
                            },
                            onViewResource = handleViewResource,
                            onStudyModule = onNavigateToLearningModule
                        )
                    }
                }
            }
        }
    }

    if (showPackManagerDialog) {
        var packPathInput by remember { mutableStateOf("/data/local/tmp/cbse_sci10_starter.lmpack") }
        AlertDialog(
            onDismissRequest = { showPackManagerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Content Pack Manager", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Installed Packs (${installedPacks.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (installedPacks.isEmpty()) {
                        Text(
                            text = "No modular packs installed. Default offline syllabus active.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        installedPacks.forEach { pack ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pack.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${pack.packId} (v${pack.version})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Provider: ${pack.authorOrProvider}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                    IconButton(
                                        onClick = {
                                            val baseStorage = File(context.filesDir, "content_packs")
                                            learningViewModel.uninstallContentPack(pack.packId, baseStorage)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Uninstall Pack",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "Install Pack from Local Storage",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = packPathInput,
                        onValueChange = { packPathInput = it },
                        label = { Text("Pack Path (.lmpack)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val targetFile = File(packPathInput.trim())
                            val baseStorage = File(context.filesDir, "content_packs")
                            learningViewModel.importContentPack(targetFile, baseStorage)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Install & Verify Pack")
                    }

                    importMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = if (msg.contains("Successfully")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp),
                                color = if (msg.contains("Successfully")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        learningViewModel.clearImportStatusMessage()
                        showPackManagerDialog = false
                    },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    viewingResource?.let { (node, content) ->
        AlertDialog(
            onDismissRequest = { viewingResource = null },
            title = {
                Column {
                    Text(node.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(node.code, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewingResource = null },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    showCreateDialogForNode?.let { targetNode ->
        TrackCreationDialog(
            onDismiss = { showCreateDialogForNode = null },
            onCreate = { title, mode, examName, commitment ->
                learningViewModel.createLearningTrack(
                    title = title,
                    learningMode = mode,
                    targetCurriculumNodeId = targetNode.nodeId,
                    dailyCommitmentMinutes = commitment,
                    targetExamName = examName.ifBlank { null }
                )
                showCreateDialogForNode = null
                onNavigateToTrackDashboard()
            }
        )
    }
}

@Composable
fun CurriculumNodeCard(
    node: CurriculumNodeEntity,
    allNodes: List<CurriculumNodeEntity>,
    onStartTrack: () -> Unit,
    onViewResource: (CurriculumNodeEntity) -> Unit,
    onStudyModule: (String) -> Unit = {}
) {
    val subTopics = remember(allNodes, node.nodeId) {
        allNodes.filter { it.parentNodeId == node.nodeId }
    }

    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = node.nodeType,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = node.code,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = node.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onStudyModule(node.nodeId) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Study Notes",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(onClick = { onStudyModule(node.nodeId) }) {
                        Text("Study Module", fontSize = 11.sp)
                    }
                    TextButton(onClick = onStartTrack) {
                        Text(stringResource(R.string.curriculum_btn_create_track), fontSize = 11.sp)
                    }
                }
            }

            if (node.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = node.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (subTopics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${subTopics.size} Subtopics / Modules",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (expanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subTopics.forEach { sub ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = sub.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (sub.description.isNotBlank()) {
                                            Text(text = sub.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onStudyModule(sub.nodeId) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MenuBook,
                                                contentDescription = "Study Notes",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "${sub.estimatedMinutes}m", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


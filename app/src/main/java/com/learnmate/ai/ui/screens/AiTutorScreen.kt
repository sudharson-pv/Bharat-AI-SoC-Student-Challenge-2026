package com.learnmate.ai.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.learnmate.ai.R
import com.learnmate.ai.domain.ChatMessage
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.viewmodel.LearningUiState
import com.learnmate.ai.viewmodel.LearningViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AiTutorScreen(
    learningViewModel: LearningViewModel,
    initialTopic: String = "",
    onNavigateBack: () -> Unit
) {
    val profileState by learningViewModel.profileState.collectAsState()
    val profile = (profileState as? LearningUiState.Success<LearnerProfile>)?.data

    val isAiEnabled by learningViewModel.isAiEnabled.collectAsState()
    val chatMessages by learningViewModel.chatMessages.collectAsState()
    val isGenerating by learningViewModel.isGenerating.collectAsState()
    val streamingDelta by learningViewModel.streamingDelta.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val activeDomain = profile?.targetDomain ?: "General Engineering"
    val activeTitle = if (initialTopic.isNotBlank()) initialTopic else "$activeDomain Study"

    LaunchedEffect(activeDomain) {
        learningViewModel.startOrGetChatSession(domain = activeDomain, title = activeTitle)
    }

    LaunchedEffect(chatMessages.size, streamingDelta) {
        if (chatMessages.isNotEmpty() || streamingDelta.isNotEmpty()) {
            val totalItems = chatMessages.size + if (isGenerating) 1 else 0
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val isKeyboardVisible = remember {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    LaunchedEffect(isKeyboardVisible.value) {
        if (isKeyboardVisible.value) {
            val totalItems = chatMessages.size + if (isGenerating) 1 else 0
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
            bringIntoViewRequester.bringIntoView()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.ai_tutor_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (isAiEnabled) "On-Device Llama 3.2 1B (8da4w)" else "Verified Textbook Mode",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "AI",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAiEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = isAiEnabled,
                            onCheckedChange = { learningViewModel.setAiEnabled(it) },
                            modifier = Modifier.scale(0.8f)
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
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Suggestion Chips (Horizontally scrollable for all screen widths)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val domain = profile?.targetDomain?.lowercase() ?: ""
                val branch = profile?.subdomain?.lowercase() ?: ""
                val topic = initialTopic.ifBlank { profile?.currentTopic ?: "" }
                val suggestions = when {
                    domain.contains("finance") || domain.contains("mba") || branch.contains("finance") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Explain DCF Valuation",
                        "Working Capital Cycle",
                        "DuPont Analysis Framework",
                        "NPV vs IRR Rule"
                    )
                    domain.contains("electronic") || domain.contains("vlsi") || branch.contains("vlsi") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Explain PN Junction",
                        "Why CMOS low power?",
                        "Static Timing Analysis setup/hold",
                        "Euler Path in CMOS layout"
                    )
                    domain.contains("computer") || domain.contains("software") || domain.contains("ai") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Explain Binary Search Trees",
                        "Time Complexity of QuickSort",
                        "ACID vs BASE Properties",
                        "Deadlock Prevention methods"
                    )
                    domain.contains("science") || domain.contains("physics") || domain.contains("chemistry") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Hooke's Law & Elasticity",
                        "Exothermic vs Endothermic",
                        "Newton's Third Law examples",
                        "Photosynthesis dark reactions"
                    )
                    domain.contains("mechanical") || branch.contains("mechanical") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Otto vs Diesel Cycle",
                        "Stress-Strain Curve explanation",
                        "Bernoulli's Principle application",
                        "Heat Exchanger LMTD"
                    )
                    domain.contains("civil") || branch.contains("civil") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Bending Moment & Shear Force",
                        "Portland Cement hydration",
                        "Soil Compaction vs Consolidation",
                        "Truss Method of Joints"
                    )
                    domain.contains("medicine") || domain.contains("health") || branch.contains("biomed") -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Action Potential propagation",
                        "Cardiac Cycle phases",
                        "Gram Positive vs Negative bacteria",
                        "Mechanism of Enzyme inhibition"
                    )
                    else -> listOf(
                        if (topic.isNotBlank()) "Explain $topic" else "Explain key concepts",
                        "Practical applications in ${profile?.targetDomain ?: "this subject"}",
                        "Important formulas and definitions",
                        "Common exam questions"
                    )
                }
                suggestions.forEach { prompt ->
                    SuggestionChip(
                        onClick = {
                            inputText = prompt
                            learningViewModel.sendTutorQuestion(prompt)
                            inputText = ""
                        },
                        label = { Text(prompt, fontSize = 11.sp) }
                    )
                }
            }

            // Chat Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatMessages, key = { it.messageId }) { msg ->
                    ChatBubbleItem(message = msg)
                }

                // Streaming indicator
                if (isGenerating && streamingDelta.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Generating on-device...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = streamingDelta,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Disclaimer Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = if (isAiEnabled) {
                        stringResource(R.string.ai_tutor_disclaimer)
                    } else {
                        stringResource(R.string.ai_tutor_verified_textbook_banner)
                    },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Input Bar (Guarded with BringIntoViewRequester and IME Inset Protection)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(bringIntoViewRequester),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(stringResource(R.string.ai_tutor_input_placeholder), fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    coroutineScope.launch {
                                        val totalItems = chatMessages.size + if (isGenerating) 1 else 0
                                        if (totalItems > 0) {
                                            listState.animateScrollToItem(totalItems - 1)
                                        }
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val q = inputText.trim()
                            if (q.isNotBlank() && !isGenerating) {
                                learningViewModel.sendTutorQuestion(q)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (inputText.isNotBlank() && !isGenerating) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = stringResource(R.string.btn_send),
                            tint = if (inputText.isNotBlank() && !isGenerating) Color.White else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleItem(message: ChatMessage) {
    val isUser = message.senderRole == "USER"
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestampEpochMs) { timeFormat.format(Date(message.timestampEpochMs)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)) else null,
            elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 0.dp else 1.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Provenance Badge
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isAiGenerated) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_tutor_tag_ai),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2E7D32).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_tutor_tag_curriculum),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = formattedTime,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Message Text
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    lineHeight = 20.sp
                )

                // User timestamp
                if (isUser) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }

                // Token Performance Metrics
                if (!isUser && message.isAiGenerated && message.generatedTokens > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val speed = message.tokensPerSec?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                    Text(
                        text = "${message.generatedTokens} tokens • ${speed} tok/s • TTFT: ${message.ttftMs ?: 0}ms",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this


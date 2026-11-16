package com.learnmate.ai.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.learnmate.ai.ai.LearnMateAiCoreEngine
import com.learnmate.ai.ai.ModelRuntimeState
import com.learnmate.ai.data.AppPreferences
import com.learnmate.ai.data.contentpack.ContentPackDocumentImporter
import com.learnmate.ai.data.contentpack.ContentPackInstaller
import com.learnmate.ai.data.db.ContentPackEntity
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.CurriculumResourceEntity
import com.learnmate.ai.data.repository.AiTutorRepository
import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.ChatMessage
import com.learnmate.ai.domain.ChatSession
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.PersonalisedRoadmap
import com.learnmate.ai.domain.SkillGapReport
import com.learnmate.ai.domain.StudyTask
import com.learnmate.ai.domain.LessonModule
import com.learnmate.ai.domain.TrackTopicProgress
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.KnowledgeCheckSessionState
import com.learnmate.ai.domain.QuestionSubmissionResult
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.recommendation.RecommendedTopicItem
import com.learnmate.ai.domain.recommendation.RevisionTopicItem
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.AssessmentTrendPoint
import com.learnmate.ai.domain.assessment.QuestionBankCoverageReport
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import com.learnmate.ai.domain.analytics.LearnerAnalyticsReport
import com.learnmate.ai.domain.planner.DailyStudyPlan
import com.learnmate.ai.domain.planner.WeeklyStudyPlan
import com.learnmate.ai.domain.lifecycle.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class LearningUiState<out T> {
    object Idle : LearningUiState<Nothing>()
    object Loading : LearningUiState<Nothing>()
    data class Success<T>(val data: T) : LearningUiState<T>()
    data class Empty(val message: String) : LearningUiState<Nothing>()
    data class Error(val message: String) : LearningUiState<Nothing>()
}

class LearningViewModel(
    private val learningRepository: LearningRepository,
    private val aiTutorRepository: AiTutorRepository,
    private val appPreferences: AppPreferences? = null,
    val aiCoreEngine: LearnMateAiCoreEngine? = null
) : ViewModel() {

    private val _currentUserId = MutableStateFlow(appPreferences?.getActiveUserId() ?: "default_learner")
    val currentUserId: String get() = _currentUserId.value
    val currentUserIdFlow: StateFlow<String> = _currentUserId.asStateFlow()

    private val _allProfiles = MutableStateFlow<List<LearnerProfile>>(emptyList())
    val allProfiles: StateFlow<List<LearnerProfile>> = _allProfiles.asStateFlow()

    private val _profileState = MutableStateFlow<LearningUiState<LearnerProfile>>(LearningUiState.Idle)
    val profileState: StateFlow<LearningUiState<LearnerProfile>> = _profileState.asStateFlow()

    private val _tracksState = MutableStateFlow<LearningUiState<List<LearningTrack>>>(LearningUiState.Idle)
    val tracksState: StateFlow<LearningUiState<List<LearningTrack>>> = _tracksState.asStateFlow()

    private val _activeTrack = MutableStateFlow<LearningTrack?>(null)
    val activeTrack: StateFlow<LearningTrack?> = _activeTrack.asStateFlow()

    private val _curriculumNodes = MutableStateFlow<List<CurriculumNodeEntity>>(emptyList())
    val curriculumNodes: StateFlow<List<CurriculumNodeEntity>> = _curriculumNodes.asStateFlow()

    private val _roadmapState = MutableStateFlow<LearningUiState<PersonalisedRoadmap>>(LearningUiState.Idle)
    val roadmapState: StateFlow<LearningUiState<PersonalisedRoadmap>> = _roadmapState.asStateFlow()

    private val _studyTasksState = MutableStateFlow<LearningUiState<List<StudyTask>>>(LearningUiState.Idle)
    val studyTasksState: StateFlow<LearningUiState<List<StudyTask>>> = _studyTasksState.asStateFlow()

    private val _skillGapState = MutableStateFlow<LearningUiState<SkillGapReport>>(LearningUiState.Idle)
    val skillGapState: StateFlow<LearningUiState<SkillGapReport>> = _skillGapState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val _streamingDelta = MutableStateFlow("")
    val streamingDelta: StateFlow<String> = _streamingDelta.asStateFlow()

    private val _activeLessonModule = MutableStateFlow<LessonModule?>(null)
    val activeLessonModule: StateFlow<LessonModule?> = _activeLessonModule.asStateFlow()

    private val _activeTopicProgress = MutableStateFlow<TrackTopicProgress?>(null)
    val activeTopicProgress: StateFlow<TrackTopicProgress?> = _activeTopicProgress.asStateFlow()

    private val _knowledgeCheckState = MutableStateFlow<KnowledgeCheckSessionState?>(null)
    val knowledgeCheckState: StateFlow<KnowledgeCheckSessionState?> = _knowledgeCheckState.asStateFlow()

    private val _topicMasteries = MutableStateFlow<List<TopicMasteryResult>>(emptyList())
    val topicMasteries: StateFlow<List<TopicMasteryResult>> = _topicMasteries.asStateFlow()

    private val _learningPath = MutableStateFlow<List<RecommendedTopicItem>>(emptyList())
    val learningPath: StateFlow<List<RecommendedTopicItem>> = _learningPath.asStateFlow()

    private val _revisionQueue = MutableStateFlow<List<RevisionTopicItem>>(emptyList())
    val revisionQueue: StateFlow<List<RevisionTopicItem>> = _revisionQueue.asStateFlow()

    private val _spacedRepetitionQueue = MutableStateFlow<List<SpacedRepetitionItem>>(emptyList())
    val spacedRepetitionQueue: StateFlow<List<SpacedRepetitionItem>> = _spacedRepetitionQueue.asStateFlow()

    private val _analyticsReport = MutableStateFlow<LearnerAnalyticsReport?>(null)
    val analyticsReport: StateFlow<LearnerAnalyticsReport?> = _analyticsReport.asStateFlow()

    private val _dailyStudyPlan = MutableStateFlow<DailyStudyPlan?>(null)
    val dailyStudyPlan: StateFlow<DailyStudyPlan?> = _dailyStudyPlan.asStateFlow()

    private val _assessmentHistory = MutableStateFlow<List<AssessmentAttemptRecord>>(emptyList())
    val assessmentHistory: StateFlow<List<AssessmentAttemptRecord>> = _assessmentHistory.asStateFlow()

    private val _coverageReport = MutableStateFlow<com.learnmate.ai.domain.outcomes.CurriculumCoverageReport?>(null)
    val coverageReport: StateFlow<com.learnmate.ai.domain.outcomes.CurriculumCoverageReport?> = _coverageReport.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isAiEnabled: StateFlow<Boolean> = aiTutorRepository.isAiEnabled
    val runtimeState: StateFlow<ModelRuntimeState> = aiTutorRepository.runtimeState
    val engineError: StateFlow<String?> = aiTutorRepository.lastError

    init {
        loadLearnerData()
        loadAllProfiles()
    }

    fun switchUser(userId: String) {
        appPreferences?.setActiveUserId(userId)
        _currentUserId.value = userId
        loadLearnerData()
        loadTracks()
        loadAllProfiles()
    }

    fun loadAllProfiles() {
        viewModelScope.launch {
            _allProfiles.value = learningRepository.getAllProfiles()
        }
    }

    fun loadLearnerData() {
        viewModelScope.launch {
            _profileState.value = LearningUiState.Loading
            val profile = learningRepository.getProfile(currentUserId)
            if (profile != null) {
                _profileState.value = LearningUiState.Success(profile)
                loadRoadmapAndTasks(profile.userId)
            } else {
                _profileState.value = LearningUiState.Empty("No learner profile configured yet.")
            }
            loadTracks()
            loadCurriculumNodes()
        }
    }

    fun loadTracks() {
        viewModelScope.launch {
            _tracksState.value = LearningUiState.Loading
            val tracks = learningRepository.getTracksForUser(currentUserId)
            if (tracks.isNotEmpty()) {
                _tracksState.value = LearningUiState.Success(tracks)
                val active = tracks.firstOrNull { it.isTrackActive } ?: tracks.first()
                _activeTrack.value = active
                loadAdaptiveLearningEngine(active.trackId)
            } else {
                _tracksState.value = LearningUiState.Empty("No learning tracks found.")
            }
        }
    }

    fun loadCurriculumNodes() {
        viewModelScope.launch {
            val nodes = learningRepository.getAllCurriculumNodes()
            _curriculumNodes.value = nodes
        }
    }

    fun createLearningTrack(
        title: String,
        learningMode: LearningMode,
        targetCurriculumNodeId: String?,
        dailyCommitmentMinutes: Int = 60,
        targetExamName: String? = null,
        targetExamDateEpochMs: Long? = null
    ) {
        viewModelScope.launch {
            _tracksState.value = LearningUiState.Loading
            val newTrack = learningRepository.createTrack(
                userId = currentUserId,
                title = title,
                learningMode = learningMode,
                targetCurriculumNodeId = targetCurriculumNodeId,
                dailyCommitmentMinutes = dailyCommitmentMinutes,
                targetExamName = targetExamName,
                targetExamDateEpochMs = targetExamDateEpochMs,
                setAsActive = true
            )
            _activeTrack.value = newTrack
            val updatedTracks = learningRepository.getTracksForUser(currentUserId)
            _tracksState.value = LearningUiState.Success(updatedTracks)
            loadRoadmapAndTasks(currentUserId)
            loadAdaptiveLearningEngine(newTrack.trackId)
        }
    }

    fun switchActiveTrack(trackId: String) {
        viewModelScope.launch {
            learningRepository.switchActiveTrack(currentUserId, trackId)
            val updatedTracks = learningRepository.getTracksForUser(currentUserId)
            _tracksState.value = LearningUiState.Success(updatedTracks)
            _activeTrack.value = updatedTracks.firstOrNull { it.trackId == trackId }
            loadRoadmapAndTasks(currentUserId)
            loadAdaptiveLearningEngine(trackId)
        }
    }

    fun createOrUpdateProfile(profile: LearnerProfile) {
        viewModelScope.launch {
            _profileState.value = LearningUiState.Loading
            learningRepository.saveProfile(profile)
            _currentUserId.value = profile.userId
            _profileState.value = LearningUiState.Success(profile)
            loadTracks()
            loadRoadmapAndTasks(profile.userId)
            loadAllProfiles()
        }
    }

    private suspend fun loadRoadmapAndTasks(userId: String) {
        val roadmap = learningRepository.getRoadmap(userId)
        if (roadmap != null && roadmap.steps.isNotEmpty()) {
            _roadmapState.value = LearningUiState.Success(roadmap)
        } else {
            _roadmapState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your roadmap.")
        }

        val tasks = learningRepository.getStudyTasks(userId)
        if (tasks.isNotEmpty()) {
            _studyTasksState.value = LearningUiState.Success(tasks)
        } else {
            _studyTasksState.value = LearningUiState.Empty("No study tasks scheduled yet.")
        }

        val gapReport = learningRepository.getSkillGapReport(userId)
        if (gapReport != null) {
            _skillGapState.value = LearningUiState.Success(gapReport)
        } else {
            _skillGapState.value = LearningUiState.Empty("Diagnostic assessment not completed.")
        }
    }

    fun submitDiagnosticAssessment(
        profile: LearnerProfile,
        questions: List<AssessmentQuestion>,
        answers: Map<String, Int>
    ) {
        viewModelScope.launch {
            _skillGapState.value = LearningUiState.Loading
            try {
                val currentTrackId = _activeTrack.value?.trackId ?: "track_${profile.userId}_default"
                val report = learningRepository.submitAssessment(profile, questions, answers, currentTrackId)

                // If AI Core Engine is active, synthesize personalized AI roadmap and daily study plan
                aiCoreEngine?.let { engine ->
                    try {
                        val aiRoadmap = engine.synthesizePersonalizedRoadmap(profile, report)
                        learningRepository.saveRoadmap(aiRoadmap, currentTrackId)
                        val learnerCtx = com.learnmate.ai.domain.LearnerContext.fromProfile(profile, currentTrackId, report)
                        val aiTasks = engine.generateDailyStudyPlan(learnerCtx, aiRoadmap)
                        if (aiTasks.isNotEmpty()) {
                            learningRepository.saveStudyTasks(aiTasks, currentTrackId)
                        }
                    } catch (_: Exception) {
                        // Fall back cleanly to deterministic roadmap generated in submitAssessment
                    }
                }

                _skillGapState.value = LearningUiState.Success(report)
                loadRoadmapAndTasks(profile.userId)
                loadAdaptiveLearningEngine(currentTrackId)
            } catch (e: Exception) {
                _skillGapState.value = LearningUiState.Error(e.message ?: "Assessment grading failed")
            }
        }
    }

    suspend fun getOrGenerateDiagnosticAssessment(profile: LearnerProfile): List<AssessmentQuestion> {
        val aiEngine = aiCoreEngine
        if (aiEngine != null) {
            val questions = aiEngine.generateDiagnosticAssessment(profile)
            if (questions.isNotEmpty()) {
                learningRepository.saveDiagnosticQuestions(questions)
                return questions.take(10)
            }
        }
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(profile.targetDomain)
        if (domainQuestions.isNotEmpty()) {
            return domainQuestions.take(10)
        }
        return learningRepository.getAllQuestions().take(10)
    }

    suspend fun getQuestionsForDomain(domain: String): List<AssessmentQuestion> {
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(domain)
        if (domainQuestions.isNotEmpty()) return domainQuestions.take(10)
        return learningRepository.getAllQuestions().take(10)
    }

    suspend fun getAllDiagnosticQuestions(): List<AssessmentQuestion> {
        return learningRepository.getAllQuestions().take(10)
    }

    fun toggleStudyTask(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            learningRepository.toggleTaskCompletion(currentUserId, taskId, isCompleted)
            val updatedTasks = learningRepository.getStudyTasks(currentUserId)
            _studyTasksState.value = LearningUiState.Success(updatedTasks)
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        aiTutorRepository.setAiEnabled(enabled)
    }

    fun startOrGetChatSession(domain: String, title: String) {
        viewModelScope.launch {
            val currentTrackId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            val sessions = learningRepository.getChatSessionsForTrack(currentTrackId)
            val existing = sessions.firstOrNull { it.domain.equals(domain, ignoreCase = true) }
            val session = existing ?: learningRepository.createChatSession(
                userId = currentUserId,
                domain = domain,
                title = title,
                isAiEnabled = aiTutorRepository.isAiEnabled.value,
                trackId = currentTrackId
            )
            _activeSession.value = session
            val msgs = learningRepository.getMessagesForSession(session.sessionId)
            _chatMessages.value = msgs
        }
    }

    fun sendTutorQuestion(question: String) {
        val session = _activeSession.value ?: return
        if (question.isBlank() || _isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingDelta.value = ""

            val res = aiTutorRepository.askTutor(
                sessionId = session.sessionId,
                userId = currentUserId,
                userQuestion = question,
                currentTopic = session.domain
            ) { fullText, _ ->
                _streamingDelta.value = fullText
            }

            val updatedMessages = learningRepository.getMessagesForSession(session.sessionId)
            _chatMessages.value = updatedMessages
            _streamingDelta.value = ""
            _isGenerating.value = false
        }
    }

    private val _installedPacks = MutableStateFlow<List<ContentPackEntity>>(emptyList())
    val installedPacks: StateFlow<List<ContentPackEntity>> = _installedPacks.asStateFlow()

    private val _packAuditStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val packAuditStatus: StateFlow<Map<String, Boolean>> = _packAuditStatus.asStateFlow()

    private val _packAssignedTracks = MutableStateFlow<Map<String, List<LearningTrack>>>(emptyMap())
    val packAssignedTracks: StateFlow<Map<String, List<LearningTrack>>> = _packAssignedTracks.asStateFlow()

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage.asStateFlow()

    private val _importProgress = MutableStateFlow<Pair<String, Int>?>(null)
    val importProgress: StateFlow<Pair<String, Int>?> = _importProgress.asStateFlow()

    fun clearImportStatusMessage() {
        _importStatusMessage.value = null
    }

    fun loadInstalledPacks(baseStorageDir: File? = null) {
        viewModelScope.launch {
            val packs = learningRepository.getAllContentPacks()
            _installedPacks.value = packs
            
            // Query assigned tracks for each installed pack
            val trackMap = mutableMapOf<String, List<LearningTrack>>()
            for (p in packs) {
                trackMap[p.packId] = learningRepository.getAssignedTracksForPack(currentUserId, p.packId)
            }
            _packAssignedTracks.value = trackMap

            // Run integrity audit on installed packs if base storage is supplied
            if (baseStorageDir != null) {
                val auditMap = mutableMapOf<String, Boolean>()
                for (p in packs) {
                    auditMap[p.packId] = learningRepository.auditInstalledPack(p.packId, baseStorageDir)
                }
                _packAuditStatus.value = auditMap
            }
        }
    }

    fun importContentPackFromSafUri(
        context: Context,
        uri: Uri,
        baseStorageDir: File,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _importProgress.value = Pair("CONNECTING_STORAGE", 5)
            val result = learningRepository.importContentPackFromUri(
                context = context,
                uri = uri,
                baseStorageDir = baseStorageDir,
                onProgress = { stage, pct ->
                    _importProgress.value = Pair(stage, pct)
                }
            )
            _importProgress.value = null

            when (result) {
                is ContentPackDocumentImporter.ImportResult.Success -> {
                    val msg = "Successfully imported ${result.title} (${result.nodesInstalled} nodes)"
                    _importStatusMessage.value = msg
                    loadCurriculumNodes()
                    loadInstalledPacks(baseStorageDir)
                    onComplete?.invoke(true, msg)
                }
                is ContentPackDocumentImporter.ImportResult.Failure -> {
                    val msg = "Import failed [${result.errorCode}]: ${result.errorMessage}"
                    _importStatusMessage.value = msg
                    onComplete?.invoke(false, msg)
                }
            }
        }
    }

    fun assignPackToTrack(trackId: String, packId: String, baseStorageDir: File? = null) {
        viewModelScope.launch {
            learningRepository.assignPackToTrack(currentUserId, trackId, packId)
            loadTracks()
            loadInstalledPacks(baseStorageDir)
        }
    }

    fun assignCurriculumNodeToTrack(trackId: String, nodeId: String, baseStorageDir: File? = null) {
        viewModelScope.launch {
            learningRepository.assignCurriculumToTrack(currentUserId, trackId, nodeId)
            loadTracks()
            loadInstalledPacks(baseStorageDir)
        }
    }

    fun unassignCurriculumFromTrack(trackId: String, baseStorageDir: File? = null) {
        viewModelScope.launch {
            learningRepository.unassignCurriculumFromTrack(currentUserId, trackId)
            loadTracks()
            loadInstalledPacks(baseStorageDir)
        }
    }

    fun importContentPack(packFile: File, baseStorageDir: File, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val result = learningRepository.installContentPack(packFile, baseStorageDir)
            when (result) {
                is ContentPackInstaller.InstallResult.Success -> {
                    val msg = "Successfully installed ${result.title} (${result.nodesInstalled} nodes)"
                    _importStatusMessage.value = msg
                    loadCurriculumNodes()
                    loadInstalledPacks(baseStorageDir)
                    onComplete?.invoke(true, msg)
                }
                is ContentPackInstaller.InstallResult.Failure -> {
                    val msg = "Failed to install pack [${result.errorCode}]: ${result.message}"
                    _importStatusMessage.value = msg
                    onComplete?.invoke(false, msg)
                }
            }
        }
    }

    fun uninstallContentPack(
        packId: String,
        baseStorageDir: File,
        forceIfAssigned: Boolean = false,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val assigned = learningRepository.getAssignedTracksForPack(currentUserId, packId)
            if (assigned.isNotEmpty() && !forceIfAssigned) {
                val warning = "Pack is currently assigned to ${assigned.size} track(s): ${assigned.joinToString { it.title }}"
                onComplete?.invoke(false, warning)
                return@launch
            }

            val success = learningRepository.uninstallContentPack(packId, baseStorageDir, forceIfAssigned, currentUserId)
            if (success) {
                _importStatusMessage.value = "Pack $packId uninstalled successfully"
                loadCurriculumNodes()
                loadTracks()
                loadInstalledPacks(baseStorageDir)
                onComplete?.invoke(true, null)
            } else {
                onComplete?.invoke(false, "Failed to uninstall pack")
            }
        }
    }

    suspend fun resolveResourceForNode(
        nodeId: String,
        preferredLanguageCode: String,
        fallbackLanguageCode: String = "en"
    ): LearningRepository.ResourceResolution {
        return learningRepository.resolveResourceForNode(nodeId, preferredLanguageCode, fallbackLanguageCode)
    }

    suspend fun getResourcesForNode(nodeId: String): List<CurriculumResourceEntity> {
        return learningRepository.getResourcesForNode(nodeId)
    }

    // ── Gate 6.4 Learning Journeys & Knowledge Checks ──────────────────────────

    fun loadLessonModule(nodeId: String, preferredLanguage: String? = null) {
        viewModelScope.launch {
            val profile = (profileState.value as? LearningUiState.Success<LearnerProfile>)?.data
            val lang = preferredLanguage ?: profile?.preferredLanguageCode ?: "en"
            val trackId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"

            val module = learningRepository.getLessonModule(nodeId, lang)
            _activeLessonModule.value = module

            val progress = learningRepository.getTopicProgress(trackId, currentUserId, nodeId)
            _activeTopicProgress.value = progress

            _knowledgeCheckState.value = KnowledgeCheckSessionState(
                nodeId = nodeId,
                totalQuestions = module.knowledgeChecks.size
            )
        }
    }

    fun submitKnowledgeCheck(userAnswers: Map<String, List<Int>>) {
        val module = _activeLessonModule.value ?: return
        val results = mutableMapOf<String, QuestionSubmissionResult>()
        var correctCount = 0

        for (q in module.knowledgeChecks) {
            val selected = userAnswers[q.questionId] ?: emptyList()
            val isCorrect = selected.sorted() == q.correctIndices.sorted()
            if (isCorrect) correctCount++

            results[q.questionId] = QuestionSubmissionResult(
                questionId = q.questionId,
                selectedIndices = selected,
                isCorrect = isCorrect,
                scoreEarned = if (isCorrect) q.marks else 0.0,
                explanation = q.verifiedExplanation,
                referenceCitation = q.referenceCitation
            )
        }

        val total = module.knowledgeChecks.size
        val scorePercent = if (total > 0) (correctCount.toDouble() / total) * 100.0 else 100.0

        val state = KnowledgeCheckSessionState(
            nodeId = module.nodeId,
            totalQuestions = total,
            answeredCount = userAnswers.size,
            correctCount = correctCount,
            scorePercent = scorePercent,
            isSubmitted = true,
            results = results
        )
        _knowledgeCheckState.value = state

        // Score >= 60% passes as COMPLETED, otherwise marks as NEEDS_REVISION
        val newStatus = if (scorePercent >= 60.0) LearningProgressStatus.COMPLETED else LearningProgressStatus.NEEDS_REVISION
        updateTopicProgress(newStatus, scorePercent)

        val trackId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
        viewModelScope.launch {
            learningRepository.recordDiagnosticAttempt(
                trackId = trackId,
                userId = currentUserId,
                nodeId = module.nodeId,
                correctCount = correctCount,
                totalQuestions = total,
                topicTitle = module.topicTitle
            )
            loadAdaptiveLearningEngine(trackId)
        }
    }

    fun resetKnowledgeCheck() {
        val module = _activeLessonModule.value ?: return
        _knowledgeCheckState.value = KnowledgeCheckSessionState(
            nodeId = module.nodeId,
            totalQuestions = module.knowledgeChecks.size
        )
    }

    fun updateTopicProgress(status: LearningProgressStatus, scorePercent: Double? = null) {
        viewModelScope.launch {
            val module = _activeLessonModule.value ?: return@launch
            val trackId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            val progress = learningRepository.updateTopicProgress(
                trackId = trackId,
                userId = currentUserId,
                nodeId = module.nodeId,
                status = status,
                scorePercent = scorePercent,
                topicTitle = module.topicTitle
            )
            _activeTopicProgress.value = progress
            loadAdaptiveLearningEngine(trackId)
        }
    }

    // ── Gate 6.5 Adaptive Engine Actions ────────────────────────────────────────

    fun loadAdaptiveLearningEngine(trackId: String? = null) {
        viewModelScope.launch {
            val tId = trackId ?: _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            try {
                val masteries = learningRepository.getAllTopicMasteries(tId, currentUserId)
                _topicMasteries.value = masteries
                val path = learningRepository.getPersonalisedLearningPath(tId, currentUserId)
                _learningPath.value = path
                val revisions = learningRepository.getRevisionQueue(tId, currentUserId)
                _revisionQueue.value = revisions
                loadGate66Intelligence(tId)
            } catch (_: Exception) {
                // Fail-safe default
            }
        }
    }

    fun loadGate66Intelligence(trackId: String? = null) {
        viewModelScope.launch {
            val tId = trackId ?: _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            try {
                val srs = learningRepository.getSpacedRepetitionQueue(tId, currentUserId)
                _spacedRepetitionQueue.value = srs
                val analytics = learningRepository.getLearnerAnalytics(tId, currentUserId)
                _analyticsReport.value = analytics
                val plan = learningRepository.getDailyStudyPlan(tId, currentUserId)
                _dailyStudyPlan.value = plan
                val history = learningRepository.getAllAssessmentAttemptsForTrack(tId, currentUserId)
                _assessmentHistory.value = history
                val report = learningRepository.getCurriculumCoverageReport(tId, currentUserId)
                _coverageReport.value = report
            } catch (_: Exception) {
                // Fail-safe default
            }
        }
    }

    fun loadGate67Quality(trackId: String? = null) {
        viewModelScope.launch {
            val tId = trackId ?: _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            try {
                val report = learningRepository.getCurriculumCoverageReport(tId, currentUserId)
                _coverageReport.value = report
            } catch (_: Exception) {
                // Fail-safe
            }
        }
    }

    fun recordSpacedRepetitionReview(nodeId: String, reviewScorePercent: Double) {
        viewModelScope.launch {
            val tId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            learningRepository.recordSpacedRepetitionReview(tId, currentUserId, nodeId, reviewScorePercent)
            loadAdaptiveLearningEngine(tId)
        }
    }

    fun scheduleManualRevision(nodeId: String, daysFromNow: Int) {
        viewModelScope.launch {
            val tId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            val targetEpoch = System.currentTimeMillis() + (daysFromNow * 86_400_000L)
            learningRepository.scheduleManualRevision(tId, currentUserId, nodeId, targetEpoch)
            loadGate66Intelligence(tId)
        }
    }

    fun recordDetailedAssessmentAttempt(attempt: AssessmentAttemptRecord) {
        viewModelScope.launch {
            learningRepository.recordAssessmentAttempt(attempt)
            loadAdaptiveLearningEngine(attempt.trackId)
        }
    }

    fun submitTopicDiagnostic(
        nodeId: String,
        correctCount: Int,
        totalQuestions: Int,
        topicTitle: String = "Topic Diagnostic"
    ) {
        viewModelScope.launch {
            val tId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            learningRepository.recordDiagnosticAttempt(
                trackId = tId,
                userId = currentUserId,
                nodeId = nodeId,
                correctCount = correctCount,
                totalQuestions = totalQuestions,
                topicTitle = topicTitle
            )
            loadAdaptiveLearningEngine(tId)
        }
    }

    fun completeRevision(nodeId: String) {
        viewModelScope.launch {
            val tId = _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            learningRepository.recordRevisionCompletion(tId, currentUserId, nodeId)
            loadAdaptiveLearningEngine(tId)
        }
    }

    // ── Gate 6.11 Data Lifecycle & Privacy Management ────────────────────────
    val lifecycleManager = LearnerDataLifecycleManager(learningRepository)

    fun createBackup(
        passphrase: CharArray,
        targetFile: File,
        formatVersion: Byte = LearnerDataSecurity.CURRENT_FORMAT_VERSION,
        onResult: (BackupResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = lifecycleManager.createBackup(
                userId = currentUserId,
                passphrase = passphrase,
                targetFile = targetFile,
                formatVersion = formatVersion
            )
            onResult(result)
        }
    }

    fun restoreBackup(
        sourceFile: File,
        passphrase: CharArray,
        confirmOverwrite: Boolean = false,
        onResult: (RestoreResult) -> Unit
    ) {
        viewModelScope.launch {
            val result = lifecycleManager.restoreBackup(
                sourceFile = sourceFile,
                passphrase = passphrase,
                confirmOverwrite = confirmOverwrite,
                targetUserId = currentUserId
            )
            if (result is RestoreResult.Success) {
                loadLearnerData()
            }
            onResult(result)
        }
    }

    fun exportLearnerDataJson(targetFile: File, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = lifecycleManager.exportLearnerDataJson(currentUserId, targetFile)
            onResult(result)
        }
    }

    fun exportProgressSummaryMarkdown(targetFile: File, onResult: (Result<File>) -> Unit) {
        viewModelScope.launch {
            val result = lifecycleManager.exportProgressSummaryMarkdown(currentUserId, targetFile)
            onResult(result)
        }
    }

    fun getDataInventory(): List<DataInventoryItem> {
        return lifecycleManager.getDataInventory()
    }

    fun getPrivacyPolicyDisclosure(): String {
        return lifecycleManager.getPrivacyPolicyDisclosure()
    }

    fun deleteTrack(trackId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            learningRepository.deleteLearningTrack(trackId, currentUserId)
            loadTracks()
            onComplete()
        }
    }

    fun deleteProfile(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            learningRepository.deleteLearnerProfile(currentUserId)
            loadLearnerData()
            onComplete()
        }
    }

    class Factory(
        private val learningRepository: LearningRepository,
        private val aiTutorRepository: AiTutorRepository,
        private val appPreferences: AppPreferences? = null,
        private val aiCoreEngine: LearnMateAiCoreEngine? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
                return LearningViewModel(learningRepository, aiTutorRepository, appPreferences, aiCoreEngine) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

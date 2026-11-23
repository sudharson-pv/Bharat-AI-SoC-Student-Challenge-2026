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
import com.learnmate.ai.domain.AssignmentInstance
import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.lifecycle.*
import com.learnmate.ai.data.repository.YouTubeDataRepository
import com.learnmate.ai.domain.youtube.YouTubeVideoItem
import com.learnmate.ai.domain.youtube.VideoStudyNotes
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

data class DayCheckpointResult(
    val dayIndex: Int,
    val isPassed: Boolean,
    val scorePercent: Int,
    val correctCount: Int,
    val totalQuestions: Int,
    val feedback: String,
    val remedialAdvice: String? = null,
    val attemptNumber: Int = 1,
    val topic: String = ""
)

data class AssignmentResult(
    val assignmentId: String,
    val scorePercent: Int,
    val feedback: String,
    val isSubmitted: Boolean = true
)

class LearningViewModel(
    private val learningRepository: LearningRepository,
    private val aiTutorRepository: AiTutorRepository,
    val appPreferences: AppPreferences? = null,
    val aiCoreEngine: LearnMateAiCoreEngine? = null,
    val youtubeRepository: YouTubeDataRepository = YouTubeDataRepository(learningRepository)
) : ViewModel() {

    companion object {
        private const val TAG = "LearningViewModel"
    }

    private val _currentUserId = MutableStateFlow(appPreferences?.getActiveUserId() ?: "default_learner")
    val currentUserId: String get() = _currentUserId.value
    val currentUserIdFlow: StateFlow<String> = _currentUserId.asStateFlow()

    private val _youtubeVideosState = MutableStateFlow<LearningUiState<List<YouTubeVideoItem>>>(LearningUiState.Idle)
    val youtubeVideosState: StateFlow<LearningUiState<List<YouTubeVideoItem>>> = _youtubeVideosState.asStateFlow()

    private val _videoStudyNotesState = MutableStateFlow<Map<String, VideoStudyNotes>>(emptyMap())
    val videoStudyNotesState: StateFlow<Map<String, VideoStudyNotes>> = _videoStudyNotesState.asStateFlow()

    private val _isLoadingVideoNotes = MutableStateFlow(false)
    val isLoadingVideoNotes: StateFlow<Boolean> = _isLoadingVideoNotes.asStateFlow()

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

    private val _checkpointResults = MutableStateFlow<Map<Int, DayCheckpointResult>>(emptyMap())
    val checkpointResults: StateFlow<Map<Int, DayCheckpointResult>> = _checkpointResults.asStateFlow()

    private val _unlockedDays = MutableStateFlow<Set<Int>>(setOf(1))
    val unlockedDays: StateFlow<Set<Int>> = _unlockedDays.asStateFlow()

    private val _completedAssignments = MutableStateFlow<Map<Int, AssignmentResult>>(emptyMap())
    val completedAssignments: StateFlow<Map<Int, AssignmentResult>> = _completedAssignments.asStateFlow()

    private val _weeklyStudyPlan = MutableStateFlow<WeeklyStudyPlan?>(null)
    val weeklyStudyPlan: StateFlow<WeeklyStudyPlan?> = _weeklyStudyPlan.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    val isAiEnabled: StateFlow<Boolean> = aiTutorRepository.isAiEnabled
    val runtimeState: StateFlow<ModelRuntimeState> = aiTutorRepository.runtimeState
    val engineError: StateFlow<String?> = aiTutorRepository.lastError

    init {
        loadLearnerData()
        loadAllProfiles()
        refreshModelStatus()
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
            android.util.Log.d(TAG, "loadLearnerData: loading for currentUserId='$currentUserId'")
            var profile = learningRepository.getProfile(currentUserId)
            if (profile == null) {
                val allProfiles = learningRepository.getAllProfiles()
                android.util.Log.d(TAG, "loadLearnerData: profile for '$currentUserId' was null. Found ${allProfiles.size} total profiles in DB.")
                if (allProfiles.isNotEmpty()) {
                    val restoredProfile = allProfiles.first()
                    android.util.Log.i(TAG, "loadLearnerData: restoring most recent profile userId='${restoredProfile.userId}', onboardingCompleted=${restoredProfile.onboardingCompleted}")
                    profile = restoredProfile
                    _currentUserId.value = restoredProfile.userId
                    appPreferences?.setActiveUserId(restoredProfile.userId)
                }
            }

            if (profile != null) {
                android.util.Log.i(TAG, "loadLearnerData: loaded profile for userId='${profile.userId}', domain='${profile.targetDomain}', onboardingCompleted=${profile.onboardingCompleted}")
                _profileState.value = LearningUiState.Success(profile)
                loadRoadmapAndTasks(profile.userId)
            } else {
                android.util.Log.w(TAG, "loadLearnerData: no profile found in DB for currentUserId='$currentUserId'. State -> Empty")
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

    suspend fun createOrUpdateProfileAsync(profile: LearnerProfile) {
        val previousProfile = (_profileState.value as? LearningUiState.Success)?.data
        val domainChanged = previousProfile != null && !previousProfile.targetDomain.equals(profile.targetDomain, ignoreCase = true)
        val focusAreaChanged = previousProfile != null && (
            !previousProfile.subdomain.equals(profile.subdomain, ignoreCase = true) ||
            domainChanged ||
            !previousProfile.goal.equals(profile.goal, ignoreCase = true)
        )

        val finalizedOnboardingCompleted = profile.onboardingCompleted || (previousProfile?.onboardingCompleted == true)
        val finalizedProfile = if (profile.onboardingCompleted != finalizedOnboardingCompleted) {
            profile.copy(onboardingCompleted = finalizedOnboardingCompleted)
        } else {
            profile
        }

        _profileState.value = LearningUiState.Loading
        learningRepository.saveProfile(finalizedProfile)
        _currentUserId.value = finalizedProfile.userId
        appPreferences?.setActiveUserId(finalizedProfile.userId)
        android.util.Log.i(TAG, "createOrUpdateProfileAsync: saved profile userId='${finalizedProfile.userId}', onboardingCompleted=${finalizedProfile.onboardingCompleted}, domain='${finalizedProfile.targetDomain}', focusArea='${finalizedProfile.subdomain}'")

        _profileState.value = LearningUiState.Success(finalizedProfile)
        val tracks = learningRepository.getTracksForUser(finalizedProfile.userId)
        if (tracks.isNotEmpty()) {
            _tracksState.value = LearningUiState.Success(tracks)
            _activeTrack.value = tracks.firstOrNull { it.isTrackActive } ?: tracks.first()
        }

        // If focus area, specialization or domain changed, immediately invalidate and reset focus-dependent artifacts
        if (focusAreaChanged) {
            val focusDisplayName = finalizedProfile.subdomain.ifBlank { finalizedProfile.targetDomain }
            _roadmapState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your roadmap for $focusDisplayName.")
            _studyTasksState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your study plan.")
            _checkpointResults.value = emptyMap()
            _completedAssignments.value = emptyMap()
            _skillGapState.value = LearningUiState.Empty("Take the diagnostic assessment to identify skill gaps in $focusDisplayName.")
        }

        // Invalidate stale video recommendations from the previous profile/topic
        _youtubeVideosState.value = LearningUiState.Loading

        loadRoadmapAndTasks(finalizedProfile.userId)
        loadAllProfiles()
    }

    fun createOrUpdateProfile(profile: LearnerProfile) {
        viewModelScope.launch {
            createOrUpdateProfileAsync(profile)
        }
    }

    private suspend fun loadRoadmapAndTasks(userId: String) {
        val activeTrackId = _activeTrack.value?.trackId
        val currentProfile = (_profileState.value as? LearningUiState.Success)?.data
        val normTargetDomain = currentProfile?.let { TopicTaxonomy.normalizeDomain(it.targetDomain) }

        val rawRoadmap = if (activeTrackId != null) {
            learningRepository.getRoadmapForTrack(activeTrackId) ?: learningRepository.getRoadmap(userId)
        } else {
            learningRepository.getRoadmap(userId)
        }
        val isFocusMismatched = rawRoadmap != null && currentProfile != null && (
            (normTargetDomain != null && !TopicTaxonomy.normalizeDomain(rawRoadmap.domain).equals(normTargetDomain, ignoreCase = true)) ||
            (currentProfile.subdomain.isNotBlank() && rawRoadmap.subdomain.isNotBlank() && !rawRoadmap.subdomain.equals(currentProfile.subdomain, ignoreCase = true))
        )

        if (isFocusMismatched) {
            val focusName = currentProfile?.subdomain?.ifBlank { currentProfile.targetDomain } ?: "your focus area"
            _roadmapState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your roadmap for $focusName.")
            _studyTasksState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your study plan.")
            _checkpointResults.value = emptyMap()
            _completedAssignments.value = emptyMap()
            return
        }

        val roadmap = if (rawRoadmap != null && !isFocusMismatched) rawRoadmap else null

        if (roadmap != null && roadmap.steps.isNotEmpty()) {
            _roadmapState.value = LearningUiState.Success(roadmap)
        } else {
            _roadmapState.value = LearningUiState.Empty("Take the diagnostic assessment to generate your roadmap.")
        }

        val allTasks = if (activeTrackId != null) {
            learningRepository.getStudyTasksForTrack(activeTrackId)
        } else {
            learningRepository.getStudyTasks(userId)
        }

        val validStepIds = roadmap?.steps?.map { it.id }?.toSet() ?: emptySet()
        val validTopics = roadmap?.steps?.map { it.topic.lowercase().trim() } ?: emptyList()

        val checkpoints = mutableMapOf<Int, DayCheckpointResult>()
        val assignments = mutableMapOf<Int, AssignmentResult>()
        val regularTasks = mutableListOf<StudyTask>()

        for (t in allTasks) {
            val taskTopic = t.topic.lowercase().trim()
            val belongsToRoadmap = if (roadmap != null) {
                t.roadmapStepId in validStepIds || validTopics.any { taskTopic.contains(it) || it.contains(taskTopic) }
            } else {
                val isAcidPoison = taskTopic.contains("acid") || taskTopic.contains("chemical")
                val isNonScienceDomain = normTargetDomain == "Electronics and VLSI" || normTargetDomain == "Computer Science and IT"
                !(isAcidPoison && isNonScienceDomain)
            }

            if (t.notes.startsWith("CHECKPOINT|") || t.id.startsWith("checkpoint_")) {
                if (roadmap == null || belongsToRoadmap || validTopics.any { t.notes.lowercase().contains(it) }) {
                    val parts = t.notes.removePrefix("CHECKPOINT|").split("|").associate { part ->
                        val idx = part.indexOf(":")
                        if (idx > 0) part.substring(0, idx) to part.substring(idx + 1) else part to ""
                    }
                    val score = parts["score"]?.toIntOrNull() ?: if (t.isCompleted) 100 else 0
                    val correct = parts["correct"]?.toIntOrNull() ?: if (t.isCompleted) 5 else 0
                    val total = parts["total"]?.toIntOrNull() ?: 5
                    val attempt = parts["attempt"]?.toIntOrNull() ?: 1
                    val feedback = parts["feedback"] ?: if (t.isCompleted) "Checkpoint passed!" else "Needs improvement."
                    val remedial = parts["remedial"]
                    val topic = parts["topic"] ?: t.topic
                    checkpoints[t.dayIndex] = DayCheckpointResult(
                        dayIndex = t.dayIndex,
                        isPassed = t.isCompleted,
                        scorePercent = score,
                        correctCount = correct,
                        totalQuestions = total,
                        feedback = feedback,
                        remedialAdvice = remedial,
                        attemptNumber = attempt,
                        topic = topic
                    )
                }
            } else if (t.notes.startsWith("ASSIGNMENT|") || t.id.startsWith("assignment_")) {
                if (roadmap == null || belongsToRoadmap || validTopics.any { t.notes.lowercase().contains(it) }) {
                    val parts = t.notes.removePrefix("ASSIGNMENT|").split("|").associate { part ->
                        val idx = part.indexOf(":")
                        if (idx > 0) part.substring(0, idx) to part.substring(idx + 1) else part to ""
                    }
                    val score = parts["score"]?.toIntOrNull() ?: 100
                    val feedback = parts["feedback"] ?: "Assignment completed."
                    assignments[t.dayIndex] = AssignmentResult(
                        assignmentId = t.id,
                        scorePercent = score,
                        feedback = feedback,
                        isSubmitted = t.isCompleted
                    )
                }
            } else {
                if (belongsToRoadmap) {
                    regularTasks.add(t)
                }
            }
        }
        _checkpointResults.value = checkpoints
        _completedAssignments.value = assignments

        if (regularTasks.isNotEmpty()) {
            _studyTasksState.value = LearningUiState.Success(regularTasks)
        } else {
            _studyTasksState.value = LearningUiState.Empty("No study tasks scheduled yet.")
        }

        // Calculate unlocked days sequentially:
        // Day 1 is always unlocked.
        // Day D > 1 is unlocked iff Day D-1 regular tasks are all completed AND Day D-1 checkpoint assessment was passed.
        val unlocked = mutableSetOf(1)
        val regularByDay = regularTasks.groupBy { it.dayIndex }
        val maxDay = maxOf(1, regularTasks.maxOfOrNull { it.dayIndex } ?: (roadmap?.steps?.size ?: 1))
        for (d in 1..maxDay) {
            if (unlocked.contains(d)) {
                val dayTasks = regularByDay[d] ?: emptyList()
                val tasksDone = dayTasks.isNotEmpty() && dayTasks.all { it.isCompleted }
                val cpPassed = checkpoints[d]?.isPassed == true
                if (tasksDone && cpPassed) {
                    unlocked.add(d + 1)
                }
            }
        }
        _unlockedDays.value = unlocked

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

                // If AI Core Engine is active, synthesize personalized AI skill gaps, roadmap and daily study plan
                var finalReport = report
                aiCoreEngine?.let { engine ->
                    try {
                        val aiReport = engine.interpretSkillGaps(profile, questions, answers, currentTrackId)
                        if (aiReport.skillGaps.isNotEmpty()) {
                            finalReport = aiReport
                            learningRepository.saveSkillGapReport(aiReport, currentTrackId)
                        }
                        val aiRoadmap = engine.synthesizePersonalizedRoadmapAsync(profile, finalReport, trackId = currentTrackId)
                        learningRepository.saveRoadmap(aiRoadmap, currentTrackId)
                        val learnerCtx = com.learnmate.ai.domain.LearnerContext.fromProfile(profile, currentTrackId, finalReport)
                        val dueSrs = learningRepository.getSpacedRepetitionQueue(currentTrackId, profile.userId)
                        val aiTasks = engine.recommendDailyStudyPlan(learnerCtx, aiRoadmap, dueSrs, currentTrackId)
                        if (aiTasks.isNotEmpty()) {
                            learningRepository.saveStudyTasks(aiTasks, currentTrackId)
                        }
                    } catch (_: Exception) {
                        // Fall back cleanly to deterministic roadmap generated in submitAssessment
                    }
                }

                _skillGapState.value = LearningUiState.Success(finalReport)
                loadRoadmapAndTasks(profile.userId)
                loadAdaptiveLearningEngine(currentTrackId)
            } catch (e: Exception) {
                _skillGapState.value = LearningUiState.Error(e.message ?: "Assessment grading failed")
            }
        }
    }

    suspend fun getOrGenerateDiagnosticAssessment(profile: LearnerProfile): List<AssessmentQuestion> {
        val aiEngine = aiCoreEngine
        val targetDom = profile.targetDomain
        val normDom = TopicTaxonomy.normalizeDomain(targetDom)

        if (aiEngine != null) {
            val questions = aiEngine.generateDiagnosticAssessment(profile)
            if (questions.isNotEmpty()) {
                learningRepository.saveDiagnosticQuestions(questions)
                return questions.take(10)
            }
        }
        val tailored = aiEngine?.generateTailoredDomainQuestions(profile)
        if (!tailored.isNullOrEmpty()) {
            learningRepository.saveDiagnosticQuestions(tailored)
            return tailored.take(10)
        }
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(normDom)
        if (domainQuestions.isNotEmpty()) {
            return domainQuestions.take(10)
        }
        return emptyList()
    }

    suspend fun getQuestionsForDomain(domain: String): List<AssessmentQuestion> {
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(domain)
        if (domainQuestions.isNotEmpty()) return domainQuestions.take(10)
        val dummyProfile = LearnerProfile(userId = "temp", name = "", targetDomain = domain)
        val tailored = aiCoreEngine?.generateTailoredDomainQuestions(dummyProfile)
        if (!tailored.isNullOrEmpty()) return tailored.take(10)
        return learningRepository.getAssessmentQuestionsForDomain(TopicTaxonomy.normalizeDomain(domain)).take(10)
    }

    suspend fun getAllDiagnosticQuestions(): List<AssessmentQuestion> {
        return learningRepository.getAllQuestions().take(10)
    }

    fun toggleStudyTask(taskId: String, isCompleted: Boolean) {
        viewModelScope.launch {
            learningRepository.toggleTaskCompletion(currentUserId, taskId, isCompleted)
            loadRoadmapAndTasks(currentUserId)
        }
    }

    fun isDayUnlocked(dayIndex: Int): Boolean {
        return dayIndex <= 1 || _unlockedDays.value.contains(dayIndex)
    }

    suspend fun getCheckpointQuestions(
        profile: LearnerProfile,
        dayIndex: Int,
        topic: String,
        attemptNumber: Int = 1
    ): List<AssessmentQuestion> {
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(profile.targetDomain)
        val candidateQuestions = if (domainQuestions.isNotEmpty()) domainQuestions
            else (aiCoreEngine?.generateTailoredDomainQuestions(profile) ?: emptyList())

        val topicMatches = candidateQuestions.filter {
            it.topic.contains(topic, ignoreCase = true) || topic.contains(it.topic, ignoreCase = true)
        }
        val pool = if (topicMatches.size >= 5) topicMatches else candidateQuestions

        return if (attemptNumber > 1) {
            val rng = kotlin.random.Random(dayIndex * 31 + attemptNumber * 101)
            pool.shuffled(rng).take(5)
        } else {
            val offset = ((dayIndex - 1) * 5) % maxOf(1, pool.size)
            val sub = pool.drop(offset) + pool.take(offset)
            sub.take(5)
        }
    }

    suspend fun submitDayCheckpointAssessmentAsync(
        profile: LearnerProfile,
        dayIndex: Int,
        topic: String,
        questions: List<AssessmentQuestion>,
        answers: Map<String, Int>,
        attemptNumber: Int = 1
    ): DayCheckpointResult {
        val total = questions.size
        var correct = 0
        val missedTopics = mutableListOf<String>()
        for (q in questions) {
            val ans = answers[q.id]
            if (ans != null && ans == q.correctIndex) {
                correct++
            } else {
                missedTopics.add(q.topic.ifBlank { topic })
            }
        }
        val scorePercent = if (total > 0) (correct * 100) / total else 0
        val isPassed = scorePercent >= 60

        val feedback = if (isPassed) {
            "Passed! Score: $scorePercent% ($correct/$total correct). Day ${dayIndex + 1} is now unlocked!"
        } else {
            "Checkpoint Not Passed ($scorePercent%). Minimum 60% required to unlock Day ${dayIndex + 1}."
        }

        val remedial = if (!isPassed) {
            val reviewTopics = missedTopics.distinct().joinToString(", ")
            "Review the following core concept(s): $reviewTopics. Consult the AI Tutor or textbook module for clarification, then retake the checkpoint assessment."
        } else null

        val result = DayCheckpointResult(
            dayIndex = dayIndex,
            isPassed = isPassed,
            scorePercent = scorePercent,
            correctCount = correct,
            totalQuestions = total,
            feedback = feedback,
            remedialAdvice = remedial,
            attemptNumber = attemptNumber,
            topic = topic
        )

        val currentTrackId = _activeTrack.value?.trackId ?: "track_${profile.userId}_default"
        val taskId = "checkpoint_${currentTrackId}_day_${dayIndex}"
        val notes = "CHECKPOINT|score:$scorePercent|correct:$correct|total:$total|attempt:$attemptNumber|feedback:$feedback${if (remedial != null) "|remedial:$remedial" else ""}|topic:$topic"

        val task = StudyTask(
            id = taskId,
            userId = profile.userId,
            roadmapStepId = "step_checkpoint_day_${dayIndex}",
            dayIndex = dayIndex,
            weekIndex = (dayIndex - 1) / 7 + 1,
            title = "Day $dayIndex Checkpoint Assessment",
            topic = topic,
            durationMinutes = 10,
            isCompleted = isPassed,
            completedAtEpochMs = System.currentTimeMillis(),
            notes = notes
        )

        try {
            learningRepository.saveStudyTasks(listOf(task), currentTrackId)
            try {
                learningRepository.recordDiagnosticAttempt(
                    trackId = currentTrackId,
                    userId = profile.userId,
                    nodeId = "day_${dayIndex}_checkpoint",
                    correctCount = correct,
                    totalQuestions = total,
                    topicTitle = topic
                )
            } catch (_: Exception) {}

            loadRoadmapAndTasks(profile.userId)
        } catch (_: Exception) {}

        return result
    }

    fun submitDayCheckpointAssessment(
        profile: LearnerProfile,
        dayIndex: Int,
        topic: String,
        questions: List<AssessmentQuestion>,
        answers: Map<String, Int>,
        attemptNumber: Int = 1,
        onResult: (DayCheckpointResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = submitDayCheckpointAssessmentAsync(profile, dayIndex, topic, questions, answers, attemptNumber)
            onResult(res)
        }
    }

    suspend fun generateAssignmentForDay(
        profile: LearnerProfile,
        dayIndex: Int,
        topic: String
    ): AssignmentInstance {
        val trackId = _activeTrack.value?.trackId ?: "track_${profile.userId}_default"
        val domainQuestions = learningRepository.getAssessmentQuestionsForDomain(profile.targetDomain)
        val questions = if (domainQuestions.isNotEmpty()) domainQuestions
            else (aiCoreEngine?.generateTailoredDomainQuestions(profile) ?: emptyList())
        return DeterministicAssessmentEngine.createAssignmentInstance(
            userId = profile.userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = questions,
            count = 3
        )
    }

    suspend fun submitAssignmentAsync(
        profile: LearnerProfile,
        assignment: AssignmentInstance,
        answers: Map<String, Int>
    ): AssignmentResult {
        val total = assignment.questions.size
        var correct = 0
        for (q in assignment.questions) {
            val ans = answers[q.id]
            if (ans != null && ans == q.correctIndex) {
                correct++
            }
        }
        val scorePercent = if (total > 0) (correct * 100) / total else 100
        val feedback = "Assignment submitted with score $scorePercent% ($correct/$total correct). Practical skills verified."
        val currentTrackId = _activeTrack.value?.trackId ?: "track_${profile.userId}_default"
        val taskId = "assignment_${currentTrackId}_day_${assignment.dayIndex}"
        val notes = "ASSIGNMENT|score:$scorePercent|feedback:$feedback"

        val task = StudyTask(
            id = taskId,
            userId = profile.userId,
            roadmapStepId = "step_assignment_day_${assignment.dayIndex}",
            dayIndex = assignment.dayIndex,
            weekIndex = (assignment.dayIndex - 1) / 7 + 1,
            title = "Day ${assignment.dayIndex} AI Assignment",
            topic = assignment.questions.firstOrNull()?.topic ?: "Practical Application",
            durationMinutes = 15,
            isCompleted = true,
            completedAtEpochMs = System.currentTimeMillis(),
            notes = notes
        )

        try {
            learningRepository.saveStudyTasks(listOf(task), currentTrackId)
            loadRoadmapAndTasks(profile.userId)
        } catch (_: Exception) {}

        return AssignmentResult(
            assignmentId = assignment.assignmentInstanceId,
            scorePercent = scorePercent,
            feedback = feedback,
            isSubmitted = true
        )
    }

    fun submitAssignment(
        profile: LearnerProfile,
        assignment: AssignmentInstance,
        answers: Map<String, Int>,
        onResult: (AssignmentResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = submitAssignmentAsync(profile, assignment, answers)
            onResult(res)
        }
    }

    fun loadWeeklyStudyPlan(trackId: String? = null) {
        viewModelScope.launch {
            val tId = trackId ?: _activeTrack.value?.trackId ?: "track_${currentUserId}_default"
            val plan = learningRepository.getWeeklyStudyPlan(tId, currentUserId)
            _weeklyStudyPlan.value = plan
        }
    }

    val modelAssetManager: com.learnmate.ai.ai.ModelAssetManager
        get() = aiTutorRepository.assetManager

    val modelEngine: com.learnmate.ai.ai.ExecuTorchLlamaEngine
        get() = aiTutorRepository.engine

    fun refreshModelStatus() {
        viewModelScope.launch {
            aiTutorRepository.engine.refreshModelPresenceState()
        }
    }

    fun initializeAndActivateModel(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = aiTutorRepository.initializeModelAsync()
            if (res.isSuccess) {
                aiTutorRepository.setAiEnabled(true)
                onResult?.invoke(true, null)
            } else {
                onResult?.invoke(false, res.exceptionOrNull()?.message ?: "Initialization failed")
            }
        }
    }

    fun runModelSmokeTest(onResult: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch {
            val res = aiTutorRepository.engine.runSmokeTest()
            if (res.isSuccess) {
                val metrics = res.getOrNull()
                onResult?.invoke(true, "Smoke test passed (${metrics?.tokenCount} tokens, ${metrics?.tokensPerSecond} TPS, TTFT: ${metrics?.ttftMs}ms)")
            } else {
                onResult?.invoke(false, res.exceptionOrNull()?.message ?: "Smoke test failed")
            }
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

    // ── YouTube Data API v3 & Local Llama Video Notes ────────────────────────

    fun loadYouTubeResourcesForTopic(profile: LearnerProfile?, topic: String) {
        viewModelScope.launch {
            _youtubeVideosState.value = LearningUiState.Loading
            try {
                val result = if (profile != null) {
                    youtubeRepository.getCuratedVideosForLearner(profile, topic)
                } else {
                    youtubeRepository.searchVideos(topic)
                }
                if (result.isSuccess) {
                    val videos = result.getOrNull().orEmpty()
                    if (videos.isNotEmpty()) {
                        _youtubeVideosState.value = LearningUiState.Success(videos)
                    } else {
                        _youtubeVideosState.value = LearningUiState.Empty("No video lectures found for $topic.")
                    }
                } else {
                    val error = result.exceptionOrNull()
                    val msg = error?.message ?: "Unable to fetch video recommendations."
                    _youtubeVideosState.value = LearningUiState.Error(msg)
                }
            } catch (e: Exception) {
                _youtubeVideosState.value = LearningUiState.Error(e.message ?: "Failed to load video resources.")
            }
        }
    }

    suspend fun generateVideoNotesAsync(
        video: YouTubeVideoItem,
        transcriptOrDescription: String = ""
    ): VideoStudyNotes {
        _isLoadingVideoNotes.value = true
        return try {
            val notes = youtubeRepository.generateStudyNotesWithLocalLlama(
                video = video,
                transcriptOrDescription = transcriptOrDescription,
                aiCoreEngine = aiCoreEngine
            )
            val currentMap = _videoStudyNotesState.value.toMutableMap()
            currentMap[video.id] = notes
            _videoStudyNotesState.value = currentMap
            notes
        } finally {
            _isLoadingVideoNotes.value = false
        }
    }

    fun generateVideoNotes(
        video: YouTubeVideoItem,
        transcriptOrDescription: String = "",
        onResult: (VideoStudyNotes) -> Unit = {}
    ) {
        viewModelScope.launch {
            val notes = generateVideoNotesAsync(video, transcriptOrDescription)
            onResult(notes)
        }
    }

    class Factory(
        private val learningRepository: LearningRepository,
        private val aiTutorRepository: AiTutorRepository,
        val appPreferences: AppPreferences? = null,
        private val aiCoreEngine: LearnMateAiCoreEngine? = null,
        private val youtubeRepository: YouTubeDataRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LearningViewModel::class.java)) {
                val ytRepo = youtubeRepository ?: YouTubeDataRepository(learningRepository)
                return LearningViewModel(learningRepository, aiTutorRepository, appPreferences, aiCoreEngine, ytRepo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

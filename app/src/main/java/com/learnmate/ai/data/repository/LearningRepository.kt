package com.learnmate.ai.data.repository

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.data.contentpack.BuiltInContentManifestRegistry
import com.learnmate.ai.data.contentpack.ContentPackDocumentImporter
import com.learnmate.ai.data.contentpack.ContentPackInstaller
import com.learnmate.ai.data.db.AssessmentQuestionEntity
import com.learnmate.ai.data.db.ChatMessageEntity
import com.learnmate.ai.data.db.ChatSessionEntity
import com.learnmate.ai.data.db.ContentPackEntity
import com.learnmate.ai.data.db.CurriculumNodeEntity
import com.learnmate.ai.data.db.CurriculumResourceEntity
import com.learnmate.ai.data.db.LearnMateDao
import com.learnmate.ai.data.db.LearnerProfileEntity
import com.learnmate.ai.data.db.LearnerTrackEntity
import com.learnmate.ai.data.db.NodePrerequisiteEntity
import com.learnmate.ai.data.db.StudyTaskEntity
import com.learnmate.ai.data.db.VerifiedTopicEntity
import com.learnmate.ai.data.roadmap.DeterministicRoadmapEngine
import com.learnmate.ai.data.studyplan.StudyPlanScheduler
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.ChatMessage
import com.learnmate.ai.domain.ChatSession
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.LearningMode
import com.learnmate.ai.domain.LearningTrack
import com.learnmate.ai.domain.PersonalisedRoadmap
import com.learnmate.ai.domain.SkillGapReport
import com.learnmate.ai.domain.StudyTask
import com.learnmate.ai.domain.TrackTopicProgress
import com.learnmate.ai.domain.LearningProgressStatus
import com.learnmate.ai.domain.curriculum.CurriculumEngine
import com.learnmate.ai.domain.mastery.KnowledgeMasteryEngine
import com.learnmate.ai.domain.mastery.MasteryLevel
import com.learnmate.ai.domain.mastery.TopicMasteryResult
import com.learnmate.ai.domain.recommendation.PersonalisedLearningPathEngine
import com.learnmate.ai.domain.recommendation.RecommendationPriority
import com.learnmate.ai.domain.recommendation.RecommendedTopicItem
import com.learnmate.ai.domain.recommendation.RevisionTopicItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import com.learnmate.ai.domain.assessment.AssessmentReliabilityEngine
import com.learnmate.ai.domain.assessment.AssessmentTrendPoint
import com.learnmate.ai.domain.assessment.QuestionBankCoverageReport
import com.learnmate.ai.domain.assessment.QuestionResponseRecord
import com.learnmate.ai.domain.repetition.SpacedRepetitionConfig
import com.learnmate.ai.domain.repetition.SpacedRepetitionEngine
import com.learnmate.ai.domain.repetition.SpacedRepetitionItem
import com.learnmate.ai.domain.analytics.LearnerAnalyticsReport
import com.learnmate.ai.domain.analytics.LearningAnalyticsEngine
import com.learnmate.ai.domain.planner.DailyStudyPlan
import com.learnmate.ai.domain.planner.PersonalisedStudyPlannerEngine
import com.learnmate.ai.domain.planner.PlannedStudyTask
import com.learnmate.ai.domain.planner.PlannedTaskType
import com.learnmate.ai.domain.planner.WeeklyStudyPlan
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Main application repository coordinating universal multi-track profile, curriculum taxonomy,
 * assessment, roadmaps, study tasks, and chat history persistence.
 *
 * Implements dual-layer persistence: backed by Room SQLite via [dao] when available,
 * with synchronized in-memory caching for zero-latency lookups and standalone JVM unit testing.
 */
class LearningRepository(
    private val dao: LearnMateDao? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // Synchronous memory cache for user-isolated queries and fast testing
    private val profilesByUser = mutableMapOf<String, LearnerProfile>()
    private val tracksByUser = mutableMapOf<String, MutableList<LearningTrack>>()
    private val skillGapsByUser = mutableMapOf<String, SkillGapReport>()
    private val roadmapsByUser = mutableMapOf<String, PersonalisedRoadmap>()
    private val studyTasksByUser = mutableMapOf<String, MutableList<StudyTask>>()
    private val chatSessionsByUser = mutableMapOf<String, MutableList<ChatSession>>()
    private val chatMessagesBySession = mutableMapOf<String, MutableList<ChatMessage>>()
    private val inMemoryTopicProgress = mutableMapOf<String, com.learnmate.ai.domain.TrackTopicProgress>()
    private val inMemoryTopics = mutableListOf<VerifiedTopicEntity>()
    private val inMemoryQuestions = mutableListOf<AssessmentQuestionEntity>()
    private val inMemoryCurriculumNodes = mutableListOf<CurriculumNodeEntity>()
    private val inMemoryPrerequisites = mutableListOf<NodePrerequisiteEntity>()
    private val inMemoryContentPacks = mutableListOf<ContentPackEntity>()
    private val quarantinedPackIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isPackQuarantined(packId: String): Boolean {
        if (quarantinedPackIds.contains(packId)) return true
        val entity = inMemoryContentPacks.firstOrNull { it.packId == packId }
            ?: dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId }
        return entity?.isDeprecated == true && entity.deprecationNotice?.startsWith("QUARANTINED") == true
    }

    suspend fun quarantinePack(packId: String, reason: String) = withContext(ioDispatcher) {
        quarantinedPackIds.add(packId)
        val dbPack = dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId }
            ?: inMemoryContentPacks.firstOrNull { it.packId == packId }
        if (dbPack != null) {
            val updated = dbPack.copy(
                isDeprecated = true,
                deprecationNotice = "QUARANTINED: $reason"
            )
            dao?.insertContentPackSync(updated)
            val idx = inMemoryContentPacks.indexOfFirst { it.packId == packId }
            if (idx != -1) {
                inMemoryContentPacks[idx] = updated
            }
        }
    }

    suspend fun liftQuarantine(packId: String) = withContext(ioDispatcher) {
        quarantinedPackIds.remove(packId)
        val dbPack = dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId }
            ?: inMemoryContentPacks.firstOrNull { it.packId == packId }
        if (dbPack != null && dbPack.deprecationNotice?.startsWith("QUARANTINED") == true) {
            val updated = dbPack.copy(
                isDeprecated = false,
                deprecationNotice = null
            )
            dao?.insertContentPackSync(updated)
            val idx = inMemoryContentPacks.indexOfFirst { it.packId == packId }
            if (idx != -1) {
                inMemoryContentPacks[idx] = updated
            }
        }
    }

    // Reactive streams for fallback mode
    private val profileFlows = mutableMapOf<String, MutableStateFlow<LearnerProfile?>>()
    private val tracksFlows = mutableMapOf<String, MutableStateFlow<List<LearningTrack>>>()
    private val activeTrackFlows = mutableMapOf<String, MutableStateFlow<LearningTrack?>>()
    private val roadmapFlows = mutableMapOf<String, MutableStateFlow<PersonalisedRoadmap?>>()
    private val studyTasksFlows = mutableMapOf<String, MutableStateFlow<List<StudyTask>>>()
    private val skillGapFlows = mutableMapOf<String, MutableStateFlow<SkillGapReport?>>()
    private val chatSessionFlows = mutableMapOf<String, MutableStateFlow<List<ChatSession>>>()
    private val messageFlows = mutableMapOf<String, MutableStateFlow<List<ChatMessage>>>()

    private val gson = Gson()
    private val inMemoryAssessmentAttempts = mutableMapOf<String, MutableList<AssessmentAttemptRecord>>()
    private val inMemorySrsItems = mutableMapOf<String, MutableMap<String, SpacedRepetitionItem>>()

    // ── Taxonomy & Universal Curriculum Initialization ──────────────────────────

    suspend fun initializeCurriculumIfNeeded() = withContext(ioDispatcher) {
        val verifiedTopics = TopicTaxonomy.getVerifiedTopics()
        val questions = TopicTaxonomy.getDiagnosticQuestions()
        inMemoryTopics.clear()
        inMemoryTopics.addAll(verifiedTopics)
        inMemoryQuestions.clear()
        inMemoryQuestions.addAll(questions)

        val universalNodes = CurriculumEngine.getUniversalBaselineNodes()
        val universalPrereqs = CurriculumEngine.getUniversalBaselinePrerequisites()
        inMemoryCurriculumNodes.clear()
        inMemoryCurriculumNodes.addAll(universalNodes)
        inMemoryPrerequisites.clear()
        inMemoryPrerequisites.addAll(universalPrereqs)

        val baselinePack = ContentPackEntity(
            packId = "pack_builtin_legacy_gate4",
            title = "Gate 4 Verified Engineering Baseline",
            version = "1.0.0",
            minAppVersion = "1.4.0",
            category = "ENGINEERING",
            primaryLanguage = "en",
            supportedLanguagesJson = "[\"en\", \"ta\", \"hi\", \"kn\"]",
            authorOrProvider = "LearnMate Editorial Board",
            provenanceNotice = "Accredited textbooks: Sze, Weste, Cormen, Cooper, Shigley, Moran, Incropera",
            checksumSha256 = "7ee6a2dd55e1ef8b58718406346626ac6cf57968c8168cc7892db67ecb1a081d",
            isInstalled = true,
            isBuiltIn = true,
            isDeprecated = false,
            installedAtEpochMs = 1700000000000L
        )
        inMemoryContentPacks.clear()
        inMemoryContentPacks.add(baselinePack)

        dao?.let {
            val count = it.getTopicCount()
            if (count < verifiedTopics.size) {
                it.insertTopics(verifiedTopics)
                it.insertQuestions(questions)
            }
            val nodeCount = it.getAllCurriculumNodes().size
            if (nodeCount < universalNodes.size) {
                it.insertContentPack(baselinePack)
                it.insertCurriculumNodes(universalNodes)
                it.insertPrerequisites(universalPrereqs)
            }
        }
    }

    // ── Universal Learner Profile ────────────────────────────────────────────────

    suspend fun saveProfile(profile: LearnerProfile) = withContext(ioDispatcher) {
        profilesByUser[profile.userId] = profile
        getOrCreateProfileFlow(profile.userId).value = profile

        dao?.let {
            val entity = LearnerProfileEntity(
                userId = profile.userId,
                name = profile.name,
                targetDomain = profile.targetDomain,
                subdomain = profile.subdomain,
                goal = profile.goal,
                targetRole = profile.targetRole,
                previousExperience = profile.previousBackground,
                currentLevel = profile.currentLevel,
                audienceLevel = profile.audienceLevel.displayName,
                dailyStudyMinutes = profile.dailyStudyMinutes,
                weeklyStudyDays = profile.weeklyStudyDays,
                currentTopic = profile.currentTopic,
                onboardingCompleted = profile.onboardingCompleted,
                assessmentCompleted = profile.assessmentCompleted,
                isDemoAccount = profile.isDemoAccount,
                preferredLanguageCode = profile.preferredLanguageCode,
                defaultEducationLevel = profile.educationLevel.name,
                defaultLearnerCategory = profile.learnerCategory.name,
                isMinor = profile.isMinor,
                guardianConsentGiven = profile.guardianConsentGiven,
                updatedAtEpochMs = System.currentTimeMillis()
            )
            it.upsertProfile(entity)

            // Ensure baseline active learning track exists for this learner
            val defaultTrackId = "track_${profile.userId}_default"
            val existingTrack = it.getTrackById(defaultTrackId)
            if (existingTrack == null) {
                // Dynamically resolve curriculum node from installed packs matching user's target domain
                val matchedNode = if (profile.targetDomain.isNotBlank()) {
                    it.getAllCurriculumNodes().firstOrNull { node ->
                        node.parentNodeId == null && (
                            node.title.equals(profile.targetDomain, ignoreCase = true) ||
                            node.code.equals(profile.targetDomain, ignoreCase = true)
                        )
                    }
                } else null
                val targetNodeId = matchedNode?.nodeId // Safe Unresolved-Track state (null if not yet installed)

                val defaultTrack = LearnerTrackEntity(
                    trackId = defaultTrackId,
                    userId = profile.userId,
                    title = "${profile.targetDomain.ifBlank { "Universal Learning" }} Track",
                    learningMode = "ACADEMIC",
                    targetCurriculumNodeId = targetNodeId,
                    dailyCommitmentMinutes = profile.dailyStudyMinutes,
                    isTrackActive = true,
                    createdAtEpochMs = entity.createdAtEpochMs,
                    lastAccessedEpochMs = entity.updatedAtEpochMs
                )
                it.upsertTrack(defaultTrack)
            }
        }

        // Keep in-memory tracks in sync
        val memTracks = tracksByUser.getOrPut(profile.userId) { mutableListOf() }
        if (memTracks.isEmpty()) {
            val defaultTrack = LearningTrack(
                trackId = "track_${profile.userId}_default",
                userId = profile.userId,
                title = "${profile.targetDomain.ifBlank { "Universal Learning" }} Track",
                learningMode = LearningMode.ACADEMIC,
                targetCurriculumNodeId = null,
                dailyCommitmentMinutes = profile.dailyStudyMinutes,
                isTrackActive = true
            )
            memTracks.add(defaultTrack)
            getOrCreateTracksFlow(profile.userId).value = memTracks.toList()
            getOrCreateActiveTrackFlow(profile.userId).value = defaultTrack
        }
    }

    suspend fun getProfile(userId: String): LearnerProfile? = withContext(ioDispatcher) {
        profilesByUser[userId]?.let { return@withContext it }
        val entity = dao?.getProfile(userId)
        if (entity != null) {
            val model = mapEntityToProfile(entity)
            profilesByUser[userId] = model
            getOrCreateProfileFlow(userId).value = model
            model
        } else null
    }

    fun observeProfile(userId: String): Flow<LearnerProfile?> {
        val localFlow = dao?.observeProfile(userId)?.map { it?.let { mapEntityToProfile(it) } }
        return localFlow ?: getOrCreateProfileFlow(userId)
    }

    suspend fun getAllProfiles(): List<LearnerProfile> = withContext(ioDispatcher) {
        val entities = dao?.getAllProfiles() ?: emptyList()
        val mapped = entities.map { mapEntityToProfile(it) }
        mapped.forEach { profilesByUser[it.userId] = it }
        mapped.ifEmpty { profilesByUser.values.toList() }
    }

    fun observeAllProfiles(): Flow<List<LearnerProfile>> {
        val localFlow = dao?.observeAllProfiles()?.map { list -> list.map { mapEntityToProfile(it) } }
        return localFlow ?: kotlinx.coroutines.flow.flowOf(profilesByUser.values.toList())
    }

    private fun getOrCreateProfileFlow(userId: String): MutableStateFlow<LearnerProfile?> {
        return profileFlows.getOrPut(userId) { MutableStateFlow(profilesByUser[userId]) }
    }

    private fun mapEntityToProfile(entity: LearnerProfileEntity): LearnerProfile {
        return LearnerProfile(
            userId = entity.userId,
            name = entity.name,
            targetDomain = entity.targetDomain,
            subdomain = entity.subdomain,
            goal = entity.goal,
            targetRole = entity.targetRole,
            previousBackground = entity.previousExperience,
            currentLevel = entity.currentLevel,
            audienceLevel = AudienceLevel.fromDisplayName(entity.audienceLevel),
            dailyStudyMinutes = entity.dailyStudyMinutes,
            weeklyStudyDays = entity.weeklyStudyDays,
            currentTopic = entity.currentTopic,
            onboardingCompleted = entity.onboardingCompleted,
            assessmentCompleted = entity.assessmentCompleted,
            isDemoAccount = entity.isDemoAccount,
            preferredLanguageCode = entity.preferredLanguageCode,
            educationLevel = EducationLevel.fromString(entity.defaultEducationLevel),
            learnerCategory = LearnerCategory.fromString(entity.defaultLearnerCategory),
            isMinor = entity.isMinor,
            guardianConsentGiven = entity.guardianConsentGiven
        )
    }

    // ── Multi-Track Management ───────────────────────────────────────────────────

    suspend fun getTracksForUser(userId: String): List<LearningTrack> = withContext(ioDispatcher) {
        tracksByUser[userId]?.let { if (it.isNotEmpty()) return@withContext it.toList() }
        val entities = dao?.getTracksForUser(userId)
        if (!entities.isNullOrEmpty()) {
            val models = entities.map { mapEntityToTrack(it) }
            tracksByUser[userId] = models.toMutableList()
            getOrCreateTracksFlow(userId).value = models
            models
        } else {
            emptyList()
        }
    }

    fun observeTracksForUser(userId: String): Flow<List<LearningTrack>> {
        val localFlow = dao?.observeTracksForUser(userId)?.map { list -> list.map { mapEntityToTrack(it) } }
        return localFlow ?: getOrCreateTracksFlow(userId)
    }

    private fun getOrCreateTracksFlow(userId: String): MutableStateFlow<List<LearningTrack>> {
        return tracksFlows.getOrPut(userId) { MutableStateFlow(tracksByUser[userId]?.toList() ?: emptyList()) }
    }

    suspend fun getActiveTrackForUser(userId: String): LearningTrack? = withContext(ioDispatcher) {
        val fromDao = dao?.getActiveTrackForUser(userId)
        if (fromDao != null) {
            val track = mapEntityToTrack(fromDao)
            getOrCreateActiveTrackFlow(userId).value = track
            return@withContext track
        }
        val memoryMatch = tracksByUser[userId]?.firstOrNull { it.isTrackActive }
        getOrCreateActiveTrackFlow(userId).value = memoryMatch
        memoryMatch
    }

    fun observeActiveTrackForUser(userId: String): Flow<LearningTrack?> {
        val localFlow = dao?.observeActiveTrackForUser(userId)?.map { it?.let { mapEntityToTrack(it) } }
        return localFlow ?: getOrCreateActiveTrackFlow(userId)
    }

    private fun getOrCreateActiveTrackFlow(userId: String): MutableStateFlow<LearningTrack?> {
        return activeTrackFlows.getOrPut(userId) {
            MutableStateFlow(tracksByUser[userId]?.firstOrNull { it.isTrackActive })
        }
    }

    suspend fun createTrack(
        userId: String,
        title: String,
        learningMode: LearningMode = LearningMode.ACADEMIC,
        targetCurriculumNodeId: String? = null,
        dailyCommitmentMinutes: Int = 60,
        targetExamName: String? = null,
        targetExamDateEpochMs: Long? = null,
        setAsActive: Boolean = true
    ): LearningTrack = withContext(ioDispatcher) {
        if (targetCurriculumNodeId != null) {
            val nodePackId = dao?.getNodeById(targetCurriculumNodeId)?.packId
                ?: inMemoryCurriculumNodes.firstOrNull { it.nodeId == targetCurriculumNodeId }?.packId
            if (nodePackId != null && isPackQuarantined(nodePackId)) {
                throw IllegalStateException("Quarantined content pack '$nodePackId' cannot be enrolled in learning track.")
            }
        }
        val trackId = "track_${UUID.randomUUID()}"
        val track = LearningTrack(
            trackId = trackId,
            userId = userId,
            title = title,
            learningMode = learningMode,
            targetCurriculumNodeId = targetCurriculumNodeId,
            targetExamName = targetExamName,
            targetExamDateEpochMs = targetExamDateEpochMs,
            dailyCommitmentMinutes = dailyCommitmentMinutes,
            isTrackActive = setAsActive,
            createdAtEpochMs = System.currentTimeMillis(),
            lastAccessedEpochMs = System.currentTimeMillis()
        )

        val entity = LearnerTrackEntity(
            trackId = track.trackId,
            userId = track.userId,
            title = track.title,
            learningMode = track.learningMode.name,
            targetCurriculumNodeId = track.targetCurriculumNodeId,
            targetExamName = track.targetExamName,
            targetExamDateEpochMs = track.targetExamDateEpochMs,
            dailyCommitmentMinutes = track.dailyCommitmentMinutes,
            isTrackActive = track.isTrackActive,
            createdAtEpochMs = track.createdAtEpochMs,
            lastAccessedEpochMs = track.lastAccessedEpochMs
        )

        dao?.let {
            if (setAsActive) {
                it.deactivateAllTracks(userId)
            }
            it.upsertTrack(entity)
        }

        val list = tracksByUser.getOrPut(userId) { mutableListOf() }
        if (setAsActive) {
            list.forEachIndexed { idx, t -> list[idx] = t.copy(isTrackActive = false) }
        }
        list.add(0, track)
        getOrCreateTracksFlow(userId).value = list.toList()
        if (setAsActive) {
            getOrCreateActiveTrackFlow(userId).value = track
        }
        track
    }

    suspend fun switchActiveTrack(userId: String, trackId: String) = withContext(ioDispatcher) {
        dao?.setActiveTrack(userId, trackId)

        tracksByUser[userId]?.let { list ->
            list.forEachIndexed { idx, t ->
                list[idx] = t.copy(
                    isTrackActive = (t.trackId == trackId),
                    lastAccessedEpochMs = if (t.trackId == trackId) System.currentTimeMillis() else t.lastAccessedEpochMs
                )
            }
            getOrCreateTracksFlow(userId).value = list.toList()
            val active = list.firstOrNull { it.trackId == trackId }
            getOrCreateActiveTrackFlow(userId).value = active
        }
    }

    private fun mapEntityToTrack(entity: LearnerTrackEntity): LearningTrack {
        return LearningTrack(
            trackId = entity.trackId,
            userId = entity.userId,
            title = entity.title,
            learningMode = LearningMode.fromString(entity.learningMode),
            targetCurriculumNodeId = entity.targetCurriculumNodeId,
            targetExamName = entity.targetExamName,
            targetExamDateEpochMs = entity.targetExamDateEpochMs,
            dailyCommitmentMinutes = entity.dailyCommitmentMinutes,
            isTrackActive = entity.isTrackActive,
            createdAtEpochMs = entity.createdAtEpochMs,
            lastAccessedEpochMs = entity.lastAccessedEpochMs
        )
    }

    // ── Universal Curriculum Queries ─────────────────────────────────────────────

    suspend fun getRootCurriculumNodes(): List<CurriculumNodeEntity> = withContext(ioDispatcher) {
        val fromDao = dao?.getRootNodes()
        if (!fromDao.isNullOrEmpty()) return@withContext fromDao
        inMemoryCurriculumNodes.filter { it.parentNodeId == null }
    }

    suspend fun getChildrenOfCurriculumNode(parentNodeId: String): List<CurriculumNodeEntity> = withContext(ioDispatcher) {
        val fromDao = dao?.getChildrenOfNode(parentNodeId)
        if (!fromDao.isNullOrEmpty()) return@withContext fromDao
        inMemoryCurriculumNodes.filter { it.parentNodeId == parentNodeId }
    }

    suspend fun getAllCurriculumNodes(): List<CurriculumNodeEntity> = withContext(ioDispatcher) {
        val fromDao = dao?.getAllCurriculumNodes()
        if (!fromDao.isNullOrEmpty()) return@withContext fromDao
        inMemoryCurriculumNodes.toList()
    }

    suspend fun getAllPrerequisites(): List<NodePrerequisiteEntity> = withContext(ioDispatcher) {
        val fromDao = dao?.getAllPrerequisites()
        if (!fromDao.isNullOrEmpty()) return@withContext fromDao
        inMemoryPrerequisites.toList()
    }

    // ── Assessment & Skill Gaps ──────────────────────────────────────────────────

    suspend fun saveDiagnosticQuestions(questions: List<AssessmentQuestion>) = withContext(ioDispatcher) {
        val entities = questions.map { q ->
            AssessmentQuestionEntity(
                questionId = q.id,
                domain = q.domain,
                subdomain = q.subdomain,
                topic = q.topic,
                questionText = q.questionText,
                optionsJson = com.google.gson.Gson().toJson(q.options),
                correctIndex = q.correctIndex,
                verifiedExplanation = q.explanation
            )
        }
        dao?.insertQuestions(entities)
        inMemoryQuestions.removeAll { mem -> questions.any { it.id == mem.questionId } }
        inMemoryQuestions.addAll(entities)
    }

    suspend fun getAssessmentQuestionsForDomain(domain: String): List<AssessmentQuestion> = withContext(ioDispatcher) {
        val universalMatch = DeterministicAssessmentEngine.getAllUniversalDiagnosticQuestions()
            .filter { it.domain.contains(domain, ignoreCase = true) || domain.contains(it.domain, ignoreCase = true) || it.topic.contains(domain, ignoreCase = true) }
        if (universalMatch.isNotEmpty()) {
            return@withContext universalMatch
        }
        val fromDao = dao?.getQuestionsForDomain(domain)
        if (!fromDao.isNullOrEmpty()) {
            return@withContext fromDao.map { DeterministicAssessmentEngine.mapEntityToDomain(it) }
        }
        val memoryMatch = inMemoryQuestions.filter { it.domain.equals(domain, ignoreCase = true) }
        if (memoryMatch.isNotEmpty()) {
            return@withContext memoryMatch.map { DeterministicAssessmentEngine.mapEntityToDomain(it) }
        }
        TopicTaxonomy.getDiagnosticQuestions()
            .filter { it.domain.equals(domain, ignoreCase = true) }
            .map { DeterministicAssessmentEngine.mapEntityToDomain(it) }
    }

    suspend fun getAllQuestions(): List<AssessmentQuestion> = withContext(ioDispatcher) {
        val universal = DeterministicAssessmentEngine.getAllUniversalDiagnosticQuestions()
        val legacy = TopicTaxonomy.getDiagnosticQuestions().map { DeterministicAssessmentEngine.mapEntityToDomain(it) }
        universal + legacy
    }

    suspend fun submitAssessment(
        profile: LearnerProfile,
        questions: List<AssessmentQuestion>,
        answers: Map<String, Int>,
        trackId: String = "track_${profile.userId}_default"
    ): SkillGapReport = withContext(ioDispatcher) {
        val report = DeterministicAssessmentEngine.evaluateAssessment(profile, questions, answers)
        skillGapsByUser[profile.userId] = report
        getOrCreateSkillGapFlow(profile.userId).value = report

        dao?.upsertSkillGapReport(DeterministicAssessmentEngine.mapReportToEntity(report, trackId))

        // Record diagnostic attempts per topic into track study tasks for mastery engine
        val questionsByTopic = questions.groupBy { it.topic }
        for ((topic, qList) in questionsByTopic) {
            val total = qList.size
            val correct = qList.count { (answers[it.id] ?: -1) == it.correctIndex }
            val matchedNodeId = when {
                topic.contains("Chemical", ignoreCase = true) -> "cbse_sci10_ch01"
                topic.contains("Acid", ignoreCase = true) -> "cbse_sci10_ch02"
                topic.contains("Electrical", ignoreCase = true) || topic.contains("Ohm", ignoreCase = true) -> "dip_ee_ohm_kirchhoff"
                topic.contains("K-Map", ignoreCase = true) || topic.contains("Digital", ignoreCase = true) -> "eng_de_kmap"
                topic.contains("Mechanics", ignoreCase = true) || topic.contains("Physics", ignoreCase = true) -> "sci_phys_mechanics"
                topic.contains("Accounting", ignoreCase = true) || topic.contains("Double-Entry", ignoreCase = true) -> "mgmt_acc_double_entry"
                topic.contains("Rights", ignoreCase = true) || topic.contains("Polity", ignoreCase = true) -> "upsc_gs2_polity_rights"
                topic.contains("Hypothesis", ignoreCase = true) || topic.contains("Research", ignoreCase = true) -> "res_method_hypothesis"
                topic.contains("Safety", ignoreCase = true) || topic.contains("LOTO", ignoreCase = true) -> "voc_elec_safety"
                else -> topic
            }
            recordDiagnosticAttempt(
                trackId = trackId,
                userId = profile.userId,
                nodeId = matchedNodeId,
                correctCount = correct,
                totalQuestions = total,
                topicTitle = topic
            )
        }

        // Update profile status
        val updatedProfile = profile.copy(
            assessmentCompleted = true,
            currentTopic = report.weaknesses.firstOrNull() ?: report.strengths.firstOrNull() ?: profile.currentTopic
        )
        saveProfile(updatedProfile)

        // Automatically synthesize personalized roadmap from skill gap
        val roadmap = DeterministicRoadmapEngine.generateRoadmap(updatedProfile, report)
        saveRoadmap(roadmap, trackId)

        // Automatically schedule daily study tasks
        val tasks = StudyPlanScheduler.generateStudyTasks(updatedProfile, roadmap)
        saveStudyTasks(tasks, trackId)

        report
    }

    suspend fun getSkillGapReport(userId: String): SkillGapReport? = withContext(ioDispatcher) {
        skillGapsByUser[userId]?.let { return@withContext it }
        val entity = dao?.getSkillGapReport(userId)
        if (entity != null) {
            val report = DeterministicAssessmentEngine.mapEntityToReport(entity)
            skillGapsByUser[userId] = report
            getOrCreateSkillGapFlow(userId).value = report
            report
        } else null
    }

    fun observeSkillGapReport(userId: String): Flow<SkillGapReport?> {
        val localFlow = dao?.observeSkillGapReport(userId)?.map { it?.let { DeterministicAssessmentEngine.mapEntityToReport(it) } }
        return localFlow ?: getOrCreateSkillGapFlow(userId)
    }

    private fun getOrCreateSkillGapFlow(userId: String): MutableStateFlow<SkillGapReport?> {
        return skillGapFlows.getOrPut(userId) { MutableStateFlow(skillGapsByUser[userId]) }
    }

    // ── Personalized Roadmap ─────────────────────────────────────────────────────

    suspend fun saveRoadmap(roadmap: PersonalisedRoadmap, trackId: String = "track_${roadmap.userId}_default") = withContext(ioDispatcher) {
        roadmapsByUser[roadmap.userId] = roadmap
        getOrCreateRoadmapFlow(roadmap.userId).value = roadmap
        dao?.upsertRoadmap(DeterministicRoadmapEngine.mapToEntity(roadmap, trackId))
    }

    suspend fun getRoadmap(userId: String): PersonalisedRoadmap? = withContext(ioDispatcher) {
        roadmapsByUser[userId]?.let { return@withContext it }
        val entity = dao?.getRoadmap(userId)
        if (entity != null) {
            val model = DeterministicRoadmapEngine.mapFromEntity(entity)
            roadmapsByUser[userId] = model
            getOrCreateRoadmapFlow(userId).value = model
            model
        } else null
    }

    fun observeRoadmap(userId: String): Flow<PersonalisedRoadmap?> {
        val localFlow = dao?.observeRoadmap(userId)?.map { it?.let { DeterministicRoadmapEngine.mapFromEntity(it) } }
        return localFlow ?: getOrCreateRoadmapFlow(userId)
    }

    private fun getOrCreateRoadmapFlow(userId: String): MutableStateFlow<PersonalisedRoadmap?> {
        return roadmapFlows.getOrPut(userId) { MutableStateFlow(roadmapsByUser[userId]) }
    }

    // ── Study Tasks & Progress ───────────────────────────────────────────────────

    suspend fun saveStudyTasks(tasks: List<StudyTask>, trackId: String = if (tasks.isNotEmpty()) "track_${tasks.first().userId}_default" else "track_default") = withContext(ioDispatcher) {
        if (tasks.isNotEmpty()) {
            val userId = tasks.first().userId
            studyTasksByUser[userId] = tasks.toMutableList()
            getOrCreateStudyTasksFlow(userId).value = tasks
            dao?.upsertStudyTasks(tasks.map { StudyPlanScheduler.mapToEntity(it, trackId) })
        }
    }

    suspend fun getStudyTasks(userId: String): List<StudyTask> = withContext(ioDispatcher) {
        studyTasksByUser[userId]?.let { if (it.isNotEmpty()) return@withContext it.toList() }
        val entities = dao?.getStudyTasks(userId)
        if (!entities.isNullOrEmpty()) {
            val models = entities.map { StudyPlanScheduler.mapFromEntity(it) }
            studyTasksByUser[userId] = models.toMutableList()
            getOrCreateStudyTasksFlow(userId).value = models
            models
        } else emptyList()
    }

    fun observeStudyTasks(userId: String): Flow<List<StudyTask>> {
        val localFlow = dao?.observeStudyTasks(userId)?.map { entities -> entities.map { StudyPlanScheduler.mapFromEntity(it) } }
        return localFlow ?: getOrCreateStudyTasksFlow(userId)
    }

    private fun getOrCreateStudyTasksFlow(userId: String): MutableStateFlow<List<StudyTask>> {
        return studyTasksFlows.getOrPut(userId) { MutableStateFlow(studyTasksByUser[userId] ?: emptyList()) }
    }

    suspend fun toggleTaskCompletion(userId: String, taskId: String, isCompleted: Boolean) = withContext(ioDispatcher) {
        val timestamp = if (isCompleted) System.currentTimeMillis() else null
        studyTasksByUser[userId]?.let { list ->
            val idx = list.indexOfFirst { it.id == taskId }
            if (idx != -1) {
                list[idx] = list[idx].copy(isCompleted = isCompleted, completedAtEpochMs = timestamp)
                getOrCreateStudyTasksFlow(userId).value = list.toList()
            }
        }
        dao?.setStudyTaskCompleted(userId, taskId, isCompleted, timestamp)
    }

    // ── Chat Persistence ─────────────────────────────────────────────────────────

    suspend fun createChatSession(
        userId: String,
        domain: String,
        title: String,
        isAiEnabled: Boolean = true,
        trackId: String = "track_${userId}_default"
    ): ChatSession = withContext(ioDispatcher) {
        val sessionId = "session_${UUID.randomUUID()}"
        val session = ChatSession(
            sessionId = sessionId,
            userId = userId,
            domain = domain,
            title = title,
            isAiEnabled = isAiEnabled,
            trackId = trackId
        )
        val userSessions = chatSessionsByUser.getOrPut(userId) { mutableListOf() }
        userSessions.add(0, session)
        getOrCreateChatSessionFlow(userId).value = userSessions.toList()

        dao?.upsertChatSession(
            ChatSessionEntity(
                sessionId = session.sessionId,
                trackId = trackId,
                userId = session.userId,
                domain = session.domain,
                title = session.title,
                isAiEnabled = session.isAiEnabled,
                createdAtEpochMs = session.createdAtEpochMs,
                updatedAtEpochMs = session.updatedAtEpochMs
            )
        )
        session
    }

    suspend fun getChatSessions(userId: String): List<ChatSession> = withContext(ioDispatcher) {
        chatSessionsByUser[userId]?.let { return@withContext it }
        val entities = dao?.getChatSessions(userId)
        if (!entities.isNullOrEmpty()) {
            val sessions = entities.map {
                ChatSession(
                    sessionId = it.sessionId,
                    userId = it.userId,
                    domain = it.domain,
                    title = it.title,
                    isAiEnabled = it.isAiEnabled,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    trackId = it.trackId
                )
            }
            chatSessionsByUser[userId] = sessions.toMutableList()
            getOrCreateChatSessionFlow(userId).value = sessions
            sessions
        } else emptyList()
    }

    suspend fun getChatSessionsForTrack(trackId: String): List<ChatSession> = withContext(ioDispatcher) {
        val entities = dao?.getChatSessionsForTrack(trackId)
        if (entities != null) {
            entities.map {
                ChatSession(
                    sessionId = it.sessionId,
                    userId = it.userId,
                    domain = it.domain,
                    title = it.title,
                    isAiEnabled = it.isAiEnabled,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    trackId = it.trackId
                )
            }
        } else {
            chatSessionsByUser.values.flatten().filter { it.trackId == trackId }
        }
    }

    private fun getOrCreateChatSessionFlow(userId: String): MutableStateFlow<List<ChatSession>> {
        return chatSessionFlows.getOrPut(userId) { MutableStateFlow(chatSessionsByUser[userId] ?: emptyList()) }
    }

    suspend fun saveChatMessage(message: ChatMessage) = withContext(ioDispatcher) {
        val list = chatMessagesBySession.getOrPut(message.sessionId) {
            val fromDb = dao?.getMessagesForSession(message.sessionId)?.map { mapEntityToChatMessage(it) }
            fromDb?.toMutableList() ?: mutableListOf()
        }
        list.add(message)
        getOrCreateMessageFlow(message.sessionId).value = list.toList()

        dao?.let {
            val entity = ChatMessageEntity(
                messageId = message.messageId,
                sessionId = message.sessionId,
                userId = message.userId,
                senderRole = message.senderRole,
                text = message.text,
                isAiGenerated = message.isAiGenerated,
                promptTokensEstimate = message.promptTokensEstimate,
                generatedTokens = message.generatedTokens,
                ttftMs = message.ttftMs,
                tokensPerSec = message.tokensPerSec,
                timestampEpochMs = message.timestampEpochMs
            )
            it.insertMessageAndUpdateSession(entity)
        }
    }

    suspend fun getMessagesForSession(sessionId: String): List<ChatMessage> = withContext(ioDispatcher) {
        chatMessagesBySession[sessionId]?.let { return@withContext it }
        val entities = dao?.getMessagesForSession(sessionId)
        if (!entities.isNullOrEmpty()) {
            val messages = entities.map { mapEntityToChatMessage(it) }
            chatMessagesBySession[sessionId] = messages.toMutableList()
            getOrCreateMessageFlow(sessionId).value = messages
            messages
        } else emptyList()
    }

    private fun mapEntityToChatMessage(entity: ChatMessageEntity): ChatMessage {
        return ChatMessage(
            messageId = entity.messageId,
            sessionId = entity.sessionId,
            userId = entity.userId,
            senderRole = entity.senderRole,
            text = entity.text,
            isAiGenerated = entity.isAiGenerated,
            promptTokensEstimate = entity.promptTokensEstimate,
            generatedTokens = entity.generatedTokens,
            ttftMs = entity.ttftMs,
            tokensPerSec = entity.tokensPerSec,
            timestampEpochMs = entity.timestampEpochMs
        )
    }

    fun observeMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        val localFlow = dao?.observeMessagesForSession(sessionId)?.map { entities ->
            entities.map {
                ChatMessage(
                    messageId = it.messageId,
                    sessionId = it.sessionId,
                    userId = it.userId,
                    senderRole = it.senderRole,
                    text = it.text,
                    isAiGenerated = it.isAiGenerated,
                    promptTokensEstimate = it.promptTokensEstimate,
                    generatedTokens = it.generatedTokens,
                    ttftMs = it.ttftMs,
                    tokensPerSec = it.tokensPerSec,
                    timestampEpochMs = it.timestampEpochMs
                )
            }
        }
        return localFlow ?: getOrCreateMessageFlow(sessionId)
    }

    private fun getOrCreateMessageFlow(sessionId: String): MutableStateFlow<List<ChatMessage>> {
        return messageFlows.getOrPut(sessionId) { MutableStateFlow(chatMessagesBySession[sessionId] ?: emptyList()) }
    }

    suspend fun getAllContentPacks(): List<ContentPackEntity> = withContext(ioDispatcher) {
        val fromDao = dao?.getAllContentPacks()
        if (!fromDao.isNullOrEmpty()) fromDao else inMemoryContentPacks
    }

    suspend fun getContentPackById(packId: String): ContentPackEntity? = withContext(ioDispatcher) {
        dao?.getContentPackById(packId) ?: inMemoryContentPacks.firstOrNull { it.packId == packId }
    }

    suspend fun getNodesForPack(packId: String): List<CurriculumNodeEntity> = withContext(ioDispatcher) {
        dao?.getNodesForPack(packId) ?: emptyList()
    }

    suspend fun getResourcesForNode(nodeId: String): List<CurriculumResourceEntity> = withContext(ioDispatcher) {
        dao?.getResourcesForNode(nodeId) ?: emptyList()
    }

    data class ResourceResolution(
        val resource: CurriculumResourceEntity?,
        val isFallback: Boolean,
        val languageUsed: String,
        val fallbackNotice: String? = null
    )

    /**
     * Resolves curriculum resource obeying multilingual hierarchy:
     * 1. Learner's preferred language
     * 2. Pack's primary declared language
     * 3. Explanatory notice if fallback was utilized
     */
    suspend fun resolveResourceForNode(
        nodeId: String,
        preferredLanguageCode: String,
        fallbackLanguageCode: String = "en"
    ): ResourceResolution = withContext(ioDispatcher) {
        val preferredRes = dao?.getResourceForNode(nodeId, preferredLanguageCode)
        if (preferredRes != null) {
            return@withContext ResourceResolution(
                resource = preferredRes,
                isFallback = false,
                languageUsed = preferredLanguageCode
            )
        }

        // Check fallback language
        val fallbackRes = dao?.getResourceForNode(nodeId, fallbackLanguageCode)
            ?: dao?.getResourcesForNode(nodeId)?.firstOrNull()

        if (fallbackRes != null) {
            val langLabel = when (preferredLanguageCode) {
                "ta" -> "Tamil"
                "hi" -> "Hindi"
                "kn" -> "Kannada"
                "ml" -> "Malayalam (Unsupported)"
                else -> "${preferredLanguageCode.uppercase()} (Unsupported)"
            }
            val usedLabel = when (fallbackRes.languageCode) {
                "en" -> "English"
                "hi" -> "Hindi"
                "ta" -> "Tamil"
                "kn" -> "Kannada"
                else -> fallbackRes.languageCode.uppercase()
            }
            return@withContext ResourceResolution(
                resource = fallbackRes,
                isFallback = true,
                languageUsed = fallbackRes.languageCode,
                fallbackNotice = "Resource unavailable in $langLabel. Showing primary $usedLabel content."
            )
        }

        ResourceResolution(
            resource = null,
            isFallback = false,
            languageUsed = preferredLanguageCode,
            fallbackNotice = "No curriculum explanation resource found for this topic."
        )
    }

    /**
     * Audits an installed content pack's filesystem, Ed25519 signature, revocation, and hashes.
     */
    suspend fun auditInstalledPack(packId: String, baseStorageDir: File): Boolean = withContext(ioDispatcher) {
        val dbPack = dao?.getAllContentPacksSync()?.firstOrNull { it.packId == packId }
            ?: inMemoryContentPacks.firstOrNull { it.packId == packId }

        if (dbPack != null && (dbPack.isBuiltIn || BuiltInContentManifestRegistry.isRegisteredBuiltIn(packId))) {
            val nodes = dao?.getNodesForPack(packId)?.ifEmpty { null }
                ?: inMemoryCurriculumNodes.filter { it.packId == packId }.ifEmpty { null }
            val auditResult = BuiltInContentManifestRegistry.auditBuiltInPack(dbPack, nodes)
            if (!auditResult.isIntact) {
                quarantinePack(packId, auditResult.failureReason ?: "Built-in pack integrity check failed.")
                return@withContext false
            }
            liftQuarantine(packId)
            return@withContext true
        }

        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = dao)
        val isIntact = installer.auditInstalledPackIntegrity(packId)
        if (!isIntact) {
            quarantinePack(packId, "Cryptographic signature or payload integrity audit failed.")
            return@withContext false
        }
        liftQuarantine(packId)
        return@withContext true
    }

    /**
     * Streams and validates a content pack from an Android SAF content:// URI.
     */
    suspend fun importContentPackFromUri(
        context: Context,
        uri: Uri,
        baseStorageDir: File,
        onProgress: ((stage: String, percent: Int) -> Unit)? = null
    ): ContentPackDocumentImporter.ImportResult = withContext(ioDispatcher) {
        val importer = ContentPackDocumentImporter(baseStorageDir = baseStorageDir, dao = dao)
        importer.importFromUri(context, uri, onProgress = onProgress)
    }

    suspend fun installContentPack(packFile: File, baseStorageDir: File): ContentPackInstaller.InstallResult = withContext(ioDispatcher) {
        val installer = ContentPackInstaller(baseStorageDir = baseStorageDir, dao = dao)
        installer.installPack(archiveFile = packFile, trustedPublicKey = null)
    }

    /**
     * Retrieves all learner tracks associated with curriculum nodes belonging to [packId].
     */
    suspend fun getAssignedTracksForPack(userId: String, packId: String): List<LearningTrack> = withContext(ioDispatcher) {
        val nodeIds = dao?.getNodesForPack(packId)?.map { it.nodeId }?.toSet() ?: emptySet()
        if (nodeIds.isEmpty()) return@withContext emptyList()

        val userTracks = getTracksForUser(userId)
        userTracks.filter { track ->
            track.targetCurriculumNodeId != null && nodeIds.contains(track.targetCurriculumNodeId)
        }
    }

    /**
     * Assigns a content pack's root node to a specific learner track.
     */
    suspend fun assignPackToTrack(userId: String, trackId: String, packId: String) = withContext(ioDispatcher) {
        if (isPackQuarantined(packId)) {
            throw IllegalStateException("Quarantined content pack '$packId' cannot be assigned to track '$trackId'.")
        }
        val packNodes = dao?.getNodesForPack(packId) ?: emptyList()
        val rootNode = packNodes.firstOrNull { it.parentNodeId == null } ?: packNodes.firstOrNull()
        if (rootNode != null) {
            assignCurriculumToTrack(userId, trackId, rootNode.nodeId)
        }
    }

    /**
     * Assigns a curriculum node to a specific learner track.
     */
    suspend fun assignCurriculumToTrack(userId: String, trackId: String, curriculumNodeId: String) = withContext(ioDispatcher) {
        val nodePackId = dao?.getNodeById(curriculumNodeId)?.packId
            ?: inMemoryCurriculumNodes.firstOrNull { it.nodeId == curriculumNodeId }?.packId
        if (nodePackId != null && isPackQuarantined(nodePackId)) {
            throw IllegalStateException("Node '$curriculumNodeId' belongs to quarantined content pack '$nodePackId'. Assignment rejected.")
        }
        val tracks = getTracksForUser(userId)
        val track = tracks.firstOrNull { it.trackId == trackId }
        if (track != null) {
            val updated = track.copy(targetCurriculumNodeId = curriculumNodeId)
            val entity = LearnerTrackEntity(
                trackId = updated.trackId,
                userId = updated.userId,
                title = updated.title,
                learningMode = updated.learningMode.name,
                targetCurriculumNodeId = updated.targetCurriculumNodeId,
                targetExamName = updated.targetExamName,
                targetExamDateEpochMs = updated.targetExamDateEpochMs,
                dailyCommitmentMinutes = updated.dailyCommitmentMinutes,
                isTrackActive = updated.isTrackActive,
                createdAtEpochMs = updated.createdAtEpochMs,
                lastAccessedEpochMs = updated.lastAccessedEpochMs
            )
            dao?.upsertTrack(entity)
            val list = tracksByUser.getOrPut(userId) { mutableListOf() }
            val index = list.indexOfFirst { it.trackId == trackId }
            if (index != -1) {
                list[index] = updated
            } else {
                list.add(updated)
            }
            getOrCreateTracksFlow(userId).value = list.toList()
            if (updated.isTrackActive) {
                getOrCreateActiveTrackFlow(userId).value = updated
            }
        }
    }

    /**
     * Unassigns a curriculum node from a specific learner track while preserving progress.
     */
    suspend fun unassignCurriculumFromTrack(userId: String, trackId: String) = withContext(ioDispatcher) {
        val tracks = getTracksForUser(userId)
        val track = tracks.firstOrNull { it.trackId == trackId }
        if (track != null) {
            val updated = track.copy(targetCurriculumNodeId = null)
            val entity = LearnerTrackEntity(
                trackId = updated.trackId,
                userId = updated.userId,
                title = updated.title,
                learningMode = updated.learningMode.name,
                targetCurriculumNodeId = null,
                targetExamName = updated.targetExamName,
                targetExamDateEpochMs = updated.targetExamDateEpochMs,
                dailyCommitmentMinutes = updated.dailyCommitmentMinutes,
                isTrackActive = updated.isTrackActive,
                createdAtEpochMs = updated.createdAtEpochMs,
                lastAccessedEpochMs = updated.lastAccessedEpochMs
            )
            dao?.upsertTrack(entity)
            val list = tracksByUser.getOrPut(userId) { mutableListOf() }
            val index = list.indexOfFirst { it.trackId == trackId }
            if (index != -1) {
                list[index] = updated
            } else {
                list.add(updated)
            }
            getOrCreateTracksFlow(userId).value = list.toList()
            if (updated.isTrackActive) {
                getOrCreateActiveTrackFlow(userId).value = updated
            }
        }
    }

    suspend fun uninstallContentPack(
        packId: String,
        baseStorageDir: File,
        forceIfAssigned: Boolean = false,
        currentUserId: String = "default_learner"
    ): Boolean = withContext(ioDispatcher) {
        if (!forceIfAssigned) {
            val assigned = getAssignedTracksForPack(currentUserId, packId)
            if (assigned.isNotEmpty()) {
                // Return false if uninstallation blocked by active track assignments
                return@withContext false
            }
        }
        dao?.deleteContentPackSync(packId)
        val installedDir = File(baseStorageDir, "installed/$packId")
        if (installedDir.exists()) {
            installedDir.deleteRecursively()
        }
        true
    }

    // ── Gate 6.4 Universal Learning Journeys & Progress Tracking ────────────────

    suspend fun getLessonModule(nodeId: String, preferredLanguage: String = "en"): com.learnmate.ai.domain.LessonModule = withContext(ioDispatcher) {
        com.learnmate.ai.data.content.StarterCurriculumCatalog.getLessonModule(nodeId, preferredLanguage)
    }

    suspend fun getTopicProgress(trackId: String, userId: String, nodeId: String): com.learnmate.ai.domain.TrackTopicProgress = withContext(ioDispatcher) {
        val cacheKey = "${trackId}_${userId}_${nodeId}"
        inMemoryTopicProgress[cacheKey]?.let { return@withContext it }

        val task = dao?.getStudyTaskForTopic(trackId, nodeId)
        if (task != null) {
            val status = when {
                task.isCompleted -> com.learnmate.ai.domain.LearningProgressStatus.COMPLETED
                task.notes.contains("NEEDS_REVISION", ignoreCase = true) -> com.learnmate.ai.domain.LearningProgressStatus.NEEDS_REVISION
                task.notes.contains("IN_PROGRESS", ignoreCase = true) -> com.learnmate.ai.domain.LearningProgressStatus.IN_PROGRESS
                else -> com.learnmate.ai.domain.LearningProgressStatus.NOT_STARTED
            }
            val score = task.notes.substringAfter("score:", "").substringBefore("|").toDoubleOrNull()
            val progress = com.learnmate.ai.domain.TrackTopicProgress(
                trackId = trackId,
                userId = userId,
                nodeId = nodeId,
                status = status,
                scorePercent = score,
                completedAtEpochMs = task.completedAtEpochMs,
                lastAccessedEpochMs = System.currentTimeMillis()
            )
            inMemoryTopicProgress[cacheKey] = progress
            progress
        } else {
            com.learnmate.ai.domain.TrackTopicProgress(
                trackId = trackId,
                userId = userId,
                nodeId = nodeId,
                status = com.learnmate.ai.domain.LearningProgressStatus.NOT_STARTED
            )
        }
    }

    suspend fun updateTopicProgress(
        trackId: String,
        userId: String,
        nodeId: String,
        status: com.learnmate.ai.domain.LearningProgressStatus,
        scorePercent: Double? = null,
        topicTitle: String = "Curriculum Module"
    ): com.learnmate.ai.domain.TrackTopicProgress = withContext(ioDispatcher) {
        val taskId = "task_${trackId}_${nodeId}"
        val isCompleted = status == com.learnmate.ai.domain.LearningProgressStatus.COMPLETED
        val timestamp = if (isCompleted) System.currentTimeMillis() else null
        val scoreStr = if (scorePercent != null) "score:$scorePercent|" else ""
        val notes = "${status.name}|$scoreStr"

        val entity = StudyTaskEntity(
            taskId = taskId,
            trackId = trackId,
            userId = userId,
            roadmapStepId = nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = topicTitle,
            topic = nodeId,
            durationMinutes = 35,
            isCompleted = isCompleted,
            completedAtEpochMs = timestamp,
            notes = notes
        )
        dao?.upsertStudyTask(entity)

        val progress = com.learnmate.ai.domain.TrackTopicProgress(
            trackId = trackId,
            userId = userId,
            nodeId = nodeId,
            status = status,
            scorePercent = scorePercent,
            completedAtEpochMs = timestamp,
            lastAccessedEpochMs = System.currentTimeMillis()
        )
        inMemoryTopicProgress["${trackId}_${userId}_${nodeId}"] = progress
        progress
    }

    // ── Gate 6.5 Adaptive Assessment & Personalised Mastery ─────────────────────

    suspend fun recordDiagnosticAttempt(
        trackId: String,
        userId: String,
        nodeId: String,
        correctCount: Int,
        totalQuestions: Int,
        topicTitle: String = "Curriculum Module"
    ): TopicMasteryResult = withContext(ioDispatcher) {
        val scorePercent = if (totalQuestions > 0) (correctCount.toDouble() / totalQuestions.toDouble()) * 100.0 else 0.0
        val roundedScore = kotlin.math.round(scorePercent * 10.0) / 10.0
        val currentProgress = getTopicProgress(trackId, userId, nodeId)

        // Read previous attempts count from existing task notes if present
        val task = dao?.getStudyTaskForTopic(trackId, nodeId)
        val prevAttempts = task?.notes?.substringAfter("attempts:", "")?.substringBefore("|")?.toIntOrNull()
            ?: if (currentProgress.scorePercent != null) 1 else 0
        val newAttempts = prevAttempts + 1

        val newStatus = when {
            currentProgress.status == com.learnmate.ai.domain.LearningProgressStatus.COMPLETED -> com.learnmate.ai.domain.LearningProgressStatus.COMPLETED
            roundedScore >= 70.0 -> com.learnmate.ai.domain.LearningProgressStatus.IN_PROGRESS
            roundedScore < 50.0 -> com.learnmate.ai.domain.LearningProgressStatus.NEEDS_REVISION
            else -> currentProgress.status
        }

        val taskId = "task_${trackId}_${nodeId}"
        val isCompleted = newStatus == com.learnmate.ai.domain.LearningProgressStatus.COMPLETED
        val timestamp = System.currentTimeMillis()
        val notes = "${newStatus.name}|score:$roundedScore|attempts:$newAttempts|correct:$correctCount|total:$totalQuestions|epoch:$timestamp"

        val entity = StudyTaskEntity(
            taskId = taskId,
            trackId = trackId,
            userId = userId,
            roadmapStepId = nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = topicTitle,
            topic = nodeId,
            durationMinutes = 35,
            isCompleted = isCompleted,
            completedAtEpochMs = if (isCompleted) timestamp else null,
            notes = notes
        )
        dao?.upsertStudyTask(entity)

        val updatedProgress = com.learnmate.ai.domain.TrackTopicProgress(
            trackId = trackId,
            userId = userId,
            nodeId = nodeId,
            status = newStatus,
            scorePercent = roundedScore,
            completedAtEpochMs = if (isCompleted) timestamp else null,
            lastAccessedEpochMs = timestamp
        )
        inMemoryTopicProgress["${trackId}_${userId}_${nodeId}"] = updatedProgress

        getTopicMastery(trackId, userId, nodeId)
    }

    suspend fun getTopicMastery(
        trackId: String,
        userId: String,
        nodeId: String
    ): TopicMasteryResult = withContext(ioDispatcher) {
        val progress = getTopicProgress(trackId, userId, nodeId)
        val task = dao?.getStudyTaskForTopic(trackId, nodeId)

        val attempts = task?.notes?.substringAfter("attempts:", "")?.substringBefore("|")?.toIntOrNull()
            ?: if (progress.scorePercent != null) 1 else 0
        val correct = task?.notes?.substringAfter("correct:", "")?.substringBefore("|")?.toIntOrNull()
            ?: if (progress.scorePercent != null) ((progress.scorePercent!! / 100.0) * 3).toInt() else 0
        val total = task?.notes?.substringAfter("total:", "")?.substringBefore("|")?.toIntOrNull()
            ?: if (progress.scorePercent != null) 3 else 0
        val lastEpoch = task?.notes?.substringAfter("epoch:", "")?.substringBefore("|")?.toLongOrNull()
            ?: task?.completedAtEpochMs

        // Resolve title and discipline from StarterCurriculumCatalog
        val module = com.learnmate.ai.data.content.StarterCurriculumCatalog.getLessonModule(nodeId, "en")
        val topicTitle = module.topicTitle
        val discipline = module.discipline

        // Resolve direct mandatory prerequisites
        val allPrereqs = (dao?.getPrerequisitesForNode(nodeId) ?: inMemoryPrerequisites.filter { it.targetNodeId == nodeId })
            .ifEmpty {
                CurriculumEngine.getUniversalBaselinePrerequisites().filter { it.targetNodeId == nodeId }
            }

        val mandatoryPrereqIds = allPrereqs
            .filter { it.requirementType.equals("MANDATORY", ignoreCase = true) }
            .map { it.requiredNodeId }

        // Resolve completed nodes in this track
        val allTrackTasks = dao?.getStudyTasksForTrack(trackId)
            ?: inMemoryTopicProgress.values.filter { it.trackId == trackId && it.status == com.learnmate.ai.domain.LearningProgressStatus.COMPLETED }
                .map { StudyTaskEntity("task_${it.trackId}_${it.nodeId}", it.trackId, it.userId, it.nodeId, 1, 1, "", it.nodeId, 30, true, 0L, "") }
        val completedNodeIds = allTrackTasks.filter { it.isCompleted }.map { it.topic }.toSet()

        KnowledgeMasteryEngine.calculateTopicMastery(
            nodeId = nodeId,
            topicTitle = topicTitle,
            discipline = discipline,
            progressStatus = progress.status,
            attemptsCount = attempts,
            correctCount = correct,
            totalQuestions = total,
            directMandatoryPrerequisites = mandatoryPrereqIds,
            completedNodeIds = completedNodeIds,
            lastAssessedEpochMs = lastEpoch,
            availableQuestionsCount = module.knowledgeChecks.size
        )
    }

    suspend fun getAllTopicMasteries(
        trackId: String,
        userId: String
    ): List<TopicMasteryResult> = withContext(ioDispatcher) {
        val supportedIds = com.learnmate.ai.data.content.StarterCurriculumCatalog.getSupportedNodeIds()
        supportedIds.map { nodeId ->
            getTopicMastery(trackId, userId, nodeId)
        }
    }

    suspend fun getPersonalisedLearningPath(
        trackId: String,
        userId: String
    ): List<RecommendedTopicItem> = withContext(ioDispatcher) {
        val track = getActiveTrackForUser(userId) ?: tracksByUser[userId]?.firstOrNull { it.trackId == trackId } ?: LearningTrack(
            trackId = trackId,
            userId = userId,
            title = "Universal Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch01"
        )
        val allNodes = (dao?.getAllCurriculumNodes() ?: inMemoryCurriculumNodes).ifEmpty {
            CurriculumEngine.getUniversalBaselineNodes()
        }
        val allPrereqs = (dao?.getAllPrerequisites() ?: inMemoryPrerequisites).ifEmpty {
            CurriculumEngine.getUniversalBaselinePrerequisites()
        }
        val masteries = getAllTopicMasteries(trackId, userId)

        PersonalisedLearningPathEngine.generateLearningPath(
            track = track,
            nodes = allNodes,
            prerequisites = allPrereqs,
            topicMasteries = masteries
        )
    }

    suspend fun getRevisionQueue(
        trackId: String,
        userId: String
    ): List<RevisionTopicItem> = withContext(ioDispatcher) {
        val masteries = getAllTopicMasteries(trackId, userId)
        PersonalisedLearningPathEngine.generateRevisionQueue(masteries)
    }

    suspend fun recordRevisionCompletion(
        trackId: String,
        userId: String,
        nodeId: String
    ): TopicMasteryResult = withContext(ioDispatcher) {
        val currentProgress = getTopicProgress(trackId, userId, nodeId)
        updateTopicProgress(
            trackId = trackId,
            userId = userId,
            nodeId = nodeId,
            status = com.learnmate.ai.domain.LearningProgressStatus.COMPLETED,
            scorePercent = currentProgress.scorePercent ?: 85.0
        )
        getTopicMastery(trackId, userId, nodeId)
    }

    // ── Gate 6.6 Spaced Repetition & Learning Progress Intelligence ─────────────

    suspend fun recordAssessmentAttempt(
        attempt: AssessmentAttemptRecord
    ): TopicMasteryResult = withContext(ioDispatcher) {
        val cacheKey = "${attempt.trackId}_${attempt.userId}"
        val list = inMemoryAssessmentAttempts.getOrPut(cacheKey) { mutableListOf() }
        list.add(attempt)

        val taskEntity = StudyTaskEntity(
            taskId = attempt.attemptId,
            trackId = attempt.trackId,
            userId = attempt.userId,
            roadmapStepId = attempt.nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = "Assessment Attempt: ${attempt.topicTitle}",
            topic = attempt.nodeId,
            durationMinutes = maxOf(10, attempt.durationSeconds / 60),
            isCompleted = true,
            completedAtEpochMs = attempt.attemptEpochMs,
            notes = gson.toJson(attempt)
        )
        dao?.upsertStudyTask(taskEntity)

        // Also update standard topic progress and mastery
        recordDiagnosticAttempt(
            trackId = attempt.trackId,
            userId = attempt.userId,
            nodeId = attempt.nodeId,
            correctCount = attempt.correctCount,
            totalQuestions = attempt.totalQuestions,
            topicTitle = attempt.topicTitle
        )
    }

    suspend fun getAssessmentAttemptsForTopic(
        trackId: String,
        userId: String,
        nodeId: String
    ): List<AssessmentAttemptRecord> = withContext(ioDispatcher) {
        val cacheKey = "${trackId}_${userId}"
        val cached = inMemoryAssessmentAttempts[cacheKey]?.filter { it.nodeId == nodeId }
        if (!cached.isNullOrEmpty()) return@withContext cached

        val dbTasks = dao?.getAssessmentAttemptTasksForNode(trackId, nodeId) ?: emptyList()
        val attempts = dbTasks.mapNotNull { task ->
            try {
                gson.fromJson(task.notes, AssessmentAttemptRecord::class.java)
            } catch (_: Exception) {
                null
            }
        }
        if (attempts.isNotEmpty()) {
            val fullList = inMemoryAssessmentAttempts.getOrPut(cacheKey) { mutableListOf() }
            fullList.removeAll { it.nodeId == nodeId }
            fullList.addAll(attempts)
        }
        attempts
    }

    suspend fun getAllAssessmentAttemptsForTrack(
        trackId: String,
        userId: String
    ): List<AssessmentAttemptRecord> = withContext(ioDispatcher) {
        val cacheKey = "${trackId}_${userId}"
        val cached = inMemoryAssessmentAttempts[cacheKey]
        if (!cached.isNullOrEmpty()) return@withContext cached.sortedBy { it.attemptEpochMs }

        val dbTasks = dao?.getAssessmentAttemptTasksForTrack(trackId) ?: emptyList()
        val attempts = dbTasks.mapNotNull { task ->
            try {
                gson.fromJson(task.notes, AssessmentAttemptRecord::class.java)
            } catch (_: Exception) {
                null
            }
        }
        if (attempts.isNotEmpty()) {
            inMemoryAssessmentAttempts[cacheKey] = attempts.toMutableList()
        }
        attempts.sortedBy { it.attemptEpochMs }
    }

    suspend fun getAssessmentTrends(
        trackId: String,
        userId: String
    ): List<AssessmentTrendPoint> = withContext(ioDispatcher) {
        val attempts = getAllAssessmentAttemptsForTrack(trackId, userId)
        AssessmentReliabilityEngine.generateAssessmentTrends(attempts)
    }

    suspend fun getQuestionBankCoverage(nodeId: String): QuestionBankCoverageReport = withContext(ioDispatcher) {
        val questions = com.learnmate.ai.data.content.StarterCurriculumCatalog.getQuestionBankForNode(nodeId, "en")
        val module = com.learnmate.ai.data.content.StarterCurriculumCatalog.getLessonModule(nodeId, "en")
        AssessmentReliabilityEngine.evaluateQuestionBankCoverage(
            nodeId = nodeId,
            topicTitle = module.topicTitle,
            availableQuestionsCount = questions.size
        )
    }

    suspend fun initializeSpacedRepetitionForTopic(
        trackId: String,
        userId: String,
        nodeId: String,
        topicTitle: String,
        discipline: String,
        completionEpochMs: Long = System.currentTimeMillis()
    ): SpacedRepetitionItem = withContext(ioDispatcher) {
        val item = SpacedRepetitionEngine.scheduleInitialRevision(
            trackId = trackId,
            userId = userId,
            nodeId = nodeId,
            topicTitle = topicTitle,
            discipline = discipline,
            completionEpochMs = completionEpochMs
        )

        val cacheKey = "${trackId}_${userId}"
        val trackMap = inMemorySrsItems.getOrPut(cacheKey) { mutableMapOf() }
        trackMap[nodeId] = item

        val task = StudyTaskEntity(
            taskId = item.repetitionId,
            trackId = trackId,
            userId = userId,
            roadmapStepId = nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = "SRS: $topicTitle",
            topic = nodeId,
            durationMinutes = 15,
            isCompleted = false,
            completedAtEpochMs = completionEpochMs,
            notes = gson.toJson(item)
        )
        dao?.upsertStudyTask(task)
        item
    }

    suspend fun getSpacedRepetitionQueue(
        trackId: String,
        userId: String
    ): List<SpacedRepetitionItem> = withContext(ioDispatcher) {
        val cacheKey = "${trackId}_${userId}"
        val cachedMap = inMemorySrsItems[cacheKey]
        val currentEpoch = System.currentTimeMillis()

        if (!cachedMap.isNullOrEmpty()) {
            return@withContext cachedMap.values.map {
                SpacedRepetitionEngine.evaluateOverdueStatus(it, currentEpoch)
            }.sortedWith(
                compareByDescending<SpacedRepetitionItem> { it.isOverdue }
                    .thenBy { it.dueEpochMs }
            )
        }

        val dbTasks = dao?.getSpacedRepetitionTasksForTrack(trackId) ?: emptyList()
        val loadedItems = dbTasks.mapNotNull { task ->
            try {
                gson.fromJson(task.notes, SpacedRepetitionItem::class.java)
            } catch (_: Exception) {
                null
            }
        }

        val trackMap = inMemorySrsItems.getOrPut(cacheKey) { mutableMapOf() }
        loadedItems.forEach { trackMap[it.nodeId] = it }

        // If completely empty, auto-seed SRS items for any completed curriculum nodes
        if (trackMap.isEmpty()) {
            val masteries = getAllTopicMasteries(trackId, userId)
            for (m in masteries) {
                if (m.progressStatus == com.learnmate.ai.domain.LearningProgressStatus.COMPLETED || m.masteryLevel == MasteryLevel.MASTERED) {
                    val srs = SpacedRepetitionEngine.scheduleInitialRevision(
                        trackId = trackId,
                        userId = userId,
                        nodeId = m.nodeId,
                        topicTitle = m.topicTitle,
                        discipline = m.discipline,
                        completionEpochMs = m.lastAssessedEpochMs ?: currentEpoch
                    )
                    trackMap[m.nodeId] = srs
                    dao?.upsertStudyTask(
                        StudyTaskEntity(
                            taskId = srs.repetitionId,
                            trackId = trackId,
                            userId = userId,
                            roadmapStepId = m.nodeId,
                            dayIndex = 1,
                            weekIndex = 1,
                            title = "SRS: ${m.topicTitle}",
                            topic = m.nodeId,
                            durationMinutes = 15,
                            isCompleted = false,
                            completedAtEpochMs = currentEpoch,
                            notes = gson.toJson(srs)
                        )
                    )
                }
            }
        }

        trackMap.values.map {
            SpacedRepetitionEngine.evaluateOverdueStatus(it, currentEpoch)
        }.sortedWith(
            compareByDescending<SpacedRepetitionItem> { it.isOverdue }
                .thenBy { it.dueEpochMs }
        )
    }

    suspend fun recordSpacedRepetitionReview(
        trackId: String,
        userId: String,
        nodeId: String,
        reviewScorePercent: Double
    ): SpacedRepetitionItem = withContext(ioDispatcher) {
        val currentEpoch = System.currentTimeMillis()
        val existingQueue = getSpacedRepetitionQueue(trackId, userId)
        val existingItem = existingQueue.firstOrNull { it.nodeId == nodeId } ?: run {
            val module = com.learnmate.ai.data.content.StarterCurriculumCatalog.getLessonModule(nodeId, "en")
            initializeSpacedRepetitionForTopic(
                trackId = trackId,
                userId = userId,
                nodeId = nodeId,
                topicTitle = module.topicTitle,
                discipline = module.discipline,
                completionEpochMs = currentEpoch
            )
        }

        val updatedItem = SpacedRepetitionEngine.calculateNextSchedule(
            item = existingItem,
            reviewScorePercent = reviewScorePercent,
            reviewEpochMs = currentEpoch
        )

        val cacheKey = "${trackId}_${userId}"
        inMemorySrsItems.getOrPut(cacheKey) { mutableMapOf() }[nodeId] = updatedItem

        val task = StudyTaskEntity(
            taskId = updatedItem.repetitionId,
            trackId = trackId,
            userId = userId,
            roadmapStepId = nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = "SRS: ${updatedItem.topicTitle}",
            topic = nodeId,
            durationMinutes = 15,
            isCompleted = false,
            completedAtEpochMs = currentEpoch,
            notes = gson.toJson(updatedItem)
        )
        dao?.upsertStudyTask(task)
        updatedItem
    }

    suspend fun scheduleManualRevision(
        trackId: String,
        userId: String,
        nodeId: String,
        targetEpochMs: Long
    ): SpacedRepetitionItem = withContext(ioDispatcher) {
        val currentEpoch = System.currentTimeMillis()
        val existingQueue = getSpacedRepetitionQueue(trackId, userId)
        val existingItem = existingQueue.firstOrNull { it.nodeId == nodeId } ?: run {
            val module = com.learnmate.ai.data.content.StarterCurriculumCatalog.getLessonModule(nodeId, "en")
            initializeSpacedRepetitionForTopic(
                trackId = trackId,
                userId = userId,
                nodeId = nodeId,
                topicTitle = module.topicTitle,
                discipline = module.discipline,
                completionEpochMs = currentEpoch
            )
        }

        val updatedItem = SpacedRepetitionEngine.scheduleManualRevision(
            item = existingItem,
            targetEpochMs = targetEpochMs,
            currentEpochMs = currentEpoch
        )

        val cacheKey = "${trackId}_${userId}"
        inMemorySrsItems.getOrPut(cacheKey) { mutableMapOf() }[nodeId] = updatedItem

        val task = StudyTaskEntity(
            taskId = updatedItem.repetitionId,
            trackId = trackId,
            userId = userId,
            roadmapStepId = nodeId,
            dayIndex = 1,
            weekIndex = 1,
            title = "SRS: ${updatedItem.topicTitle}",
            topic = nodeId,
            durationMinutes = 15,
            isCompleted = false,
            completedAtEpochMs = currentEpoch,
            notes = gson.toJson(updatedItem)
        )
        dao?.upsertStudyTask(task)
        updatedItem
    }

    suspend fun getLearnerAnalytics(
        trackId: String,
        userId: String
    ): LearnerAnalyticsReport = withContext(ioDispatcher) {
        val attempts = getAllAssessmentAttemptsForTrack(trackId, userId)
        val srsItems = getSpacedRepetitionQueue(trackId, userId)
        val masteries = getAllTopicMasteries(trackId, userId)
        val allTasks = dao?.getStudyTasksForTrack(trackId) ?: emptyList()
        val completedEpochs = allTasks.filter { it.isCompleted }.mapNotNull { it.completedAtEpochMs }

        LearningAnalyticsEngine.computeAnalytics(
            trackId = trackId,
            userId = userId,
            attempts = attempts,
            srsItems = srsItems,
            masteries = masteries,
            completedTaskEpochs = completedEpochs
        )
    }

    suspend fun getDailyStudyPlan(
        trackId: String,
        userId: String,
        targetDailyMinutes: Int? = null
    ): DailyStudyPlan = withContext(ioDispatcher) {
        val track = getActiveTrackForUser(userId) ?: tracksByUser[userId]?.firstOrNull { it.trackId == trackId } ?: LearningTrack(
            trackId = trackId,
            userId = userId,
            title = "Universal Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch01"
        )
        val minutes = targetDailyMinutes ?: track.dailyCommitmentMinutes
        val masteries = getAllTopicMasteries(trackId, userId)
        val srsItems = getSpacedRepetitionQueue(trackId, userId)
        val allNodes = (dao?.getAllCurriculumNodes() ?: inMemoryCurriculumNodes).ifEmpty {
            CurriculumEngine.getUniversalBaselineNodes()
        }
        val allPrereqs = (dao?.getAllPrerequisites() ?: inMemoryPrerequisites).ifEmpty {
            CurriculumEngine.getUniversalBaselinePrerequisites()
        }

        PersonalisedStudyPlannerEngine.generateDailyPlan(
            track = track,
            targetDailyMinutes = minutes,
            masteries = masteries,
            srsItems = srsItems,
            nodes = allNodes,
            prerequisites = allPrereqs
        )
    }

    suspend fun getWeeklyStudyPlan(
        trackId: String,
        userId: String,
        targetDailyMinutes: Int? = null
    ): WeeklyStudyPlan = withContext(ioDispatcher) {
        val track = getActiveTrackForUser(userId) ?: tracksByUser[userId]?.firstOrNull { it.trackId == trackId } ?: LearningTrack(
            trackId = trackId,
            userId = userId,
            title = "Universal Track",
            learningMode = LearningMode.ACADEMIC,
            targetCurriculumNodeId = "cbse_sci10_ch01"
        )
        val minutes = targetDailyMinutes ?: track.dailyCommitmentMinutes
        val masteries = getAllTopicMasteries(trackId, userId)
        val srsItems = getSpacedRepetitionQueue(trackId, userId)
        val allNodes = (dao?.getAllCurriculumNodes() ?: inMemoryCurriculumNodes).ifEmpty {
            CurriculumEngine.getUniversalBaselineNodes()
        }
        val allPrereqs = (dao?.getAllPrerequisites() ?: inMemoryPrerequisites).ifEmpty {
            CurriculumEngine.getUniversalBaselinePrerequisites()
        }

        PersonalisedStudyPlannerEngine.generateWeeklyPlan(
            track = track,
            targetDailyMinutes = minutes,
            masteries = masteries,
            srsItems = srsItems,
            nodes = allNodes,
            prerequisites = allPrereqs
        )
    }

    /**
     * Gate 6.7: Generates an accredited Learning Outcomes Coverage Report for the track.
     * Evaluates objective-level mastery and identifies learning objectives needing practice (< 70%).
     */
    suspend fun getCurriculumCoverageReport(
        trackId: String,
        userId: String
    ): com.learnmate.ai.domain.outcomes.CurriculumCoverageReport = withContext(ioDispatcher) {
        val attempts = getAllAssessmentAttemptsForTrack(trackId, userId)
        com.learnmate.ai.domain.outcomes.LearningOutcomeEngine.calculateObjectivePerformances(attempts)
    }

    /**
     * Gate 6.7: Returns the list of learning objectives requiring practice (< 70% accuracy).
     */
    suspend fun getWeakLearningObjectives(
        trackId: String,
        userId: String
    ): List<com.learnmate.ai.domain.outcomes.ObjectivePerformance> = withContext(ioDispatcher) {
        val report = getCurriculumCoverageReport(trackId, userId)
        report.objectivePerformances.filter { it.requiresPractice }
    }

    // ── Gate 6.9: Lifecycle, Deletion, and Data Portability Primitives ─────────

    suspend fun saveTrack(track: LearningTrack) = withContext(ioDispatcher) {
        val entity = LearnerTrackEntity(
            trackId = track.trackId,
            userId = track.userId,
            title = track.title,
            learningMode = track.learningMode.name,
            targetCurriculumNodeId = track.targetCurriculumNodeId,
            targetExamName = track.targetExamName,
            targetExamDateEpochMs = track.targetExamDateEpochMs,
            dailyCommitmentMinutes = track.dailyCommitmentMinutes,
            isTrackActive = track.isTrackActive,
            createdAtEpochMs = track.createdAtEpochMs,
            lastAccessedEpochMs = track.lastAccessedEpochMs
        )
        dao?.upsertTrack(entity)

        val list = tracksByUser.getOrPut(track.userId) { mutableListOf() }
        list.removeAll { it.trackId == track.trackId }
        list.add(track)
        getOrCreateTracksFlow(track.userId).value = list.toList()
        if (track.isTrackActive) {
            getOrCreateActiveTrackFlow(track.userId).value = track
        }
    }

    suspend fun deleteLearningTrack(trackId: String, userId: String): Boolean = withContext(ioDispatcher) {
        // 1. Delete from Room database (SQLite cascades to study_tasks, chat_sessions, etc.)
        dao?.deleteTrack(trackId)

        // 2. Synchronize in-memory caches
        tracksByUser[userId]?.let { userTracks ->
            userTracks.removeAll { it.trackId == trackId }
            if (userTracks.isNotEmpty() && userTracks.none { it.isTrackActive }) {
                userTracks[0] = userTracks[0].copy(isTrackActive = true)
                dao?.activateTrack(userId, userTracks[0].trackId)
            }
            getOrCreateTracksFlow(userId).value = userTracks.toList()
            getOrCreateActiveTrackFlow(userId).value = userTracks.firstOrNull { it.isTrackActive }
        }

        // Clean in-memory study tasks
        studyTasksByUser[userId]?.removeAll { it.id.contains(trackId) }
        studyTasksByUser[userId]?.let { getOrCreateStudyTasksFlow(userId).value = it.toList() }

        roadmapsByUser.remove(trackId)
        skillGapsByUser.remove(trackId)
        inMemoryAssessmentAttempts.remove("${trackId}_${userId}")
        inMemorySrsItems.remove("${trackId}_${userId}")

        val keysToRemove = inMemoryTopicProgress.keys.filter { it.startsWith("${trackId}_") }
        keysToRemove.forEach { inMemoryTopicProgress.remove(it) }

        chatSessionsByUser[userId]?.let { sessions ->
            val trackSessions = sessions.filter { it.trackId == trackId }
            trackSessions.forEach { s ->
                chatMessagesBySession.remove(s.sessionId)
                dao?.deleteChatSession(s.sessionId)
            }
            sessions.removeAll { it.trackId == trackId }
            getOrCreateChatSessionFlow(userId).value = sessions.toList()
        }

        true
    }

    suspend fun deleteLearnerProfile(userId: String): Boolean = withContext(ioDispatcher) {
        // 1. Room database cascade deletion
        dao?.deleteProfile(userId)

        // 2. Memory cache cleanup
        profilesByUser.remove(userId)
        getOrCreateProfileFlow(userId).value = null

        val userTracks = tracksByUser[userId] ?: emptyList()
        for (track in userTracks) {
            inMemoryAssessmentAttempts.remove("${track.trackId}_$userId")
            inMemorySrsItems.remove("${track.trackId}_$userId")
            roadmapsByUser.remove(track.trackId)
            skillGapsByUser.remove(track.trackId)
            val keysToRemove = inMemoryTopicProgress.keys.filter { it.startsWith("${track.trackId}_") }
            keysToRemove.forEach { inMemoryTopicProgress.remove(it) }
        }

        tracksByUser.remove(userId)
        getOrCreateTracksFlow(userId).value = emptyList()
        getOrCreateActiveTrackFlow(userId).value = null

        roadmapsByUser.remove(userId)
        getOrCreateRoadmapFlow(userId).value = null

        skillGapsByUser.remove(userId)
        getOrCreateSkillGapFlow(userId).value = null

        studyTasksByUser.remove(userId)
        getOrCreateStudyTasksFlow(userId).value = emptyList()

        chatSessionsByUser[userId]?.let { sessions ->
            sessions.forEach { s -> chatMessagesBySession.remove(s.sessionId) }
        }
        chatSessionsByUser.remove(userId)
        getOrCreateChatSessionFlow(userId).value = emptyList()

        true
    }

    suspend fun getRoadmapForTrack(trackId: String): PersonalisedRoadmap? = withContext(ioDispatcher) {
        roadmapsByUser[trackId]?.let { return@withContext it }
        val entity = dao?.getRoadmapForTrack(trackId)
        if (entity != null) {
            val model = DeterministicRoadmapEngine.mapFromEntity(entity)
            roadmapsByUser[trackId] = model
            model
        } else null
    }

    suspend fun getSkillGapReportForTrack(trackId: String): SkillGapReport? = withContext(ioDispatcher) {
        skillGapsByUser[trackId]?.let { return@withContext it }
        val entity = dao?.getSkillGapReportForTrack(trackId)
        if (entity != null) {
            val model = DeterministicAssessmentEngine.mapEntityToReport(entity)
            skillGapsByUser[trackId] = model
            model
        } else null
    }

    suspend fun getStudyTasksForTrack(trackId: String): List<StudyTask> = withContext(ioDispatcher) {
        val fromDao = dao?.getStudyTasksForTrack(trackId)
        if (!fromDao.isNullOrEmpty()) {
            return@withContext fromDao.map { StudyPlanScheduler.mapFromEntity(it) }
        }
        val tasks = mutableListOf<StudyTask>()
        for ((_, taskList) in studyTasksByUser) {
            tasks.addAll(taskList.filter { it.id.contains(trackId) })
        }
        tasks
    }

    suspend fun getAllTopicProgressForTrack(
        trackId: String,
        userId: String
    ): List<com.learnmate.ai.domain.TrackTopicProgress> = withContext(ioDispatcher) {
        val results = mutableMapOf<String, com.learnmate.ai.domain.TrackTopicProgress>()

        // 1. From in-memory map
        val prefix = "${trackId}_${userId}_"
        for ((key, progress) in inMemoryTopicProgress) {
            if (key.startsWith(prefix) && progress.trackId == trackId && progress.userId == userId) {
                results[progress.nodeId] = progress
            }
        }

        // 2. From database study_tasks
        val tasks = dao?.getStudyTasksForTrack(trackId) ?: emptyList()
        for (task in tasks) {
            if (task.taskId.startsWith("task_${trackId}_")) {
                val nodeId = task.topic
                if (!results.containsKey(nodeId)) {
                    val status = when {
                        task.isCompleted -> com.learnmate.ai.domain.LearningProgressStatus.COMPLETED
                        task.notes.contains("NEEDS_REVISION", ignoreCase = true) -> com.learnmate.ai.domain.LearningProgressStatus.NEEDS_REVISION
                        task.notes.contains("IN_PROGRESS", ignoreCase = true) -> com.learnmate.ai.domain.LearningProgressStatus.IN_PROGRESS
                        else -> com.learnmate.ai.domain.LearningProgressStatus.NOT_STARTED
                    }
                    val score = task.notes.substringAfter("score:", "").substringBefore("|").toDoubleOrNull()
                    val progress = com.learnmate.ai.domain.TrackTopicProgress(
                        trackId = trackId,
                        userId = userId,
                        nodeId = nodeId,
                        status = status,
                        scorePercent = score,
                        completedAtEpochMs = task.completedAtEpochMs,
                        lastAccessedEpochMs = task.completedAtEpochMs ?: System.currentTimeMillis()
                    )
                    results[nodeId] = progress
                }
            }
        }

        results.values.toList()
    }

    suspend fun createLearnerSnapshot(userId: String): com.learnmate.ai.domain.lifecycle.LearnerBackupPayload? = withContext(ioDispatcher) {
        val profile = getProfile(userId) ?: return@withContext null
        val tracks = getTracksForUser(userId)
        val roadmaps = mutableListOf<PersonalisedRoadmap>()
        val skillGaps = mutableListOf<SkillGapReport>()
        val studyTasks = mutableListOf<StudyTask>()
        val attempts = mutableListOf<AssessmentAttemptRecord>()
        val srsItems = mutableListOf<SpacedRepetitionItem>()
        val topicProgress = mutableListOf<TrackTopicProgress>()
        val chatSessions = mutableListOf<ChatSession>()
        val chatMessages = mutableListOf<ChatMessage>()

        for (track in tracks) {
            getRoadmapForTrack(track.trackId)?.let { roadmaps.add(it) }
            getSkillGapReportForTrack(track.trackId)?.let { skillGaps.add(it) }
            studyTasks.addAll(getStudyTasksForTrack(track.trackId))
            attempts.addAll(getAllAssessmentAttemptsForTrack(track.trackId, userId))
            srsItems.addAll(getSpacedRepetitionQueue(track.trackId, userId))
            topicProgress.addAll(getAllTopicProgressForTrack(track.trackId, userId))

            val sessions = getChatSessionsForTrack(track.trackId)
            chatSessions.addAll(sessions)
            for (s in sessions) {
                chatMessages.addAll(getMessagesForSession(s.sessionId))
            }
        }

        getRoadmap(userId)?.let { if (it !in roadmaps) roadmaps.add(it) }
        getSkillGapReport(userId)?.let { if (it !in skillGaps) skillGaps.add(it) }
        val globalTasks = getStudyTasks(userId)
        for (gt in globalTasks) {
            if (studyTasks.none { it.id == gt.id }) studyTasks.add(gt)
        }
        val globalSessions = getChatSessions(userId)
        for (gs in globalSessions) {
            if (chatSessions.none { it.sessionId == gs.sessionId }) {
                chatSessions.add(gs)
                chatMessages.addAll(getMessagesForSession(gs.sessionId))
            }
        }

        val manifest = com.learnmate.ai.domain.lifecycle.LearnerBackupManifest(
            backupId = "snapshot_$userId",
            formatVersion = 2,
            roomSchemaVersion = 4,
            appVersion = "1.0.0",
            userId = userId,
            userName = profile.name,
            targetDomain = profile.targetDomain,
            tracksCount = tracks.size,
            completedTopicsCount = topicProgress.count { it.status == LearningProgressStatus.COMPLETED },
            assessmentAttemptsCount = attempts.size,
            spacedRepetitionItemsCount = srsItems.size,
            checksumSha256 = ""
        )

        com.learnmate.ai.domain.lifecycle.LearnerBackupPayload(
            manifest = manifest,
            profile = profile,
            tracks = tracks,
            roadmaps = roadmaps,
            skillGapReports = skillGaps,
            studyTasks = studyTasks,
            topicProgress = topicProgress,
            assessmentAttempts = attempts,
            spacedRepetitionItems = srsItems,
            chatSessions = chatSessions,
            chatMessages = chatMessages
        )
    }

    suspend fun importLearnerDataSnapshot(
        payload: com.learnmate.ai.domain.lifecycle.LearnerBackupPayload,
        targetUserId: String? = null
    ): Boolean = withContext(ioDispatcher) {
        try {
            val effectiveUserId = targetUserId ?: payload.profile.userId

            // 1. Restore Profile
            val restoredProfile = payload.profile.copy(userId = effectiveUserId)
            saveProfile(restoredProfile)

            // 2. Restore Tracks
            val userTrackList = tracksByUser.getOrPut(effectiveUserId) { mutableListOf() }
            if (payload.tracks.isNotEmpty()) {
                userTrackList.clear()
            }
            for (track in payload.tracks) {
                val mappedTrack = track.copy(userId = effectiveUserId)
                val trackEntity = LearnerTrackEntity(
                    trackId = mappedTrack.trackId,
                    userId = mappedTrack.userId,
                    title = mappedTrack.title,
                    learningMode = mappedTrack.learningMode.name,
                    targetCurriculumNodeId = mappedTrack.targetCurriculumNodeId,
                    targetExamName = mappedTrack.targetExamName,
                    targetExamDateEpochMs = mappedTrack.targetExamDateEpochMs,
                    dailyCommitmentMinutes = mappedTrack.dailyCommitmentMinutes,
                    isTrackActive = mappedTrack.isTrackActive,
                    createdAtEpochMs = mappedTrack.createdAtEpochMs,
                    lastAccessedEpochMs = mappedTrack.lastAccessedEpochMs
                )
                dao?.upsertTrack(trackEntity)
                userTrackList.add(mappedTrack)
            }
            getOrCreateTracksFlow(effectiveUserId).value = tracksByUser[effectiveUserId]?.toList() ?: emptyList()
            getOrCreateActiveTrackFlow(effectiveUserId).value = tracksByUser[effectiveUserId]?.firstOrNull { it.isTrackActive }

            // 3. Restore Roadmaps
            for (roadmap in payload.roadmaps) {
                val mappedRoadmap = roadmap.copy(userId = effectiveUserId)
                roadmapsByUser[mappedRoadmap.userId] = mappedRoadmap
                val defaultTrackId = if (payload.tracks.isNotEmpty()) payload.tracks.first().trackId else "track_${effectiveUserId}_default"
                dao?.upsertRoadmap(DeterministicRoadmapEngine.mapToEntity(mappedRoadmap, defaultTrackId))
            }
            getOrCreateRoadmapFlow(effectiveUserId).value = roadmapsByUser[effectiveUserId]

            // 4. Restore Skill Gaps
            for (skillGap in payload.skillGapReports) {
                val mappedSkillGap = skillGap.copy(userId = effectiveUserId)
                skillGapsByUser[mappedSkillGap.userId] = mappedSkillGap
                val defaultTrackId = if (payload.tracks.isNotEmpty()) payload.tracks.first().trackId else "track_${effectiveUserId}_default"
                dao?.upsertSkillGapReport(DeterministicAssessmentEngine.mapReportToEntity(mappedSkillGap, defaultTrackId))
            }
            getOrCreateSkillGapFlow(effectiveUserId).value = skillGapsByUser[effectiveUserId]

            // 5. Restore Study Tasks
            val userTasks = studyTasksByUser.getOrPut(effectiveUserId) { mutableListOf() }
            for (task in payload.studyTasks) {
                val mappedTask = task.copy(userId = effectiveUserId)
                userTasks.removeAll { it.id == mappedTask.id }
                userTasks.add(mappedTask)
                val defaultTrackId = if (payload.tracks.isNotEmpty()) payload.tracks.first().trackId else "track_${effectiveUserId}_default"
                dao?.upsertStudyTask(StudyPlanScheduler.mapToEntity(mappedTask, defaultTrackId))
            }
            getOrCreateStudyTasksFlow(effectiveUserId).value = userTasks.toList()

            // 6. Restore Topic Progress
            for (tp in payload.topicProgress) {
                updateTopicProgress(
                    trackId = tp.trackId,
                    userId = effectiveUserId,
                    nodeId = tp.nodeId,
                    status = tp.status,
                    scorePercent = tp.scorePercent
                )
            }

            // 7. Restore Assessment Attempts
            for (att in payload.assessmentAttempts) {
                val mappedAtt = att.copy(userId = effectiveUserId)
                val attemptList = inMemoryAssessmentAttempts.getOrPut("${mappedAtt.trackId}_$effectiveUserId") { mutableListOf() }
                attemptList.removeAll { it.attemptId == mappedAtt.attemptId }
                attemptList.add(mappedAtt)

                val taskEntity = StudyTaskEntity(
                    taskId = "attempt_${mappedAtt.attemptId}",
                    trackId = mappedAtt.trackId,
                    userId = effectiveUserId,
                    roadmapStepId = mappedAtt.nodeId,
                    dayIndex = 1,
                    weekIndex = 1,
                    title = "Diagnostic: ${mappedAtt.topicTitle}",
                    topic = mappedAtt.nodeId,
                    durationMinutes = 20,
                    isCompleted = true,
                    completedAtEpochMs = mappedAtt.attemptEpochMs,
                    notes = gson.toJson(mappedAtt)
                )
                dao?.upsertStudyTask(taskEntity)
            }

            // 8. Restore Spaced Repetition Items
            for (srs in payload.spacedRepetitionItems) {
                val mappedSrs = srs.copy(userId = effectiveUserId)
                val trackSrs = inMemorySrsItems.getOrPut("${mappedSrs.trackId}_$effectiveUserId") { mutableMapOf() }
                trackSrs[mappedSrs.nodeId] = mappedSrs

                val srsTask = StudyTaskEntity(
                    taskId = "srs_${mappedSrs.repetitionId}",
                    trackId = mappedSrs.trackId,
                    userId = effectiveUserId,
                    roadmapStepId = mappedSrs.nodeId,
                    dayIndex = 1,
                    weekIndex = 1,
                    title = "SRS: ${mappedSrs.topicTitle}",
                    topic = mappedSrs.nodeId,
                    durationMinutes = 15,
                    isCompleted = false,
                    completedAtEpochMs = mappedSrs.lastReviewedEpochMs,
                    notes = gson.toJson(mappedSrs)
                )
                dao?.upsertStudyTask(srsTask)
            }

            // 9. Restore Chat Sessions & Messages
            val userSessions = chatSessionsByUser.getOrPut(effectiveUserId) { mutableListOf() }
            for (session in payload.chatSessions) {
                val mappedSession = session.copy(userId = effectiveUserId)
                userSessions.removeAll { it.sessionId == mappedSession.sessionId }
                userSessions.add(mappedSession)
                val sessionEntity = ChatSessionEntity(
                    sessionId = mappedSession.sessionId,
                    trackId = mappedSession.trackId,
                    userId = effectiveUserId,
                    domain = mappedSession.domain,
                    title = mappedSession.title,
                    isAiEnabled = mappedSession.isAiEnabled,
                    createdAtEpochMs = mappedSession.createdAtEpochMs,
                    updatedAtEpochMs = mappedSession.updatedAtEpochMs
                )
                dao?.upsertChatSession(sessionEntity)
            }
            getOrCreateChatSessionFlow(effectiveUserId).value = userSessions.toList()

            for (msg in payload.chatMessages) {
                val mappedMsg = msg.copy(userId = effectiveUserId)
                saveChatMessage(mappedMsg)
            }

            true
        } catch (e: Exception) {
            false
        }
    }
}

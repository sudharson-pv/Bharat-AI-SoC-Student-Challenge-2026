package com.learnmate.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LearnMateDao {

    // ── Learner Profile ──────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: LearnerProfileEntity)

    @Query("SELECT * FROM learner_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfile(userId: String): LearnerProfileEntity?

    @Query("SELECT * FROM learner_profiles WHERE userId = :userId LIMIT 1")
    fun observeProfile(userId: String): Flow<LearnerProfileEntity?>

    @Query("SELECT * FROM learner_profiles ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllProfiles(): List<LearnerProfileEntity>

    @Query("SELECT * FROM learner_profiles ORDER BY updatedAtEpochMs DESC")
    fun observeAllProfiles(): Flow<List<LearnerProfileEntity>>

    @Query("DELETE FROM learner_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)

    // ── Multi-Track Operations ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: LearnerTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<LearnerTrackEntity>)

    @Query("SELECT * FROM learner_tracks WHERE userId = :userId ORDER BY isTrackActive DESC, lastAccessedEpochMs DESC")
    suspend fun getTracksForUser(userId: String): List<LearnerTrackEntity>

    @Query("SELECT * FROM learner_tracks WHERE userId = :userId ORDER BY isTrackActive DESC, lastAccessedEpochMs DESC")
    fun observeTracksForUser(userId: String): Flow<List<LearnerTrackEntity>>

    @Query("SELECT * FROM learner_tracks WHERE userId = :userId AND isTrackActive = 1 LIMIT 1")
    suspend fun getActiveTrackForUser(userId: String): LearnerTrackEntity?

    @Query("SELECT * FROM learner_tracks WHERE userId = :userId AND isTrackActive = 1 LIMIT 1")
    fun observeActiveTrackForUser(userId: String): Flow<LearnerTrackEntity?>

    @Query("SELECT * FROM learner_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): LearnerTrackEntity?

    @Query("UPDATE learner_tracks SET isTrackActive = 0 WHERE userId = :userId")
    suspend fun deactivateAllTracks(userId: String)

    @Query("UPDATE learner_tracks SET isTrackActive = 1, lastAccessedEpochMs = :timestamp WHERE trackId = :trackId AND userId = :userId")
    suspend fun activateTrack(userId: String, trackId: String, timestamp: Long = System.currentTimeMillis())

    @Transaction
    suspend fun setActiveTrack(userId: String, trackId: String) {
        deactivateAllTracks(userId)
        activateTrack(userId, trackId)
    }

    @Query("DELETE FROM learner_tracks WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)

    // ── Content Packs ────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentPack(pack: ContentPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertContentPackSync(pack: ContentPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPackDependency(dependency: PackDependencyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCurriculumNode(node: CurriculumNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNodePrerequisite(prereq: NodePrerequisiteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCurriculumResource(resource: CurriculumResourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentPacks(packs: List<ContentPackEntity>)

    @Query("SELECT * FROM content_packs ORDER BY installedAtEpochMs DESC")
    suspend fun getAllContentPacks(): List<ContentPackEntity>

    @Query("SELECT * FROM content_packs ORDER BY installedAtEpochMs DESC")
    fun getAllContentPacksSync(): List<ContentPackEntity>

    @Query("SELECT * FROM content_packs WHERE packId = :packId LIMIT 1")
    suspend fun getContentPackById(packId: String): ContentPackEntity?

    @Query("UPDATE content_packs SET isInstalled = :isInstalled WHERE packId = :packId")
    fun markContentPackInstalled(packId: String, isInstalled: Boolean)

    @Query("DELETE FROM content_packs WHERE packId = :packId")
    fun deleteContentPackSync(packId: String)

    // ── Universal Curriculum Nodes ───────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurriculumNodes(nodes: List<CurriculumNodeEntity>)

    @Query("SELECT * FROM curriculum_nodes WHERE parentNodeId IS NULL ORDER BY orderIndex ASC")
    suspend fun getRootNodes(): List<CurriculumNodeEntity>

    @Query("SELECT * FROM curriculum_nodes WHERE parentNodeId = :parentNodeId ORDER BY orderIndex ASC")
    suspend fun getChildrenOfNode(parentNodeId: String): List<CurriculumNodeEntity>

    @Query("SELECT * FROM curriculum_nodes WHERE nodeId = :nodeId LIMIT 1")
    suspend fun getNodeById(nodeId: String): CurriculumNodeEntity?

    @Query("SELECT * FROM curriculum_nodes WHERE packId = :packId ORDER BY orderIndex ASC")
    suspend fun getNodesForPack(packId: String): List<CurriculumNodeEntity>

    @Query("SELECT * FROM curriculum_nodes")
    suspend fun getAllCurriculumNodes(): List<CurriculumNodeEntity>

    // ── Node Prerequisites ───────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrerequisites(prereqs: List<NodePrerequisiteEntity>)

    @Query("SELECT * FROM node_prerequisites WHERE targetNodeId = :targetNodeId")
    suspend fun getPrerequisitesForNode(targetNodeId: String): List<NodePrerequisiteEntity>

    @Query("SELECT * FROM node_prerequisites")
    suspend fun getAllPrerequisites(): List<NodePrerequisiteEntity>

    // ── Curriculum Resources ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<CurriculumResourceEntity>)

    @Query("SELECT * FROM curriculum_resources WHERE nodeId = :nodeId AND languageCode = :languageCode LIMIT 1")
    suspend fun getResourceForNode(nodeId: String, languageCode: String): CurriculumResourceEntity?

    @Query("SELECT * FROM curriculum_resources WHERE nodeId = :nodeId")
    suspend fun getResourcesForNode(nodeId: String): List<CurriculumResourceEntity>

    // ── Assessment Blueprints & Questions V2 ─────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssessmentQuestionsV2(questions: List<AssessmentQuestionV2Entity>)

    @Query("SELECT * FROM assessment_questions_v2 WHERE nodeId = :nodeId")
    suspend fun getQuestionsV2ForNode(nodeId: String): List<AssessmentQuestionV2Entity>

    @Query("SELECT * FROM assessment_questions_v2")
    suspend fun getAllAssessmentQuestionsV2(): List<AssessmentQuestionV2Entity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlueprint(blueprint: AssessmentBlueprintEntity)

    @Query("SELECT * FROM assessment_blueprints WHERE blueprintId = :blueprintId LIMIT 1")
    suspend fun getBlueprintById(blueprintId: String): AssessmentBlueprintEntity?

    // ── Legacy Taxonomy & Verified Curriculum (Preserved) ────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<VerifiedTopicEntity>)

    @Query("SELECT * FROM verified_topics WHERE LOWER(domain) = LOWER(:domain)")
    suspend fun getTopicsForDomain(domain: String): List<VerifiedTopicEntity>

    @Query("SELECT * FROM verified_topics WHERE topicId = :topicId LIMIT 1")
    suspend fun getTopicById(topicId: String): VerifiedTopicEntity?

    @Query("SELECT COUNT(*) FROM verified_topics")
    suspend fun getTopicCount(): Int

    // ── Legacy Diagnostic Assessment Questions (Preserved) ───────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<AssessmentQuestionEntity>)

    @Query("SELECT * FROM assessment_questions WHERE LOWER(domain) = LOWER(:domain)")
    suspend fun getQuestionsForDomain(domain: String): List<AssessmentQuestionEntity>

    @Query("SELECT * FROM assessment_questions WHERE LOWER(topic) = LOWER(:topic)")
    suspend fun getQuestionsForTopic(topic: String): List<AssessmentQuestionEntity>

    @Query("SELECT COUNT(*) FROM assessment_questions")
    suspend fun getQuestionCount(): Int

    // ── Skill Gap Reports (Legacy & Track-Scoped) ────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkillGapReport(report: SkillGapReportEntity)

    @Query("SELECT * FROM skill_gap_reports WHERE userId = :userId LIMIT 1")
    suspend fun getSkillGapReport(userId: String): SkillGapReportEntity?

    @Query("SELECT * FROM skill_gap_reports WHERE userId = :userId LIMIT 1")
    fun observeSkillGapReport(userId: String): Flow<SkillGapReportEntity?>

    @Query("SELECT * FROM skill_gap_reports WHERE trackId = :trackId LIMIT 1")
    suspend fun getSkillGapReportForTrack(trackId: String): SkillGapReportEntity?

    @Query("SELECT * FROM skill_gap_reports WHERE trackId = :trackId LIMIT 1")
    fun observeSkillGapReportForTrack(trackId: String): Flow<SkillGapReportEntity?>

    // ── Personalized Roadmap (Legacy & Track-Scoped) ─────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoadmap(roadmap: PersonalisedRoadmapEntity)

    @Query("SELECT * FROM personalised_roadmaps WHERE userId = :userId LIMIT 1")
    suspend fun getRoadmap(userId: String): PersonalisedRoadmapEntity?

    @Query("SELECT * FROM personalised_roadmaps WHERE userId = :userId LIMIT 1")
    fun observeRoadmap(userId: String): Flow<PersonalisedRoadmapEntity?>

    @Query("SELECT * FROM personalised_roadmaps WHERE trackId = :trackId LIMIT 1")
    suspend fun getRoadmapForTrack(trackId: String): PersonalisedRoadmapEntity?

    @Query("SELECT * FROM personalised_roadmaps WHERE trackId = :trackId LIMIT 1")
    fun observeRoadmapForTrack(trackId: String): Flow<PersonalisedRoadmapEntity?>

    // ── Study Tasks (Legacy & Track-Scoped) ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyTasks(tasks: List<StudyTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudyTask(task: StudyTaskEntity)

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId AND topic = :nodeId LIMIT 1")
    suspend fun getStudyTaskForTopic(trackId: String, nodeId: String): StudyTaskEntity?

    @Query("SELECT * FROM study_tasks WHERE userId = :userId ORDER BY weekIndex ASC, dayIndex ASC")
    suspend fun getStudyTasks(userId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE userId = :userId ORDER BY weekIndex ASC, dayIndex ASC")
    fun observeStudyTasks(userId: String): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId ORDER BY weekIndex ASC, dayIndex ASC")
    suspend fun getStudyTasksForTrack(trackId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId ORDER BY weekIndex ASC, dayIndex ASC")
    fun observeStudyTasksForTrack(trackId: String): Flow<List<StudyTaskEntity>>

    @Query("UPDATE study_tasks SET isCompleted = :completed, completedAtEpochMs = :timestamp WHERE taskId = :taskId AND userId = :userId")
    suspend fun setStudyTaskCompleted(userId: String, taskId: String, completed: Boolean, timestamp: Long?)

    @Query("UPDATE study_tasks SET isCompleted = :completed, completedAtEpochMs = :timestamp WHERE taskId = :taskId AND trackId = :trackId")
    suspend fun setStudyTaskCompletedInTrack(trackId: String, taskId: String, completed: Boolean, timestamp: Long?)

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId AND taskId LIKE 'attempt_%' ORDER BY completedAtEpochMs DESC")
    suspend fun getAssessmentAttemptTasksForTrack(trackId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId AND topic = :nodeId AND taskId LIKE 'attempt_%' ORDER BY completedAtEpochMs DESC")
    suspend fun getAssessmentAttemptTasksForNode(trackId: String, nodeId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId AND taskId LIKE 'srs_%'")
    suspend fun getSpacedRepetitionTasksForTrack(trackId: String): List<StudyTaskEntity>

    @Query("SELECT * FROM study_tasks WHERE trackId = :trackId AND topic = :nodeId AND taskId LIKE 'srs_%' LIMIT 1")
    suspend fun getSpacedRepetitionTaskForNode(trackId: String, nodeId: String): StudyTaskEntity?

    @Query("DELETE FROM study_tasks WHERE userId = :userId")
    suspend fun clearStudyTasks(userId: String)

    @Query("DELETE FROM study_tasks WHERE trackId = :trackId")
    suspend fun clearStudyTasksForTrack(trackId: String)

    // ── Chat Sessions & Messages (Legacy & Track-Scoped) ─────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions WHERE userId = :userId ORDER BY updatedAtEpochMs DESC")
    suspend fun getChatSessions(userId: String): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE userId = :userId ORDER BY updatedAtEpochMs DESC")
    fun observeChatSessions(userId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE trackId = :trackId ORDER BY updatedAtEpochMs DESC")
    suspend fun getChatSessionsForTrack(trackId: String): List<ChatSessionEntity>

    @Query("SELECT * FROM chat_sessions WHERE trackId = :trackId ORDER BY updatedAtEpochMs DESC")
    fun observeChatSessionsForTrack(trackId: String): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getChatSessionById(sessionId: String): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    suspend fun deleteChatSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestampEpochMs ASC")
    fun observeMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun clearMessagesForSession(sessionId: String)

    @Query("UPDATE chat_sessions SET updatedAtEpochMs = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long)

    @Transaction
    suspend fun insertMessageAndUpdateSession(message: ChatMessageEntity) {
        insertChatMessage(message)
        updateSessionTimestamp(message.sessionId, message.timestampEpochMs)
    }
}

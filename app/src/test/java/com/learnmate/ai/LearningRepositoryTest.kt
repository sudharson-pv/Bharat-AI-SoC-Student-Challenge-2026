package com.learnmate.ai

import com.learnmate.ai.data.repository.LearningRepository
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.ChatMessage
import com.learnmate.ai.domain.LearnerProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearningRepositoryTest {

    private lateinit var repository: LearningRepository

    @Before
    fun setUp() {
        repository = LearningRepository() // In-memory testing mode
    }

    @Test
    fun testSaveAndGetLearnerProfile() = runBlocking {
        val profile = LearnerProfile(
            userId = "user_repo_1",
            name = "Divya Krishnan",
            targetDomain = "Electronics and VLSI",
            subdomain = "Digital VLSI & RTL",
            goal = "Learn Verilog",
            targetRole = "RTL Design Engineer",
            previousBackground = "EE Undergrad",
            currentLevel = "Beginner",
            audienceLevel = AudienceLevel.UNDERGRADUATE,
            dailyStudyMinutes = 60
        )

        repository.saveProfile(profile)
        val loaded = repository.getProfile(profile.userId)

        assertNotNull(loaded)
        assertEquals("Divya Krishnan", loaded!!.name)
        assertEquals("Electronics and VLSI", loaded.targetDomain)
        assertEquals("RTL Design Engineer", loaded.targetRole)
    }

    @Test
    fun testSubmitAssessmentTriggersRoadmapAndTasks() = runBlocking {
        val profile = LearnerProfile(
            userId = "user_repo_2",
            name = "Karthik Raja",
            targetDomain = "Electronics and VLSI",
            subdomain = "Semiconductor Physics",
            goal = "Master CMOS",
            dailyStudyMinutes = 45
        )
        repository.saveProfile(profile)

        val questions = repository.getAssessmentQuestionsForDomain("Electronics and VLSI")
        assertTrue(questions.isNotEmpty())

        val answers = questions.associate { it.id to it.correctIndex }
        val report = repository.submitAssessment(profile, questions, answers)

        assertNotNull(report)
        assertEquals(100, report.overallScorePercent)

        // Verify profile updated
        val updatedProfile = repository.getProfile(profile.userId)
        assertTrue(updatedProfile!!.assessmentCompleted)

        // Verify roadmap generated
        val roadmap = repository.getRoadmap(profile.userId)
        assertNotNull(roadmap)
        assertTrue(roadmap!!.steps.isNotEmpty())

        // Verify study tasks generated
        val tasks = repository.getStudyTasks(profile.userId)
        assertTrue(tasks.isNotEmpty())

        // Verify task completion toggling
        val firstTask = tasks.first()
        assertFalse(firstTask.isCompleted)
        repository.toggleTaskCompletion(profile.userId, firstTask.id, true)

        val reloadedTasks = repository.getStudyTasks(profile.userId)
        assertTrue(reloadedTasks.first().isCompleted)
    }

    @Test
    fun testChatPersistenceAndSessionTracking() = runBlocking {
        val session = repository.createChatSession(
            userId = "user_chat_1",
            domain = "Electronics and VLSI",
            title = "PN Junction Doubts",
            isAiEnabled = true
        )

        assertNotNull(session.sessionId)
        assertEquals("user_chat_1", session.userId)

        val userMessage = ChatMessage(
            messageId = "msg_1",
            sessionId = session.sessionId,
            userId = "user_chat_1",
            senderRole = "USER",
            text = "What is built-in potential?"
        )
        repository.saveChatMessage(userMessage)

        val tutorMessage = ChatMessage(
            messageId = "msg_2",
            sessionId = session.sessionId,
            userId = "user_chat_1",
            senderRole = "TUTOR",
            text = "Built-in potential is ~0.7V for Silicon.",
            isAiGenerated = true,
            promptTokensEstimate = 12,
            generatedTokens = 10,
            ttftMs = 450,
            tokensPerSec = 12.5
        )
        repository.saveChatMessage(tutorMessage)

        val messages = repository.getMessagesForSession(session.sessionId)
        assertEquals(2, messages.size)
        assertEquals("USER", messages[0].senderRole)
        assertEquals("TUTOR", messages[1].senderRole)
        assertTrue(messages[1].isAiGenerated)
    }

    @Test
    fun testChatPersistenceAcrossProcessRestart_loadsPersistedBeforeAppending() = runBlocking {
        // Shared backing store simulating persistent SQLite DB across app restarts
        val persistedMessages = mutableListOf<com.learnmate.ai.data.db.ChatMessageEntity>()

        val fakeDao = java.lang.reflect.Proxy.newProxyInstance(
            com.learnmate.ai.data.db.LearnMateDao::class.java.classLoader,
            arrayOf(com.learnmate.ai.data.db.LearnMateDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertMessageAndUpdateSession" -> {
                    val msg = args[0] as com.learnmate.ai.data.db.ChatMessageEntity
                    persistedMessages.add(msg)
                    null
                }
                "getMessagesForSession" -> {
                    val sId = args[0] as String
                    persistedMessages.filter { it.sessionId == sId }
                }
                else -> null
            }
        } as com.learnmate.ai.data.db.LearnMateDao

        // Process 1: User asks a question and app saves it to SQLite
        val repoProcess1 = LearningRepository(dao = fakeDao)
        val msg1 = ChatMessage("m_1", "session_restart_1", "user_1", "USER", "First question before kill")
        repoProcess1.saveChatMessage(msg1)
        assertEquals(1, persistedMessages.size)

        // Process 2: App is killed and relaunched. New repo instance has cold memory cache.
        // New incoming message arrives (e.g. from tutor or background) BEFORE getMessagesForSession is called
        val repoProcess2 = LearningRepository(dao = fakeDao)
        val msg2 = ChatMessage("m_2", "session_restart_1", "user_1", "TUTOR", "Answer after process restart")
        repoProcess2.saveChatMessage(msg2)

        // Verify that repoProcess2 loaded persisted msg1 before appending msg2
        val sessionMessages = repoProcess2.getMessagesForSession("session_restart_1")
        assertEquals("Must contain both pre-restart and post-restart messages", 2, sessionMessages.size)
        assertEquals("m_1", sessionMessages[0].messageId)
        assertEquals("m_2", sessionMessages[1].messageId)
    }
}


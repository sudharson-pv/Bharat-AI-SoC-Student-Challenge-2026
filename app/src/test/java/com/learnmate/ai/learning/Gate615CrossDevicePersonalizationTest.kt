package com.learnmate.ai.learning

import com.learnmate.ai.data.assessment.DeterministicAssessmentEngine
import com.learnmate.ai.domain.AssessmentQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gate615CrossDevicePersonalizationTest {

    private val sampleQuestions = (1..15).map { idx ->
        AssessmentQuestion(
            id = "q_$idx",
            domain = "Universal Learning",
            subdomain = "Applied Science",
            topic = "Topic ${(idx - 1) % 5 + 1}",
            questionText = "Question text $idx?",
            options = listOf("Correct Answer $idx", "Distractor A", "Distractor B", "Distractor C"),
            correctIndex = 0,
            explanation = "Explanation for question $idx"
        )
    }

    @Test
    fun testDeterministicCrossDevicePersonalizationInvariance() {
        val userId = "QA_USER_77"
        val trackId = "track_universal_01"
        val dayIndex = 42
        val installationId = "inst_moto_g54_primary"

        // Run assignment generation twice with identical parameters and installationId
        val assignmentRun1 = DeterministicAssessmentEngine.generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            installationId = installationId
        )

        val assignmentRun2 = DeterministicAssessmentEngine.generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            installationId = installationId
        )

        // Must produce identical count
        assertEquals(5, assignmentRun1.size)
        assertEquals(5, assignmentRun2.size)

        // Must select identical question IDs in identical order on the same installation
        val ids1 = assignmentRun1.map { it.id }
        val ids2 = assignmentRun2.map { it.id }
        assertEquals("Reopening on the same device must match questions exactly", ids1, ids2)

        // Must produce identical option order and matching correct index
        for (i in 0 until 5) {
            val q1 = assignmentRun1[i]
            val q2 = assignmentRun2[i]
            assertEquals(q1.options, q2.options)
            assertEquals(q1.correctIndex, q2.correctIndex)
            assertEquals("Correct Answer ${q1.id.removePrefix("q_")}", q1.options[q1.correctIndex])
        }

        // Different day must produce different permutation
        val assignmentDay43 = DeterministicAssessmentEngine.generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = 43,
            availableQuestions = sampleQuestions,
            count = 5,
            installationId = installationId
        )
        val idsDay43 = assignmentDay43.map { it.id }
        assertNotEquals("Different dayIndex must vary assignment selection", ids1, idsDay43)
    }

    @Test
    fun testCrossDeviceInstallationIdVariance() {
        val userId = "QA_USER_88"
        val trackId = "track_universal_01"
        val dayIndex = 10

        val device1InstallationId = "moto_g54_device_alpha"
        val device2InstallationId = "pixel8_device_beta"

        val device1Assignment = DeterministicAssessmentEngine.generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            installationId = device1InstallationId
        )

        val device2Assignment = DeterministicAssessmentEngine.generatePersonalizedAssignment(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            installationId = device2InstallationId
        )

        assertEquals(5, device1Assignment.size)
        assertEquals(5, device2Assignment.size)

        val ids1 = device1Assignment.map { it.id }
        val ids2 = device2Assignment.map { it.id }

        // Two distinct devices for the same user profile must produce variance (different questions or different permutation)
        val isIdentical = (ids1 == ids2) && device1Assignment.indices.all { i ->
            device1Assignment[i].options == device2Assignment[i].options
        }
        assertTrue("Distinct installation IDs must produce personalized variation across devices", !isIdentical)
    }

    @Test
    fun testCreateAssignmentInstanceMetadataAndIntegrity() {
        val userId = "QA_USER_99"
        val trackId = "track_math_advanced"
        val dayIndex = 5
        val blueprintId = "bp_algebra_v2"
        val installationId = "inst_moto_g54_test"

        val instance = DeterministicAssessmentEngine.createAssignmentInstance(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            blueprintId = blueprintId,
            installationId = installationId
        )

        assertNotNull(instance.assignmentInstanceId)
        assertNotNull(instance.attemptId)
        assertEquals(userId, instance.userId)
        assertEquals(trackId, instance.trackId)
        assertEquals(blueprintId, instance.blueprintId)
        assertEquals(installationId, instance.installationId)
        assertEquals(dayIndex, instance.dayIndex)
        assertEquals(5, instance.questions.size)
        assertEquals(5, instance.objectiveMapping.size)
        assertTrue(instance.generationSeed != 0L)
    }

    @Test
    fun testEquivalenceOfObjectivesAndDifficultyAcrossInstallations() {
        val userId = "QA_USER_101"
        val trackId = "track_ee_circuits"
        val dayIndex = 14
        val blueprintId = "bp_ac_circuits_v1"

        val instAlpha = "installation_device_alpha_uuid"
        val instBeta = "installation_device_beta_uuid"

        val assignmentAlpha = DeterministicAssessmentEngine.createAssignmentInstance(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            blueprintId = blueprintId,
            installationId = instAlpha
        )

        val assignmentBeta = DeterministicAssessmentEngine.createAssignmentInstance(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            blueprintId = blueprintId,
            installationId = instBeta
        )

        // 1. Prove distinct installation IDs
        assertNotEquals(assignmentAlpha.installationId, assignmentBeta.installationId)

        // 2. Prove distinct assignment IDs and attempt IDs
        assertNotEquals(assignmentAlpha.assignmentInstanceId, assignmentBeta.assignmentInstanceId)
        assertNotEquals(assignmentAlpha.attemptId, assignmentBeta.attemptId)

        // 3. Prove distinct seeds
        assertNotEquals(assignmentAlpha.generationSeed, assignmentBeta.generationSeed)

        // 4. Prove equivalent objective coverage
        val objectivesAlpha = assignmentAlpha.objectiveMapping.values.toSet()
        val objectivesBeta = assignmentBeta.objectiveMapping.values.toSet()
        assertEquals(
            "Both installations must cover the required blueprint objectives",
            objectivesAlpha,
            objectivesBeta
        )

        // 5. Equivalence Evaluation: Mean difficulty difference is bounded within epsilon (0.00)
        // (Do NOT use p < 0.05 from equality rejection test as evidence of equivalence)
        assertEquals(
            "Difficulty band must be preserved identically across variants",
            assignmentAlpha.difficultyBand,
            assignmentBeta.difficultyBand
        )
    }

    @Test
    fun testPersistenceAcrossSimulatedProcessRestarts() {
        val userId = "QA_USER_102"
        val trackId = "track_cs_algorithms"
        val dayIndex = 21
        val blueprintId = "bp_graph_theory_v1"
        val persistedInstallationId = "inst_persisted_hardware_id"

        // First session launch
        val initialSession = DeterministicAssessmentEngine.createAssignmentInstance(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            blueprintId = blueprintId,
            installationId = persistedInstallationId
        )

        // Simulate app restart: re-instantiate using identical persisted installationId
        val rebootSession = DeterministicAssessmentEngine.createAssignmentInstance(
            userId = userId,
            trackId = trackId,
            dayIndex = dayIndex,
            availableQuestions = sampleQuestions,
            count = 5,
            blueprintId = blueprintId,
            installationId = persistedInstallationId
        )

        // Verify exact persistence and replay
        assertEquals(initialSession.generationSeed, rebootSession.generationSeed)
        assertEquals(initialSession.questions.map { it.id }, rebootSession.questions.map { it.id })
        assertEquals(initialSession.difficultyBand, rebootSession.difficultyBand)
    }
}


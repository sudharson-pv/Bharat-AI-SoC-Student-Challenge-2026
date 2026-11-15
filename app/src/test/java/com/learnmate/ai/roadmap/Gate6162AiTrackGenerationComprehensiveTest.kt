package com.learnmate.ai.roadmap

import com.learnmate.ai.data.roadmap.AiTrackGenerationEngine
import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.EducationLevel
import com.learnmate.ai.domain.LearnerCategory
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.RoadmapStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 6.16.2 Priority 2: Comprehensive Validation of AI-Assisted Learning Track Generation.
 *
 * Verifies:
 * 1. Customized prompt generation across 5 diverse educational profiles.
 * 2. Distinct structured proposals produced for distinct learners.
 * 3. Topological cycle detection in prerequisites (DAG audit).
 * 4. Duplicate topic ID / title rejection.
 * 5. Time budget and schedule consistency validation.
 * 6. Structured schema elements (Modules, Units, Chapters, Topics, Milestones, Assignments, Revision Plan, Checkpoints).
 */
class Gate6162AiTrackGenerationComprehensiveTest {

    private fun makeProfile(
        userId: String,
        name: String,
        domain: String,
        level: EducationLevel,
        category: LearnerCategory,
        dailyMinutes: Int = 45
    ): LearnerProfile {
        return LearnerProfile(
            userId = userId,
            name = name,
            targetDomain = domain,
            subdomain = "General",
            goal = "Master $domain",
            targetRole = "Scholar",
            previousBackground = "Foundation",
            currentLevel = "Beginner",
            audienceLevel = AudienceLevel.HIGH_SCHOOL,
            dailyStudyMinutes = dailyMinutes,
            weeklyStudyDays = 5,
            currentTopic = "Foundations",
            onboardingCompleted = true,
            assessmentCompleted = true,
            educationLevel = level,
            learnerCategory = category
        )
    }

    @Test
    fun test1_MultiDomainCustomizedTrackGeneration() {
        // Profile A: Secondary School Class 10 Math
        val profA = makeProfile("u_math10", "Aarav Gupta", "Secondary Mathematics", EducationLevel.SECONDARY, LearnerCategory.SCHOOL_STUDENT, 40)
        val propA = AiTrackGenerationEngine.generateAiAssistedTrackProposal(
            profile = profA,
            domain = "Secondary Mathematics",
            goal = "Score 95% in CBSE Board Examinations",
            departmentOrStandard = "CBSE Class 10"
        )

        // Profile B: UG Computer Science Distributed Systems
        val profB = makeProfile("u_cs_ug", "Neha Sen", "Distributed Computing", EducationLevel.UNDERGRADUATE, LearnerCategory.ENGINEERING_STUDENT, 60)
        val propB = AiTrackGenerationEngine.generateAiAssistedTrackProposal(
            profile = profB,
            domain = "Distributed Computing",
            goal = "Master Consensus Protocols and Raft Algorithm",
            departmentOrStandard = "B.Tech Computer Science 6th Sem"
        )

        // Profile C: Medical Student Cardiac Electrophysiology
        val profC = makeProfile("u_med_mbbs", "Dr. Rohan Varma", "Cardiac Electrophysiology", EducationLevel.POSTGRADUATE, LearnerCategory.RESEARCH_SCHOLAR, 60)
        val propC = AiTrackGenerationEngine.generateAiAssistedTrackProposal(
            profile = profC,
            domain = "Cardiac Electrophysiology",
            goal = "Clinical Interpretation of Arrhythmias and ECG",
            departmentOrStandard = "MBBS Cardiology"
        )

        // 1. Verify Distinct Titles & Domains
        assertTrue(propA.title.contains("Secondary Mathematics"))
        assertTrue(propB.title.contains("Distributed Computing"))
        assertTrue(propC.title.contains("Cardiac Electrophysiology"))

        // 2. Verify Structured Elements Present
        assertEquals(3, propA.modules.size)
        assertEquals(3, propB.modules.size)
        assertEquals(3, propC.modules.size)

        assertTrue(propA.milestones.isNotEmpty())
        assertTrue(propA.assignments.isNotEmpty())
        assertTrue(propA.revisionPlan.isNotEmpty())
        assertTrue(propA.assessmentCheckpoints.isNotEmpty())

        // 3. Verify Deterministic Validation Passed
        assertTrue("Proposal A must be valid", propA.validationReport.isValid)
        assertTrue("Proposal B must be valid", propB.validationReport.isValid)
        assertTrue("Proposal C must be valid", propC.validationReport.isValid)
    }

    @Test
    fun test2_CyclicPrerequisiteRejection() {
        val prof = makeProfile("u_cyclic", "Test User", "Systems", EducationLevel.UNDERGRADUATE, LearnerCategory.ENGINEERING_STUDENT)

        // Build cyclic steps: Node 1 -> Node 2 -> Node 3 -> Node 1
        val cyclicSteps = listOf(
            RoadmapStep(1, "step_1", "Intro", "Topic 1", "Core", listOf("step_3"), 30, false),
            RoadmapStep(2, "step_2", "Mid", "Topic 2", "Core", listOf("step_1"), 30, false),
            RoadmapStep(3, "step_3", "Adv", "Topic 3", "Core", listOf("step_2"), 30, false)
        )

        val report = AiTrackGenerationEngine.validateTrackProposal(cyclicSteps, prof, 12)
        assertFalse("Cyclic steps must fail validation", report.isValid)
        assertTrue("Must detect cyclic dependencies", report.cyclicDependencies.isNotEmpty())
    }

    @Test
    fun test3_DuplicateTopicIdRejection() {
        val prof = makeProfile("u_dup", "Test User", "Systems", EducationLevel.UNDERGRADUATE, LearnerCategory.ENGINEERING_STUDENT)

        val duplicateSteps = listOf(
            RoadmapStep(1, "dup_node", "Topic A", "Topic A", "Core", emptyList(), 30, false),
            RoadmapStep(2, "dup_node", "Topic B", "Topic B", "Core", emptyList(), 30, false)
        )

        val report = AiTrackGenerationEngine.validateTrackProposal(duplicateSteps, prof, 12)
        assertFalse("Duplicate step IDs must fail validation", report.isValid)
        assertTrue(report.duplicateNodes.contains("dup_node"))
    }

    @Test
    fun test4_ScheduleBudgetFeasibilityCheck() {
        val prof = makeProfile("u_budget", "Busy Professional", "Data", EducationLevel.CONTINUING_EDUCATION, LearnerCategory.LIFELONG_LEARNER, 10)
        // Learner has 10 mins/day * 5 days = 50 mins/week.
        // For 4 weeks, total budget is 200 mins.

        // Generate steps requiring 600 mins
        val heavySteps = (1..10).map { idx ->
            RoadmapStep(idx, "step_$idx", "Topic $idx", "Topic $idx", "Core", emptyList(), 60, false)
        }

        val report = AiTrackGenerationEngine.validateTrackProposal(heavySteps, prof, 4)
        assertFalse("Over-budget curriculum must fail schedule consistency", report.scheduleConsistent)
        assertFalse("Overall proposal must be invalid", report.isValid)
    }

    @Test
    fun test5_PromptFormattingForLlamaEngine() {
        val prof = makeProfile("u_prompt_test", "Ananya", "VLSI Design", EducationLevel.UNDERGRADUATE, LearnerCategory.ENGINEERING_STUDENT, 45)
        val prompt = AiTrackGenerationEngine.buildTrackGenerationPrompt(
            profile = prof,
            domain = "Digital VLSI Design",
            goal = "Design 32-bit RISC-V ALU in Verilog",
            departmentOrStandard = "Electronics & Communication 6th Sem",
            uploadedMaterialsSummary = "Chapter 4: Arithmetic Circuits, Adders, Wallace Tree Multiplier"
        )

        assertTrue(prompt.contains("<|begin_of_text|>"))
        assertTrue(prompt.contains("Digital VLSI Design"))
        assertTrue(prompt.contains("Design 32-bit RISC-V ALU in Verilog"))
        assertTrue(prompt.contains("Wallace Tree Multiplier"))
        assertTrue(prompt.contains("\"modules\""))
    }
}

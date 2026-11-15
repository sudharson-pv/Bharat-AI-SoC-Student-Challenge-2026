package com.learnmate.ai.assessment

import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine
import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine.DiagnosticQuestion
import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine.DiagnosticRubric
import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine.QuestionProvenance
import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 6.16.2 Priority 3: Comprehensive Test Suite for AI Diagnostic Question Generation & Deterministic Validation.
 *
 * Verifies:
 * 1. Multi-domain diagnostic suite generation across 10 diverse educational domains.
 * 2. All 6 pedagogical question types (MCQ_SINGLE, MCQ_MULTI, NUMERICAL, SHORT_ANSWER, CONCEPTUAL_EXPLANATION, CASE_STUDY).
 * 3. Pre-flight answer-key validation to prevent malformed or hallucinated questions from reaching learners.
 * 4. Learner-facing projection security (complete redaction of answers, keys, rubrics, and explanations).
 * 5. Deterministic scoring across all formats (zero LLM grading, 100% Kotlin compiled validation).
 * 6. Llama-3.2-Instruct prompt construction with proper tokenizer header tokens and syllabus grounding.
 */
class Gate6162AiDiagnosticQuestionComprehensiveTest {

    data class TestDomainProfile(
        val domainName: String,
        val topicName: String,
        val educationLevel: String,
        val learningGoal: String,
        val syllabusSnippet: String
    )

    private val tenUniversalDomains = listOf(
        TestDomainProfile(
            domainName = "Secondary Physics",
            topicName = "Electromagnetic Induction & Faraday Law",
            educationLevel = "NCERT Class 12 Senior Secondary",
            learningGoal = "Master magnetic flux rate of change and Lenz law direction",
            syllabusSnippet = "NCERT Physics Vol 1 Ch 6: Magnetic Flux, Faraday Law, Induced EMF, Lenz Rule"
        ),
        TestDomainProfile(
            domainName = "Polytechnic Electrical",
            topicName = "Three-Phase Star-Delta Motor Starting",
            educationLevel = "State Technical Board Diploma Year 2",
            learningGoal = "Calculate starting current reduction in induction motors",
            syllabusSnippet = "Diploma Electrical Machines-II: Induction Motor Starters, Line vs Phase values"
        ),
        TestDomainProfile(
            domainName = "UG Computer Science",
            topicName = "Single-Source Shortest Paths: Dijkstra vs Bellman-Ford",
            educationLevel = "B.Tech Computer Science Semester 4",
            learningGoal = "Analyze time complexity and negative weight cycle handling",
            syllabusSnippet = "CS204 Algorithms: Graph Theory, Priority Queues, Dynamic Programming"
        ),
        TestDomainProfile(
            domainName = "UG VLSI Design",
            topicName = "CMOS Inverter Dynamic Power & Noise Margins",
            educationLevel = "B.Tech ECE VLSI Semester 6",
            learningGoal = "Compute high and low noise margins and switching power dissipation",
            syllabusSnippet = "EC601 CMOS Digital ICs: VTC Curves, VIL, VIH, Short-Circuit Power"
        ),
        TestDomainProfile(
            domainName = "UG Medicine & Anatomy",
            topicName = "Cardiac Action Potential Phases & SA Node Pacemaker",
            educationLevel = "MBBS Year 1 Human Physiology",
            learningGoal = "Explain Phase 0 to Phase 4 ion channel kinetics in cardiomyocytes",
            syllabusSnippet = "Guyton & Hall Physiology Ch 9: Cardiac Muscle, Automaticity, Calcium plateau"
        ),
        TestDomainProfile(
            domainName = "Higher Economics",
            topicName = "IS-LM Equilibrium & Monetary Transmission",
            educationLevel = "M.A. Macroeconomics Semester 2",
            learningGoal = "Evaluate fiscal vs monetary multiplier under liquidity trap conditions",
            syllabusSnippet = "Econ 501 Advanced Macroeconomics: Goods Market Equilibrium, Money Demand, ZLB"
        ),
        TestDomainProfile(
            domainName = "Financial Accounting",
            topicName = "Indirect Cash Flow Statement & Non-Cash Working Capital",
            educationLevel = "B.Com Professional Semester 3",
            learningGoal = "Reconcile net profit to operating cash flows under IFRS/IndAS",
            syllabusSnippet = "Corporate Accounting: Operating Activities, Depreciation add-back, Working Capital"
        ),
        TestDomainProfile(
            domainName = "UPSC Civil Services",
            topicName = "Fundamental Rights & Constitutional Writs under Article 32",
            educationLevel = "General Studies Paper II Governance & Constitution",
            learningGoal = "Distinguish Habeas Corpus, Mandamus, Quo Warranto, and Certiorari",
            syllabusSnippet = "Indian Polity M. Laxmikanth: Supreme Court Jurisdiction, Basic Structure Doctrine"
        ),
        TestDomainProfile(
            domainName = "Doctoral Edge AI",
            topicName = "INT8 Post-Training Quantization & Gradient Bounds",
            educationLevel = "Ph.D. Computer Engineering Research",
            learningGoal = "Formulate scale and zero-point calibration minimizing KL divergence",
            syllabusSnippet = "Advanced Neural Compression: Symmetric vs Asymmetric Quantization, Per-channel scale"
        ),
        TestDomainProfile(
            domainName = "Vocational Electrician",
            topicName = "Substation Pipe Earthing & Soil Resistivity Testing",
            educationLevel = "ITI Electrician National Trade Certificate",
            learningGoal = "Measure earth electrode resistance using Wenner four-point method",
            syllabusSnippet = "ITI Trade Theory Year 1: Neutral grounding, Earth pit maintenance, Megger testing"
        )
    )

    @Test
    fun test1_GenerateSuiteAcross10DiverseLearnerDomains() {
        assertEquals("Must cover exactly 10 diverse educational domains", 10, tenUniversalDomains.size)

        for (domainProfile in tenUniversalDomains) {
            val suite = AiDiagnosticQuestionEngine.generateComprehensiveDiagnosticSuite(
                domain = domainProfile.domainName,
                topic = domainProfile.topicName,
                level = domainProfile.educationLevel,
                goals = domainProfile.learningGoal,
                uploadedMaterialsSummary = domainProfile.syllabusSnippet
            )

            // 1. Suite must have 6 distinct pedagogical questions
            assertEquals("Suite for '${domainProfile.domainName}' must contain 6 questions", 6, suite.size)

            val types = suite.map { it.type }
            assertTrue(types.contains(QuestionType.MCQ_SINGLE))
            assertTrue(types.contains(QuestionType.MCQ_MULTI))
            assertTrue(types.contains(QuestionType.NUMERICAL))
            assertTrue(types.contains(QuestionType.SHORT_ANSWER))
            assertTrue(types.contains(QuestionType.CONCEPTUAL_EXPLANATION))
            assertTrue(types.contains(QuestionType.CASE_STUDY))

            // 2. Each question must have complete metadata and pass pre-flight validation
            for (q in suite) {
                assertTrue("Question ID must not be blank", q.questionId.isNotBlank())
                assertTrue("Prompt must not be blank", q.prompt.isNotBlank())
                assertTrue("Learning objective must be defined", q.learningObjective.isNotBlank())
                assertTrue("Difficulty must be valid", q.difficulty in listOf("EASY", "MEDIUM", "HARD"))
                assertTrue("Reference citation must be present", q.referenceCitation.isNotBlank())
                assertEquals(QuestionProvenance.AI_GENERATED_EXECUTORCH_VALIDATED, q.provenance)

                val isValid = AiDiagnosticQuestionEngine.validateQuestionAnswerKey(q)
                assertTrue("Pre-flight answer key validation must pass for question ${q.questionId}", isValid)
            }
        }
    }

    @Test
    fun test2_PreFlightAnswerKeyValidationIntegrity() {
        val validMcq = DiagnosticQuestion(
            questionId = "val_mcq",
            nodeId = "node_1",
            type = QuestionType.MCQ_SINGLE,
            learningObjective = "Identify fundamental unit",
            difficulty = "EASY",
            prompt = "What is the SI unit of resistance?",
            options = listOf("Ohm", "Volt", "Ampere", "Watt"),
            correctIndices = listOf(0),
            verifiedExplanation = "Ohm is the unit of electrical resistance.",
            referenceCitation = "NCERT Physics"
        )
        assertTrue(AiDiagnosticQuestionEngine.validateQuestionAnswerKey(validMcq))

        // Corrupted MCQ: Out-of-bounds correct index
        val badIndexMcq = validMcq.copy(correctIndices = listOf(99))
        assertFalse("Out-of-bounds index must fail pre-flight validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(badIndexMcq))

        // Corrupted MCQ: Duplicate options
        val duplicateOptionsMcq = validMcq.copy(options = listOf("Ohm", "Ohm", "Volt", "Ampere"))
        assertFalse("Duplicate options must fail pre-flight validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(duplicateOptionsMcq))

        // Corrupted MCQ: Empty options
        val emptyOptionsMcq = validMcq.copy(options = emptyList(), correctIndices = emptyList())
        assertFalse("Empty options must fail pre-flight validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(emptyOptionsMcq))

        // Valid Numerical
        val validNum = DiagnosticQuestion(
            questionId = "val_num",
            nodeId = "node_2",
            type = QuestionType.NUMERICAL,
            learningObjective = "Calculate speed of light",
            difficulty = "MEDIUM",
            prompt = "Calculate velocity in 10^8 m/s",
            targetNumericalValue = 3.0,
            numericalTolerance = 0.05,
            verifiedExplanation = "c ~ 3.0 x 10^8 m/s",
            referenceCitation = "Standard Physics"
        )
        assertTrue(AiDiagnosticQuestionEngine.validateQuestionAnswerKey(validNum))

        // Corrupted Numerical: Null target value
        val nullNum = validNum.copy(targetNumericalValue = null)
        assertFalse("Null target must fail validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(nullNum))

        // Corrupted Numerical: Negative tolerance
        val badTolNum = validNum.copy(numericalTolerance = -0.1)
        assertFalse("Negative tolerance must fail validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(badTolNum))

        // Valid Short Answer
        val validShort = DiagnosticQuestion(
            questionId = "val_short",
            nodeId = "node_3",
            type = QuestionType.SHORT_ANSWER,
            learningObjective = "Identify algorithm type",
            difficulty = "EASY",
            prompt = "What paradigm does Merge Sort use?",
            expectedKeywords = listOf("divide and conquer"),
            expectedAnswer = "Divide and conquer",
            verifiedExplanation = "Merge sort recursively divides and merges.",
            referenceCitation = "CLRS"
        )
        assertTrue(AiDiagnosticQuestionEngine.validateQuestionAnswerKey(validShort))

        // Corrupted Short Answer: Empty keywords and empty expected answer
        val emptyShort = validShort.copy(expectedKeywords = emptyList(), expectedAnswer = "")
        assertFalse("Empty answer keys must fail validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(emptyShort))

        // Valid Conceptual with Rubric
        val validConceptual = DiagnosticQuestion(
            questionId = "val_concept",
            nodeId = "node_4",
            type = QuestionType.CONCEPTUAL_EXPLANATION,
            learningObjective = "Explain encapsulation",
            difficulty = "HARD",
            prompt = "Explain encapsulation in OOP.",
            rubric = DiagnosticRubric(
                requiredKeyTerms = listOf("data hiding", "class", "getter", "setter"),
                minimumWordCount = 15,
                accreditedCitation = "OOP Principles"
            ),
            verifiedExplanation = "Encapsulation bundles data and methods.",
            referenceCitation = "OOP Principles"
        )
        assertTrue(AiDiagnosticQuestionEngine.validateQuestionAnswerKey(validConceptual))

        // Corrupted Conceptual: Null rubric
        val nullRubric = validConceptual.copy(rubric = null)
        assertFalse("Null rubric must fail validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(nullRubric))

        // Corrupted Conceptual: Blank required key terms
        val emptyRubricTerms = validConceptual.copy(
            rubric = DiagnosticRubric(requiredKeyTerms = emptyList())
        )
        assertFalse("Empty rubric key terms must fail validation",
            AiDiagnosticQuestionEngine.validateQuestionAnswerKey(emptyRubricTerms))
    }

    @Test
    fun test3_HiddenAnswerKeySecurityInLearnerFacingView() {
        val original = DiagnosticQuestion(
            questionId = "sec_q1",
            nodeId = "node_sec",
            type = QuestionType.MCQ_SINGLE,
            learningObjective = "Evaluate gate propagation delay",
            difficulty = "HARD",
            prompt = "Which gate exhibits the lowest propagation delay?",
            options = listOf("Inverter", "NAND2", "NOR2", "XOR2"),
            correctIndices = listOf(0),
            targetNumericalValue = 12.5,
            expectedKeywords = listOf("inverter", "not gate"),
            expectedAnswer = "Inverter",
            rubric = DiagnosticRubric(requiredKeyTerms = listOf("capacitance", "fanout")),
            verifiedExplanation = "Inverter has the lowest logical effort (1.0).",
            referenceCitation = "Weste & Harris CMOS VLSI Design"
        )

        val learnerView = AiDiagnosticQuestionEngine.getLearnerFacingQuestion(original)

        // Sensitive evaluation fields must be stripped clean
        assertTrue("correctIndices must be empty in learner view", learnerView.correctIndices.isEmpty())
        assertNull("targetNumericalValue must be null in learner view", learnerView.targetNumericalValue)
        assertTrue("expectedKeywords must be empty in learner view", learnerView.expectedKeywords.isEmpty())
        assertEquals("expectedAnswer must be blank in learner view", "", learnerView.expectedAnswer)
        assertNull("rubric must be stripped in learner view", learnerView.rubric)
        assertEquals("verifiedExplanation must be stripped in learner view", "", learnerView.verifiedExplanation)
        assertEquals("referenceCitation must be stripped in learner view", "", learnerView.referenceCitation)

        // Essential question presentation fields must remain intact
        assertEquals(original.questionId, learnerView.questionId)
        assertEquals(original.nodeId, learnerView.nodeId)
        assertEquals(original.type, learnerView.type)
        assertEquals(original.learningObjective, learnerView.learningObjective)
        assertEquals(original.difficulty, learnerView.difficulty)
        assertEquals(original.prompt, learnerView.prompt)
        assertEquals(original.options, learnerView.options)
    }

    @Test
    fun test4_DeterministicGradingEngine_AllSixFormats() {
        // 1. MCQ_SINGLE
        val mcqSingle = DiagnosticQuestion(
            questionId = "grade_mcq_s",
            nodeId = "n1",
            type = QuestionType.MCQ_SINGLE,
            learningObjective = "Ohm's law",
            difficulty = "EASY",
            prompt = "What is V = I * R?",
            options = listOf("Ohm's Law", "Faraday's Law", "Ampere's Law"),
            correctIndices = listOf(0),
            verifiedExplanation = "V=IR defines Ohm's Law.",
            referenceCitation = "Basic Circuit Theory"
        )
        val mcqPass = AiDiagnosticQuestionEngine.evaluateMcq(mcqSingle, listOf(0))
        assertTrue(mcqPass.isCorrect)
        assertEquals(100, mcqPass.scorePercent)
        assertEquals(1.0, mcqPass.earnedMarks, 0.001)

        val mcqFail = AiDiagnosticQuestionEngine.evaluateMcq(mcqSingle, listOf(1))
        assertFalse(mcqFail.isCorrect)
        assertEquals(0, mcqFail.scorePercent)

        // 2. MCQ_MULTI
        val mcqMulti = DiagnosticQuestion(
            questionId = "grade_mcq_m",
            nodeId = "n2",
            type = QuestionType.MCQ_MULTI,
            learningObjective = "Operating system kernels",
            difficulty = "MEDIUM",
            prompt = "Which are monolithic kernels?",
            options = listOf("Linux", "FreeBSD", "QNX", "MS-DOS"),
            correctIndices = listOf(0, 1),
            verifiedExplanation = "Linux and FreeBSD are monolithic; QNX is a microkernel.",
            referenceCitation = "Silberschatz OS Concepts"
        )
        val multiPass = AiDiagnosticQuestionEngine.evaluateMcq(mcqMulti, listOf(0, 1))
        assertTrue(multiPass.isCorrect)
        assertEquals(100, multiPass.scorePercent)

        val multiPartial = AiDiagnosticQuestionEngine.evaluateMcq(mcqMulti, listOf(0))
        assertFalse("Partial selection must not receive full score in strict mode", multiPartial.isCorrect)
        assertEquals(0, multiPartial.scorePercent)

        // 3. NUMERICAL
        val numerical = DiagnosticQuestion(
            questionId = "grade_num",
            nodeId = "n3",
            type = QuestionType.NUMERICAL,
            learningObjective = "Kinematics",
            difficulty = "MEDIUM",
            prompt = "Find time t for d=20m, a=10m/s^2 from rest.",
            targetNumericalValue = 2.0,
            numericalTolerance = 0.05,
            verifiedExplanation = "t = sqrt(2d/a) = sqrt(40/10) = 2.0 s.",
            referenceCitation = "Physics 101"
        )
        val numExact = AiDiagnosticQuestionEngine.evaluateNumerical(numerical, 2.0)
        assertTrue(numExact.isCorrect)

        val numInTol = AiDiagnosticQuestionEngine.evaluateNumerical(numerical, 2.04)
        assertTrue(numInTol.isCorrect)

        val numOutTol = AiDiagnosticQuestionEngine.evaluateNumerical(numerical, 2.10)
        assertFalse(numOutTol.isCorrect)

        // 4. SHORT_ANSWER
        val shortAnswer = DiagnosticQuestion(
            questionId = "grade_short",
            nodeId = "n4",
            type = QuestionType.SHORT_ANSWER,
            learningObjective = "CPU Scheduling",
            difficulty = "EASY",
            prompt = "What scheduling algorithm guarantees starvation-free fair time sharing?",
            expectedKeywords = listOf("round robin", "rr"),
            verifiedExplanation = "Round Robin provides cyclic time slices.",
            referenceCitation = "OS Architecture"
        )
        val shortPass = AiDiagnosticQuestionEngine.evaluateShortAnswer(shortAnswer, "The Round Robin (RR) algorithm.")
        assertTrue(shortPass.isCorrect)
        assertEquals(100, shortPass.scorePercent)

        val shortFail = AiDiagnosticQuestionEngine.evaluateShortAnswer(shortAnswer, "First Come First Served.")
        assertFalse(shortFail.isCorrect)

        // 5. CONCEPTUAL_EXPLANATION
        val conceptual = DiagnosticQuestion(
            questionId = "grade_concept",
            nodeId = "n5",
            type = QuestionType.CONCEPTUAL_EXPLANATION,
            learningObjective = "Database Transactions",
            difficulty = "HARD",
            prompt = "Explain ACID properties in relational databases.",
            rubric = DiagnosticRubric(
                requiredKeyTerms = listOf("atomicity", "consistency", "isolation", "durability"),
                minimumWordCount = 15,
                accreditedCitation = "Database System Concepts"
            ),
            verifiedExplanation = "ACID ensures reliable transaction processing.",
            referenceCitation = "Silberschatz DB"
        )
        val conceptGood = "Relational databases enforce atomicity, consistency, isolation, and durability to maintain state under concurrent execution and failures."
        val conceptPass = AiDiagnosticQuestionEngine.evaluateConceptualRubric(conceptual, conceptGood)
        assertTrue("Comprehensive answer must pass rubric", conceptPass.isCorrect)
        assertTrue(conceptPass.scorePercent >= 70)

        val conceptIncomplete = "It means atomicity and consistency."
        val conceptFail = AiDiagnosticQuestionEngine.evaluateConceptualRubric(conceptual, conceptIncomplete)
        assertFalse("Incomplete answer must fail minimum rubric threshold", conceptFail.isCorrect)

        // 6. PROBLEM_SOLVING / CASE_STUDY
        val caseStudy = DiagnosticQuestion(
            questionId = "grade_case",
            nodeId = "n6",
            type = QuestionType.CASE_STUDY,
            learningObjective = "Fault Tolerance",
            difficulty = "HARD",
            prompt = "Diagnose network split-brain scenario.",
            rubric = DiagnosticRubric(
                requiredKeyTerms = listOf("quorum", "partition", "consensus", "raft"),
                minimumWordCount = 15,
                accreditedCitation = "Distributed Systems"
            ),
            verifiedExplanation = "Split-brain is resolved via quorum and consensus protocols.",
            referenceCitation = "Distributed Systems Principles"
        )
        val caseAnswer = "During a network partition, the cluster must rely on a majority quorum and consensus protocols like Raft to prevent dual leaders."
        val casePass = AiDiagnosticQuestionEngine.evaluateProblemSolving(caseStudy, caseAnswer, marksPerQuestion = 5.0)
        assertTrue(casePass.isCorrect)
        assertTrue(casePass.earnedMarks >= 3.5)
    }

    @Test
    fun test5_PromptGenerationContractForLlamaInstruct() {
        val prompt = AiDiagnosticQuestionEngine.buildQuestionGenerationPrompt(
            domain = "Secondary Chemistry",
            level = "NCERT Class 10",
            topic = "Acids, Bases and Salts",
            goals = "Understand pH scale and neutralization reactions",
            format = QuestionType.NUMERICAL,
            uploadedMaterialsSummary = "Chapter 2: Acids and Bases, pH scale 0-14, H+ concentration"
        )

        // Must conform to Llama-3.2-Instruct special tokens
        assertTrue(prompt.contains("<|begin_of_text|>"))
        assertTrue(prompt.contains("<|start_header_id|>system<|end_header_id|>"))
        assertTrue(prompt.contains("<|eot_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>user<|end_header_id|>"))
        assertTrue(prompt.contains("<|start_header_id|>assistant<|end_header_id|>"))

        // Must inject domain constraints and syllabus context
        assertTrue(prompt.contains("Domain: Secondary Chemistry"))
        assertTrue(prompt.contains("Topic: Acids, Bases and Salts"))
        assertTrue(prompt.contains("Chapter 2: Acids and Bases"))
        assertTrue(prompt.contains("Format requested: NUMERICAL"))
    }
}


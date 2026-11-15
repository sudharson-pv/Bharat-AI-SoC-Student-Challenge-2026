package com.learnmate.ai.assessment

import com.learnmate.ai.data.assessment.AiDiagnosticQuestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDiagnosticQuestionEngineTest {

    @Test
    fun testEvaluateMcqDeterministic() {
        val question = AiDiagnosticQuestionEngine.DiagnosticQuestion(
            questionId = "mcq_1",
            nodeId = "cbse_sci10_ch01",
            type = AiDiagnosticQuestionEngine.QuestionType.MCQ_SINGLE,
            prompt = "What type of reaction is 2H2 + O2 -> 2H2O?",
            options = listOf("Combination", "Decomposition", "Displacement", "Double Displacement"),
            correctIndices = listOf(0),
            verifiedExplanation = "Two elements combine to form a single compound.",
            referenceCitation = "NCERT Class 10 Science, Chapter 1"
        )

        // Correct selection
        val resultCorrect = AiDiagnosticQuestionEngine.evaluateMcq(question, listOf(0))
        assertTrue(resultCorrect.isCorrect)
        assertEquals(100, resultCorrect.scorePercent)

        // Incorrect selection
        val resultIncorrect = AiDiagnosticQuestionEngine.evaluateMcq(question, listOf(1))
        assertFalse(resultIncorrect.isCorrect)
        assertEquals(0, resultIncorrect.scorePercent)
    }

    @Test
    fun testEvaluateNumericalTolerance() {
        val question = AiDiagnosticQuestionEngine.DiagnosticQuestion(
            questionId = "num_1",
            nodeId = "physics_gravity",
            type = AiDiagnosticQuestionEngine.QuestionType.NUMERICAL,
            prompt = "What is the acceleration due to gravity g on Earth in m/s^2?",
            targetNumericalValue = 9.81,
            numericalTolerance = 0.05,
            verifiedExplanation = "Standard acceleration due to gravity is approximately 9.81 m/s^2.",
            referenceCitation = "Halliday & Resnick, Principles of Physics"
        )

        // Value within tolerance: 9.83 is within 9.81 +/- 0.05
        val resultPass = AiDiagnosticQuestionEngine.evaluateNumerical(question, 9.83)
        assertTrue(resultPass.isCorrect)
        assertEquals(100, resultPass.scorePercent)

        // Value outside tolerance: 9.90 is outside 9.81 +/- 0.05
        val resultFail = AiDiagnosticQuestionEngine.evaluateNumerical(question, 9.90)
        assertFalse(resultFail.isCorrect)
        assertEquals(0, resultFail.scorePercent)
    }

    @Test
    fun testEvaluateShortAnswerNormalized() {
        val question = AiDiagnosticQuestionEngine.DiagnosticQuestion(
            questionId = "short_1",
            nodeId = "cs_ds_stack",
            type = AiDiagnosticQuestionEngine.QuestionType.SHORT_ANSWER,
            prompt = "What principle defines a Stack data structure?",
            expectedKeywords = listOf("LIFO", "last in first out"),
            verifiedExplanation = "Stacks operate strictly on the Last In First Out order.",
            referenceCitation = "Cormen et al., Introduction to Algorithms"
        )

        // User answers with varied case and punctuation
        val resultPass = AiDiagnosticQuestionEngine.evaluateShortAnswer(question, "It follows lifo principle!")
        assertTrue(resultPass.isCorrect)
        assertEquals(100, resultPass.scorePercent)

        val resultFail = AiDiagnosticQuestionEngine.evaluateShortAnswer(question, "First in first out queue.")
        assertFalse(resultFail.isCorrect)
    }

    @Test
    fun testEvaluateConceptualRubric() {
        val question = AiDiagnosticQuestionEngine.DiagnosticQuestion(
            questionId = "rubric_1",
            nodeId = "ee_kirchhoff",
            type = AiDiagnosticQuestionEngine.QuestionType.CONCEPTUAL_EXPLANATION,
            prompt = "Explain Kirchhoff's Current Law and its conservation basis.",
            rubric = AiDiagnosticQuestionEngine.DiagnosticRubric(
                requiredKeyTerms = listOf("current", "node", "conservation", "charge"),
                minimumWordCount = 10,
                accreditedCitation = "Boylestad, Introductory Circuit Analysis"
            ),
            verifiedExplanation = "KCL states that algebraic sum of currents entering a node is zero, based on conservation of electric charge.",
            referenceCitation = "Boylestad, Introductory Circuit Analysis"
        )

        val completeAnswer = "Kirchhoff's current law states that total current entering any node equals total current leaving, directly conserving charge."
        val resultPass = AiDiagnosticQuestionEngine.evaluateConceptualRubric(question, completeAnswer)

        assertTrue(resultPass.isCorrect)
        assertTrue("Complete answer must score >= 80%", resultPass.scorePercent >= 80)
        assertTrue(resultPass.feedback.contains("Rubric Score"))
    }
}


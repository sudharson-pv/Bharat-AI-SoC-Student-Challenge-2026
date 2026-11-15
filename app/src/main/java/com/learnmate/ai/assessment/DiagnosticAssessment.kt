package com.learnmate.ai.assessment

data class DiagnosticQuestion(
    val id: String,
    val domainId: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int
)

data class AssessmentResult(
    val score: Int,
    val totalQuestions: Int,
    val identifiedSkillGaps: List<String>
)


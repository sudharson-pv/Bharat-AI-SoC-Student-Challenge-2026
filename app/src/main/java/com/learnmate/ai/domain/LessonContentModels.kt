package com.learnmate.ai.domain

/**
 * Gate 6.4: Verified Lesson Content and Learning Journey Data Contracts.
 * Supports structured offline learning modules, visual diagrams, worked examples,
 * key terms glossary, accredited textbook references, and deterministic knowledge checks.
 */

enum class LearningProgressStatus(val displayName: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    NEEDS_REVISION("Needs Revision");

    companion object {
        fun fromString(value: String?): LearningProgressStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NOT_STARTED
    }
}

enum class KnowledgeQuestionFormat {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE;

    companion object {
        fun fromString(value: String?): KnowledgeQuestionFormat =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SINGLE_CHOICE
    }
}

data class KeyTerm(
    val term: String,
    val definition: String
)

data class WorkedExample(
    val title: String,
    val problemStatement: String,
    val steps: List<String>,
    val finalAnswer: String
)

data class TextDiagram(
    val title: String,
    val caption: String,
    val asciiArt: String,
    val diagramType: String = "SCHEMATIC" // SCHEMATIC, FLOWCHART, TIMING, ARCHITECTURE, COMPARISON
)

data class LessonSection(
    val heading: String,
    val content: String,
    val keyTakeaway: String? = null,
    val formula: String? = null
)

data class DeterministicQuestion(
    val questionId: String,
    val nodeId: String,
    val format: KnowledgeQuestionFormat,
    val questionText: String,
    val options: List<String>,
    val correctIndices: List<Int>,
    val verifiedExplanation: String,
    val referenceCitation: String,
    val marks: Double = 1.0,
    val difficultyLevel: String = "INTERMEDIATE",
    val languageCode: String = "en",
    val learningObjectiveId: String = "",
    val cognitiveLevel: String = "CONCEPTUAL",
    val provenanceType: String = "ACCREDITED_TEXTBOOK"
)

data class LessonModule(
    val nodeId: String,
    val courseCode: String,
    val topicTitle: String,
    val discipline: String,
    val educationLevel: String,
    val estimatedMinutes: Int = 30,
    val learningObjectives: List<String>,
    val prerequisites: List<String> = emptyList(),
    val sections: List<LessonSection>,
    val diagrams: List<TextDiagram> = emptyList(),
    val workedExamples: List<WorkedExample> = emptyList(),
    val keyTerms: List<KeyTerm> = emptyList(),
    val summary: String,
    val references: List<CurriculumReference>,
    val knowledgeChecks: List<DeterministicQuestion> = emptyList(),
    val languageCode: String = "en",
    val isFallbackLanguage: Boolean = false,
    val fallbackNotice: String? = null,
    val isStarterCollection: Boolean = true,
    val starterDisclaimer: String = "Starter Learning Module: Aligned with verified accredited references. Does not represent complete syllabus coverage."
)

data class QuestionSubmissionResult(
    val questionId: String,
    val selectedIndices: List<Int>,
    val isCorrect: Boolean,
    val scoreEarned: Double,
    val explanation: String,
    val referenceCitation: String
)

data class KnowledgeCheckSessionState(
    val nodeId: String,
    val totalQuestions: Int,
    val answeredCount: Int = 0,
    val correctCount: Int = 0,
    val scorePercent: Double = 0.0,
    val isSubmitted: Boolean = false,
    val results: Map<String, QuestionSubmissionResult> = emptyMap()
)

data class TrackTopicProgress(
    val trackId: String,
    val userId: String,
    val nodeId: String,
    val status: LearningProgressStatus = LearningProgressStatus.NOT_STARTED,
    val scorePercent: Double? = null,
    val completedAtEpochMs: Long? = null,
    val lastAccessedEpochMs: Long = System.currentTimeMillis()
)

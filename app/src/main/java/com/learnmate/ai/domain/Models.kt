package com.learnmate.ai.domain

enum class AudienceLevel(val displayName: String, val description: String) {
    HIGH_SCHOOL("Foundational", "Clear analogies, simplified explanations, zero heavy jargon"),
    UNDERGRADUATE("Academic", "Rigorous conceptual definitions, standard notation, systematic steps"),
    PROFESSIONAL("Industry Specialist", "Pragmatic edge cases, low-level architecture, performance trade-offs");

    companion object {
        fun fromDisplayName(name: String): AudienceLevel {
            return entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) } ?: UNDERGRADUATE
        }
    }
}

enum class EducationLevel(val displayName: String, val description: String) {
    PRIMARY("Primary School", "Grades 1-5 foundational concepts"),
    SECONDARY("Secondary School", "Grades 6-10 standard syllabus"),
    HIGHER_SECONDARY("Higher Secondary", "Grades 11-12 pre-university / intermediate"),
    DIPLOMA("Diploma / Polytechnic", "Applied technical and vocational syllabus"),
    UNDERGRADUATE("Undergraduate / Bachelor's", "Standard degree level coursework"),
    POSTGRADUATE("Postgraduate / Master's", "Advanced theoretical and applied curriculum"),
    DOCTORAL("Doctoral / PhD", "Specialized research and doctoral studies"),
    CONTINUING_EDUCATION("Professional / Continuing", "Lifelong learning and workplace competencies");

    companion object {
        fun fromString(value: String): EducationLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: if (value.equals("POLYTECHNIC", ignoreCase = true) || value.equals("VOCATIONAL_DIPLOMA", ignoreCase = true)) DIPLOMA
                else UNDERGRADUATE
    }
}

enum class LearnerCategory(val displayName: String, val description: String) {
    SCHOOL_STUDENT("School Student", "Primary, secondary or higher secondary student"),
    DIPLOMA_STUDENT("Diploma / Polytechnic Student", "Technical polytechnic / vocational diploma student"),
    VOCATIONAL_TRAINEE("Vocational / Trade Trainee", "ITI, trade apprenticeships, and vocational craft certifications"),
    ENGINEERING_STUDENT("Engineering Student", "Engineering degree across all branches"),
    ARTS_SCIENCE_STUDENT("Arts & Science Student", "Humanities, sciences, commerce, or social sciences"),
    MANAGEMENT_MBA_STUDENT("Management / MBA Student", "Business administration and management studies"),
    COMPETITIVE_EXAM_ASPIRANT("Competitive Exam Aspirant", "UPSC, GATE, CAT, JEE, NEET, or state exams"),
    RESEARCH_SCHOLAR("Research Scholar / PhD", "Doctoral candidate or academic researcher"),
    LIFELONG_LEARNER("Lifelong / Professional Learner", "Independent or upskilling professional");

    companion object {
        fun fromString(value: String): LearnerCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: if (value.equals("COLLEGE_STUDENT", ignoreCase = true)) ENGINEERING_STUDENT
                else if (value.equals("VOCATIONAL", ignoreCase = true) || value.equals("VOCATIONAL_TRADES", ignoreCase = true) || value.equals("VOCATIONAL_LEARNER", ignoreCase = true)) VOCATIONAL_TRAINEE
                else if (value.equals("POLYTECHNIC", ignoreCase = true)) DIPLOMA_STUDENT
                else ENGINEERING_STUDENT
    }
}

enum class LearningMode(val displayName: String, val description: String) {
    ACADEMIC("Academic Learning", "Curriculum-aligned textbook study and examination preparation"),
    COMPETITIVE_EXAM("Competitive Exam Prep", "Exam syllabus, time-bounded drills, and negative marking"),
    RESEARCH("Research & Scholar Mode", "Literature review, methodology, and theoretical depth"),
    PROFESSIONAL_SKILLS("Professional Skills", "Applied industry competencies, case studies, and practical mastery");

    companion object {
        fun fromString(value: String): LearningMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ACADEMIC
    }
}

enum class ProficiencyLevel(val displayName: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
    MASTERY("Mastery");

    companion object {
        fun fromString(value: String): ProficiencyLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BEGINNER
    }
}

data class LearningTrack(
    val trackId: String,
    val userId: String,
    val title: String,
    val learningMode: LearningMode = LearningMode.ACADEMIC,
    val targetCurriculumNodeId: String? = null,
    val targetExamName: String? = null,
    val targetExamDateEpochMs: Long? = null,
    val dailyCommitmentMinutes: Int = 60,
    val isTrackActive: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val lastAccessedEpochMs: Long = System.currentTimeMillis()
)

data class CurriculumNode(
    val nodeId: String,
    val parentNodeId: String?,
    val packId: String,
    val nodeType: String,
    val code: String,
    val title: String,
    val description: String = "",
    val estimatedMinutes: Int = 30,
    val orderIndex: Int = 0
)

data class CurriculumResource(
    val resourceId: String,
    val nodeId: String,
    val languageCode: String = "en",
    val verifiedExplanation: String,
    val referenceTitle: String,
    val referenceAuthor: String,
    val referenceEdition: String,
    val referenceChapter: String,
    val referenceSection: String,
    val referenceIdentifier: String,
    val coreKeywords: List<String> = emptyList()
)

data class NodePrerequisite(
    val prerequisiteId: String,
    val targetNodeId: String,
    val requiredNodeId: String,
    val requirementType: String = "MANDATORY"
)

data class DomainCategory(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val subdomains: List<String> = emptyList(),
    val coreTopics: List<String> = emptyList()
)

enum class ConsentStatus {
    NOT_REQUIRED,  // For adult learners (18+)
    PENDING,       // Minor declared, awaiting independent guardian verification workflow
    VERIFIED;      // Verified through external cryptographic or institutional parental verification

    companion object {
        fun fromString(value: String?): ConsentStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
        }
    }
}

data class LearnerProfile(
    val userId: String,
    val name: String,
    val targetDomain: String,
    val subdomain: String = "",
    val goal: String = "",
    val targetRole: String = "",
    val previousBackground: String = "",
    val currentLevel: String = "Beginner", // Beginner, Intermediate, Advanced
    val audienceLevel: AudienceLevel = AudienceLevel.UNDERGRADUATE,
    val dailyStudyMinutes: Int = 60,
    val weeklyStudyDays: Int = 5,
    val currentTopic: String = "",
    val onboardingCompleted: Boolean = false,
    val assessmentCompleted: Boolean = false,
    val isDemoAccount: Boolean = false,
    val preferredLanguageCode: String = "en",
    val preferredContentLanguageCode: String = "en",
    val educationLevel: EducationLevel = EducationLevel.UNDERGRADUATE,
    val learnerCategory: LearnerCategory = LearnerCategory.ENGINEERING_STUDENT,
    val learningGoals: List<String> = emptyList(),
    val isMinor: Boolean = false,
    val guardianConsentGiven: Boolean = false,
    val consentStatus: ConsentStatus = if (isMinor) (if (guardianConsentGiven) ConsentStatus.PENDING else ConsentStatus.PENDING) else ConsentStatus.NOT_REQUIRED
) {
    /**
     * Strict Privacy Rule: Minor behavioral profiling is locked out unless
     * consent has undergone independent verification (status == VERIFIED).
     * Selecting a local checkbox only results in PENDING status.
     */
    val isBehavioralProfilingAllowed: Boolean
        get() = !isMinor || consentStatus == ConsentStatus.VERIFIED
}

data class CurriculumReference(
    val title: String = "",
    val author: String = "",
    val edition: String = "",
    val chapter: String = "",
    val section: String = "",
    val identifier: String = "" // ISBN or standard identifier
) {
    val isAvailable: Boolean get() = title.isNotBlank()
    val formattedCitation: String get() {
        if (!isAvailable) return "Awaiting textbook verification"
        val parts = mutableListOf<String>()
        if (title.isNotBlank()) parts.add(title)
        if (author.isNotBlank()) parts.add("by $author")
        if (edition.isNotBlank()) parts.add("($edition)")
        if (chapter.isNotBlank()) parts.add("Ch. $chapter")
        if (section.isNotBlank()) parts.add("Sec. $section")
        if (identifier.isNotBlank()) parts.add("[$identifier]")
        return parts.joinToString(", ")
    }
}

data class VerifiedTopic(
    val topicId: String,
    val domain: String,
    val subdomain: String,
    val title: String,
    val description: String,
    val verifiedExplanation: String,
    val coreKeywords: List<String> = emptyList(),
    val prerequisiteTopicIds: List<String> = emptyList(),
    val estimatedMinutes: Int = 30,
    val reference: CurriculumReference = CurriculumReference()
)

data class RoadmapStep(
    val stepNumber: Int,
    val id: String,
    val title: String,
    val topic: String,
    val description: String,
    val prerequisites: List<String>,
    val estimatedMinutes: Int,
    val addressesSkillGap: Boolean,
    val status: String = "NOT_STARTED", // "NOT_STARTED", "IN_PROGRESS", "MASTERED"
    val verifiedExplanation: String = "",
    val reference: CurriculumReference? = null
)

data class PersonalisedRoadmap(
    val userId: String,
    val domain: String,
    val subdomain: String,
    val goal: String,
    val targetRole: String,
    val steps: List<RoadmapStep>,
    val profileFingerprint: String = "",
    val provenance: com.learnmate.ai.domain.provenance.AiOperationProvenance? = null
)

data class StudyTask(
    val id: String,
    val userId: String,
    val roadmapStepId: String,
    val dayIndex: Int,
    val weekIndex: Int,
    val title: String,
    val topic: String,
    val durationMinutes: Int,
    val isCompleted: Boolean = false,
    val completedAtEpochMs: Long? = null,
    val notes: String = "",
    val provenance: com.learnmate.ai.domain.provenance.AiOperationProvenance? = null
)

data class AssessmentQuestion(
    val id: String,
    val domain: String,
    val subdomain: String,
    val topic: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val isAiGenerated: Boolean = false,
    val provenance: com.learnmate.ai.domain.provenance.AiOperationProvenance? = null
) {
    val isValid: Boolean get() =
        id.isNotBlank() &&
        questionText.isNotBlank() &&
        options.size == 4 &&
        options.distinct().size == 4 &&
        correctIndex in 0..3 &&
        explanation.isNotBlank()
}

/**
 * Gate 6.16 P1.1: Assignment Instance with cross-device tracking,
 * deterministic generation seed, objective mapping, and persisted responses.
 */
data class AssignmentInstance(
    val assignmentInstanceId: String,
    val attemptId: String,
    val userId: String,
    val trackId: String,
    val blueprintId: String,
    val installationId: String,
    val dayIndex: Int,
    val generationSeed: Long,
    val questions: List<AssessmentQuestion>,
    val objectiveMapping: Map<String, String> = emptyMap(),
    val persistedAnswers: Map<String, Int> = emptyMap(),
    val difficultyBand: String = "STANDARD",
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class SkillGapItem(
    val topic: String,
    val masteryPercent: Int,
    val targetPercent: Int,
    val gapSeverity: String, // "HIGH", "MEDIUM", "LOW"
    val rationale: String,
    val recommendedAction: String
)

data class SkillGapReport(
    val userId: String,
    val domain: String,
    val subdomain: String,
    val overallScorePercent: Int,
    val totalQuestions: Int,
    val correctCount: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val skillGaps: List<SkillGapItem>,
    val profileFingerprint: String = "",
    val provenance: com.learnmate.ai.domain.provenance.AiOperationProvenance? = null
)

data class ChatSession(
    val sessionId: String,
    val userId: String,
    val domain: String,
    val title: String,
    val isAiEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val trackId: String = ""
)

data class ChatMessage(
    val messageId: String,
    val sessionId: String,
    val userId: String,
    val senderRole: String, // "USER", "TUTOR", "SYSTEM"
    val text: String,
    val isAiGenerated: Boolean = false,
    val promptTokensEstimate: Int = 0,
    val generatedTokens: Int = 0,
    val ttftMs: Long? = null,
    val tokensPerSec: Double? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

package com.learnmate.ai.domain

/**
 * Shared, versioned context representing the active learner's state, goals,
 * preferences, and historical learning progress across all LearnMate AI features.
 *
 * Ensures:
 * - Single source of truth for learner profile, goals, and constraints.
 * - Strict multi-learner and track isolation.
 * - Version tracking for stale diagnostic and roadmap detection.
 * - Separation between AI prompt context and deterministic execution state.
 */
data class LearnerContext(
    val learnerId: String,
    val activeTrackId: String,
    val profileVersion: Int = 1,
    val modelVersion: String = "Meta-Llama-3.2-1B-Instruct-q8da4w",
    val educationalLevel: String,
    val domain: String,
    val departmentOrBranch: String,
    val courseOrSpecialization: String,
    val academicYearOrExperience: String,
    val currentKnowledgeLevel: String,
    val primaryLearningGoal: String,
    val secondaryLearningGoals: List<String> = emptyList(),
    val subjectsOfInterest: List<String> = emptyList(),
    val preferredLanguageCode: String = "en",
    val dailyAvailableMinutes: Int = 60,
    val learningPreferences: Map<String, String> = emptyMap(),
    val existingAssessmentReport: SkillGapReport? = null,
    val completedActivities: List<String> = emptyList(),
    val pendingActivities: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Returns true if the provided report was generated for a different profile version
     * (e.g. domain, branch, education level, or primary goal changed).
     */
    fun isDiagnosticStale(report: SkillGapReport?): Boolean {
        if (report == null) return true
        val currentFingerprint = computeFingerprint(domain, departmentOrBranch, educationalLevel, primaryLearningGoal)
        val reportDomainMatch = report.domain.equals(domain, ignoreCase = true)
        val reportSubdomainMatch = report.subdomain.isBlank() || report.subdomain.equals(departmentOrBranch, ignoreCase = true)
        return !reportDomainMatch || !reportSubdomainMatch
    }

    companion object {
        fun computeFingerprint(
            domain: String,
            branch: String,
            level: String,
            goal: String
        ): Int {
            return "${domain.trim().lowercase()}_${branch.trim().lowercase()}_${level.trim().lowercase()}_${goal.trim().lowercase()}".hashCode()
        }

        fun fromProfile(
            profile: LearnerProfile,
            trackId: String? = null,
            assessmentReport: SkillGapReport? = null,
            completedActivities: List<String> = emptyList(),
            pendingActivities: List<String> = emptyList()
        ): LearnerContext {
            val effTrackId = trackId ?: "track_${profile.userId}_default"
            val fingerprint = computeFingerprint(
                domain = profile.targetDomain,
                branch = profile.subdomain,
                level = profile.educationLevel.name,
                goal = profile.goal
            )
            val subjects = mutableListOf<String>()
            if (profile.currentTopic.isNotBlank()) subjects.add(profile.currentTopic)
            if (profile.subdomain.isNotBlank() && !subjects.contains(profile.subdomain)) {
                subjects.add(profile.subdomain)
            }

            return LearnerContext(
                learnerId = profile.userId,
                activeTrackId = effTrackId,
                profileVersion = fingerprint,
                educationalLevel = profile.educationLevel.name,
                domain = profile.targetDomain.ifBlank { "General Education" },
                departmentOrBranch = profile.subdomain.ifBlank { profile.targetDomain },
                courseOrSpecialization = profile.targetRole.ifBlank { profile.subdomain },
                academicYearOrExperience = profile.previousBackground.ifBlank { profile.currentLevel },
                currentKnowledgeLevel = profile.currentLevel,
                primaryLearningGoal = profile.goal.ifBlank { "Master foundational concepts" },
                secondaryLearningGoals = profile.learningGoals,
                subjectsOfInterest = subjects,
                preferredLanguageCode = profile.preferredLanguageCode.ifBlank { "en" },
                dailyAvailableMinutes = profile.dailyStudyMinutes.coerceIn(15, 480),
                existingAssessmentReport = assessmentReport,
                completedActivities = completedActivities,
                pendingActivities = pendingActivities
            )
        }
    }
}

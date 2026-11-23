package com.learnmate.ai.domain

import java.security.MessageDigest

/**
 * Shared, versioned context representing the active learner's state, goals,
 * preferences, and historical learning progress across all LearnMate AI features.
 *
 * Ensures:
 * - Single source of truth for learner profile, goals, and constraints.
 * - Strict multi-learner and track isolation.
 * - Cryptographic SHA-256 fingerprinting for stale diagnostic and roadmap detection.
 * - Separation between AI prompt context and deterministic execution state.
 */
data class LearnerContext(
    val learnerId: String,
    val activeTrackId: String,
    val profileVersion: Int = 1,
    val profileFingerprint: String = "",
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
    // ── Focus-Area Driven Architecture Accessors ──────────────────────────────
    val focusArea: String get() = departmentOrBranch
    val specialization: String get() = courseOrSpecialization
    val targetLearningOutcome: String get() = primaryLearningGoal
    val targetCareerRole: String get() = courseOrSpecialization
    val educationLevel: String get() = educationalLevel
    val targetDurationWeeks: Int get() = 12
    val currentRoadmapPosition: Int get() = (completedActivities.size + 1).coerceAtLeast(1)
    val verifiedSkillGaps: List<String> get() = existingAssessmentReport?.weaknesses ?: emptyList()
    val diagnosticAssessmentRef: String? get() = existingAssessmentReport?.provenance?.operationId

    /**
     * Returns true if the provided report or previous context was generated for a different profile version
     * (e.g. domain, branch, specialization, education level, knowledge level, primary goal, subjects, or language changed).
     */
    fun isDiagnosticStale(report: SkillGapReport?): Boolean {
        if (report == null) return true
        if (report.profileFingerprint.isNotBlank() && profileFingerprint.isNotBlank()) {
            return !report.profileFingerprint.equals(profileFingerprint, ignoreCase = true)
        }
        val reportDomainMatch = report.domain.equals(domain, ignoreCase = true)
        val reportSubdomainMatch = report.subdomain.isBlank() || report.subdomain.equals(departmentOrBranch, ignoreCase = true)
        return !reportDomainMatch || !reportSubdomainMatch
    }

    fun isDiagnosticStaleForContext(previousContext: LearnerContext?): Boolean {
        if (previousContext == null) return true
        if (previousContext.profileFingerprint.isNotBlank() && profileFingerprint.isNotBlank()) {
            return !previousContext.profileFingerprint.equals(profileFingerprint, ignoreCase = true)
        }
        return !previousContext.domain.equals(domain, ignoreCase = true) ||
               !previousContext.departmentOrBranch.equals(departmentOrBranch, ignoreCase = true) ||
               !previousContext.primaryLearningGoal.equals(primaryLearningGoal, ignoreCase = true)
    }

    companion object {
        fun computeSha256Fingerprint(
            domain: String,
            branch: String,
            specialization: String,
            educationLevel: String,
            currentKnowledgeLevel: String,
            primaryGoal: String,
            relevantSubjects: List<String>,
            preferredLanguage: String
        ): String {
            val raw = listOf(
                domain.trim().lowercase(),
                branch.trim().lowercase(),
                specialization.trim().lowercase(),
                educationLevel.trim().lowercase(),
                currentKnowledgeLevel.trim().lowercase(),
                primaryGoal.trim().lowercase(),
                relevantSubjects.map { it.trim().lowercase() }.sorted().joinToString(","),
                preferredLanguage.trim().lowercase()
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
            return hashBytes.joinToString("") { "%02x".format(it) }
        }

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
            val legacyFingerprint = computeFingerprint(
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

            val sha256Fingerprint = computeSha256Fingerprint(
                domain = profile.targetDomain.ifBlank { "General Education" },
                branch = profile.subdomain.ifBlank { profile.targetDomain },
                specialization = profile.targetRole.ifBlank { profile.subdomain },
                educationLevel = profile.educationLevel.name,
                currentKnowledgeLevel = profile.currentLevel,
                primaryGoal = profile.goal.ifBlank { "Master foundational concepts" },
                relevantSubjects = subjects,
                preferredLanguage = profile.preferredLanguageCode.ifBlank { "en" }
            )

            return LearnerContext(
                learnerId = profile.userId,
                activeTrackId = effTrackId,
                profileVersion = legacyFingerprint,
                profileFingerprint = sha256Fingerprint,
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

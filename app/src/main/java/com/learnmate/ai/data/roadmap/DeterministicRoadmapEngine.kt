package com.learnmate.ai.data.roadmap

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.learnmate.ai.data.db.PersonalisedRoadmapEntity
import com.learnmate.ai.data.taxonomy.TopicTaxonomy
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.domain.PersonalisedRoadmap
import com.learnmate.ai.domain.RoadmapStep
import com.learnmate.ai.domain.SkillGapReport

/**
 * Deterministic Roadmap Engine.
 * Synthesizes a structured personalized curriculum prioritizing identified skill gaps
 * and establishing clear prerequisite dependencies.
 */
object DeterministicRoadmapEngine {
    private val gson = Gson()

    fun generateRoadmap(
        profile: LearnerProfile,
        skillGapReport: SkillGapReport?
    ): PersonalisedRoadmap {
        val normDomain = TopicTaxonomy.normalizeDomain(profile.targetDomain)
        // 1. Gather catalog topics for this specific focus area (PRIMARY) or domain
        val focusDef = com.learnmate.ai.domain.focus.FocusAreaIntelligence.resolveFocusArea(
            focusAreaName = profile.subdomain.ifBlank { profile.targetDomain },
            domain = profile.targetDomain
        )
        val catalogEntry = TopicTaxonomy.DOMAIN_CATEGORIES.firstOrNull {
            it.title.equals(profile.targetDomain, ignoreCase = true) ||
            it.title.equals(normDomain, ignoreCase = true)
        }
        val defaultCoreTopics = if (focusDef.suggestedRoadmapTopics.isNotEmpty()) {
            focusDef.suggestedRoadmapTopics
        } else {
            catalogEntry?.coreTopics ?: listOf(
                "${profile.targetDomain} Core Principles",
                "${profile.subdomain.ifBlank { profile.targetDomain }} Key Methodologies",
                "Applied Techniques for ${profile.targetRole.ifBlank { "Specialist" }}",
                "Advanced Synthesis & Problem Solving",
                "Capstone Project: ${profile.goal.ifBlank { "Domain Mastery" }}"
            )
        }

        // 2. Identify prioritized gaps (Filtered strictly to this focus area & domain to prevent cross-contamination)
        val allVerified = TopicTaxonomy.getVerifiedTopics()
        val domainAllowedTopics = defaultCoreTopics + allVerified
            .filter {
                val nd = TopicTaxonomy.normalizeDomain(it.domain)
                nd.equals(normDomain, ignoreCase = true) ||
                it.domain.contains(profile.subdomain, ignoreCase = true) ||
                it.title.contains(profile.subdomain, ignoreCase = true)
            }
            .map { it.title }

        val rawWeaknesses = skillGapReport?.weaknesses ?: emptyList()
        val weaknesses = rawWeaknesses.filter { w ->
            domainAllowedTopics.any { dt -> dt.contains(w, ignoreCase = true) || w.contains(dt, ignoreCase = true) }
        }

        // 3. Merge: gaps first, then remaining core topics
        val prioritizedTopics = (weaknesses + defaultCoreTopics)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        // 4. Generate sequential roadmap steps with prerequisites & verified textbook references
        val steps = prioritizedTopics.mapIndexed { idx, topic ->
            val isGap = weaknesses.any { it.equals(topic, ignoreCase = true) }
            val prereqs = if (idx == 0) emptyList() else listOf("Step $idx: ${prioritizedTopics[idx - 1]}")
            val stepEstimatedMinutes = (profile.dailyStudyMinutes * (idx + 1)).coerceIn(30, 180)

            val matchedTopic = allVerified.firstOrNull {
                it.title.equals(topic, ignoreCase = true) ||
                    it.title.contains(topic, ignoreCase = true) ||
                    topic.contains(it.title, ignoreCase = true)
            }
            val ref = if (matchedTopic != null && matchedTopic.referenceTitle.isNotBlank()) {
                com.learnmate.ai.domain.CurriculumReference(
                    title = matchedTopic.referenceTitle,
                    author = matchedTopic.referenceAuthor,
                    edition = matchedTopic.referenceEdition,
                    chapter = matchedTopic.referenceChapter,
                    section = matchedTopic.referenceSection,
                    identifier = matchedTopic.referenceIdentifier
                )
            } else null

            val stepTitle = if (idx == prioritizedTopics.lastIndex && !isGap && focusDef.displayName.isNotBlank()) {
                "Capstone: ${focusDef.displayName} Project"
            } else if (isGap) {
                "Priority Remediation: $topic"
            } else {
                "Core Competency: $topic"
            }

            val stepDesc = if (isGap) {
                "Targets identified gap from your diagnostic assessment. Focuses on foundational verified curriculum and hands-on exercises in ${focusDef.displayName}."
            } else {
                "Builds advanced mastery toward your goal: '${profile.goal.ifBlank { focusDef.targetOutcomeSummary }}'."
            }

            RoadmapStep(
                stepNumber = idx + 1,
                id = "step_${profile.userId}_${idx + 1}",
                title = stepTitle,
                topic = topic,
                description = stepDesc,
                prerequisites = prereqs,
                estimatedMinutes = stepEstimatedMinutes,
                addressesSkillGap = isGap,
                status = if (idx == 0) "IN_PROGRESS" else "NOT_STARTED",
                verifiedExplanation = matchedTopic?.verifiedExplanation ?: (if (isGap) "Targeted skill gap remediation." else "Core accredited milestone."),
                reference = ref
            )
        }

        return PersonalisedRoadmap(
            userId = profile.userId,
            domain = profile.targetDomain,
            subdomain = profile.subdomain,
            goal = profile.goal,
            targetRole = profile.targetRole,
            steps = steps
        )
    }

    fun mapToEntity(roadmap: PersonalisedRoadmap, trackId: String = "track_${roadmap.userId}_default"): PersonalisedRoadmapEntity {
        return PersonalisedRoadmapEntity(
            trackId = trackId,
            userId = roadmap.userId,
            domain = roadmap.domain,
            subdomain = roadmap.subdomain,
            goal = roadmap.goal,
            targetRole = roadmap.targetRole,
            totalSteps = roadmap.steps.size,
            stepsJson = gson.toJson(roadmap.steps),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    fun mapFromEntity(entity: PersonalisedRoadmapEntity): PersonalisedRoadmap {
        val stepsType = object : TypeToken<List<RoadmapStep>>() {}.type
        val steps: List<RoadmapStep> = try {
            val raw: List<RoadmapStep> = gson.fromJson(entity.stepsJson, stepsType) ?: emptyList()
            raw.mapIndexed { idx, s ->
                RoadmapStep(
                    stepNumber = if (s.stepNumber > 0) s.stepNumber else (idx + 1),
                    id = s.id ?: "step_${entity.userId}_${idx + 1}",
                    title = s.title ?: "Step ${idx + 1}",
                    topic = s.topic ?: s.title ?: "General",
                    description = s.description ?: s.title ?: "",
                    prerequisites = s.prerequisites ?: emptyList(),
                    estimatedMinutes = if (s.estimatedMinutes > 0) s.estimatedMinutes else 45,
                    addressesSkillGap = s.addressesSkillGap,
                    status = s.status ?: "NOT_STARTED",
                    verifiedExplanation = s.verifiedExplanation ?: "",
                    reference = s.reference
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        return PersonalisedRoadmap(
            userId = entity.userId,
            domain = entity.domain,
            subdomain = entity.subdomain,
            goal = entity.goal,
            targetRole = entity.targetRole,
            steps = steps
        )
    }
}


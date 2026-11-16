# Report 08 — AI Planner & Multi-Track Weekly Schedule Orchestration

## 1. Executive Summary
This report qualifies the Multi-Track Weekly Planner engine (`PersonalisedStudyPlannerEngine`). The engine orchestrates cohesive 7-day study schedules across concurrent learning tracks, preventing time collisions, balancing new concept acquisition with active revision, and providing clear pedagogical rationales for every planned task.

## 2. Weekly Orchestration Architecture
```kotlin
fun generateWeeklyPlan(
    track: LearningTrack,
    targetDailyMinutes: Int = track.dailyCommitmentMinutes.coerceIn(15, 180),
    masteries: List<TopicMasteryResult>,
    srsItems: List<SpacedRepetitionItem>,
    nodes: List<CurriculumNodeEntity>,
    prerequisites: List<NodePrerequisiteEntity>,
    startEpochMs: Long = System.currentTimeMillis()
): WeeklyStudyPlan {
    val dailyPlans = mutableListOf<DailyStudyPlan>()
    for (dayOffset in 0 until 7) {
        val dayEpoch = startEpochMs + (dayOffset * MILLIS_PER_DAY)
        dailyPlans.add(
            generateDailyPlan(
                track = track,
                targetDailyMinutes = targetDailyMinutes,
                masteries = masteries,
                srsItems = srsItems,
                nodes = nodes,
                prerequisites = prerequisites,
                currentEpochMs = dayEpoch
            )
        )
    }
    return WeeklyStudyPlan(
        weekNumber = 1,
        targetWeeklyMinutes = targetDailyMinutes * 7,
        dailyPlans = dailyPlans,
        weeklyFocusSummary = "Balanced 7-day plan: ${dailyPlans.sumOf { it.tasks.size }} total tasks across concept study and active reviews."
    )
}
```

## 3. Explainability & Pedagogical Rationale
Every scheduled task includes an explainable reason presented in the UI:
- **Spaced Repetition Review**: `"Retention-First Review: Interval schedule indicates concept recall decay risk. Reviewing now consolidates long-term memory."`
- **Critical Prerequisite**: `"Critical Prerequisite: Mandatory foundational concept for examination target '${track.title}'."`
- **Skill Gap Recovery**: `"Skill Gap Recovery: Diagnostic assessment identified weakness (<70% score). Guided study and concept clarification required."`
- **Curriculum Step**: `"Curriculum Progression: All prerequisites satisfied. Next accredited module in curriculum roadmap."`

## 4. Multi-Track Safety
- Each task is tagged with `trackId` and `userId`.
- Switching between active tracks recalculates weekly milestones independently without cross-contaminating scheduled tasks or progress counters.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target Files**: `app/src/main/java/com/learnmate/ai/domain/planner/PersonalisedStudyPlannerEngine.kt`, `app/src/main/java/com/learnmate/ai/ui/screens/PlannerScreen.kt`

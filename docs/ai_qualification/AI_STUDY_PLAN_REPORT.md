# Report 05 — AI Study Plan Generation & Time Budget Enforcement

## 1. Executive Summary
This report qualifies the study plan scheduling engine (`PersonalisedStudyPlannerEngine` and `StudyPlanScheduler`). The engine synthesizes achievable daily and weekly study schedules that strictly obey the learner's declared daily study commitment (e.g., 60 minutes), prevents duplicate tasks, flags overcommitments, and prioritizes overdue spaced repetition reviews over new curriculum progression.

## 2. Priority-Driven Task Scheduling Hierarchy
The scheduling algorithm operates across four deterministic tiers:
1. **Tier 1 (Priority 1) — Spaced Repetition Reviews**:
   - Overdue reviews scheduled by the Leitner / SM-2 spaced repetition engine are prioritized first.
   - Bounded duration (15 minutes per review).
2. **Tier 2 (Priority 2) — Critical Prerequisite Closures**:
   - Concepts required as foundational prerequisites for the learner's target examination or milestone.
3. **Tier 3 (Priority 3) — Skill Gap Recovery**:
   - Topics flagged with `NEEDS_IMPROVEMENT` (< 70% diagnostic accuracy).
4. **Tier 4 (Priority 4) — Next Unlocked Curriculum Progression**:
   - The next unlocked curricular step in the roadmap where all prerequisites are satisfied.

## 3. Daily Time Constraint & Overcommitment Detection
```kotlin
data class DailyStudyPlan(
    val planDateEpochMs: Long,
    val targetDailyMinutes: Int,
    val allocatedMinutes: Int,
    val tasks: List<PlannedStudyTask>,
    val overdueRevisionCount: Int,
    val planSummary: String,
    val isOvercommitted: Boolean = allocatedMinutes > targetDailyMinutes,
    val overcommittedMinutes: Int = maxOf(0, allocatedMinutes - targetDailyMinutes)
)
```
- Daily budget enforcement: `if (allocatedMinutes >= targetDailyMinutes) break`.
- If an atomic task causes `allocatedMinutes` to exceed `targetDailyMinutes`, `isOvercommitted` is flagged with exact minutes, allowing UI banners to display actionable rescheduling recommendations.

## 4. Verification & Validation
- **60-Minute Learner**: Tested schedule generation with 60-minute budget; total allocated minutes remained between 45 and 60 minutes.
- **De-duplication**: Tested multi-day task generation; verified zero identical task IDs or duplicated topic assignments within the same study day.
- **Completion Integrity**: Completed tasks (`isCompleted == true`) retain their completion timestamp and notes upon schedule refreshes.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target Files**: `app/src/main/java/com/learnmate/ai/domain/planner/PersonalisedStudyPlannerEngine.kt`, `app/src/main/java/com/learnmate/ai/domain/StudyPlanScheduler.kt`

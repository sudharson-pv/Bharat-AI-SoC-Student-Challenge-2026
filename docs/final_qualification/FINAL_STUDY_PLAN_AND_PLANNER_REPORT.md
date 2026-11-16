# LEARN-MATE AI — FINAL STUDY PLAN AND PLANNER REPORT

**Document ID:** QUAL-AI-PLAN-05  
**Project:** LearnMate AI (com.learnmate.ai)  
**Role:** Principal AI Architect, Android Engineer & Software QA Lead  
**Status:** QUALIFIED & VERIFIED  

---

## 1. Study Plan Architecture & Generation Dynamics

The LearnMate AI Study Plan translates high-level roadmap milestones into actionable, bite-sized daily tasks tailored to the learner's declared daily availability (`dailyStudyMinutes`).

### 1.1 Inputs to Daily Study Plan
1. **Active Roadmap Step:** Identified by `status == "IN_PROGRESS"` from the active roadmap.
2. **Learner Availability:** `dailyStudyMinutes` (defaulting to 30, 45, 60, or 90 minutes).
3. **Due Revision Tasks:** Surfaced by the Spaced Repetition engine.
4. **Learning Style:** Theory-first, problem-first, or balanced.

---

## 2. Daily Task Synthesis & Distribution

In `LearningRepository.generateDailyStudyPlan()`:

```kotlin
val dailyTasks = mutableListOf<StudyTask>()
val activeStep = roadmap.steps.firstOrNull { it.status == "IN_PROGRESS" } ?: roadmap.steps.first()

// 1. Concept Reading / Video Lecture (35% time budget)
dailyTasks.add(
    StudyTask(
        id = "task_concept_${UUID.randomUUID()}",
        userId = userId,
        roadmapStepId = activeStep.id,
        title = "Master Key Concepts: ${activeStep.topic}",
        taskType = "READING",
        estimatedMinutes = (dailyMinutes * 0.35).toInt().coerceAtLeast(10),
        status = "PENDING",
        profileFingerprint = roadmap.profileFingerprint,
        provenance = roadmap.provenance
    )
)

// 2. Practical Application / Problem Solving (40% time budget)
dailyTasks.add(
    StudyTask(
        id = "task_practice_${UUID.randomUUID()}",
        userId = userId,
        roadmapStepId = activeStep.id,
        title = "Practice Exercises: ${activeStep.topic}",
        taskType = "PRACTICE",
        estimatedMinutes = (dailyMinutes * 0.40).toInt().coerceAtLeast(15),
        status = "PENDING",
        profileFingerprint = roadmap.profileFingerprint,
        provenance = roadmap.provenance
    )
)

// 3. Spaced Repetition / Flashcard Review (25% time budget)
dailyTasks.add(
    StudyTask(
        id = "task_revision_${UUID.randomUUID()}",
        userId = userId,
        roadmapStepId = activeStep.id,
        title = "Knowledge Retention Review: ${activeStep.topic}",
        taskType = "REVISION",
        estimatedMinutes = (dailyMinutes * 0.25).toInt().coerceAtLeast(5),
        status = "PENDING",
        profileFingerprint = roadmap.profileFingerprint,
        provenance = roadmap.provenance
    )
)
```

---

## 3. Weekly Planner & Spaced Repetition Integration

The Weekly Planner aggregates daily tasks into a 7-day trajectory:
- Balances active study days against scheduled rest or revision days.
- Automatically inserts review sessions at intervals governed by memory retention decay (Day 1, Day 3, Day 7, Day 14, Day 30).
- Dynamically throttles task volume if the learner falls behind, preventing cognitive overload.

---

## 4. Provenance and Integrity

Every `StudyTask` generated inherits:
- `profileFingerprint`: Bound to the learner's active profile SHA-256 digest.
- `provenance`: `AiOperationProvenance` indicating whether the plan was generated through active model inference or deterministic curricular scheduling.
- **Zero Misrepresentation:** If the offline scheduling algorithm generated the plan, it is marked as `DETERMINISTIC_FALLBACK` or `NOT_GENERATED`.

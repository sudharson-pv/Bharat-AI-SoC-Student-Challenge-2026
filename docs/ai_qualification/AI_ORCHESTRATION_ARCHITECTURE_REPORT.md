# Report 11 — Unified AI Orchestration Layer & Separation of Concerns

## 1. Executive Summary
This report qualifies the centralized AI orchestration layer (`LearnMateAiCoreEngine.kt`) in LearnMate AI. It details the structural boundary enforcement between probabilistic AI generation (handled by Meta Llama 3.2 1B Instruct via ExecuTorch) and deterministic application state (grading, room database transactions, prerequisite validation, and navigation).

## 2. Architectural Boundary & Separation of Concerns
```
┌─────────────────────────────────────────────────────────────┐
│                    LEARNMATE AI UI (JETPACK COMPOSE)        │
└──────────────────────────────▲──────────────────────────────┘
                               │ StateFlow / Events
┌──────────────────────────────▼──────────────────────────────┐
│                    LEARNING VIEW MODEL                      │
└───────────────▲─────────────────────────────▲───────────────┘
                │                             │
┌───────────────▼─────────────┐ ┌─────────────▼───────────────┐
│     DETERMINISTIC LAYER     │ │    AI ORCHESTRATION LAYER   │
│                             │ │                             │
│ • DeterministicGrading      │ │ • LearnMateAiCoreEngine     │
│ • Prerequisite DAG Checks   │ │ • PromptBudgeter            │
│ • Room Persistence (DAO)    │ │ • Schema Parsing & Retries  │
│ • Navigation Routing        │ │ • ExecuTorchLlamaEngine     │
│ • Time Math (SM-2 / SRS)    │ │ • On-Device Llama 3.2 1B    │
└─────────────────────────────┘ └─────────────────────────────┘
```

## 3. Core Engine Orchestration Responsibilities
`LearnMateAiCoreEngine` consolidates all AI operations into unified, fail-safe suspend methods:
1. `generateDiagnosticAssessment(context: LearnerContext): List<AssessmentQuestion>`
2. `evaluateDiagnosticAssessment(context: LearnerContext, questions: List<AssessmentQuestion>, answers: Map<String, Int>): SkillGapReport`
3. `synthesizePersonalizedRoadmap(context: LearnerContext, report: SkillGapReport?, previousRoadmap: PersonalisedRoadmap?): PersonalisedRoadmap`
4. `generateDailyStudyPlan(context: LearnerContext, roadmap: PersonalisedRoadmap): List<StudyTask>`
5. `generateTailoredDomainQuestions(profile: LearnerProfile): List<AssessmentQuestion>`

## 4. Fail-Closed Fallback Invariant
- If the on-device Llama model runtime is loading, interrupted, or produces malformed JSON after 2 retries, `LearnMateAiCoreEngine` immediately falls back to rich, verified domain-tailored deterministic engines.
- The UI never hangs, crashes, or outputs blank cards.
- AI-generated entities are transparently tagged with `isAiGenerated = true` for auditing and provenance.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target File**: `app/src/main/java/com/learnmate/ai/ai/LearnMateAiCoreEngine.kt`

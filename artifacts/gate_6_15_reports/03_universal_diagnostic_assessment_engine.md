# GATE 6.15 — REPORT 03: UNIVERSAL DIAGNOSTIC ASSESSMENT ENGINE

## 1. Pedagogical Scope & Engine Principles
The Diagnostic Assessment Engine in LearnMate AI assesses prior learner knowledge across any selected discipline. It evaluates conceptual readiness, identifies knowledge gaps, and calibrates initial learning paths.

### Core Non-Negotiable Invariants
1. **100% Deterministic Evaluation:** Assessment grading is performed purely in Kotlin code via exact mathematical matching against validated answer keys.
2. **Zero Generative AI Grading:** The LLM is strictly prohibited from grading questions, scoring submissions, or guessing mastery percentages.
3. **Accredited Citations:** Every question carries an accredited textbook source citation (Author, Title, Edition, Chapter, Section).
4. **Bloom's Taxonomy Cognitive Mapping:** Questions are categorized across Bloom's levels (Remember, Understand, Apply, Analyze, Evaluate, Create).

---

## 2. Dynamic Domain Diagnostic Dispatch
When a learner launches `DiagnosticAssessmentScreen`:
1. `learningViewModel.getQuestionsForDomain(targetDomain)` queries the local Room database for verified questions mapped to that domain.
2. If custom domain questions are present, they are served immediately.
3. If not, the engine serves universal diagnostic checkpoints from `StarterCurriculumCatalog` spanning secondary education, engineering, polytechnic, arts, management, civil services, and trades.

---

## 3. Skill-Gap Report Generation
Upon submission, `DeterministicAssessmentEngine.evaluateAssessment` produces a structured `SkillGapReport`:
- `overallScorePercent`: Monotonically calculated integer score.
- `strengths`: Topics with mastery score $\ge \text{targetThreshold}$.
- `weaknesses`: Topics with mastery score $< \text{targetThreshold}$.
- `skillGaps`: Granular diagnostic items categorized by severity (`HIGH`, `MEDIUM`, `LOW`) with concrete remedial study recommendations.


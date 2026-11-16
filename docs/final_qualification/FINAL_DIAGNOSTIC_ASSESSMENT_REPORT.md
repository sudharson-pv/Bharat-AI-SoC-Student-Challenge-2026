# LEARN-MATE AI — FINAL DIAGNOSTIC ASSESSMENT REPORT

**Document ID:** QUAL-AI-DIAG-03  
**Project:** LearnMate AI (com.learnmate.ai)  
**Role:** Principal AI Architect, Android Engineer & Software QA Lead  
**Status:** QUALIFIED & VERIFIED  

---

## 1. Diagnostic Assessment Workflow Redesign

### 1.1 Previous Problem
In earlier versions, completing onboarding or the learner profile navigated to an oversized, generic questionnaire displaying approximately 52 questions, often disconnected from the learner's specific academic branch or discipline.

### 1.2 Remediated Workflow
The workflow is now strictly bounded, profile-linked, and validated:

```
Learner Profile Completion / Edit
        ↓
Compute SHA-256 Profile Fingerprint
        ↓
Check `isDiagnosticStale()`
        ↓
Invoke Local Llama 3.2 1B Model (Prompt Template DIAGNOSTIC_V2)
        ↓
Parse & Validate Exactly 10 Domain-Specific Questions
        ↓
Apply Session-Seed Option Randomization
        ↓
Present Assessment with Visual Provenance Badge
        ↓
Evaluate 10 Answers → Generate SkillGapReport & Strengths/Weaknesses
        ↓
Synthesize Personalized 6-Step Roadmap
```

---

## 2. Question Generation Invariants & Validation Protocol

In `LearnMateAiCoreEngine.kt`, the generation method enforces 7 strict pedagogical invariants:

1. **Exact Cardinality:** The assessment contains **exactly 10 questions** (`questions.size == 10`).
2. **Distinct Options:** Each question has **exactly 4 unique options** (`opts.size == 4 && opts.distinct().size == 4`).
3. **Valid Correct Index:** `correctIndex` is strictly in range `0..3`.
4. **Pedagogical Explanation:** Verified explanation explaining why the correct choice is right and citing fundamental principles.
5. **Difficulty Taxonomy:** Explicitly labeled (`EASY` [questions 1-3], `MEDIUM` [questions 4-7], `HARD` [questions 8-10]).
6. **Learning Objective:** Each question states the target concept or skill being measured.
7. **Profile Fingerprint & Provenance:** Each question carries an `AiOperationProvenance` record with the exact `profileFingerprint`.

---

## 3. Session-Seed Option Randomization Fix

### 3.1 Previous Defect: Static `hashCode()` Shuffle
Previously, questions were shuffled using `q.id.hashCode()`. Because `q.id` is constant, the question options appeared in the exact same order every time the user took the assessment, enabling answer position memorization.

### 3.2 Remediation: Ephemeral Session Seed
In `DiagnosticAssessmentScreen.kt`:

```kotlin
val assessmentSessionSeed = remember { UUID.randomUUID().mostSignificantBits }

val randomizedOptions = remember(currentQuestion.id, assessmentSessionSeed) {
    val rng = java.util.Random(assessmentSessionSeed xor currentQuestion.id.hashCode().toLong())
    val indexedOptions = currentQuestion.options.mapIndexed { idx, optText ->
        idx to optText
    }
    indexedOptions.shuffled(rng)
}
```

### 3.3 Security & Usability Guarantees:
- **Across Sessions:** Each new assessment session generates a new `UUID`, producing a fresh permutation of options.
- **Within an Active Session:** The options remain completely stable as the user navigates between Question 1 through 10, preventing UI jitter.
- **Correct Index Parity:** Shuffling stores the original option index (`idx to optText`). When the user selects an option, the system verifies `originalIndex == currentQuestion.correctIndex`.

---

## 4. Bounded Retry & Honest Fallback Protocol

If the on-device Llama 3.2 1B model produces malformed JSON or times out:
1. The engine retries up to 2 times with adjusted temperature (`0.2` -> `0.1`).
2. If the model fails or is offline, the engine invokes `generateTailoredDomainQuestions(profile)`.
3. The fallback questions are **strictly domain-specific** (Finance, VLSI, CS, Science, Mechanical, Civil, Medical, or Universal).
4. The provenance label is set to `DETERMINISTIC_FALLBACK` with reason:
   `"On-device Llama runtime offline or returned non-conforming JSON; applied accredited curriculum fallback."`
5. The UI displays an amber badge: `Deterministic Fallback (Curriculum Catalog)`. Fallback is **never disguised** as AI generation.

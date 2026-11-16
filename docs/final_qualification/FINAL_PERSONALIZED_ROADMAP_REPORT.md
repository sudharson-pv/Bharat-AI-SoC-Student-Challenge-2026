# LEARN-MATE AI — FINAL PERSONALIZED ROADMAP REPORT

**Document ID:** QUAL-AI-ROAD-04  
**Project:** LearnMate AI (com.learnmate.ai)  
**Role:** Principal AI Architect, Android Engineer & Software QA Lead  
**Status:** QUALIFIED & VERIFIED  

---

## 1. Roadmap Architecture & Synthesis Protocol

The personalized learning roadmap represents a learner's progressive curricular pathway toward their target career role and domain mastery. In LearnMate AI, roadmap generation is dynamic, personalized, and bound to the learner's profile and diagnostic assessment outcomes.

### 1.1 Inputs to Roadmap Synthesis
In `LearnMateAiCoreEngine.synthesizePersonalizedRoadmap()`:
1. **`profile`**: Learner profile (target domain, branch, career goal, education level, daily study time).
2. **`report`**: The learner's `SkillGapReport` containing evaluated strengths and weaknesses.
3. **`previousRoadmap`**: Any previously active roadmap for preserving completed step state.

---

## 2. Dynamic Prioritization: Weakness Remediation First

The synthesis algorithm enforces pedagogical sequencing:

```kotlin
val weaknesses = report?.weaknesses ?: emptyList()
val domainTopics = resolveDomainCurriculum(profile.targetDomain, profile.subdomain)

// Prioritize identified skill gaps at the beginning
val orderedTopics = (weaknesses + domainTopics).distinct().take(6)
```

### 2.1 Remediation Highlighting
Steps addressing identified skill gaps are highlighted in the UI and data model:
- `addressesSkillGap = true`
- `title = "Priority Remediation: <Topic>"`
- `description = "Targets identified gap from your diagnostic assessment. Rebuilds foundational concepts and practical problem solving in <Topic>."`

Core competencies beyond immediate gaps are structured as:
- `addressesSkillGap = false`
- `title = "Core Competency: <Topic>"`
- `description = "Builds advanced mastery toward your goal: '<Goal>' in <Topic>."`

---

## 3. Progress State Preservation Across Re-assessments

When a learner retakes an assessment or updates their profile, their historical progress must not be wiped out arbitrarily.

In `synthesizePersonalizedRoadmap`:
```kotlin
val prevStepMap = previousRoadmap?.steps?.associateBy { it.topic.lowercase() } ?: emptyMap()

val steps = orderedTopics.mapIndexed { idx, topic ->
    val existing = prevStepMap[topic.lowercase()]
    val status = existing?.status ?: if (idx == 0) "IN_PROGRESS" else "NOT_STARTED"
    ...
}
```

- If a topic was previously marked `COMPLETED`, its status is preserved.
- Unstarted topics are initialized to `NOT_STARTED` (or `IN_PROGRESS` for Step 1).

---

## 4. SHA-256 Fingerprint Binding & Provenance

Every generated roadmap carries:
1. `profileFingerprint`: 64-character SHA-256 digest of the learner's profile at synthesis time.
2. `provenance`: An `AiOperationProvenance` record with:
   - `operationName = "PERSONALIZED_ROADMAP_SYNTHESIS"`
   - `isRealInferenceExecuted`: reflects model status in active memory.
   - `validationStatus = "AI_VALIDATED"` or `"DETERMINISTIC_FALLBACK"`.

---

## 5. Domain Specialization Case Studies

| Learner Domain | Diagnostic Weakness | Step 1 (Priority Remediation) | Step 2 (Priority Remediation) | Step 3-6 (Core Competencies) |
|---|---|---|---|---|
| **Electronics (VLSI)** | Static Timing Analysis (STA) | Priority Remediation: Static Timing Analysis (STA) | Core Competency: CMOS Inverter Fundamentals | Verilog RTL Design, Low Power CMOS, Deep Sub-Micron Effects, SoC Integration |
| **Finance (MBA)** | Cost of Capital (WACC) | Priority Remediation: Cost of Capital (WACC) | Core Competency: Time Value of Money | Financial Statement Analysis, Portfolio Theory, DCF Modeling, Strategic Finance Capstone |
| **Computer Science** | Operating Systems Concurrency | Priority Remediation: OS Concurrency & Deadlocks | Core Competency: Core Data Structures | Algorithmic Complexity, Graph Algorithms, Distributed Systems (CAP), Full-Stack Architecture |

# LEARN-MATE AI — FINAL SKILL GAP AND REVISION REPORT

**Document ID:** QUAL-AI-GAP-06  
**Project:** LearnMate AI (com.learnmate.ai)  
**Role:** Principal AI Architect, Android Engineer & Software QA Lead  
**Status:** QUALIFIED & VERIFIED  

---

## 1. Skill Gap Analysis Architecture

Skill gap analysis evaluates learner performance across the 10 diagnostic assessment questions to identify specific competencies requiring targeted remediation.

### 1.1 Evaluation Pipeline
1. **User Answer Mapping:** Maps learner selected option back to the canonical `correctIndex`.
2. **Topic Clustering:** Aggregates accuracy by sub-topic.
3. **Threshold Categorization:**
   - **Strength:** Topic accuracy >= 80%.
   - **Moderate:** Topic accuracy between 50% and 79%.
   - **Weakness (Skill Gap):** Topic accuracy < 50%.
4. **Report Construction:** Produces a `SkillGapReport` with `overallScorePercent`, `strengths`, `weaknesses`, and specific `skillGaps` items.

---

## 2. Critical Cross-Domain Leakage Remediation

### 2.1 The Vulnerability in `LearningRepository.getAllTopicMasteries()`
Previously, lines 1373–1380 of `LearningRepository.kt` exhibited a dangerous fallback:

```kotlin
// PREVIOUS VULNERABLE CODE:
val candidateNodes = curriculumNodeDao.getNodeIdsByDomain(targetDomain)
val nodes = if (candidateNodes.isNotEmpty()) {
    candidateNodes
} else {
    // BUG: Fell back to StarterCurriculumCatalog (which contains VLSI nodes)
    StarterCurriculumCatalog.getSupportedNodeIds()
}
```

When a learner selected a non-VLSI domain (such as Business/Finance, Civil Engineering, or Medicine) that had no pre-seeded static curriculum nodes in the database, `candidateNodes` was empty. The repository silently fell back to `StarterCurriculumCatalog.getSupportedNodeIds()`, which injected VLSI topics (`vlsi_cmos_inverter`, `vlsi_sta`) into a Finance or Medical student's skill-gap report!

### 2.2 Structural Remediation
The defect was resolved by eliminating the foreign domain fallback:

```kotlin
// REMEDIATED CLEAN CODE:
val candidateNodes = curriculumNodeDao.getNodeIdsByDomain(targetDomain)
val nodes = if (candidateNodes.isNotEmpty()) {
    candidateNodes
} else {
    // Scope strictly to active roadmap steps for this custom domain, or return empty
    val activeRoadmap = getPersonalisedRoadmap(userId)
    if (activeRoadmap != null && activeRoadmap.domain.equals(targetDomain, ignoreCase = true)) {
        activeRoadmap.steps.map { it.topic }
    } else {
        emptyList()
    }
}
```

Now, learners in custom or non-default domains:
1. Never receive VLSI or foreign engineering topics.
2. Are evaluated strictly against their own roadmap steps and diagnostic topics.
3. If no nodes exist, clean domain-scoped empty state is returned without cross-pollination.

---

## 3. Targeted Revision Scheduling (Spaced Repetition)

When weaknesses are identified:
1. High-priority revision cards are generated for each topic in `report.weaknesses`.
2. Initial retention probability is set to `0.3` (requiring immediate Day 1 review).
3. As the learner completes practice problems and revision tasks, retention curves update following the formula:
   $$R = e^{-\frac{t}{S}}$$
   where $t$ is time elapsed and $S$ is memory stability.
4. Topics with $R < 0.7$ automatically resurface in the learner's Daily Study Plan.

# GATE 6.15 — REPORT 10: PEDAGOGICAL CALIBRATION & COGNITIVE LOAD MODEL

## 1. Cognitive Load Theory in Offline Self-Paced Learning
According to Sweller's Cognitive Load Theory (CLT), working memory capacity is strictly bounded (typically $4 \pm 1$ information chunks). In self-directed mobile learning, excessive extraneous cognitive load directly causes comprehension failure and premature app abandonment.

### Pedagogical Mitigations Implemented
1. **Curriculum Chunking:** Learning milestones are partitioned into bite-sized units with estimated completion durations bounded between 15 and 45 minutes.
2. **Scaffolded Progression:** `DeterministicRoadmapEngine.kt` enforces prerequisite graphs (`node_prerequisites` table). Advanced concepts cannot be scheduled until foundational nodes demonstrate mastery $\ge 0.75$.
3. **Dual Coding Strategy:** Theoretical explanations are accompanied by structured summaries, core keyword highlights, and verified textbook citations.

---

## 2. Psychometric Sample Size Calibration
To prevent premature labeling of learners as "struggling" or "mastered":
- Mastery metrics are reported with exact sample size qualifiers (e.g. "80% (4/5 questions correct)").
- Objective confidence scores require a minimum of $n \ge 5$ discrete assessment items.
- If $n < 5$, the interface displays "Preliminary Evidence ($n$ items tested)" and avoids categorical weakness determinations.


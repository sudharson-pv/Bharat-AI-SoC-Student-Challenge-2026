# GATE 6.14.1 — REPORT 06: UNIVERSAL SPACED REPETITION & DAILY PLAN AUDIT

## 1. Algorithm Specification (SM-2 / Modified Anki Model)
LearnMate AI implements a localized, modified SuperMemo SM-2 algorithm for spaced repetition and retention maintenance:

$$\text{EF}' = \max\left(1.3, \text{EF} + \left(0.1 - (5 - q) \times (0.08 + (5 - q) \times 0.02)\right)\right)$$
$$\text{Interval}(n) = \begin{cases} 
1 \text{ day} & n = 1 \\ 
6 \text{ days} & n = 2 \\ 
\text{Interval}(n-1) \times \text{EF}' & n > 2 
\end{cases}$$

Where:
- $q \in [0, 5]$ is the recall performance score.
- $\text{EF}$ is the Easiness Factor (initialized at $2.5$, bounded below by $1.3$).
- $n$ is the repetition number.

---

## 2. Daily Plan Generation & Interleaving
The Daily Study Planner generates a balanced schedule that combines:
1. **Due Spaced Repetitions (Review Items):** Flashcards and quiz concepts whose calculated due date $\le \text{today}$.
2. **Active Roadmap Milestones (New Content):** High-priority unmastered concepts from the active curriculum track.
3. **Diagnostic Retention Probes:** Incomplete diagnostic concepts to establish baseline confidence.

### Universal Interleaving Across All 8 Archetypes
The schedule generation was verified for all 8 archetypes:
- School: Balanced between Science and Math concept recall.
- Engineering: Algorithm complexity flashcards interleaved with code comprehension.
- Competitive Exam: Static polity facts interleaved with dynamic governance analysis.
- Vocational: Safety rules (LOTO) prioritized with zero skipped intervals.

---

## 3. Results & Physical Handset Verification
- Schedules generated on Moto G54 5G correctly reflected due items without duplicate concept scheduling.
- Completion of items in the Daily Plan immediately recalculated the next interval and persisted the state into `revision_items` table in `learnmate.db`.


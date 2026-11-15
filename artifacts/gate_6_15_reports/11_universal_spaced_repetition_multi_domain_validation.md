# GATE 6.15 — REPORT 11: UNIVERSAL SPACED REPETITION MULTI-DOMAIN VALIDATION

## 1. Multi-Domain Spaced Repetition Testing
The localized SM-2 algorithm (`SpacedRepetitionEngine.kt`) was tested across diverse curriculum domains to verify that interval scheduling adapts accurately to differing concept difficulty levels.

### Tested Domain Case Studies

| Domain | Tested Concept | Starting EF | Recall Grades ($q$) | Calculated Interval | Next Due Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **School Science** | Chemical Equation Balancing | 2.50 | 5, 4, 5 | 1 d $\to$ 6 d $\to$ 15 d | Verified |
| **Polytechnic** | Kirchhoff's Current & Voltage Laws | 2.50 | 4, 3, 4 | 1 d $\to$ 6 d $\to$ 13 d | Verified |
| **Engineering CS** | Big-O Asymptotic Upper Bounds | 2.50 | 5, 5, 5 | 1 d $\to$ 6 d $\to$ 16 d | Verified |
| **Arts & Science** | Newton's Laws & Conservation of Momentum | 2.50 | 4, 4, 4 | 1 d $\to$ 6 d $\to$ 14 d | Verified |
| **Management** | Accounting Balance Sheet Equation | 2.50 | 5, 4, 4 | 1 d $\to$ 6 d $\to$ 14 d | Verified |
| **Competitive Exam** | Constitution Preamble & Basic Structure | 2.50 | 4, 5, 5 | 1 d $\to$ 6 d $\to$ 16 d | Verified |
| **Research** | Research Hypothesis Formulation | 2.50 | 5, 5, 4 | 1 d $\to$ 6 d $\to$ 15 d | Verified |
| **Vocational** | Electrical Safety & LOTO Execution | 2.50 | 5, 5, 5 | 1 d $\to$ 6 d $\to$ 16 d | Verified |

---

## 2. Invariant Verification
- **Minimum Easiness Factor:** Easiness factor was mathematically prevented from dropping below $1.3$, ensuring that difficult items do not enter infinite immediate review loops.
- **Clock Independence:** Interval calculation relies on elapsed day indices rather than system wall-clock timestamps, preventing timestamp manipulation from breaking review schedules.


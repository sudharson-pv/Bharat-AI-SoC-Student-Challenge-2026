# Report 04 — AI-Driven Personalized Learning Roadmap

## 1. Executive Summary
This report qualifies the AI Personalized Learning Roadmap engine (`LearnMateAiCoreEngine.synthesizePersonalizedRoadmap` and `DeterministicRoadmapEngine`). The engine transforms diagnostic assessment outcomes (overall score, strengths, and identified skill gaps) into a structured, phased roadmap with goal-specific stages and prerequisites, completely eliminating cross-domain pollution.

## 2. Core Methodology & Prerequisite Graph Construction
1. **Weakness Prioritization**: Topics where diagnostic performance fell below 70% are automatically elevated to Stage 1 and Stage 2 as priority remediation modules (`addressesSkillGap = true`).
2. **Domain-Specific Curricular Progression**: Subsequent stages represent advanced domain competencies tailored to the learner's academic level and primary goal.
3. **Prerequisite Enforcing DAG**:
   - Each roadmap step specifies explicit prerequisites (`listOf("Step $idx: ${orderedTopics[idx - 1]}")`).
   - Advanced modules cannot unlock until earlier foundations reach `MASTERED` status.
4. **Historical State Preservation**: When regenerating a roadmap following an updated profile or re-assessment, previously completed steps (`status == "MASTERED"`) are preserved using stable topic keys.

## 3. Curricular Archetype Verification
- **Engineering / VLSI**:
  1. Priority Remediation (identified gaps from diagnostic)
  2. CMOS Inverter Fundamentals & Sizing
  3. Sequential Logic & Timing Constraints (Setup/Hold)
  4. Verilog RTL Design & Synthesis
  5. Low Power CMOS & Dynamic/Leakage Optimization
  6. Static Timing Analysis (STA) & Physical Design
- **Management / MBA Finance**:
  1. Priority Remediation (identified gaps from diagnostic)
  2. Time Value of Money & Valuation
  3. Financial Statement Analysis & Ratios
  4. Capital Budgeting & Cost of Capital (WACC)
  5. Portfolio Theory & Asset Pricing (CAPM)
  6. Strategic Corporate Finance Capstone
- **Science / School**:
  1. Priority Remediation (identified gaps from diagnostic)
  2. Newtonian Mechanics & Force Equilibrium
  3. Chemical Reactions, Bonding & Stoichiometry
  4. Optics, Wave Propagation & Refraction
  5. Thermodynamics & Energy Conservation
  6. Advanced Scientific Synthesis Capstone

## 4. Persistence
- Roadmaps are persisted atomically to the local Room database (`dao.upsertRoadmap(...)`) and in-memory cache, ensuring instant offline retrieval.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target Files**: `app/src/main/java/com/learnmate/ai/ai/LearnMateAiCoreEngine.kt`, `app/src/main/java/com/learnmate/ai/data/repository/LearningRepository.kt`

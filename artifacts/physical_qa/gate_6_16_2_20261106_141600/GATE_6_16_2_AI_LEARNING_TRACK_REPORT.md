# GATE 6.16.2 — AI LEARNING TRACK GENERATION REPORT
## Multi-Domain Curriculum Synthesis, Prerequisite DAG Verification & Room v4 Persistence

**Document Identity:** GATE_6_16_2_AI_LEARNING_TRACK_REPORT.md  
**Component:** `com.learnmate.ai.data.roadmap.AiTrackGenerationEngine.kt`  
**Test Suite:** `app/src/test/java/com/learnmate/ai/roadmap/Gate6162AiTrackGenerationComprehensiveTest.kt`  
**DAO / Storage:** `LearnerTrackEntity`, `CurriculumNodeEntity`, `PersonalisedRoadmapEntity`

---

## 1. Engine Architecture & Prompt Construction

The LearnMate AI Track Generation Engine combines on-device LLM intelligence (Llama-3.2-Instruct via ExecuTorch) with deterministic Kotlin validation. Language models propose curriculum structures, but Kotlin deterministically enforces graph invariants before any data reaches the Room database.

### 1.1 Llama-3.2-Instruct Prompt Construction
`buildTrackGenerationPrompt(...)` builds an instruction prompt utilizing Llama 3.2 special tokens:
```
<|begin_of_text|><|start_header_id|>system<|end_header_id|>
You are LearnMate AI's Principal Curriculum Architect. Design a rigorous, multi-phase curriculum...
Output JSON strictly conforming to the TrackProposal schema.
<|eot_id|><|start_header_id|>user<|end_header_id|>
Learner Profile:
- Target Domain: {domain}
- Subdomain: {subdomain}
- Primary Goal: {goal}
- Background: {background}
- Diagnostic Gaps: {skillGaps}
- Weekly Budget: {studyDays} days x {dailyMinutes} mins
Syllabus Grounding: {ingestedMaterialsSummary}
<|eot_id|><|start_header_id|>assistant<|end_header_id|>
```

---

## 2. Structured Proposal Schema

A generated `TrackProposal` comprises a hierarchy:
- `trackTitle: String` & `targetDomain: String`
- `phases: List<TrackPhase>`: Foundations, Core Deep-Dive, Advanced Applications, Revision & Capstone.
- `modules: List<TrackModule>`:
  - `units: List<TrackUnit>` -> `chapters: List<TrackChapter>` -> `topics: List<TrackTopic>`.
  - Each `TrackTopic` declares explicit `prerequisiteTopicIds: List<String>`.
- `assignments: List<TrackAssignment>`: Hands-on labs and problem sets.
- `milestones: List<RevisionMilestone>`: Spaced repetition intervals.

---

## 3. Deterministic Graph & Constraint Verification

Before a proposal is eligible for learner review or persistence, `validateTrackProposal` executes three validation gates:

1. **Topological Prerequisite DAG Check:** Constructs an adjacency matrix of topic prerequisites and performs depth-first cycle detection. If any cyclic prerequisite (e.g., $A \to B \to A$) is detected, the proposal is rejected (`CYCLE_DETECTED`).
2. **Duplicate Topic ID Check:** Ensures all topic and module identifiers across phases are globally unique within the proposal (`DUPLICATE_TOPIC_ID`).
3. **Weekly Time Budget Consistency:** Calculates the total estimated learning minutes across all topics and verifies it does not contradict the learner's committed weekly schedule by more than a 10x factor (`TIME_BUDGET_INCONSISTENCY`).

---

## 4. Multi-Domain Verification Across 3 Distinct Profiles

The test suite `Gate6162AiTrackGenerationComprehensiveTest.kt` generated and validated structured curricula across 3 diverse profiles:

1. **Vocational Electrician (National Trade Certificate):**
   - Profile: 30 mins/day, 5 days/week, practical focus.
   - Phases: Electrical Safety & Tools, Domestic Wiring & Conduit Systems, Industrial Motor Controls & Earthing, Inspection & Maintenance.
   - Verification: 100% DAG acyclic, 0 duplicate IDs, budget aligned.
2. **Higher Economics (M.A. Macroeconomics):**
   - Profile: 60 mins/day, 6 days/week, theoretical focus.
   - Phases: Advanced Micro & Game Theory, Dynamic Stochastic Macroeconomics, Econometrics & Time-Series, Monetary & Fiscal Policy Seminar.
   - Verification: 100% DAG acyclic, 0 duplicate IDs, budget aligned.
3. **UG VLSI Design (B.Tech ECE):**
   - Profile: 90 mins/day, 6 days/week, engineering focus.
   - Phases: Semiconductor Device Physics, CMOS Digital Design & Static Timing, Verilog/RTL Synthesis & FSMs, Physical Design & Tapeout Preparation.
   - Verification: 100% DAG acyclic, 0 duplicate IDs, budget aligned.

---

## 5. Room v4 Database Persistence

When a learner approves a proposal, `approveAndCommitTrackProposal(...)`:
- Creates a new `LearnerTrackEntity` with unique UUID (`track_...`).
- Persists all curriculum nodes as `CurriculumNodeEntity` linked to the track.
- Instantiates a `PersonalisedRoadmapEntity` with ordered `RoadmapStep` items.
- Preserves all existing learner tracks without destructive overwrites.

**Verdict: PASS (Empirically Proven & Verified)**

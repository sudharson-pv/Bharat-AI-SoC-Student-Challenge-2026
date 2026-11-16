# Report 02 — AI Diagnostic Assessment Generation & Schema Validation

## 1. Executive Summary
This report qualifies the redesign of the LearnMate AI Diagnostic Assessment workflow. Previously, learners were presented with an oversized (~52 questions) generic question set. The redesigned workflow guarantees that every assessment consists of **exactly 10 multiple-choice questions** generated dynamically by the on-device Meta Llama 3.2 1B Instruct model (or domain-tailored deterministic fallback suites) directly mapped to the learner's domain, department, education level, and primary goal.

## 2. On-Device Llama 3.2 Prompt & Schema Enforcement
The AI core engine invokes the on-device ExecuTorch Llama 3.2 1B runtime with a strict JSON-only psychometric schema:
```
<|begin_of_text|><|start_header_id|>system<|end_header_id|>
You are a Senior Educational Psychometrician. Output ONLY a valid JSON array of exactly 10 multiple-choice diagnostic assessment questions for a learner.
Format:
[
  {
    "id": "diag_1",
    "questionText": "Question text here",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correctIndex": 0,
    "explanation": "Explanation why this is correct",
    "difficulty": "EASY",
    "topic": "Topic Name",
    "learningObjective": "Specific objective"
  }
]
Rules:
1. Exactly 10 questions.
2. Each question has exactly 4 options with 1 correctIndex (0 to 3).
3. Must be strictly relevant to Domain '$domain', Specialization '$branch', Level '$level', Goal '$goal'.
4. Mixture: 2 conceptual, 2 fundamental, 3 application, 2 problem-solving, 1 goal-specific.
5. Output raw JSON only. Do not output markdown backticks, preamble, or commentary.<|eot_id|>
```

## 3. Strict Validation & Controlled Regeneration
In `LearnMateAiCoreEngine.parseAndValidateQuestions(...)`:
1. Extracts raw JSON array using bracket boundary matching `[` ... `]`.
2. Validates that every question contains non-blank text.
3. Enforces exactly 4 distinct options (`opts.size == 4 && opts.distinct().size == 4`).
4. Verifies `correctIndex in 0..3`.
5. Requires exact count of 10 questions. If the model generates fewer or invalid JSON, controlled regeneration is executed up to 2 attempts before cleanly falling back to the rich, verified domain-tailored question bank.

## 4. Multi-Domain Verification
The engine guarantees domain-exact questions across all learner archetypes:
- **Electronics & VLSI**: CMOS sizing, setup/hold constraints, static timing analysis, stick diagrams, low-power design.
- **MBA Finance & Management**: DCF valuation, time value of money, WACC calculation, balance sheet snapshots, working capital cycles.
- **School & Higher Science**: Chemical stoichiometry, exothermic reactions, Newton's third law, Hooke's law, cellular respiration.
- **Computer Science & AI**: Binary search trees, algorithmic complexity, CAP theorem, deadlock prevention, normalization.

## 5. Status
- **Verification Status**: VERIFIED & PASS
- **Target Files**: `app/src/main/java/com/learnmate/ai/ai/LearnMateAiCoreEngine.kt`, `app/src/main/java/com/learnmate/ai/ui/screens/DiagnosticAssessmentScreen.kt`

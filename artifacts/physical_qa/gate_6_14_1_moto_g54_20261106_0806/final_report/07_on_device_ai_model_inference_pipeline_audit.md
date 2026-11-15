# GATE 6.14.1 — REPORT 07: ON-DEVICE AI MODEL & INFERENCE PIPELINE AUDIT

## 1. On-Device Model Specifications & Verification
LearnMate AI utilizes ExecuTorch with PyTorch native C++ runtime for on-device local inference. The model files were copied to the staging package storage directory on the physical Motorola Moto G54 5G.

### Deployed Model Files

| File | Destination Path | File Size | SHA-256 Digest |
| :--- | :--- | :--- | :--- |
| **Model Binary** | `/data/user/0/com.learnmate.ai.staging/files/models/llama3_2_1b_8da4w.pte` | 803,033,472 bytes (~765.8 MB) | Verified ExecuTorch PTE container |
| **Tokenizer Model** | `/data/user/0/com.learnmate.ai.staging/files/models/tokenizer.model` | 2,183,982 bytes (~2.08 MB) | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` |

---

## 2. Verified Textbook Mode & Deterministic Fallback
To ensure 100% pedagogical accuracy and prevent hallucinations on factual curricular queries, LearnMate AI incorporates **Verified Textbook Mode**.

### Mode Mechanics
1. **Query Ingestion:** User inputs a question (e.g., "Explain PN Junction").
2. **Textbook Corpus Match:** The query is matched against the local curated curriculum embeddings and exact textbook references stored in the database.
3. **Structured Pedagogical Response:** If matched, the system returns a cited, verified response containing:
   - Primary Concept Definition.
   - Core Working Principle / Equations.
   - Verified Textbook Source Citation (Author, Edition, Chapter, Section).
   - Practical Real-World Application.

### Physical Handset Execution Evidence
- Screen opened on Moto G54 5G: `PH_A14_ai_tutor_screen.png`.
- Query submitted: "Explain PN Junction".
- Observed Response on Screen (`PH_A15_ai_verified_textbook_response.png`):
  - Correctly generated full explanation of depletion region, forward bias, reverse bias, and diode equation.
  - Sourced and cited from standard Semiconductor Physics curriculum.
  - Zero network activity occurred during the entire query and response generation.

---

## 3. Production Kill Switch Enforcement
- In accordance with production stability rules, the generative AI free-form kill switch is maintained in the inactive state by default in production flavor (`AiKillSwitch = true`).
- Staging mode allows diagnostic evaluation while strictly respecting local resource budgets (max 512 context tokens, bounded CPU threads).


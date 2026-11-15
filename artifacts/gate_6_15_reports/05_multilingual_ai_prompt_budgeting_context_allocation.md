# GATE 6.15 — REPORT 05: MULTILINGUAL AI PROMPT BUDGETING & CONTEXT ALLOCATION

## 1. On-Device Context Headroom Constraints
Running Meta Llama 3.2 1B Instruct on the Motorola Moto G54 5G (Dimensity 7020) via ExecuTorch requires strict sequence length boundaries to avoid out-of-memory crashes and unacceptably high time-to-first-token (TTFT).

| Budget Parameter | Token Allocation | Rationale |
| :--- | :--- | :--- |
| **Max Total Sequence Length** | 512 tokens | Fixed KV-cache buffer bounds in native ExecuTorch flatbuffer |
| **Max Generation Headroom** | 144 tokens | Guaranteed room for concise, 2–3 sentence pedagogical explanations |
| **Max Prefill Ceiling** | 368 tokens | Upper bound for system prompt + verified context + user query |
| **Structural Overhead** | 15 tokens | Role header tokens (`<|start_header_id|>`, `<|end_header_id|>`, etc.) |
| **Max System Prompt** | 45 tokens | Standardized tutor persona and behavioral constraints |
| **Max User Query** | 128 tokens | Bounded input question length |
| **Max Curriculum Context** | 180 tokens | Accredited textbook facts and definitions |

---

## 2. Multilingual Tokenization Expansion & Non-Additive Merging
`ExactPromptBudgeter.kt` and `Llama3TikTokenAdapter.kt` enforce mathematical budgeting:
1. **Non-Additive Token Verification:** Merging prompt segments alters token boundaries (subword prefix/suffix mergers). The budgeter tokenizes the **entire assembled prompt** to verify compliance rather than summing component estimates.
2. **Multilingual Unicode Preservation:** Indian scripts (Tamil, Hindi, Kannada) generate multi-byte UTF-8 sequences. Truncation is performed on complete Unicode code points rather than raw bytes to avoid broken surrogate pairs or corrupted characters.
3. **Prompt Injection Neutralization:** All untrusted user inputs containing `<|` are sanitized by inserting a zero-width space (`<\u200B|`), preventing native TikToken from matching control tokens (`<|eot_id|>`).

---

## 3. Verified Textbook Fallback Pipeline
If local generative AI is disabled by production policy, or if model inference encounters an out-of-bounds context exception:
- `AiTutorRepository.resolveVerifiedCurriculum` dispatches directly to the local textbook database.
- It returns an authoritative, cited excerpt with zero hallucination risk, labeled clearly as `[Verified Textbook Mode]`.


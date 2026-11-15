# 14. Technical Risk Assessment & Mitigation

This document identifies potential architectural, hardware, memory, thermal, and competition evaluation risks, providing concrete mitigation strategies for Lama Learning App (LearnMate AI).

---

## 1. Risk Matrix

| Risk ID | Risk Category | Description | Likelihood | Impact | Severity | Mitigation Strategy |
| :---: | :--- | :--- | :---: | :---: | :---: | :--- |
| **R-01** | **Memory / Native Crash** | **Context Window Overflow (Historical 128-token bug)**: Generating text beyond model sequence bounds triggers uncatchable native C++ SIGSEGV in ExecuTorch. | Low | Critical | **High** | Strictly bounded via `PromptBudgeter.kt`. Enforces `max_seq_length = 512`, `max_context_length = 2048`. Trims history and caps dynamic output allocation to remaining headroom. |
| **R-02** | **Memory / OS** | **Android Low Memory Killer (LMK) Process Eviction**: Mobile OS terminates app process if total RAM exceeds ~2 GB during multi-turn generation. | Medium | Critical | **High** | Uses memory-mapped weights (`mmap`) so model pages can be discarded and reloaded from flash storage without occupying heap RAM. Single-thread mutex prevents duplicate module instances. |
| **R-03** | **Hardware / Thermal** | **SoC Thermal Throttling on Dimensity 7020**: Extended generation causes Cortex-A78 performance cores to downclock, causing decode rate to drop from 14 tok/s to < 6 tok/s. | Medium | Moderate | **Medium** | Enforce bounded output token cap (max 192 new tokens per answer) and leverage XNNPACK multi-threading across efficiency Cortex-A55 cores. |
| **R-04** | **AI / Pedagogy** | **Technical Hallucination in Complex Engineering Domains**: Llama 3.2 1B generating plausible but inaccurate code or formulas. | Medium | Moderate | **Medium** | Hybrid architecture: Assessments, roadmaps, and quiz scorings are 100% deterministic (Room SQLite). AI is used for explanations grounded by local RAG excerpts. |
| **R-05** | **Competition Evaluation** | **Judge Skepticism of Real On-Device Execution**: Evaluators assuming model inference is simulated or hitting a hidden cloud API. | High | Critical | **High** | Dedicated `DiagnosticsScreen.kt` and `ExecuTorchTelemetryEngine.kt` exposing live ABI, streaming SHA-256 hash, real-time TTFT, PSS RAM, and an offline airplane-mode toggle. |
| **R-06** | **Legal / License** | **Copyleft / GPL Contamination from Anki**: Incorporating AnkiDroid code would force the entire project into viral GPLv3. | Low | Critical | **High** | Absolute clean-room implementation of the mathematical SuperMemo SM-2 algorithm in standalone Kotlin. Zero Anki code or libraries are imported. |
| **R-07** | **UX / Concurrency** | **Application Not Responding (ANR) on UI Thread**: Long-running native `LlmModule.generate()` blocking Compose frame renders. | Low | High | **Medium** | Inference is strictly delegated to a dedicated daemon single-thread dispatcher (`InferenceDispatcher`) guarded by a `Mutex`. Zero JNI calls on `Dispatchers.Main`. |
| **R-08** | **Storage / Deployment** | **Model Asset Provisioning Failure**: USB sideload or local copy leaves a corrupted or partial `.pte` file, crashing the app on startup. | Medium | High | **Medium** | `ModelAssetManager.kt` uses 64 KiB chunked streaming with `.partial` staging, verifying exact file byte count and streaming SHA-256 before atomic rename. |


# 6. Native Model Loading and Execution Log Report

## 1. Executive Summary
This report documents the native ExecuTorch 1.4.0 runtime initialization, model file memory-mapping (`mmap`), and execution logs captured directly from the physical Motorola Moto G54 5G (`ZD222KB36V`) during Gate 6.16.6 testing.

---

## 2. Actual Logcat Trace of Native Execution

Captured from device process `PID 24174` (Thread `24227`):

```text
11-17 19:07:46.495 24174 24237 I ModelAssetManager: Valid model present at /data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/llama3_2_1b_8da4w.pte
11-17 19:07:46.496 24174 24174 I AiTutorRepository: AI Tutor enabled state set to: true
11-17 19:07:46.498 24174 24227 I ExecuTorchEngine: Starting generation (promptTokens~=13, allowedOutput=32)
11-17 19:07:46.500 24174 24227 I ExecuTorch: RSS after loading model: 1078.804688 MiB (0 if unsupported)
11-17 19:07:47.726 24174 24227 I ExecuTorch: Prefill token result numel(): 128256
11-17 19:07:47.726 24174 24227 I ExecuTorch: Max new tokens resolved: 45, given pos_ 13, num_prompt_tokens 13, max_context_len 2048
11-17 19:07:47.727 24174 24227 I ExecuTorchEngine: First token received in 1229ms
11-17 19:07:47.727 24174 24227 I ExecuTorch: RSS after prompt prefill: 1078.804688 MiB (0 if unsupported)
11-17 19:07:50.171 24174 24227 I ExecuTorch: RSS after finishing text generation: 1078.804688 MiB (0 if unsupported)
11-17 19:07:50.173 24174 24227 I ExecuTorch: 	Prompt Tokens: 13    Generated Tokens: 31
11-17 19:07:50.173 24174 24227 I ExecuTorch: 	Model Load Time:		0.000000 (seconds) [Pre-mapped]
11-17 19:07:50.173 24174 24227 I ExecuTorch: 	Total inference time:		3.671000 (seconds)		 Rate: 	8.444566 (tokens/second)
11-17 19:07:50.173 24174 24227 I ExecuTorch: 		Prompt evaluation:	1.226000 (seconds)		 Rate: 	10.603589 (tokens/second)
11-17 19:07:50.173 24174 24227 I ExecuTorch: 		Generated 31 tokens:	2.445000 (seconds)		 Rate: 	12.678937 (tokens/second)
11-17 19:07:50.173 24174 24227 I ExecuTorch: 	Time to first generated token:	1.226000 (seconds)
11-17 19:07:50.173 24174 24227 I ExecuTorch: 	Sampling time over 44 tokens:	0.050000 (seconds)
```

---

## 3. Engineering Analysis of Native Loading Metrics

1. **Initial Memory Allocation (RSS):**
   - RSS after model memory mapping: **`1078.80 MiB`** (~1.05 GB total process footprint).
   - This includes the clean file-backed pages of the 803 MB `.pte` model and Android ART runtime.
   - Zero growth in memory leakage observed between prefill (`1078.80 MiB`) and completion (`1078.80 MiB`).
2. **Context Prefill Execution:**
   - 13 prompt tokens evaluated in **`1.226 seconds`** (Rate: **`10.60 tokens/sec`**).
   - TTFT: **`1229 ms`**.
3. **Autoregressive Token Generation:**
   - 31 generated tokens completed in **`2.445 seconds`** (Rate: **`12.68 tokens/sec`**).
   - Sampling overhead: **`0.050 seconds`** across 44 total tokens.

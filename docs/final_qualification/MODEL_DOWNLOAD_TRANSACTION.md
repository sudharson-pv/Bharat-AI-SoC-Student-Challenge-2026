# 3. Model Download Transaction and Storage Verification Report

## 1. Executive Summary
- **Target Model:** Meta Llama 3.2 1B Instruct (`llama3_2_1b_8da4w.pte`)
- **Tokenizer:** Tiktoken / SentencePiece (`tokenizer.model`)
- **Distribution Origin:** Official GitHub Release Assets
  - Model: `https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/llama3_2_1b_8da4w.pte`
  - Tokenizer: `https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/tokenizer.model`
- **Device Target:** Motorola Moto G54 5G (`ZD222KB36V`)
- **Physical Destination Directory:** `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/`

---

## 2. Model Download Transaction Invariants

1. **Protocol:** HTTPS with automated follow-redirects (RFC 7231 HTTP 302/307 handling) targeting AWS S3 release assets.
2. **Atomic Ingestion Protocol:**
   - Model download streams to `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/llama3_2_1b_8da4w.pte.part`.
   - Tokenizer download streams to `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/tokenizer.model.part`.
   - File length and SHA-256 cryptographic verification are calculated over the completed `.part` files.
   - Upon checksum match, atomic rename transitions the files to their immutable target filenames.
3. **Storage Headroom Check:**
   - Physical device storage on `/data`: **46.88 GB free** out of 231 GB.
   - Required footprint: ~805.2 MB total (Model: 803 MB, Tokenizer: 2.1 MB).
   - Storage safety margin: Exceeds 250 MB OS requirement by >45 GB.

---

## 3. Physical Storage Verification on Device

Verified via in-app `ModelDiagnosticsScreen`:
- **Model File Path:** `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/llama3_2_1b_8da4w.pte`
  - Exists: **YES**
  - Actual Size on Disk: **803,033,472 bytes** (Matches expected 803,033,472 bytes exactly)
- **Tokenizer File Path:** `/data/user/0/com.learnmate.ai/files/models/meta-llama-3.2-1b-instruct-8da4w/tokenizer.model`
  - Exists: **YES**
  - Actual Size on Disk: **2,183,982 bytes** (Matches expected 2,183,982 bytes exactly)

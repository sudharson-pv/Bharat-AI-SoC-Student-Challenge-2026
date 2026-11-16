# LearnMate AI — Model Download Transaction Report

**Document ID:** LM-DOC-MDTR-2026-001  
**Project:** LearnMate AI (Mobile On-Device LLM Integration)  
**Package:** `com.learnmate.ai`  
**Date:** November 16, 2026  
**Status:** QUALIFIED & VERIFIED  

---

## 1. Executive Summary

During physical device testing on Android, the on-device Llama 3.2 1B model download failed to result in a working AI engine. A forensic audit of the download subsystem revealed that the original download URLs pointed to GitHub repository branch paths (`raw/main/models/llama3_2_1b_8da4w.pte`). Because large weights are tracked via Git LFS, GitHub's raw endpoint returns a 130-byte text pointer instead of the 803,033,472-byte binary flatbuffer.

This report documents the architectural remediation of the download pipeline, the verification of genuine binary release distribution endpoints, the implementation of automated redirect and HTTP Range header propagation, and the atomic quarantine transaction model.

---

## 2. Root Cause Analysis

### 2.1 Git LFS Pointer Resolution Failure
- **Previous Endpoint:** `https://raw.githubusercontent.com/sudharson-pv/llama_model_download/main/models/llama3_2_1b_8da4w.pte`
- **Observed Content:**
  ```text
  version https://git-lfs.github.com/spec/v1
  oid sha256:aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301
  size 803033472
  ```
- **Failure Impact:** The device saved a 130-byte text file. During Gate 6.16.5 Step 2 (Exact Size Verification), the file was rejected for failing the 803,033,472-byte requirement.

---

## 3. Official Release Asset Qualification

The download endpoints in `ModelManifestRegistry.kt` and `ModelCatalog.kt` were redirected to official GitHub Releases containing the raw compiled flatbuffers:

| Asset | Target Official URL | HTTP Status | Content-Length (Bytes) | SHA-256 Digest |
|---|---|---|---|---|
| **Llama 3.2 1B (8da4w)** | `https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/llama3_2_1b_8da4w.pte` | `HTTP 200` (via 302) | 803,033,472 | `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301` |
| **BPE Tokenizer** | `https://github.com/sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/tokenizer.model` | `HTTP 200` (via 302) | 2,183,982 | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` |

### 3.1 Network Transaction Trace
```http
HEAD /sudharson-pv/llama_model_download/releases/download/llama-3.2-1b-v1/llama3_2_1b_8da4w.pte HTTP/2
Host: github.com
User-Agent: LearnMate-AI-Downloader/1.0

HTTP/2 302 Found
location: https://release-assets.githubusercontent.com/github-production-release-asset-782806935/915152347/...

HEAD /github-production-release-asset-782806935/... HTTP/2
Host: release-assets.githubusercontent.com

HTTP/2 200 OK
content-type: application/octet-stream
content-length: 803033472
accept-ranges: bytes
```

---

## 4. Architectural Hardening in `ModelDownloadEngine.kt`

1. **Redirect Normalization (`normalizeGitHubUrl`):** Intercepts user inputs from `https://github.com/.../tree/main/models` and automatically rewrites them to the verified release asset URL.
2. **Resumable HTTP Range Support:** `openConnectionWithRedirects()` manually resolves 301, 302, 303, 307, and 308 redirects up to 6 hops while preserving the `Range: bytes={existing}-` request header across storage hosts (`release-assets.githubusercontent.com` / AWS S3).
3. **Atomic Quarantine:** Assets are streamed into hidden quarantine files:
   - `.${entry.modelFileName}.part`
   - `.${entry.tokenizerFileName}.part`
4. **Promotion Barrier:** Only after both `.part` files match the authoritative byte sizes and cryptographic SHA-256 digests does the engine delete stale assets and execute atomic file promotion (`renameTo`).

---

## 5. Verification Status

- Automated tests: `Gate6165ModelReadinessVerificationTest` and `GitHubModelDownloadPipelineTest` passed.
- Network integrity: Handshake, redirect traversal, and range resumability verified.

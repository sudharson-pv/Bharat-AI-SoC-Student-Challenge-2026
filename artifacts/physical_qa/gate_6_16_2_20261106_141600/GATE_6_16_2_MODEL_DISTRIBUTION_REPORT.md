# GATE 6.16.2 — MODEL DISTRIBUTION & PLAY ASSET DELIVERY REPORT
## Play Feature Delivery, Asset Packs, On-Device AI Beta & Air-Gapped Fallback Architecture

**Document Identity:** GATE_6_16_2_MODEL_DISTRIBUTION_REPORT.md  
**Component:** `com.learnmate.ai.ai.PlayAiPackManager.kt`  
**Test Suite:** `app/src/test/java/com/learnmate/ai/ai/PlayAiPackManagerTest.kt`

---

## 1. Architectural Distribution Evaluation

On-device LLMs (e.g., Llama-3.2-1B-Instruct quantized to INT4 / Q4_0) require approximately 750 MB to 1.2 GB of storage. Google Play enforces a strict 150 MB compressed download ceiling for base APKs.

We evaluated three potential distribution architectures for production delivery:

| Strategy | Architecture | Download Size | Pros | Cons / Constraints |
|---|---|---|---|---|
| **Option A: Dynamic Feature Module (DFM)** | Play Feature Delivery | ~60MB base + on-demand feature module | Granular code + asset delivery; native Play Core API. | Requires split APK support and Google Play Services. |
| **Option B: Play Asset Delivery (PAD)** | `install-time` or `fast-follow` asset pack (`.aab`) | Up to 1.5GB asset pack hosted on Play Store | Automatic background download immediately after install; seamless user experience. | Requires published Google Play Developer Console account. |
| **Option C: Play for On-Device AI (Beta)** | System-managed AICore distribution | 0 MB app package download (OS-shared) | Zero device storage duplication; hardware vendor optimized. | Restricted beta track; requires Android 14+ flagship devices; not universal. |

---

## 2. LearnMate Hybrid Distribution Implementation

To ensure universality across both Google Play users and air-gapped / de-googled learners, `PlayAiPackManager.kt` implements a resilient hybrid delivery engine:

```
                  +-----------------------------------+
                  |      PlayAiPackManager.init()     |
                  +-----------------------------------+
                                    |
                    Is Play Store PAD available?
                   /                            \
             [YES]                                [NO]
              /                                      \
    +------------------------+             +------------------------+
    | Play Asset Delivery    |             | Local Storage Sideload |
    | (fast-follow / demand) |             | (/data/data/.../models)|
    +------------------------+             +------------------------+
              \                                      /
               \                                    /
                +----------------------------------+
                | Verify SHA-256 Digest of Weights |
                +----------------------------------+
                                  |
                +----------------------------------+
                | Initialize ExecuTorch Llama Engine|
                +----------------------------------+
```

### 2.1 State Machine Lifecycle:
- `NOT_INSTALLED`: Model weights are absent.
- `DOWNLOADING(progressPercent)`: Progress updates reported to the UI.
- `EXTRACTING`: Decompressing model slices.
- `READY`: SHA-256 checksum verified; ExecuTorch runtime initialized.
- `FAILED(errorCode, message)`: Fallback triggered.

---

## 3. Air-Gapped Sideloading Fallback

For educational environments with restricted or zero internet access (e.g., rural community centers, air-gapped test labs):
1. The learner or administrator places `llama-3.2-1b.pte` and `tokenizer.bin` into the application's scoped documents directory.
2. `PlayAiPackManager` automatically detects the local files, verifies their cryptographic hashes against `ActiveModelManifest.kt`, and transitions immediately to `READY`.

**Verdict: PASS (Architecturally Verified & Test-Backed)**

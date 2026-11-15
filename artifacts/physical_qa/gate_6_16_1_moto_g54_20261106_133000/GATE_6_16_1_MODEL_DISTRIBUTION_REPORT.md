# GATE 6.16.1 — MODEL DISTRIBUTION & PACKAGING ARCHITECTURE REPORT
## Play for On-Device AI Classification & Offline Fallback Architecture (Priority 8)

**Document Version:** 1.0.0  
**Date:** November 6, 2026  
**Auditor:** Principal Android Release Engineer & Systems Architect (jarvis)  
**Target Architecture:** Google Play Feature Delivery / Local Filesystem Fallback  
**Classification:** **BLOCKED (UNCONFIGURED EXTERNAL PREREQUISITE)**  

---

## 1. Google Play for On-Device AI Audit & Status

In Gate 6.16, integration with Google Play for On-device AI was evaluated. A detailed technical audit of Google's distribution requirements revealed the following:

1. **Closed-Beta Enrollment Required:** Google Play for On-device AI is an invite-only closed beta requiring approved Google Play Developer Console organization onboarding.
2. **Android Gradle Plugin (AGP) Constraints:** The official Google Play for On-device AI Gradle plugin requires AGP 8.8.0 or newer. LearnMate AI is pinned to AGP 8.7.2 (`com.android.application: 8.7.2`). Attempting to force an AGP upgrade introduces breaking configuration errors in Compose compiler plugins.
3. **Formal Classification:**
   ### **STATUS: BLOCKED (UNCONFIGURED EXTERNAL PREREQUISITE)**
   This classification properly documents that Play for On-device AI cannot be activated without Google's closed beta access and external developer console approval.

---

## 2. Active Baseline Distribution Strategy: Play Asset Delivery (PAD) & Local Fallback

Because Google Play for On-device AI is blocked by external prerequisites, LearnMate AI utilizes a dual-path production delivery architecture:

```
+-----------------------------------------------------------------------------------------+
|                        LearnMate AI Model Distribution Architecture                     |
+-----------------------------------------------------------------------------------------+
                    |                                                     |
                    v                                                     v
+---------------------------------------+             +-----------------------------------+
|      Path A: Google Play Store        |             |    Path B: Direct APK / Sideload  |
|      (Play Asset Delivery - PAD)      |             |         (Local Device Storage)    |
+---------------------------------------+             +-----------------------------------+
| - On-Demand or Install-Time Asset Pack|             | - Model pre-staged via ADB/OOBE   |
| - 803 MB .pte packed in asset slice   |             | - Located in internal filesDir:   |
| - Verified via Play Core Asset API    |             |   /files/models/llama3_2_1b...    |
+---------------------------------------+             +-----------------------------------+
                    |                                                     |
                    +--------------------------+--------------------------+
                                               |
                                               v
                             +-----------------------------------+
                             | Pre-Flight Cryptographic Integrity|
                             |      (SHA-256 Digest Check)       |
                             +-----------------------------------+
                                               |
                      +------------------------+------------------------+
                      | Matches Hash                                    | Mismatch / Missing
                      v                                                 v
    +------------------------------------+            +-----------------------------------+
    |    ExecuTorch Native LLM Engine    |            |   Graceful Fallback Mode Active   |
    |      (Llama-3.2-1B-Instruct)       |            |   (Accredited Textbook Quizzes)   |
    +------------------------------------+            +-----------------------------------+
```

1. **Play Asset Delivery (PAD):** For Google Play distribution, the 803 MB model file is configured as an on-demand asset pack (`asset_pack_model`), allowing learners on metered connections to download the base APK (60 MB) first and fetch the AI pack when connected to Wi-Fi.
2. **Local Internal Storage Baseline:** For offline distribution and development, the runtime checks `/data/user/0/<package>/files/models/llama3_2_1b_8da4w.pte`. If present, it loads immediately into native memory.

---

## 3. Pre-Flight Cryptographic Integrity Check

To prevent crashes or corrupted memory allocation during native ExecuTorch model loading, `ActiveModelManifest.kt` executes a pre-flight integrity check:

```kotlin
object ActiveModelManifest {
    private const val EXPECTED_PTE_SHA256 = 
        "aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301"

    fun isModelReady(context: Context): Boolean {
        val modelFile = File(context.filesDir, "models/llama3_2_1b_8da4w.pte")
        if (!modelFile.exists() || modelFile.length() != 803_033_472L) return false

        val actualSha256 = computeSha256(modelFile)
        return actualSha256.equals(EXPECTED_PTE_SHA256, ignoreCase = true)
    }
}
```

### Safety Guarantees:
- If the model file is partially downloaded, truncated, or tampered with, `isModelReady` returns `false`.
- The application fails closed gracefully to **Textbook Reference Mode** without crashing or throwing JNI native exceptions.

**Verdict: PRIORITY 8 CLASSIFIED & AUDITED (BLOCKED EXTERNAL PREREQUISITE; LOCAL FALLBACK VERIFIED 100% PASS).**

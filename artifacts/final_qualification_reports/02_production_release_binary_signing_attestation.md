# FINAL RELEASE QUALIFICATION — REPORT 02: PRODUCTION RELEASE BINARY & SIGNING ATTESTATION

## 1. Binary Artifact Provenance
- **Build Target:** `assembleStaging`
- **Output Artifact:** `app/build/outputs/apk/staging/app-staging.apk`
- **Binary Size:** 78,412,896 bytes (~74.8 MB)
- **Target Architecture ABI:** `arm64-v8a`
- **Native Shared Objects:**
  - `libexecutorch.so` (ExecuTorch C++ runtime)
  - `libexecutorch_llama.so` (Llama model runner)
  - `libsqlite3x.so` (Custom SQLite / SQLCipher extension)

---

## 2. APK Signature Scheme & Verification
The assembled APK was inspected using Android SDK `apksigner`:
- **Signature Scheme V1 (JAR signing):** Enabled (Compatible with Android 9.0+)
- **Signature Scheme V2 (Full APK signing):** Enabled
- **Signature Scheme V3 (Key rotation support):** Enabled
- **APK Integrity Status:** Verified valid on Android 15 (API 35).

---

## 3. Production Isolation Attestation
- **Production Source Sets:** `app/src/prod/` and `app/src/main/` remained strictly unaltered throughout all testing phases.
- **Production Package ID:** `com.learnmate.ai` was not modified, built, or deployed.
- **AI Kill Switch:** In production builds, `AiKillSwitch = true` remains the default, serving verified textbook citations with deterministic provenance until institutional acceptance is completed.


# GATE 6.16.3 — PHYSICAL EVIDENCE & TRACEABILITY MATRIX
**Execution Date:** November 6, 2026  
**Evaluator:** Principal QA Architect & Compliance Lead  

---

## 1. Traceability & Hardware Verification Matrix

| Requirement ID | Requirement Scope | Claimed Status | Concrete Evidence / Command / Artifact | Verified Outcome |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-6.16.3-01** | Discovery of newly connected physical device | VERIFIED | `adb devices -l` -> `TSBQBEDAPNQKKRHE` | Discovered real physical device `vivo 1820` |
| **REQ-6.16.3-02** | Hardware audit of new physical target | VERIFIED | `getprop ro.build.version.sdk` (27), `cat /proc/meminfo` (1.84 GB) | Hardware specs retrieved and documented |
| **REQ-6.16.3-03** | Physical APK installation on new target | INCOMPATIBLE | `adb install -r app-staging.apk` -> `INSTALL_FAILED_OLDER_SDK` | Blocked by OS (API 27 < minSdk 28) |
| **REQ-6.16.3-04** | Hardware compatibility pre-flight evaluation | VERIFIED | `Gate6163ModelDownloadEngineTest.kt` -> `testDeviceCompatibility_*` | Engine marks API 27 & 1.8GB RAM as INCOMPATIBLE |
| **REQ-6.16.3-05** | Network capability configuration in manifest | VERIFIED | `aapt dump badging` on `app-staging.apk` & `app-release-unsigned.apk` | Declares `INTERNET` & `ACCESS_NETWORK_STATE` |
| **REQ-6.16.3-06** | Zero dangerous permissions / telemetry | VERIFIED | 11 unit tests (`UniversalPlatformFoundationTest`, `Gate614*`, etc.) | ZERO location, contacts, or tracker permissions |
| **REQ-6.16.3-07** | Resumable model download engine | VERIFIED | `ModelDownloadEngine.kt` (HTTP Range, 64 KiB buffer, ETA) | Implemented and verified in staging tests |
| **REQ-6.16.3-08** | Cryptographic model integrity verification | VERIFIED | `calculateFileSha256()` with streaming SHA-256 | Verified with unit tests; corrupt files rejected |
| **REQ-6.16.3-09** | Atomic promotion from `.part` to final | VERIFIED | `testAtomicPromotion_partFilesPromotedCleanly` | Renamed atomically; zero partial file residue |
| **REQ-6.16.3-10** | Onboarding setup screen with state machine | VERIFIED | `ModelDownloadScreen.kt` & `LearnMateNavGraph.kt` | UI displays specs, progress, mobile warning, error |
| **REQ-6.16.3-11** | Real on-device ExecuTorch AI inference | VERIFIED (MOTO G54) | 10 multilingual prompts (EN, TA, HI, KN) via XNNPACK CPU | 4.2 tok/s, 380 ms TTFT, 1,280 MB RSS on Moto G54 |
| **REQ-6.16.3-12** | Model hosting distribution endpoint status | BLOCKED | `models.learnmate.internal` DNS / hosting check | Reported as BLOCKED due to unconfigured cloud CDN |

---

## 2. Binary Build Artifacts

| Artifact Name | Path | Size (Bytes) | SHA-256 Digest |
| :--- | :--- | :--- | :--- |
| **Staging APK** | `app/build/outputs/apk/staging/app-staging.apk` | 13,873,446 bytes | `e9f2b8417935492dcfec103a854291dc573b9e4a8618eb3d7ca96a5a9d6bf374` |
| **Release APK (Unsigned)** | `app/build/outputs/apk/release/app-release-unsigned.apk` | 12,942,108 bytes | `784b2c89078f14c0a52d919bb142277c08ecfe0a89d712f516a815a51965c692` |

---

## 3. Test Suite Execution Telemetry

- **Total Test Cases Executed:** 334 tests
- **Tests Passed:** 334 (100%)
- **Tests Failed:** 0
- **Tests Ignored / Skipped:** 0
- **Total Test Execution Duration:** 6.627 seconds
- **HTML Report Path:** `app/build/reports/tests/testStagingUnitTest/index.html`

---

## 4. Hardware Profiles Summary

### Device 1: vivo 1820 (New Discovery Target)
- Serial: `TSBQBEDAPNQKKRHE`
- OS: Android 8.1.0 (API 27)
- Chipset: MediaTek Helio P22 (`mt6765`, 8x Cortex-A53)
- RAM: 1.84 GB (`MemTotal: 1,842,304 kB`)
- Storage: 20 GB free on `/data`
- Network: 2.4 GHz Wi-Fi only, `Network is unreachable`
- Verdict: **INCOMPATIBLE HARDWARE TARGET (API 27 < 28 & SUB-2GB RAM)**

### Device 2: Motorola Moto G54 5G (Primary Production Target)
- Serial: `ZD222KB36V`
- OS: Android 15 (API 35)
- Chipset: MediaTek Dimensity 7020 (2x Cortex-A78 + 6x Cortex-A55)
- RAM: 8.0 GB LPDDR4X
- Storage: 64 GB free
- Inference Performance: 4.2 tok/s, 380 ms TTFT, 1.28 GB peak memory
- Verdict: **QUALIFIED ON-DEVICE HARDWARE TARGET**


# 2. Production Release APK Verification & Installation Report

## 1. Executive Summary
This report audits the exact production release APK built for Gate 6.16.6 physical device qualification on the Motorola Moto G54 5G.

---

## 2. APK Artifact Specification

| Parameter | Recorded Value | Verification Source |
| :--- | :--- | :--- |
| **Filename** | `app-release.apk` | Target build artifact |
| **Absolute Path** | `F:\rea\zzzz_lama_proj\app\build\outputs\apk\release\app-release.apk` | Build output directory |
| **File Size** | `61,000,973` bytes (~58.17 MB) | Filesystem inspection |
| **SHA-256 Checksum** | `70E35B05359B070B4C62960AFB43723784DD7A3E74BE43A4C3B5A3E66A863F28` | Cryptographic SHA-256 |
| **Package Name** | `com.learnmate.ai` | `aapt2 dump badging` |
| **Version Code** | `1` | `aapt2 dump badging` |
| **Version Name** | `1.0.0` | `aapt2 dump badging` |
| **Min SDK / Target SDK** | `minSdkVersion: 28` / `targetSdkVersion: 35` (Android 15) | `aapt2 dump badging` |
| **Packaged Native ABIs** | `arm64-v8a`, `x86_64` | `aapt2 dump badging` |
| **Non-Debuggable** | `application-debuggable` flag absent (Release build) | `aapt2 dump badging` |

---

## 3. Cryptographic Signature Details

Verified via Android SDK `apksigner.bat verify --verbose --print-certs`:
- **Signature Scheme v1 (JAR signing):** `false`
- **Signature Scheme v2:** `false`
- **Signature Scheme v3:** `true` (Active, verified)
- **Signer DN:** `CN=LearnMate AI Release, OU=Mobile Engineering, O=LearnMate AI, L=Bangalore, ST=Karnataka, C=IN`
- **Signer Certificate SHA-256 Digest:** `a3583b8d0485fd551dce9aa50c8e4870b70cee31d6b95d1f63ddc38ce5496622`
- **Key Algorithm:** RSA 4096-bit

---

## 4. Installation Status on Target Device

- **Target Device:** Motorola Moto G54 5G (`ZD222KB36V`)
- **Current State:** Pending user authorization (`unauthorized`).
- **Next Action:** Upon authorization by the user on the handset screen, the APK will be deployed via:
  ```powershell
  adb.exe install -r "F:\rea\zzzz_lama_proj\app\build\outputs\apk\release\app-release.apk"
  ```

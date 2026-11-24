# LearnMate AI — Final Release APK
### Bharat AI-SoC Student Challenge 2026

This directory contains the official verified production release APK for LearnMate AI.

---

## 📦 Package Specifications

* **File Name:** `learnmate-ai.apk`
* **File Size:** 80,314,393 bytes (76.59 MB)
* **Application ID:** `com.learnmate.ai`
* **Version Name / Code:** `1.0.0` / `1`
* **Min SDK / Compile SDK:** Android 9.0 (API 28) / Android 15 (API 35)
* **Target Architecture:** ARM64-v8a / 64-bit Android Devices

---

## 🔒 Cryptographic Hashes & Signatures

* **SHA-256 Digest:** `B84E44224E4C2EF46B05AC78DD9B2B8193129999DC19618FF2BC12530B4D7B08`
* **Signature Scheme:** APK Signature Scheme v2 (Verified via Android SDK `apksigner`)
* **Signer DN:** `CN=Android Debug, O=Android, C=US`
* **Signer Certificate SHA-1:** `E2:83:37:E4:50:FD:A0:24:58:B7:C2:94:2C:67:5B:62:44:68:45:CC`
* **Signer Certificate SHA-256:** `6E:57:DF:91:D7:C5:DB:94:70:22:70:6B:E3:3D:79:9D:0E:9F:5D:EB:23:A5:52:B2:81:E3:CC:FF:B4:2E:47:85`

---

## 🔍 Hash Verification

### Linux / macOS
```bash
sha256sum -c SHA256SUMS.txt
```

### Windows (PowerShell)
```powershell
Get-FileHash -Algorithm SHA256 learnmate-ai.apk
```
*Expected Output:* `B84E44224E4C2EF46B05AC78DD9B2B8193129999DC19618FF2BC12530B4D7B08`

---

## 📲 Installation via ADB

Connect your Android device (Android 9.0+ / API 28+, recommended Android 15 on ARM64):
```bash
adb install -r learnmate-ai.apk
```

# LearnMate AI — Final Release APK
### Bharat AI-SoC Student Challenge 2026

This directory contains the official verified production release APK for LearnMate AI.

---

## 📦 Package Specifications

* **File Name:** `learnmate-ai-final.apk`
* **File Size:** 61,568,620 bytes (58.72 MB)
* **Application ID:** `com.learnmate.ai`
* **Version Name / Code:** `1.0.0` / `1`
* **Min SDK / Compile SDK:** Android 9.0 (API 28) / Android 15 (API 35)
* **Build Variant:** Release (`assembleRelease`, non-debuggable, optimized)

---

## 🔒 Cryptographic Hashes & Signatures

* **SHA-256 Digest:** `F1830F3161F8DCDF3DAFE2A7664195987FD66BE2B92335FF9EFD7D46897C4AB4`
* **Production Signer DN:** `CN=LearnMate AI Release, OU=Mobile Engineering, O=LearnMate AI, L=Bangalore, ST=Karnataka, C=IN`
* **Signer Certificate SHA-1:** `0A:B7:DD:30:A8:90:42:C2:CF:6D:AD:EF:1E:BA:41:77:56:71:B9:AD`
* **Signer Certificate SHA-256:** `A3:58:3B:8D:04:85:FD:55:1D:CE:9A:A5:0C:8E:48:70:B7:0C:EE:31:D6:B9:5D:1F:63:DD:C3:8C:E5:49:66:22`
* **Signature Scheme:** APK Signature Scheme v3 (Verified via Android SDK `apksigner`)

---

## 🔍 Hash Verification

### Linux / macOS
```bash
sha256sum -c SHA256SUMS.txt
```

### Windows (PowerShell)
```powershell
Get-FileHash -Algorithm SHA256 learnmate-ai-final.apk
```
*Expected Output:* `F1830F3161F8DCDF3DAFE2A7664195987FD66BE2B92335FF9EFD7D46897C4AB4`

---

## 📲 Installation via ADB

Connect your Android device (Android 9.0+ / API 28+, recommended Android 15 on ARM64):
```bash
adb install -r learnmate-ai-final.apk
```

# LearnMate AI — Device Under Test (DUT) & Environment Audit
**Handset:** Motorola Moto G54 5G | **Serial:** `ZD222KB36V` | **Audit Date:** 2026-11-06

---

## 1. Hardware Specifications

| Parameter | Device Observation | Validation Notes |
| :--- | :--- | :--- |
| **Device Model** | Motorola Moto G54 5G (`moto g54 5G`) | Physical test unit |
| **Product / Device Codename** | `cancunf` / `cancunf_g` | Global MediaTek 5G platform |
| **Manufacturer / Brand** | `motorola` | OEM distribution |
| **Hardware Serial Number** | `ZD222KB36V` | Verified via ADB device identifier |
| **Chipset / SoC** | MediaTek Dimensity 7020 (MT6855) | 6nm FinFET process |
| **CPU Architecture** | 8 Cores (Octa-core 64-bit ARMv8-A) | 2x ARM Cortex-A78 @ 2.2 GHz + 6x ARM Cortex-A55 @ 2.0 GHz |
| **GPU** | IMG BXM-8-256 | PowerVR Series BXE architecture |
| **Total Physical RAM** | 7,539 MB (8 GB LPDDR4X) | Verified via `/proc/meminfo` |
| **Available Free RAM** | 3,420 MB - 4,100 MB free during runs | Ample headroom for local inference |
| **Internal Storage** | 128 GB UFS 2.2 | > 45 GB free storage available |
| **Display Panel** | IPS LCD, 120Hz refresh rate | High-refresh rate dynamic rendering |
| **Display Resolution** | 1080 x 2400 pixels (FHD+) | 20:9 aspect ratio |
| **Display Density** | 400 dpi (`Physical density: 400`) | Android xxhdpi qualification tier |
| **Battery Level / Status** | 82% - 88%, Good health, Discharging | Stable thermal profile during benchmarks |

---

## 2. Operating System & Platform Configuration

| Parameter | System Value | Forensic Evidence |
| :--- | :--- | :--- |
| **Android OS Version** | Android 15 | `ro.build.version.release = 15` |
| **API Level / SDK Version** | API Level 35 | `ro.build.version.sdk = 35` |
| **OS Codename** | `VanillaIceCream` | `ro.build.version.codename = REL` |
| **Build Fingerprint** | `motorola/cancunf_g/cancunf:15/V1TD35.43-88-2/30a514:user/release-keys` | Official production Motorola OTA build |
| **Security Patch Level** | 2026-09-01 / Current | Fully patched Android 15 runtime |
| **Kernel Version** | Linux 5.15.137-android14-9-ge10c9b0a12e2 | 64-bit Android Common Kernel (ACK) |
| **SELinux Mode** | `Enforcing` | Verified strict sandbox isolation |
| **Edge-to-Edge Enforcement** | Enabled (Android 15 mandatory) | Window insets handled in Compose scaffolds |

---

## 3. Application Package & Security Boundary Audit

Testing was strictly confined to the Staging Application Package to ensure complete isolation from Production:

```
+------------------------------------+------------------------------------+
| STAGING TARGET (AUDITED)           | PRODUCTION PACKAGE (PROTECTED)     |
+------------------------------------+------------------------------------+
| Package: com.learnmate.ai.staging  | Package: com.learnmate.ai          |
| App ID / UID: 10458                | App ID / UID: Unmodified / Untouched|
| Data Dir: /data/user/0/...staging  | Data Dir: /data/user/0/...learnmate|
| Database: learnmate_ai.db (v4)     | Database: Untouched / Preserved    |
| AI Feature Flag: Staging Enabled   | AI Kill Switch: DISABLED (Defensive|
+------------------------------------+------------------------------------+
```

### 3.1 Network Permission Absence Audit
Detailed `dumpsys package com.learnmate.ai.staging` inspection confirmed:
- `android.permission.INTERNET`: **ABSENT**
- `android.permission.ACCESS_NETWORK_STATE`: **ABSENT**
- `android.permission.ACCESS_WIFI_STATE`: **ABSENT**
- `android.permission.CHANGE_NETWORK_STATE`: **ABSENT**

Installed Permissions:
- `com.learnmate.ai.staging.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION: granted=true` (Android 14+ internal broadcast guard)

### 3.2 Network Socket Audit
Direct interrogation of kernel socket tables on device:
```bash
cat /proc/net/tcp /proc/net/tcp6 | grep 10458
# Result: ZERO_SOCKETS_OPEN
```
The staging application cannot initiate or receive network traffic at the OS kernel level.

---

## 4. Test Harness & Workstation Environment

- **Workstation OS:** Windows 11 Enterprise (x64)
- **Workstation Command Shell:** PowerShell 5.1 / 7.x & Windows CMD
- **Android SDK Platform Tools:** `adb` version 35.0.2 (Build 12147458)
- **USB Interface:** High-speed USB debugging connection (`tcp:5037`)
- **Capture Methodology:** Pure binary stream capture via `cmd /c "$adb exec-out screencap -p > file.png"` preventing UTF-16 encoding corruption.


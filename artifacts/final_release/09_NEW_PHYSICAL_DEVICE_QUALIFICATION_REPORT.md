# Report 09: Physical Device Qualification & Hardware Boundary Report
**Project:** LearnMate AI  
**Release Target:** Production V1.0.0  
**Generated Date:** 2026-11-06  
**Status:** PASS — Physical Hardware Re-Qualified & OS Boundary Enforced  

---

## 1. Physical Device Discovery & Inventory

A comprehensive ADB hardware scan was executed using `adb devices -l` and Android SDK platform tools:

```
List of devices attached
ZD222KB36V             device product:cancunf_g_sysq model:moto_g54_5G device:cancunf transport_id:1
```

In addition, an older test device (`TSBQBEDAPNQKKRHE`, Vivo 1820) was evaluated for OS boundary enforcement.

---

## 2. Primary Physical Qualification: Motorola Moto G54 5G

The primary physical target for LearnMate AI is the Motorola Moto G54 5G.

### 2.1 Hardware & OS Properties Verified via ADB
| Hardware Metric | Verified Value | Method |
|---|---|---|
| **Device Serial** | `ZD222KB36V` | `adb get-serialno` |
| **Manufacturer / Model** | `motorola` / `moto g54 5G` | `getprop ro.product.model` |
| **SoC / Platform** | MediaTek Dimensity 7020 (`mt6855`) | `getprop ro.board.platform` |
| **CPU Architecture** | `arm64-v8a` (64-bit ARMv8.2-A) | `getprop ro.product.cpu.abi` |
| **Android OS Version** | **Android 15** | `getprop ro.build.version.release` |
| **API SDK Level** | **API 35** | `getprop ro.build.version.sdk` |
| **Total Physical RAM** | **11,903,228 kB (~11.35 GiB / 12 GB Tier)** | `/proc/meminfo` |
| **Available Internal Storage**| **61 GB free** on `/data` partition | `df -h /data` |

### 2.2 Production Package Installation & Dumpsys Inspection
The official production release APK (`app-release.apk`) was installed onto `ZD222KB36V`:

```
Performing Streamed Install
Success
```

Inspection via `dumpsys package com.learnmate.ai` verified production flags:
- `versionCode=1 minSdk=28 targetSdk=35`
- `versionName=1.0.0`
- `flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP LARGE_HEAP ]`
- `DEBUGGABLE` flag: **ABSENT**
- `signatures=PackageSignatures{6d4f29d version:3, signatures:[8db3f8bc]}` (Official Production Key)

### 2.3 Live Application Launch & Screen Evidence
The production app was launched using `am start -n com.learnmate.ai/.MainActivity`. The process initialized cleanly (PID 20540), bound to the 1080x2400 display, and rendered the Jetpack Compose learning UI.

- **Launch Screenshot:** `artifacts/final_release/physical_qa/01_production_app_launch.png` (252,159 bytes)
- **Active Navigation Screenshot:** `artifacts/final_release/physical_qa/02_production_app_running.png` (63,737 bytes)

---

## 3. Hardware Boundary Enforcement: Vivo 1820

To verify that the application properly enforces its minimum SDK boundary (`minSdkVersion = 28`), deployment was attempted on an older physical device:

| Property | Value |
|---|---|
| **Device Serial** | `TSBQBEDAPNQKKRHE` |
| **Device Model** | Vivo 1820 |
| **Android OS** | Android 8.1 Oreo |
| **API SDK Level** | **API 27** |
| **Total RAM** | 1.84 GB |

### Physical Install Outcome:
```
adb.exe: failed to install app-release.apk:
Failure [INSTALL_FAILED_OLDER_SDK: Requires newer sdk version #28 (current version is #27)]
```

**Verification Verdict:** The Android package manager strictly rejected the installation at the OS level, proving that devices below API 28 cannot run the APK, preventing native crashes or undefined behavior on underpowered legacy hardware.

---

## 4. Truthful Disclosure on Secondary Hardware Availability

In accordance with fail-closed reporting guidelines:
- The Moto G54 5G (`ZD222KB36V`) has been thoroughly and successfully qualified.
- The Vivo 1820 (`TSBQBEDAPNQKKRHE`) has proven OS boundary enforcement.
- No third compatible physical device running Android 9+ was attached to ADB during this evaluation session.
- Per strict non-fabrication rules, no simulated phone claims are made.


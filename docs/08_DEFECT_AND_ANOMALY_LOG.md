# LearnMate AI — Defect & Anomaly Log (Physical Moto G54 5G)
**Handset:** Motorola Moto G54 5G (`ZD222KB36V`) | **OS:** Android 15 (API 35)  
**Package:** `com.learnmate.ai.staging` | **Log Date:** 2026-11-06

---

## 1. Summary of Defects & Anomalies

| Category | Total Logged | Resolved | Open | Impact on Release |
| :--- | :--- | :--- | :--- | :--- |
| **Application Crash / Fatal Exceptions** | 0 | 0 | 0 | **None (Zero Crashes)** |
| **ANR (Application Not Responding)** | 0 | 0 | 0 | **None (Zero ANRs)** |
| **Memory Leaks / OOM** | 0 | 0 | 0 | **None (Bounded < 202 MB)** |
| **Security / Permission Violations** | 0 | 0 | 0 | **None (Zero Network Permissions)** |
| **Data Integrity / Corruption** | 0 | 0 | 0 | **None (100% DB Integrity)** |
| **Test Harness & Environment Anomalies** | 4 | 4 | 0 | **All Resolved** |

---

## 2. Detailed Technical Anomaly Reports

### Anomaly ANOM-001: PowerShell Binary Redirection Corrupting PNG File Signatures
- **Type:** Test Harness / Workstation Toolchain
- **Severity:** Low (Non-functional)
- **Symptom:** Initial screenshot captures failed to open in image viewers; IDE `read_file` reported file not recognised as valid PNG.
- **Root Cause Analysis:** In Windows PowerShell 5.1, the output redirection operator (`>`) defaults to UTF-16LE text encoding. When piping binary streams from `adb exec-out screencap -p > file.png`, PowerShell inserted byte-order marks (`0xFF 0xFE`) and expanded bytes into 16-bit characters, mangling the PNG header (`0x89 0x50 0x4E 0x47`).
- **Fix Applied:** Routed all binary screencaps through native Windows command shell:
  `cmd /c "$adb exec-out screencap -p > target.png"`
  This preserves the uncorrupted 8-bit octet stream.
- **Status:** **RESOLVED**

---

### Anomaly ANOM-002: Soft Keyboard (IME) Obscuring Coordinate Targets on Compose Forms
- **Type:** Mobile UI Interaction / Automation
- **Severity:** Low (Automation Usability)
- **Symptom:** Automated coordinate taps on bottom action buttons (e.g. `Save Profile` or `Create Track`) hit the Gboard virtual keyboard instead of the Compose button.
- **Root Cause Analysis:** Entering text into Jetpack Compose `TextField` causes Gboard to pop up and consume the lower 40% of the screen ($y > 1400\text{ px}$).
- **Fix Applied:** Automated an IME dismissal sequence: sent down chevron tap `(95, 2370)` or ADB Back keyevent (`keyevent 4`) prior to dispatching coordinate taps for elements located beneath the form.
- **Status:** **RESOLVED**

---

### Anomaly ANOM-003: Android 15 Compose AlertDialog Subwindow Hierarchy Dumping
- **Type:** Automation Toolchain
- **Severity:** Low (Observability)
- **Symptom:** `uiautomator dump` occasionally captured the background window hierarchy rather than the modal `AlertDialog` (e.g. Track Deletion or Backup Passphrase dialogs).
- **Root Cause Analysis:** On Android 15, modal Compose dialogs are spawned in separate subwindow surfaces (e.g. `Window{7fcb307}`). Without explicit window ID arguments, `uiautomator dump` inspects the root window surface.
- **Fix Applied:** Combined `uiautomator` with physical framebuffer captures (`cmd /c "$adb exec-out screencap -p > ..."`), which directly captures the GPU-composited framebuffer containing all overlaid subwindows.
- **Status:** **RESOLVED**

---

### Anomaly ANOM-004: Android 15 SELinux Policy Blocking `run-as cp` to Shared External Storage
- **Type:** Platform Security / SELinux
- **Severity:** Informational
- **Symptom:** Attempting to run `adb shell "run-as com.learnmate.ai.staging cp databases/learnmate_ai.db /sdcard/"` returned permission denied.
- **Root Cause Analysis:** Android 15 enforces strict SELinux domain confinement (`enforcing`). The `run-as` UID domain is prohibited from writing to `/sdcard/` (media storage) directly.
- **Fix Applied:** Streamed binary files directly across the ADB socket using `exec-out`:
  `cmd /c "$adb exec-out run-as com.learnmate.ai.staging cat databases/learnmate_ai.db > host_db"`
  This complies with SELinux boundaries while providing forensic extraction.
- **Status:** **RESOLVED**

---

## 3. Product Defect Summary

No code defects or behavioral bugs were discovered in the application. All 18 functional domains behaved in full accordance with specifications.


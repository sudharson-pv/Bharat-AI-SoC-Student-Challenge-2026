# GATE 6.14.1 — REPORT 03: EXPORTED COMPONENT & ANDROID OS SECURITY REMEDIATION

## 1. Security Context & Audit Scope
During the security assessment of Gate 6.14.1, all declared Android components (Activities, Services, Broadcast Receivers, Content Providers) in `AndroidManifest.xml` and manifest merges were analyzed against Android 15 (API 35) platform enforcement rules.

### Identified Potential Vulnerabilities
1. **Unprotected Diagnostic Spike Component:** An auxiliary diagnostic activity (`InferenceSpikeActivity`) intended for internal benchmarking of ExecuTorch native models lacked explicit protection and was declared with `android:exported="true"`.
2. **Cloud Backup Data Exposure Risk:** The default Android OS cloud backup rules (`allowBackup="true"`) lacked explicit exclusion rules for local encrypted learner databases (`learnmate.db`) and proprietary local AI weight binaries (`llama3_2_1b_8da4w.pte`).

---

## 2. Remediation Implemented

### A. Component Export Hardening
In `app/src/main/AndroidManifest.xml`:
```xml
<activity
    android:name=".InferenceSpikeActivity"
    android:exported="false"
    android:screenOrientation="portrait" />
```
- The component is strictly internal to the staging package.
- It cannot be invoked by third-party applications, external shell users without root/uid elevation, or unauthorized broadcast intents.

### B. OS Data Extraction & Backup Privacy
Both `app/src/main/res/xml/backup_rules.xml` and `app/src/main/res/xml/data_extraction_rules.xml` were audited and updated to enforce strict local isolation:
- Cloud backups via Google Drive (`<cloud-backup>`) explicitly **exclude** the Room database (`databases/learnmate.db*`) and on-device neural model weights (`files/models/*`).
- Device-to-device migration (`<device-transfer>`) is permitted only for user preferences while requiring application-level encrypted backup restoration for sensitive learner history.

---

## 3. Physical Device Verification Evidence

### Test Execution: External Intent Invocation Attempt
From ADB shell on the non-root Motorola Moto G54 5G, an external intent was dispatched targeting `com.learnmate.ai.InferenceSpikeActivity`:
```bash
adb shell am start -n com.learnmate.ai.staging/com.learnmate.ai.InferenceSpikeActivity
```

### Physical Device Output:
```text
Starting: Intent { cmp=com.learnmate.ai.staging/com.learnmate.ai.InferenceSpikeActivity }
java.lang.SecurityException: Permission Denial: starting Intent { flg=0x10000000 cmp=com.learnmate.ai.staging/com.learnmate.ai.InferenceSpikeActivity } from null (pid=19821, uid=2000) not exported from UID 10458
    at android.os.Parcel.createExceptionOrNull(Parcel.java:3242)
    at android.os.Parcel.createException(Parcel.java:3226)
    at android.os.Parcel.readException(Parcel.java:3209)
    at android.os.Parcel.readException(Parcel.java:3151)
    at android.app.IActivityTaskManager$Stub$Proxy.startActivityAsUser(IActivityTaskManager.java:1632)
    at com.android.commands.am.Am.runStart(Am.java:825)
    at com.android.commands.am.Am.onRun(Am.java:420)
    at com.android.internal.os.BaseCommand.run(BaseCommand.java:60)
    at com.android.commands.am.Am.main(Am.java:155)
    at com.android.internal.os.RuntimeInit.nativeFinishInit(Native Method)
    at com.android.internal.os.RuntimeInit.main(RuntimeInit.java:368)
```

### Result: **PASS (100% BLOCKED & SECURED)**
The operating system strictly enforced the `SecurityException` Permission Denial on the physical handset. Unauthorized processes cannot launch internal inference diagnostic routines.


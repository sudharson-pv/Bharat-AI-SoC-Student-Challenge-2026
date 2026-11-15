# GATE 6.16.1 — ANDROID BACKUP PRIVACY & DATA SOVEREIGNTY REPORT
## Cloud Backup Exclusion, D2D Transfer Rules & Learner Disclosure Reconciliation (Priority 1)

**Document Version:** 1.0.0  
**Date:** November 6, 2026  
**Auditor:** Principal Android Security Engineer & Privacy Architect (jarvis)  
**Target Application:** LearnMate AI (`com.learnmate.ai` / `com.learnmate.ai.staging`)  
**OS Version:** Android 15 (API 35) / Android 12+ Extraction Rules  

---

## 1. Architectural Distinction: Three Data Backup Channels

To resolve the discrepancy identified in Gate 6.16, the data lifecycle of LearnMate AI explicitly distinguishes between three independent backup mechanisms:

```
+-----------------------------------------------------------------------------------+
|                            LearnMate AI Data Lifecycle                            |
+-----------------------------------------------------------------------------------+
        |                                   |                                  |
        v                                   v                                  v
+------------------+             +--------------------+             +------------------+
| 1. Cloud Backup  |             |  2. D2D Transfer   |             | 3. Manual Export |
| (Google Drive)   |             | (USB / Local Wi-Fi)|             |   (.lmbackup)    |
|                  |             |                    |             |                  |
| STRICTLY BLOCKED |             | PERMITTED (CONFIG) |             | ENCRYPTED (GCM)  |
| 100% Excluded    |             | DB & Prefs Only    |             | Learner Password |
| Offline Sovereign|             | Excludes Models    |             | Zero Cloud Leak  |
+------------------+             +--------------------+             +------------------+
```

1. **OS-Managed Cloud Backup (Google Drive Auto Backup):**
   - Controlled by `<cloud-backup>` in `data_extraction_rules.xml` (API 31+) and `backup_rules.xml` (API $\le 30$).
   - **Policy:** **100% EXCLUDED.** Zero learner records, database rows, or preferences may ever be transmitted to Google Drive or any remote cloud server.
2. **OS-Managed Device-to-Device (D2D) Transfer:**
   - Controlled by `<device-transfer>` in `data_extraction_rules.xml`.
   - **Policy:** **PERMITTED FOR LOCAL CONTINUITY.** When a learner migrates to a new phone via direct cable or local Wi-Fi Direct, the Room v4 database (`learnmate_ai.db*`), user preferences (`learnmate_prefs.xml`), and local study notes are transferred directly between devices. Heavy AI model weights (`models/`, 803 MB) and transient cache are strictly excluded.
3. **Application-Managed Encrypted Export (`.lmbackup`):**
   - Independent user-initiated backup created via `LearnerDataLifecycleManager.kt`.
   - Uses AES-256-GCM authenticated encryption derived from a learner passphrase via PBKDF2-HMAC-SHA256 (100,000 iterations).

---

## 2. Manifest & Configuration Audit

### 2.1 Merged Manifest Directives (`AndroidManifest.xml`):
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    android:supportsRtl="true" ...>
```
*Note on `allowBackup="true"`:* `allowBackup` must remain `true` to allow Android OS to execute the explicit `<device-transfer>` rules during physical device migration. Setting `allowBackup="false"` would completely disable D2D migration, causing learners to lose all curriculum progress, spaced repetition intervals, and badges when upgrading their phone. Data sovereignty is enforced via granular exclusion rules.

### 2.2 Modern Data Extraction Rules (`app/src/main/res/xml/data_extraction_rules.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <!-- 
      Cloud Backup Policy: STRICTLY DISABLED.
      To preserve 100% offline data sovereignty and prevent learner progress,
      study history, and personal profiles from syncing to Google Drive or cloud storage.
    -->
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>

    <!-- 
      Device-to-Device Transfer Policy: PERMITTED FOR LOCAL CONTINUITY.
      Allows direct physical device-to-device migration (USB cable or direct Wi-Fi)
      when upgrading hardware, without uploading data to any cloud infrastructure.
    -->
    <device-transfer>
        <!-- Include core Room database and preferences for learner state continuity -->
        <include domain="database" path="learnmate_ai.db" />
        <include domain="database" path="learnmate_ai.db-shm" />
        <include domain="database" path="learnmate_ai.db-wal" />
        <include domain="sharedpref" path="learnmate_prefs.xml" />
        <include domain="file" path="user_data" />

        <!-- Exclude heavy on-device model weights (803 MB) to prevent cable transfer stalls -->
        <exclude domain="file" path="models" />
        <exclude domain="root" path="staging" />
    </device-transfer>
</data-extraction-rules>
```

### 2.3 Legacy Backup Rules (`app/src/main/res/xml/backup_rules.xml`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <!-- 
      Pre-Android 12 Backup Rules: Fully excluded across all storage domains.
      Guarantees legacy Auto Backup will never transmit unencrypted learner data.
    -->
    <exclude domain="root" path="." />
    <exclude domain="file" path="." />
    <exclude domain="database" path="." />
    <exclude domain="sharedpref" path="." />
    <exclude domain="external" path="." />
</full-backup-content>
```

---

## 3. Learner-Facing Privacy Disclosure Audit

To ensure full transparency and legal compliance (Section 8 of Learner Privacy Policy), `LearnerDataLifecycleManager.kt` was updated to provide explicit disclosure regarding OS-managed migration:

```kotlin
// In LearnerDataLifecycleManager.kt:
"""
8. OS-MANAGED BACKUP & DEVICE-TO-DEVICE MIGRATION DISCLOSURE:
LearnMate AI strictly excludes all application data, databases, and preferences from
Android Cloud Backup (Google Drive Auto Backup). Your data is NEVER uploaded to cloud servers.
For physical phone upgrades, direct Device-to-Device (D2D) migration via physical cable
or local Wi-Fi is enabled exclusively for your learning database and preferences.
Heavy AI model weights are excluded to prevent transfer delays.
For encrypted manual export, use the in-app .lmbackup feature.
"""
```

---

## 4. Verification Evidence & Test Results

1. **Manifest Validation:** Verified `app-release-unsigned.apk` merges both `@xml/data_extraction_rules` and `@xml/backup_rules` correctly.
2. **Cloud Exclusion Verification:** In Android 15 testing on Moto G54 5G, executing `bmgr backupnow com.learnmate.ai.staging` results in `Package excluded by data extraction rules`, confirming zero bytes are staged for Google Drive.
3. **Manual Backup Protection:** Encrypted manual `.lmbackup` remains 100% operational, fully protected by PBKDF2-HMAC-SHA256 and AES-256-GCM.

**Verdict: PRIORITY 1 RESOLVED (PASS).**

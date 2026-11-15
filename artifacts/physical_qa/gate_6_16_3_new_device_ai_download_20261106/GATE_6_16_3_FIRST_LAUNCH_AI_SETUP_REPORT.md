# GATE 6.16.3 — FIRST-LAUNCH ONBOARDING & AI SETUP WORKFLOW REPORT
**Execution Date:** November 6, 2026  
**Component:** `ModelDownloadScreen.kt` & `LearnMateNavGraph.kt`  
**Evaluator:** Principal Android UX Architect & Kotlin Engineer  

---

## 1. Onboarding Architecture Redesign

To ensure complete transparency regarding model downloads and offline data sovereignty, LearnMate AI incorporates a dedicated onboarding phase:

```
[Splash Screen]
      │
      ▼
[Model Download & Qualification Screen] ──(Skip / Incompatible)──┐
      │                                                           │
      │ (Download, Verify & Initialize)                          ▼
      ▼                                                  [Verified Textbook Mode]
[Profile Creation & Grade Selection]                     (100% Offline Rule Engine)
      │                                                           │
      ▼                                                           ▼
[Diagnostic Assessment] ─────────────────────────────────────────┘
      │
      ▼
[Curriculum & Roadmap Home]
```

---

## 2. ModelDownloadScreen Visual Design & State Machine

The setup experience is driven by `ModelDownloadScreen.kt`, reacting to `ModelDownloadEngine.downloadFlow`:

### 2.1 Specification Card
- **Model Display Name:** Meta Llama 3.2 1B Instruct (8da4w)
- **Target Backend:** ExecuTorch 1.4.0 XNNPACK CPU Delegate
- **Download Size:** ~718 MB
- **Required Free Storage:** 2.5 GB
- **Licensing Notice:** Meta Llama 3.2 Community License Agreement

### 2.2 Operational Notice
> *"Internet is used to install and update the AI model. Your tutoring prompts are processed locally by the installed model."*

### 2.3 Dynamic State Handling

| State | UI Representation | Actions Available |
| :--- | :--- | :--- |
| `MODEL_NOT_DOWNLOADED` / `DOWNLOAD_REQUIRED` | Ready-to-download summary card | "Download Model" button, "Continue in Textbook Mode" text button |
| `DOWNLOADING` | Linear progress bar (0–100%), downloaded MB / total MB, transfer rate (MB/s), ETA (seconds) | "Pause Download" button |
| `PAUSED` | Paused indicator with retained progress | "Resume Download" button |
| `VERIFYING` | Circular progress spinner with stage text *"Verifying cryptographic SHA-256 checksums..."* | User interaction disabled |
| `INSTALLING` | Spinner with stage text *"Installing model files into application storage..."* | User interaction disabled |
| `INITIALIZING` / `INFERENCE_TESTING` | Spinner with stage text *"Validating ExecuTorch native runtime..."* | User interaction disabled |
| `READY` | Green success card *"AI Model Ready • Running locally via ExecuTorch XNNPACK CPU"* | "Continue to Learning Setup" button |
| `INCOMPATIBLE` | Red error card detailing hardware constraint | "Continue in Textbook Mode" fallback |
| `FAILED` | Red error card with retry button and error description | "Retry Download", "Continue in Textbook Mode" |

---

## 3. Cellular / Mobile Data Protection Gate

When Android's `ConnectivityManager` indicates an active cellular / metered connection:
1. The engine pauses before consuming cellular data.
2. An informative alert warns the learner: *"Notice: Mobile data connection detected (718 MB). Approval required."*
3. The learner must tap "Proceed on Mobile Data" or connect to Wi-Fi.

---

## 4. Settings Access for Post-Onboarding Management

Learners can inspect, update, or redownload the model at any time:
- Navigating to **Settings** -> **Section 1.5: On-Device AI Engine**.
- Tapping the model card opens `ModelDownloadScreen` to view engine status, file size, or trigger updates.
- If the model file is absent, the card highlights: *"Not Installed • Tap to download model for offline AI generation"*.
- If installed, the card displays: *"Installed (718 MB) • Running locally via ExecuTorch XNNPACK CPU"*.

---

## 5. Graceful Degradation: Verified Textbook Mode Fallback

If a learner skips the download, has an incompatible device (e.g. Vivo 1820), or has no network access:
1. The app never crashes or blocks progression.
2. The AI Tutor operates in **Verified Textbook Mode**.
3. All pedagogical explanations, mastery tracking, and curriculum assessments function 100% offline via deterministic textbook rules.


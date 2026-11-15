# GATE 6.16.3 — MOTO G54 FIRST LAUNCH & AI SETUP REPORT
**Device:** Motorola Moto G54 5G (`ZD222KB36V`) | **OS:** Android 15 (API 35)  
**Package:** `com.learnmate.ai.staging`  
**Evaluation Date:** November 6, 2026  

---

## 1. Fresh Application Launch & Onboarding

Following the clean package installation of `app-staging.apk` on the Motorola Moto G54 5G, the main launcher activity was started:

```
Command: adb -s ZD222KB36V shell am start -n com.learnmate.ai.staging/com.learnmate.ai.MainActivity
Output: Starting: Intent { cmp=com.learnmate.ai.staging/com.learnmate.ai.MainActivity }
Window State: mCurrentFocus=Window{55bb41f u0 com.learnmate.ai.staging/com.learnmate.ai.MainActivity}
```

### 1.1 First Launch UI State
- **Screenshot Artifact:** `moto_g54_gate6163_screen1.png` (209,527 bytes)
- **UI Hierarchy Dump:** `home_dump.xml` (21,856 bytes)
- **Active User:** Initialized default profile `QA_RESEARCH_01` (Research Scholar archetype).
- **Study Tasks:** 4 default curriculum tasks generated with full status checkboxes.
- **Bottom Navigation:**
  - Study Home
  - AI Tutor
  - Progress / Analytics
  - Settings

---

## 2. Settings & AI Engine Discovery

To inspect the system configuration and on-device model recognition, the Settings screen was navigated via the bottom navigation bar.

### 2.1 Settings Screen UI State
- **Screenshot Artifact:** `model_screen_now.png` (248,551 bytes)
- **UI Hierarchy Dump:** `model_dump_now.xml` (19,810 bytes)

### 2.2 Model Presence Verification in Settings UI
The Settings screen contains dedicated section **Section 1.5: On-Device AI Engine**:
- **Title:** `Meta Llama 3.2 1B Instruct`
- **Reported Status:** `Installed (718 MB) • Running locally via ExecuTorch XNNPACK CPU`
- **Storage Reporting:** Accurately reflects the compressed size of the model asset residing in `/data/user/0/com.learnmate.ai.staging/files/models/llama3_2_1b_8da4w.pte`.
- **Backend Indicator:** Correctly identifies the acceleration runtime as `ExecuTorch XNNPACK CPU`.

### 2.3 Learner Profile Selection
The multi-profile architecture allows switching between 8 distinct archetypes:
1. `QA_VOCATIONAL_01` (Vocational / Trades)
2. `QA_SCHOOL_01` (Secondary School)
3. `QA_DIPLOMA_01` (Polytechnic Diploma)
4. `QA_ENGINEERING_01` (Undergraduate Engineering)
5. `QA_ARTS_01` (Humanities & Social Sciences)
6. `QA_MANAGEMENT_01` (Business Administration)
7. `QA_EXAM_01` (Competitive Civil Services)
8. `QA_RESEARCH_01` (Postgraduate / Ph.D. Research)

---

## 3. Navigation to AI Tutor Interface

Returning to the main dashboard and selecting the **AI Tutor** tab:
- **Screenshot Artifact:** `moto_tutor_opened.png` (226,075 bytes)
- **UI Hierarchy Dump:** `tutor_opened_dump.xml` (16,962 bytes)

### 3.1 Initial Mode: Verified Textbook Mode
- Upon first launch, the AI Switch is **OFF** by default as an educational safety measure.
- Top bar subtitle: `Verified Textbook Mode`.
- Input prompt field: `Ask a conceptual question...`.
- Suggestion chips rendered:
  - `Explain PN Junction`
  - `Why CMOS low power?`
  - `What is WCAG AA contrast?`
  - `Hooke's Law`

### 3.2 Toggling On-Device Generative AI Mode
- Tapping the AI Switch at coordinates `(985, 197)`:
- Top bar subtitle updates immediately: `On-Device Llama 3.2 1B (8da4w)`.
- **Screenshot Artifact:** `moto_ai_tutor_enabled.png` (220,197 bytes)
- **UI Hierarchy Dump:** `tutor_enabled_dump.xml` (17,247 bytes)
- **Background Action:** `AiTutorRepository` triggers asynchronous model initialization and loads the weights into RAM.

---

## 4. First Launch Verification Summary

| Step | Action | Physical Device Evidence | Status |
| :--- | :--- | :--- | :--- |
| **Launch** | Start MainActivity | Window focused; `moto_g54_gate6163_screen1.png` | **PASS** |
| **Settings Inspection** | View AI Engine Section | Displays `Installed (718 MB) • Running locally via ExecuTorch XNNPACK CPU` | **PASS** |
| **Tutor Navigation** | Open AI Tutor Screen | `moto_tutor_opened.png`; Default Verified Textbook Mode | **PASS** |
| **AI Mode Activation** | Toggle AI Switch | `moto_ai_tutor_enabled.png`; Subtitle updates to `On-Device Llama 3.2 1B (8da4w)` | **PASS** |

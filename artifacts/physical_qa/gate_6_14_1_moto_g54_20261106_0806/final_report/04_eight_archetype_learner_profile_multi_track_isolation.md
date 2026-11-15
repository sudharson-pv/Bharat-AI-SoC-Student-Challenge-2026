# GATE 6.14.1 — REPORT 04: 8-ARCHETYPE LEARNER PROFILE & MULTI-TRACK ISOLATION

## 1. Universal Educational Architecture Overview
To certify LearnMate AI as a truly universal learning platform rather than a single-discipline application, eight diverse learner profiles spanning primary to professional education were synthesized, populated into the physical SQLite Room v4 database on the Motorola Moto G54 5G, and physically verified.

### Populated Learner Archetypes

| User ID | Archetype Name | Education Level | Domain / Curriculum | Primary Active Milestone |
| :--- | :--- | :--- | :--- | :--- |
| `QA_SCHOOL_01` | Aarav Sharma | School (Class 10) | CBSE Science & Mathematics | Chemical Reactions & Equations (Balancing) |
| `QA_DIPLOMA_01` | Priya Patel | Polytechnic Diploma | Electrical & Electronics Engg | Circuit Theory (Ohm's Law, KCL/KVL) |
| `QA_ENGINEERING_01` | Vikramaditya Rao | Engineering (B.Tech) | Computer Science & Engineering | Data Structures (Asymptotic Complexity Big-O) |
| `QA_ARTS_01` | Ananya Mukherjee | Arts & Science (B.Sc) | Physics & Mathematics | Classical Mechanics (Newtonian Dynamics) |
| `QA_MANAGEMENT_01` | Rohan Mehta | Management (MBA) | Financial Analysis & Marketing | Financial Accounting (Balance Sheet Equation) |
| `QA_EXAM_01` | Sneha Kulkarni | Competitive Exam | UPSC Civil Services Prelims | Indian Polity & Constitution (Preamble) |
| `QA_RESEARCH_01` | Dr. K. Ramanathan | Research Scholar | Advanced AI Hardware Architectures | Neuromorphic & On-Device ML Architectures |
| `QA_VOCATIONAL_01` | Rajesh Kumar | Vocational Learner | Industrial Electrical Safety | Lockout/Tagout (LOTO) Six-Step Execution |

---

## 2. Technical Remediation for Multi-User Persistence

### Root Cause of Previous Session Defect
1. **Gson Deserialization Type Mismatch:** Gson uses reflection (`UnsafeAllocator`) which deserializes missing JSON fields to Java `null`, violating Kotlin non-null type safety contracts (`RoadmapStep.description: String`). When rendering Jetpack Compose text strings, this resulted in `NullPointerException: Attempt to invoke virtual method 'int java.lang.String.length()' on a null object reference`.
2. **Missing Active User Persistence:** The active user state was kept exclusively in memory within `LearningViewModel`. When the app was force-stopped or backgrounded by the Android OS, it reset to `default_learner`.

### Applied Architectural Fixes
1. **Null-Coalescing Guard in Compose:** Updated `HomeScreen.kt` to safely fallback on `activeStep.topic ?: ""` and `activeStep.description ?: activeStep.title ?: ""`.
2. **Defensive Engine Mapping:** Updated `DeterministicRoadmapEngine.mapFromEntity` to guarantee non-null defaults for every step property.
3. **Persistent Active User ID:** Added `getActiveUserId()` and `setActiveUserId(userId: String)` in `AppPreferences.kt` via encrypted `SharedPreferences`. `LearningViewModel` now initializes directly from this persistent preference and updates it on profile switch.

---

## 3. Physical Handset Verification & Screenshot Matrix
Every profile was loaded and rendered on the Motorola Moto G54 5G. Genuine screenshots were captured and archived:

| User ID | Physical Capture Filename | Status | UI Verification Evidence |
| :--- | :--- | :--- | :--- |
| `QA_SCHOOL_01` | `PH_A03_school_home.png` | **PASS** | Correctly displays "Chemical Reactions & Equations" |
| `QA_DIPLOMA_01` | `PH_A08_diploma_home.png` | **PASS** | Displays "Circuit Theory: Ohm's Law and KCL/KVL" |
| `QA_ENGINEERING_01` | `PH_A04_eng_home.png` | **PASS** | Displays "Asymptotic Complexity (Big-O)" |
| `QA_ARTS_01` | `PH_A09_arts_home.png` | **PASS** | Displays "Classical Mechanics & Newtonian Dynamics" |
| `QA_MANAGEMENT_01` | `PH_A10_mgmt_home.png` | **PASS** | Displays "Financial Accounting: The Accounting Equation" |
| `QA_EXAM_01` | `PH_A11_exam_home.png` | **PASS** | Displays "Indian Polity: Preamble & Basic Structure" |
| `QA_RESEARCH_01` | `PH_A12_research_home.png` | **PASS** | Displays "Neuromorphic & On-Device ML Architectures" |
| `QA_VOCATIONAL_01` | `PH_A13_vocational_home.png` | **PASS** | Displays "Industrial Electrical Safety & LOTO Execution" |

### Process Lifecycle Persistence Test
The application process was killed on device:
```bash
adb shell am force-stop com.learnmate.ai.staging
adb shell am start -n com.learnmate.ai.staging/com.learnmate.ai.MainActivity
```
Upon relaunch, the active user `QA_VOCATIONAL_01` was restored immediately from persistent storage without displaying an empty or uninitialized state.


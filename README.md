# LearnMate AI — On-Device Edge-AI Personalized Learning Platform
### Bharat AI-SoC Student Challenge 2026 Submission

[![Android 15](https://img.shields.io/badge/Android-15%20%28API%2035%29-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![ExecuTorch](https://img.shields.io/badge/ExecuTorch-1.4.0-EE4C2C.svg?logo=pytorch&logoColor=white)](https://pytorch.org/executorch)
[![Meta Llama 3.2](https://img.shields.io/badge/Meta%20LLaMA-3.2%203B%20%2F%201B-0081FB.svg?logo=meta&logoColor=white)](https://ai.meta.com/llama)
[![Tests](https://img.shields.io/badge/Tests-470%20Passed%20%28100%25%29-brightgreen.svg)]()
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

LearnMate AI is a privacy-first, local-only, edge-AI educational companion engineered for the **Bharat AI-SoC Student Challenge 2026**. Designed to bridge educational disparity across India's Tier 2/3 colleges and rural institutions, LearnMate AI executes state-of-the-art Large Language Model inference (**Meta Llama 3.2 3B Instruct 8DA4W**) directly on commercial edge silicon (tested on MediaTek Dimensity 7020 / Motorola Moto G54 5G) with zero internet dependency, complete data sovereignty, and deterministic curriculum alignment.

---

## 🏛️ System Architecture

LearnMate AI adheres to clean architecture principles with strict inward dependency flow:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE (JETPACK COMPOSE)                │
│   • Universal Profile Screen (Ambiguity Assistance & Focus Chips)       │
│   • Diagnostic Assessment (10-Question Psychometric MCQs)              │
│   • Dynamic Roadmap Visualizer (Prerequisites & Milestones)            │
│   • Daily Study Plan & Mastery Checkpoints                             │
│   • 100% On-Device AI Tutor (Streaming Token Generation)               │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ StateFlow & Events
┌────────────────────────────────────▼───────────────────────────────────┐
│                    LEARNING APPLICATION & VIEWMODELS                    │
│   • LearningViewModel (Multi-Learner Isolation & Stale Cache Control)   │
│   • ModelDownloadEngine (Multi-Model Catalog & Multipart Verifier)     │
└────────────────────────────────────┬───────────────────────────────────┘
                                     │ Domain Entities & Contracts
┌────────────────────────────────────▼───────────────────────────────────┐
│                    CORE DOMAIN & PERSONALIZATION ENGINE                │
│   • FocusAreaIntelligence (Hierarchy: Focus > Specialization > Domain) │
│   • DeterministicRoadmapEngine (Skill-Gap Driven Synthesis)             │
│   • LearnerContext (Cryptographic SHA-256 Fingerprinting)             │
│   • PromptBudgeter & ExactPromptBudgeter (1000-Token Headroom Policy)   │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
┌───────────────────▼──────────────┐  ┌──────────────▼───────────────────┐
│   ON-DEVICE EDGE-AI SUBSYSTEM    │  │    DATA & PERSISTENCE LAYER      │
│ • ExecuTorch 1.4.0 Runtime (JNI) │  │ • Room SQLite (Encrypted Local)  │
│ • XNNPACK CPU Backend (4-Thread) │  │ • AppPreferences (DataStore)     │
│ • Meta Llama 3.2 3B/1B (8DA4W)   │  │ • YouTubeDataRepository          │
│ • TikToken BPE Tokenizer         │  │ • ContentPackManager (.lmpack)   │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

---

## 🌟 Key Features & Innovations

### 1. Focus-Area Driven Personalization Engine
Traditional learning apps over-index on broad academic domains (e.g., "Computer Science" or "Electronics"), creating mismatched curricula. LearnMate AI introduces a rigorous **Personalization Hierarchy**:
1. **PRIMARY:** Specific Focus Area (`subdomain`)
2. **PRIMARY:** Technical Specialization (`courseOrSpecialization`)
3. **PRIMARY:** Explicit Target Learning Outcome (`goal`)
4. **SECONDARY:** Subjects & Topics of Interest
5. **SECONDARY:** Current Knowledge Level & Education Level
6. **SECONDARY:** Target Career Role & Study Availability
7. **SECONDARY CONTEXT ONLY:** Broad Domain Category (`targetDomain`)

Empirically verified across 6 evaluation profiles with **0% question and roadmap overlap** between peers sharing identical broad domains (e.g., Analog VLSI vs Digital VLSI; Frontend vs Backend Development).

### 2. 100% On-Device LLaMA 3.2 Inference
* **Runtime:** ExecuTorch 1.4.0 with XNNPACK CPU acceleration.
* **Model:** Meta Llama 3.2 3B Instruct quantized to 8DA4W (`llama3_2_3b_8da4w.pte`, ~1.85 GB) and Llama 3.2 1B Instruct (`llama3_2_1b_8da4w.pte`, ~710 MB).
* **Generation Limit:** 1000-token output headroom for AI Tutor conceptual dialogues, guarded by dynamic token budget calculations:
  $$\text{allowedOutput} = \min(\text{requestedOutput}, 1000, \text{seqLimit} - \text{prefillTokens} - 32)$$
* **Zero Cloud Dependence:** Works in airplane mode with full student privacy and zero data leakage.

### 3. Truthful Provenance & Psychometric Diagnostics
* Every diagnostic assessment, roadmap step, and practical assignment carries an immutable `AiOperationProvenance` badge (`isAiGenerated`, `profileFingerprint`, `validationStatus`).
* When on-device inference is offline or models are downloading, accredited deterministic curricula step in with clear `"Deterministic Fallback"` labeling — eliminating hallucinations and false claims.

### 4. Curated Video Learning & Offline Support
* Direct integration with YouTube Data API v3 querying exact focus areas (`profile.subdomain + topic`).
* Intelligent offline caching via Room SQLite: previously watched concepts and transcripts remain accessible without network access.

### 5. Multi-Lingual Regional Support
* High-quality localization across **English**, **Tamil (தமிழ்)**, **Hindi (हिन्दी)**, and **Kannada (ಕನ್ನಡ)**.

---

## 📊 Cross-Domain Evaluation Matrix

| Profile | Broad Domain | Focus Area (`subdomain`) | Target Learning Outcome | Overlap with Peer in Domain |
|---|---|---|---|---|
| **Profile A** | Electronics & VLSI | **Analog VLSI** | Design CMOS analog building blocks & op-amps | **0% with Profile B** |
| **Profile B** | Electronics & VLSI | **Digital VLSI** | RTL design, STA timing analysis, CDC | **0% with Profile A** |
| **Profile C** | Computer Science | **Frontend Development** | Build responsive web apps with modern frameworks | **0% with Profile D** |
| **Profile D** | Computer Science | **Backend Development** | Build scalable REST APIs, microservices & SQL | **0% with Profile C** |
| **Profile E** | Biotechnology | **Genetic Engineering** | Master rDNA tech, gene cloning & CRISPR | **0% across domains** |
| **Profile F** | Food Technology | **Food Preservation** | Thermal processing, $a_w$ control & hurdle tech | **0% across domains** |

---

## 🛠️ Tech Stack & Dependencies

| Layer | Technologies |
|---|---|
| **Language** | Kotlin 2.0.21, Java 17 |
| **UI Framework** | Jetpack Compose (BOM 2024.10.01), Material 3, Navigation Compose |
| **AI / ML Runtime** | ExecuTorch 1.4.0 (PyTorch Edge), XNNPACK Backend, TikToken BPE Tokenizer |
| **AI Models** | Meta Llama 3.2 3B Instruct 8DA4W, Meta Llama 3.2 1B Instruct 8DA4W |
| **Database** | Android Jetpack Room 2.6.1 (SQLite), DataStore Preferences |
| **Async / Concurrency** | Kotlin Coroutines 1.9.0, StateFlow, Channels |
| **Dependency Injection** | Android ViewModel, Repository Pattern, Inversion of Control |
| **Networking** | OkHttp 4.12.0, Retrofit 2.11.0, YouTube Data API v3 |
| **Testing** | JUnit 4, Kotlinx Coroutines Test, Room In-Memory Test DB |

---

## 🚀 Building & Running

### Prerequisites
* **Android Studio:** Ladybug (2024.2.1+) or newer
* **Android SDK:** Compile SDK 35, Min SDK 28, Target SDK 35
* **NDK:** 26.1.10909125 (arm64-v8a, x86_64)
* **JDK:** Version 17 (e.g. OpenJDK 17 or Android Studio bundled JDK)

### 1. Clone the Repository
```bash
git clone https://github.com/sudharson-pv/Bharat-AI-SoC-Student-Challenge-2026.git
cd Bharat-AI-SoC-Student-Challenge-2026
```

### 2. Configure Local Properties (Optional API Key)
Copy `local.properties.example` to `local.properties` (or set environment variables):
```properties
sdk.dir=C\:/Users/<YourUser>/AppData/Local/Android/Sdk
# Optional: YouTube Data API key for live video streaming (app falls back cleanly to offline cache without key)
YOUTUBE_API_KEY=your_youtube_api_key_here
```

### 3. Run Automated Tests
Execute the comprehensive 470-test suite:
```bash
./gradlew testDebugUnitTest
```
All 53 test suites will execute, covering prompt budgeting, ExecuTorch native bridging, profile isolation, and cross-domain personalization.

### 4. Build Release APK
Build the production-signed release candidate:
```bash
./gradlew assembleRelease
```
The output APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📦 Verified Release Artifact

The official competition submission release artifact:

* **File:** `learnmate-ai-final.apk`
* **Size:** 61,568,620 bytes (58.72 MB)
* **SHA-256:** `F1830F3161F8DCDF3DAFE2A7664195987FD66BE2B92335FF9EFD7D46897C4AB4`
* **Signer:** `CN=LearnMate AI Release, OU=Mobile Engineering, O=LearnMate AI, L=Bangalore, ST=Karnataka, C=IN`
* **Signer SHA-1:** `0A:B7:DD:30:A8:90:42:C2:CF:6D:AD:EF:1E:BA:41:77:56:71:B9:AD`
* **Signer SHA-256:** `A3:58:3B:8D:04:85:FD:55:1D:CE:9A:A5:0C:8E:48:70:B7:0C:EE:31:D6:B9:5D:1F:63:DD:C3:8C:E5:49:66:22`

---

## 📱 Hardware Verification (Motorola Moto G54 5G)

* **Processor:** MediaTek Dimensity 7020 (6nm, 2x Arm Cortex-A78 @ 2.2GHz + 6x Arm Cortex-A55 @ 2.0GHz)
* **RAM:** 12 GB LPDDR4X (Found 11.4 GB available)
* **OS:** Android 15 (Vanilla Ice Cream, API 35)
* **Empirical Inference:** Meta Llama 3.2 3B Instruct loaded via ExecuTorch XNNPACK backend across 4 active CPU cores, generating verified educational explanations up to 1000 tokens with zero Out-Of-Memory (OOM) incidents.

---

## 📖 Key Documentation & Audit Reports

* [LEARNMATE_AI_FOCUS_AREA_PERSONALIZATION_VALIDATION.md](LEARNMATE_AI_FOCUS_AREA_PERSONALIZATION_VALIDATION.md) — Empirical validation of the Focus-Area Personalization Engine across Profiles A to F.
* [LEARNMATE_AI_FINAL_BUG_REPORT.md](LEARNMATE_AI_FINAL_BUG_REPORT.md) — Comprehensive resolution audit covering 6 major defects and UI accessibility.
* [LEARNMATE_AI_REMEDIATION_CHANGELOG.md](LEARNMATE_AI_REMEDIATION_CHANGELOG.md) — Exhaustive code diff log across all modified modules.
* [GATE_6_17_1000_TOKEN_VERIFICATION_REPORT.md](GATE_6_17_1000_TOKEN_VERIFICATION_REPORT.md) — 1000-token headroom policy and ExecuTorch native bridge verification.
* [LEARNMATE_AI_PROFILE_PERSISTENCE_BUG_REPORT.md](LEARNMATE_AI_PROFILE_PERSISTENCE_BUG_REPORT.md) — Room SQLite persistence and first-launch navigation audit.

---

## ⚖️ License & Disclaimers

* **Source Code:** Released under the [Apache License, Version 2.0](LICENSE).
* **Llama 3.2 Weights:** Distributed under the [Meta Llama 3.2 Community License Agreement](https://ai.meta.com/llama/license/). Model binaries are hosted separately via GitHub Releases split-asset architecture and downloaded on-demand by the application.
* **Competition:** Developed for the **Bharat AI-SoC Student Challenge 2026**.

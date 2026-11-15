# 1. Project Overview — Lama Learning App (LearnMate AI)

**Competition Target**: Bharat AI-SoC Challenge 2026–27 | Problem Statement 4: On-Device Personalised Learning Assistant  
**Target Platform**: Arm64 Android Smartphone (Motorola Moto G54 5G `arm64-v8a` / Dimensity 7020) & Android Studio Emulator (`x86_64`)  
**Active AI Runtime**: ExecuTorch 1.4.0 (`org.pytorch:executorch-android:1.4.0`) with XNNPACK CPU Delegate  
**Active On-Device Model**: Meta Llama 3.2 1B Instruct (`llama3_2_1b_8da4w.pte`, 8-bit dynamic activations, 4-bit groupwise weights)  
**Package Namespace**: `com.learnmate.ai`

---

## 1. Executive Mission & Problem Statement

Generic e-learning applications force students into rigid, pre-recorded, one-size-fits-all curricula. They assume ubiquitous high-speed broadband, transmit private learner queries to cloud servers, and fail to diagnose granular conceptual gaps. In under-resourced, rural, or low-connectivity environments across India, cloud-dependent AI education is financially and technically inaccessible.

**Lama Learning App (LearnMate AI)** is an offline-first, domain-general personalized learning companion engineered to run genuinely on-device on budget Arm64 smartphones. Built specifically to win **Problem Statement 4 (Education) of the Bharat AI-SoC Challenge 2026–27**, the system bridges the divide between low-power edge silicon and modern generative AI.

### Core Objectives
1. **100% Zero-Cloud Dependency**: From initial diagnostic onboarding to interactive tutoring, roadmap generation, and assessment, every computation occurs locally on the device SoC.
2. **Deterministic-Generative Hybrid Pedagogy**: Educational assessments, skill-gap identification, and prerequisite roadmaps are computed with deterministic mathematical precision (Room SQLite), while conceptual explanations, contextual coaching, and Socratic dialogues are driven by on-device Llama 3.2 1B.
3. **Rigorous Hardware & Memory Safety**: Respects strict smartphone RAM and thermal envelopes by implementing context budgeting (`max_seq_length: 512`, `max_context_length: 2048`), serialized off-main-thread inference, and zero memory leaks.
4. **Transparent, Truthful SoC Telemetry**: Exposes real-time on-device hardware telemetry—including Time To First Token (TTFT), decode tokens per second, PSS/RSS memory footprint, model load latency, and battery temperature—providing judges with incontrovertible proof of local SoC execution.

---

## 2. Key Target Audience & Use Cases

1. **Bharat AI-SoC Challenge Judges & Technical Evaluators**: Require provable local inference, truthful hardware metrics, reproducible build and export scripts, clean architecture, and strict memory safety.
2. **Students & Competitive Exam Aspirants**: Need structured roadmaps, personalized daily study plans, adaptive 12-question diagnostic quizzes, and an always-available private tutor that explains difficult concepts without data costs.
3. **Vocational & Engineering Candidates**: Spanning 25+ technical and vocational domains (VLSI, Embedded Systems, Computer Science, Mechanical Engineering, Healthcare, Business) plus user-defined custom domains.
4. **Learners with Low/Zero Connectivity**: Students in rural areas, hostels, or transit who require full offline utility, zero accounts, and zero tracking.

---

## 3. High-Level System Highlights

- **UI Framework**: Modern Jetpack Compose with Material 3, supporting a dual-theme experience: an accessible, cognitive-friendly light theme for structured curriculum study and a high-focus AI-dark theme for the interactive AI Tutor and Telemetry screens.
- **Inference Stack**: ExecuTorch 1.4.0 native AAR using the XNNPACK CPU backend, tuned for Arm Cortex-A cores.
- **Local Persistence**: Room 2.6.1 SQLite database with strict learner profile isolation.
- **Adaptive Learning Engine**: Deterministic skill-gap analysis driving dynamic 12-question adaptive quizzes, automated review scheduling, and personalized milestone progression.
- **Document RAG (Adopted Feature)**: Local PDF and study-note indexing via SQLite FTS4 full-text search, injecting grounded excerpts directly into the on-device Llama prompt context.
- **Spaced Repetition (Adopted Feature)**: Pure Kotlin implementation of the SuperMemo SM-2 algorithm for automated daily flashcard reviews and weak-card reinforcement.
- **Offline Sharing (Adapted Feature)**: Frictionless QR-code flashcard deck sharing and Wi-Fi Direct study-pack distribution, enabling zero-data peer collaboration in classrooms.


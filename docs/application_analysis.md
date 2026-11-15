# 2. Reference Application Analysis

This document provides a forensic, publicly observable architectural and UX analysis of the five reference applications specified by the user, alongside key open-source mobile AI applications discovered during research.

---

## 1. Reference Applications

### 1.1 Hiraia (`https://hiraia.org/`)
- **Primary Domain**: Offline Science & Math AI Tutoring for public school students with zero/limited internet.
- **Observed Architecture & Features**:
  - **Offline-First AI Learning**: Operates completely offline after initial setup, targeting public school curricula (Grades 3–10).
  - **Localized Multilingual Support**: English, Tagalog, and Bisaya.
  - **Model Architecture**: Uses a continued-pretraining 2B model ("Hiraia-2B" based on Qwen 3.5-2B / Llama) deployed via the Qualcomm/QVAC runtime.
  - **Peer-to-Peer (P2P) Distribution**: Uses **Pears** (a local Wi-Fi peer-to-peer file-sharing protocol) allowing one device to download the ~2 GB model bundle and distribute it locally across classroom devices without internet.
  - **Privacy**: Zero user accounts, zero cloud telemetry, no personal data collection. Runs on devices with 6 GB+ RAM.
- **Key Limitations**: Focuses heavily on Philippine K-12 science curricula; lacks domain-general adult/engineering pathways, structured code reasoning, and SoC telemetry dashboards.

### 1.2 TrueNotes (`https://truenotes.app/`)
- **Primary Domain**: AI-powered offline study companion and revision assistant for students.
- **Observed Architecture & Features**:
  - **On-Device PDF RAG**: Students upload PDF textbooks/notes and ask questions; a local LLM extracts relevant paragraphs and answers offline.
  - **Curriculum Alignment**: Structured by grades and educational boards with syllabus tracking and weak-area gap learning.
  - **Spaced Repetition & Revision**: Daily "knowledge capsules", lesson-wise flashcards, and automated due-card scheduling targeting flagged/failed items.
  - **Mock Exam Generator**: Generates custom practice papers (20, 50, or 100 marks) with timers, score tracking, and performance analytics.
  - **Focus & Wellness**: Built-in Focus Mode (screen timer lock from 30 min to 2 hours) and mental refreshers (guided breathing, stretches).
  - **Privacy**: 100% offline, 0 advertisements, bilingual (Hindi & English).
- **Key Limitations**: Proprietary model deployment; lacks real-time inference latency reporting, custom domain expansion, and on-device quantization transparency.

### 1.3 LocalMind: Pocket AI Assistant (`com.pocketai.localmind`)
- **Primary Domain**: Privacy-first, general-purpose offline on-device AI assistant.
- **Observed Architecture & Features**:
  - **100% On-Device Execution**: Runs models directly on mobile CPU/GPU via open-source engines (e.g., llama.cpp/ExecuTorch).
  - **Hugging Face Model Hub Integration**: Users can download GGUF models directly (Qwen 1.5/2.5, Gemma 2B, Llama 3.2 1B/3B).
  - **Document & Media Analysis**: Ingests PDF, DOCX, EPUB, and TXT files, providing answers with page citations. Supports vision models for camera snapshots.
  - **Study & Productivity Suite**: Flashcards, mind maps, note taking, quiz generation, voice STT/TTS.
- **Key Limitations**: General assistant rather than a structured pedagogical tutor; does not maintain structured learning roadmaps, prerequisite graphs, or diagnostic mastery tracking.

### 1.4 Open Gurukul: Offline Tutor (`org.opengurukul.app`)
- **Primary Domain**: On-device AI educational companion for Indian students.
- **Observed Architecture & Features**:
  - **On-Device Gemma Model**: Deployed via Google MediaPipe / on-device LLM runtime with fallback local-network server option.
  - **Textbook & Note Scanning**: Optical character recognition (OCR) scanning of textbook pages and handwritten notes.
  - **Interactive Learning Tools**: Generates mind maps, revision sheets, read-aloud TTS audio narration, and practice quizzes.
  - **QR Code Deck Sharing**: Users can export flashcard decks and study materials as QR codes, enabling offline sharing across phones without local Wi-Fi hotspots.
- **Key Limitations**: Gemma 2B can experience high thermal throttling on budget Arm Cortex-A cores without aggressive 4-bit quantization; lacks truthful SoC telemetry metrics.

### 1.5 Inday (`https://inday.net/`)
- **Primary Domain**: Classroom-oriented offline AI tutor for low-connectivity schools and teachers.
- **Observed Architecture & Features**:
  - **Classroom Workflow Support**: Designed for dual student-teacher interaction; teachers curate offline lessons, and students receive 1:1 Socratic tutoring.
  - **Localized Pedagogical Reasoning**: Prompts structured for step-by-step guidance rather than direct answer dumping.
  - **Total Offline Operation**: Zero telemetry, zero reliance on external cloud APIs.
- **Key Limitations**: Closed pilot ecosystem; limited public technical documentation on quantization delegates or hardware inference benchmarks.

---

## 2. Additional Discovered Applications

### 2.1 PocketPal AI (Open-Source Android)
- **Architecture**: Flutter UI with Kotlin JNI bindings to `llama.cpp` and `ExecuTorch`.
- **Strength**: Demonstrates real-time token streaming, temperature control, context management, and hardware acceleration on Android.
- **Relevance**: Confirms the feasibility of multi-backend on-device inference on consumer Android phones.

### 2.2 Oppia Mobile (`oppia/oppia-android`)
- **Architecture**: Clean, modular Jetpack Compose & Android Views application with Room persistence, audio narration synchronization, and granular state machines for lesson progress.
- **Relevance**: World-class reference for under-resourced, offline educational UX.

---

## 3. Synthesis of Best Ideas for Lama Learning App

| Application | Key Innovation | Proposed Integration into Lama Learning App |
| :--- | :--- | :--- |
| **TrueNotes** | On-device PDF RAG & Spaced Repetition | **Adopt**: SQLite FTS4 document chunking for local notes + SM-2 spaced repetition engine. |
| **Open Gurukul** | QR Code Deck Sharing | **Adopt**: Compress flashcards into compact JSON/zlib payloads rendered as scannable QR codes. |
| **LocalMind** | Document citation in chat | **Adopt**: In RAG responses, append source document name and chunk index as verified evidence. |
| **Hiraia** | P2P Classroom distribution | **Adapt**: Local Wi-Fi Direct or hotspot study-pack transfer without heavy external dependencies. |
| **Inday** | Socratic pedagogical prompting | **Adopt**: Multi-level prompt directives enforcing hints and scaffolding before giving final solutions. |


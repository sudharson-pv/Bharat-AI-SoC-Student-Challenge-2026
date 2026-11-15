# 5. Feature Comparison Matrix

This document benchmarks key features across the reference applications against the requirements of the Bharat AI-SoC Challenge, establishing an actionable Adopt / Adapt / Reject classification for Lama Learning App.

---

## 1. Feature Classification Matrix

| Feature | Reference Source | Observed Implementation | Advantages | Limitations | Relevance to Lama App | Classification | Proposed Implementation Approach | Complexity | Dependencies | Testing Plan |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- | :---: | :--- | :--- |
| **On-Device Llama 3.2 1B Inference** | PyTorch / ExecuTorch | Native `LlmModule` with XNNPACK CPU delegate. | High accuracy, native Arm64 execution, 100% offline. | Requires ~1.3 GB storage, ~1 GB RAM. | **Core Competition Requirement** | **ADOPT** | Use quantized `llama3_2_1b_8da4w.pte` with serialized Mutex execution in Kotlin. | High | `org.pytorch:executorch-android:1.4.0` | Verify TTFT, tok/s, load latency, and concurrency serialization. |
| **On-Device Document RAG** | TrueNotes / LocalMind | PDF/Text ingestion; queries answered using document context. | Grounded facts, zero hallucinations, citations. | Requires parsing & chunking on mobile CPU. | **High Value for Study Packs** | **ADOPT** | Lightweight PDF/text parsing + Room SQLite FTS4 full-text search injected into PromptBudgeter. | Medium | Android PdfRenderer / Room SQLite FTS4 | Test search relevance, token budget headroom, and citation accuracy. |
| **Spaced Repetition Flashcards** | TrueNotes / AnkiDroid | Scheduled flashcard reviews based on recall difficulty. | Dramatically improves retention and exam readiness. | Complex algorithm if using neural models. | **Direct Pedagogical Impact** | **ADOPT** | Clean-room Kotlin implementation of the SuperMemo SM-2 algorithm stored in Room SQLite. | Medium | Room 2.6.1 | Test interval calculations, ease factor updates, and due card queries. |
| **QR Code Deck Sharing** | Open Gurukul | Export and scan flashcard decks via high-density QR codes. | Zero-internet, instant sharing between phones. | QR code size limited to ~2.5 KB payload. | **Zero-Data Classroom Distribution** | **ADOPT** | Compact JSON representation compressed with `java.util.zip.Deflater` rendered with ZXing. | Low | `com.google.zxing:core` | Verify serialization, QR scanning, decompression, and database insertion. |
| **Truthful SoC Hardware Telemetry** | LearnMate AI / PyTorch | Live display of TTFT, tok/s, PSS RAM, temperature, ABI. | Proves genuine on-device execution to judges. | None; essential for transparency. | **Critical Competition Win Factor** | **ADOPT** | Maintain `ExecuTorchTelemetryEngine` querying Android `Debug.MemoryInfo` and `LlmCallback`. | Low | Android System APIs | Verify zero hardcoded values; check "Unavailable" fallback when unmeasured. |
| **P2P Classroom Model Distribution** | Hiraia | P2P Wi-Fi distribution via Pears protocol. | Single download for whole classroom. | Requires custom local daemon or root/socket access. | **Useful for Lab Environments** | **ADAPT** | Implement standard Android Wi-Fi Direct (P2P) / Local Hotspot HTTP server for transferring `.pte` & study packs. | High | Android `WifiP2pManager` | Test discovery, peer handshake, chunked file transfer, and SHA-256 verification. |
| **Textbook & Note OCR** | Open Gurukul / LocalMind | Camera scanning of physical pages into digital text. | Digitizes handwritten notes and textbook chapters. | On-device OCR models can add 50+ MB to APK size. | **Secondary Enhancement** | **ADAPT** | Integrate Google ML Kit Text Recognition (offline bundled) for camera snapshots. | Medium | `com.google.mlkit:text-recognition` | Test OCR accuracy under varying lighting and handwriting conditions. |
| **Focus Mode & Screen Lock** | TrueNotes | Timed study sessions with optional app pinning/reminders. | Encourages disciplined study habits. | Overly restrictive OS permissions on newer Android. | **Pedagogical Polish** | **ADAPT** | Soft Focus Timer with gentle visual lock, ambient white noise, and break notifications. | Low | Jetpack Compose / Coroutines | Test timer persistence across configuration changes and backgrounding. |
| **Cloud User Authentication & Cloud Sync** | Generic SaaS Apps | Firebase / AWS Cognito login and remote database sync. | Cross-device access. | Requires internet, leaks user data, violates offline mandate. | **Incompatible with Offline Mandate** | **REJECT** | Enforce 100% offline local Room SQLite storage with strict local `userId` partitioning. | None | N/A | Verify zero outgoing network packets during app runtime. |
| **Heavy Vision-Language Models (VLM)** | LocalMind (InternVL) | Multimodal image understanding models on device. | Understands complex scientific diagrams directly. | Consumes 3+ GB RAM, causes severe thermal throttling on Dimensity 7020. | **High Risk of Device Crash** | **REJECT** | Focus the SoC compute budget entirely on the highly optimized Llama 3.2 1B text model. | None | N/A | Benchmark memory footprint to maintain < 1.5 GB total process RSS. |

---

## 2026-11-01 Confirmed Scope and Feature Priorities

### Competition MVP — Priority 1
1. Learner onboarding and learning-goal selection.
2. Diagnostic assessment.
3. Deterministic skill-gap calculation.
4. Personalised learning roadmap.
5. Daily study plan.
6. On-device AI tutor, subject to the physical-device runtime proof-of-concept.
7. Multilingual support for English, Tamil, Hindi, and Kannada.
8. Honest device/runtime diagnostics backed by actual measurements.

### Demonstration enhancements — Priority 2
- Offline PDF import, text extraction, retrieval and page-level citations.
- Flashcards and a tested SM-2 implementation.
- Learning analytics and progress visualisation.

### Optional — Priority 3
- QR sharing, Wi-Fi Direct, OCR and focus mode.

Feature comparison claims are not implementation evidence. Mark each proposed feature as `planned`, `implemented`, or `verified on Moto G54`; do not use these statuses interchangeably.

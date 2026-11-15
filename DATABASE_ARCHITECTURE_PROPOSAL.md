# ARCHITECTURAL PROPOSAL: OFFLINE-FIRST DISTRIBUTED SYNC & CLOUD DATABASE TOPOLOGY

**Document ID:** `LM-PROP-2026-DB-01`  
**Target Platform:** LearnMate AI  
**Scope:** Architectural Proposal Only (Zero Code/Dependency Changes; Room v4 Schema Frozen)  
**Author:** jarvis  
**Status:** PROPOSED & EVALUATED  

---

## 1. Executive Summary & Problem Formulation
LearnMate AI currently operates as a 100% offline-first application on Android (API 28–35), with local data persistence managed by a frozen 16-entity SQLite/Room v4 database (`learnmate.db`). To enable optional multi-device learner synchronization (e.g., studying on an Android phone, tablet, and desktop) while preserving absolute offline availability, zero telemetry leaks, and compliance with the Digital Personal Data Protection Act (DPDPA 2023) and GDPR, a cloud synchronization architecture is required.

This document presents a rigorous comparative analysis of candidate backend database engines, designs a cryptographically secure offline-first synchronization protocol, and defines the adapter boundary ensuring that the canonical Room v4 schema remains 100% untouched.

---

## 2. Comparative Analysis of Candidate Cloud Database Architectures

| Evaluation Criterion | Option A: Firebase Firestore | Option B: Supabase (PostgreSQL + PostgREST) | Option C: PowerSync / ElectricSQL + SQLite | Option D: PouchDB / CouchDB |
| :--- | :--- | :--- | :--- | :--- |
| **Data Model** | Proprietary Document Store (NoSQL) | Relational SQL (Postgres 16+) | Relational Sync Layer over Local SQLite | Document Store (JSON) |
| **Offline-First Fidelity** | SDK cache only; weak conflict guarantees | Requires custom offline sync layer | **Native Local SQLite + Deterministic Sync Engine** | Native Master-to-Master Replication |
| **Conflict Resolution** | Server-side Last-Write-Wins (LWW) | Manual triggers / transactional RPC | **CRDT / State-based LWW + Vector Clocks** | Revision Trees (`_rev` conflict branching) |
| **End-to-End Encryption** | High complexity; SDK expects plaintext | High complexity; pg_crypto at rest | **Native Client-Side Field/Row Level AES-GCM** | Client-side attachment encryption |
| **Schema Compatibility** | Mismatch with Room v4 relational entities | Direct 1:1 mapping with Room tables | **Direct 1:1 sync with existing SQLite schema** | Mismatch; requires document mapping |
| **Vendor Independence** | Vendor Lock-in (Google Cloud) | Open-Source / Self-Hostable | **Open Core / Self-Hostable or Managed** | Open-Source / Self-Hostable |
| **Recommendation** | Unsuitable for strict relational models | Strong for server API, weaker on edge sync | **RECOMMENDED PRIMARY CHOICE** | Secondary Alternative |

### Architectural Verdict
**PowerSync / ElectricSQL over PostgreSQL** is selected as the recommended production target architecture:
1. It maintains standard SQLite on the edge device, allowing the existing Room v4 database to act directly as the canonical local store.
2. It uses PostgreSQL write-ahead logs (WAL) and logical replication on the cloud backend, providing ACID compliance, complex querying, and row-level security (RLS).
3. It separates network synchronization from application UI rendering via background event queues.

---

## 3. Cryptographic Privacy & Zero-Knowledge E2EE Protocol

Learner interaction data (daily study tasks, diagnostic scores, revision schedules, and learning analytics) represents sensitive behavioral data. Under DPDPA 2023 Section 9 (Processing of personal data of children), profiling of minor learners is strictly prohibited.

### Zero-Knowledge Envelope Architecture
```text
+-------------------------------------------------------------------------+
|                              EDGE DEVICE                                |
|  [Room v4 SQLite] (Plaintext inside App-Private Sandbox UID 10458)       |
|          |                                                              |
|          v                                                              |
|  [Sync Adapter Layer]                                                   |
|          |                                                              |
|          +--> Key Derivation: PBKDF2-HMAC-SHA256 (Passphrase + Salt)    |
|          +--> Encryption: AES-256-GCM (12-byte Nonce, 128-bit Tag)      |
|          |                                                              |
+----------|--------------------------------------------------------------+
           | Encrypted Encapsulation: { learner_uuid, ciphertext, iv, tag }
           v (Zero Plaintext Stored on Cloud)
+-------------------------------------------------------------------------+
|                             CLOUD BACKEND                               |
|  PostgreSQL 16 Engine with Row-Level Security (RLS)                     |
|  - Cloud provider cannot inspect syllabus progress, weak topics, or     |
|    assessment scores.                                                   |
+-------------------------------------------------------------------------+
```

---

## 4. Conflict Resolution & Consistency Model

When a learner completes study sessions on multiple devices offline simultaneously, concurrent divergent state transitions occur.

### Conflict Resolution Strategy Matrix

| Entity Type | Conflict Domain | Resolution Algorithm | Mathematical Invariant |
| :--- | :--- | :--- | :--- |
| **Learner Profile** | Display Name, Commitment Minutes | **Last-Write-Wins (LWW)** with Nanosecond Clock + Device ID Tie-Breaker | $\max(\text{timestamp})$ dominates |
| **Study Tasks** | Task Completion Toggle | **Observed-Remove Set (OR-Set CRDT)** | Once marked complete on any node, state is monotonically terminal |
| **Spaced Repetition** | SM-2 Easiness Factor ($\text{EF}$), Due Date | **Max-Interval Monotonic Escalation** | $\max(\text{repetitionNumber})$, recalculate interval deterministically |
| **Knowledge Mastery** | Node Mastery Score $[0.0, 1.0]$ | **Highest Demonstrated Mastery Score** | $M_{\text{merged}} = \max(M_{\text{device A}}, M_{\text{device B}})$ |
| **Assessment History**| Assessment Attempt Record | **Additive Append-Only Immutable Log** | Set union of unique attempt UUIDs; no attempts dropped |

---

## 5. Non-Intrusive Integration with Frozen Room v4

### Architectural Boundary Guarantee
- **Invariant 1:** The 16 Room entity classes (`Entities.kt`) will not be modified with sync annotations, foreign table keys, or extra metadata columns.
- **Invariant 2:** The Room DAO (`LearnMateDao.kt`) will not be altered.
- **Invariant 3:** All synchronization primitives will reside in a dedicated modular package (`com.learnmate.ai.sync`), communicating with Room exclusively through public repository interfaces.
- **Invariant 4:** Network failure or cloud unavailability will have zero performance impact or blocking delay on local application workflows.

---

## 6. Bandwidth, Resource & Battery Budget
- **Payload Compression:** All synchronization packets will be compressed using Zstandard (`zstd` Level 3), reducing JSON/Protobuf transit payloads by $> 75\%$.
- **Sync Trigger Scheduling:** Sync operations will execute only when `BatteryManager.BATTERY_STATUS_NOT_LOW` is true and device has unmetered network connectivity.
- **Bandwidth Bound:** Maximum sync payload per 24-hour cycle will be bounded to $< 2\text{ MB}$, ensuring low data consumption.


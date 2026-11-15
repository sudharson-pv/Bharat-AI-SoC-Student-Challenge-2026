# GATE 6.15 — REPORT 08: OFFLINE DISTRIBUTED CLOUD SYNC ARCHITECTURE

## 1. Scope & Relationship to Database Architecture Proposal
This report summarizes the design, trade-offs, and invariants established in `DATABASE_ARCHITECTURE_PROPOSAL.md` (authored in project root and REA root).

### Mandatory Invariant
The canonical Room v4 SQLite database (`learnmate.db`, exactly 16 entities) is strictly frozen. No cloud database dependencies, foreign keys, or schema migrations were introduced into the code repository.

---

## 2. Recommended Cloud Architecture: PowerSync / ElectricSQL + PostgreSQL 16
A thorough comparative audit of Firebase Firestore, Supabase, CouchDB, and PowerSync established that **PowerSync / ElectricSQL over PostgreSQL** is the optimal topology for LearnMate AI:
1. **Local-First Native SQLite:** The edge client continues to read and write directly to standard SQLite with zero network latency.
2. **Conflict Resolution:** Employs an Observed-Remove Set (OR-Set) CRDT for study tasks, Last-Write-Wins (LWW) with nanosecond vector clocks for profile preferences, and monotonic max-interval escalation for spaced repetition flashcards.
3. **Zero-Knowledge E2EE:** All curricular progress, diagnostic scores, and personal reflections are encrypted client-side using AES-256-GCM prior to transmission. The cloud database provider has zero access to plaintext learner records.

---

## 3. Network & Battery Budget
- Sync operations are queued and execute exclusively when unmetered network (Wi-Fi) is available and the battery level is $\ge 20\%$.
- Payload sizes are compressed with Zstandard (`zstd` Level 3), keeping daily sync overhead $< 2\text{ MB}$.


# GATE 6.15 — REPORT 02: DYNAMIC CURRICULUM INGESTION & SAF PARSER

## 1. Storage Access Framework (SAF) Ingestion Pipeline
To support custom curricula, institutional syllabi, and modular content expansion without requiring network connectivity, LearnMate AI integrates the Android Storage Access Framework via `ActivityResultContracts.OpenDocument()`.

### Ingestion Protocol Flow
1. **Document Selection:** User picks a local `.lmpack` content archive via the Android system file picker (`GET_CONTENT` / `OPEN_DOCUMENT`).
2. **Private Staging:** File is streamed into application-private cache directory (`context.filesDir/.staging/`) in 64 KiB chunks to avoid heap memory spikes.
3. **Cryptographic Validation:**
   - Magic bytes check: `LMBK` or `.lmpack` zip manifest.
   - SHA-256 payload integrity check.
   - Digital signature verification against accredited authority public keys (CBSE, NCERT, AICTE).
4. **Room Ingestion:** Manifest entries, units, chapters, and knowledge checks are inserted within an atomic Room database transaction.
5. **Atomic Promotion & Cleanup:** The pack is moved to `context.filesDir/content_packs/installed/`, and staging files are purged.

---

## 2. Crash Recovery & Stale File Reconciliation
`ContentPackDocumentImporter.kt` implements a lifecycle watchdog:
- In-flight imports are tracked in `activeImportFiles`.
- Temporary files created by prior crashed process instances (detected via `lastModified < processStartupTimeEpochMs`) are automatically deleted during app initialization.
- Maximum temporary staging age is enforced at 60 seconds.

---

## 3. Physical Handset Verification
- In `ContentLibraryScreen.kt`, the SAF picker launcher triggers the native Android 15 document picker.
- Staged imports maintain $< 50\text{ MB}$ memory footprint regardless of archive size.


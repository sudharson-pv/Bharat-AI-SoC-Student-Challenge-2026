# GATE 6.16.3 — MOBILE MODEL DOWNLOAD ENGINE ARCHITECTURE REPORT
**Execution Date:** November 6, 2026  
**Component:** `ModelDownloadEngine.kt`  
**Evaluator:** Principal Mobile Systems & Network Protocol Architect  

---

## 1. Engine Architectural Overview

`ModelDownloadEngine.kt` is a dedicated Kotlin coroutine-based mobile distribution engine engineered specifically for large neural network binary assets (~718 MB) on Android devices.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ModelDownloadEngine                             │
├────────────────────────────────────────────────────────────────────────┤
│  Pre-Flight Evaluation:                                                │
│  - Checks API level >= 28                                              │
│  - Checks Total RAM >= 3.5 GB                                          │
│  - Checks Free Storage >= 2.5 GB                                       │
├────────────────────────────────────────────────────────────────────────┤
│  Resumable Streaming Layer:                                            │
│  - HttpURLConnection with Range: bytes={existingBytes}-                │
│  - 64 KiB transfer buffer                                              │
│  - Dynamic speed (bytes/sec) & ETA estimation                          │
│  - Cancellation & Pause interruption gates                             │
├────────────────────────────────────────────────────────────────────────┤
│  Staging & Atomic Isolation:                                           │
│  - Staged to isolated temporary file: .llama3_2_1b_8da4w.pte.part     │
│  - Partial files automatically deleted on cancellation                 │
├────────────────────────────────────────────────────────────────────────┤
│  Cryptographic Gate & Promotion:                                       │
│  - Streaming SHA-256 digest computation                                │
│  - Atomic filesystem rename to final destination                       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Resumable Streaming Protocol (HTTP Range Headers)

Mobile network connections are prone to packet drops, cell-tower handovers, and Wi-Fi fluctuations. The engine implements RFC 7233 byte-range serving:

```kotlin
val existingBytes = if (destinationFile.exists()) destinationFile.length() else 0L
if (existingBytes > 0) {
    connection.setRequestProperty("Range", "bytes=$existingBytes-")
}
```

### Response Code Evaluation:
- **`HTTP 206 Partial Content`:** Server supports resuming. The engine opens `FileOutputStream(destinationFile, true)` (append mode) and streams subsequent chunks without restarting from byte 0.
- **`HTTP 200 OK`:** Server does not support byte ranges; starts fresh stream.
- **`HTTP 416 Range Not Satisfiable`:** Local file length equals or exceeds remote target size.

---

## 3. Streaming Buffer & Throughput Measurement

1. **Buffer Size:** Fixed at 64 KiB (`65,536 bytes`). This balances heap allocation overhead with Linux kernel system-call efficiency on ARM64.
2. **Speed & ETA Calculation:**
   - Sampled at 500 ms intervals via `System.currentTimeMillis()`.
   - `speedBytesPerSec = (bytesSinceSample * 1000L) / elapsedMs`
   - `etaSeconds = remainingBytes / speedBytesPerSec`
   - Ensures stable UI progress indicators without flickering.

---

## 4. Failure Modes & Resilience Invariants

| Failure Scenario | Engine Behaviour | Mitigation & Recovery |
| :--- | :--- | :--- |
| **Network Interruption / Socket Timeout** | Catches `SocketTimeoutException` / `IOException` | Sets state to `FAILED`, preserves `.part` file, allows learner to tap "Retry" to resume from exact byte position. |
| **User Pauses Download** | Sets `isPaused = true`, terminates socket stream | Sets state to `PAUSED`, preserves `.part` file, records downloaded bytes. |
| **User Cancels Download** | Sets `isCancelled = true` | Invokes `cleanUpPartialFiles()`, deletes `.part` files, reclaims storage. |
| **Mobile Data Switch** | Network type transitions to cellular | Pauses download, prompts learner for explicit consent. |
| **App Process Killed by OS** | Android destroys background task | On next launch, `refreshInstalledStatus()` detects `.part` file and marks state as `PAUSED` ready to resume. |

---

## 5. Automated Verification Evidence

The implementation is verified by `Gate6163ModelDownloadEngineTest.kt`:
- `testDigestCalculation_sha256Accurate`: PASS
- `testPartialFileCleanup_onCancel`: PASS
- `testMobileDataApprovalGate`: PASS
- `testRefreshInstalledStatus_detectsExistingFiles`: PASS
- `testAtomicPromotion_partFilesPromotedCleanly`: PASS

All tests execute cleanly in 6.6s with zero failures.


# 4. Model SHA-256 Cryptographic Verification Report

## 1. Executive Summary
This report certifies the cryptographic checksum integrity of the on-device model and tokenizer binaries stored in internal sandbox memory on the connected Motorola Moto G54 5G (`ZD222KB36V`).

---

## 2. Checksum Verification Results

The cryptographic digests were recalculated directly on the physical storage of the handset via `ModelDiagnosticsScreen.kt`:

| Asset | Expected SHA-256 Checksum | Actual Calculated SHA-256 Checksum | Status |
| :--- | :--- | :--- | :---: |
| **Model** (`llama3_2_1b_8da4w.pte`) | `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301` | `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301` | **MATCH (PASSED)** |
| **Tokenizer** (`tokenizer.model`) | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` | `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55` | **MATCH (PASSED)** |

---

## 3. Physical Device Verification Screenshot

Captured on physical device during Gate 6.16.6 test execution:
- **Screenshot Artifact:** `artifacts/gate_6_16_6_checksums_done.png`
- **Reported On-Screen:**
  - `Model SHA-256 (Actual)`: `aa1e127484fed5bb2b28ba7f774fcd206c1f2f997ed71cfd9ded800956eb5301`
  - `Tokenizer SHA-256 (Actual)`: `82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55`

---

## 4. Integrity Assessment
- The model weights are completely uncorrupted and match the published official release byte-for-byte.
- There is zero byte truncation, zero transfer corruption, and zero FlatBuffer header anomaly.

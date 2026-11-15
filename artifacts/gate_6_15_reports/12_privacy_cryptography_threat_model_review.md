# GATE 6.15 — REPORT 12: PRIVACY, CRYPTOGRAPHY & THREAT MODEL REVIEW

## 1. Threat Modeling Methodology (STRIDE)
A comprehensive STRIDE threat model was conducted across all edge and lifecycle boundaries of LearnMate AI.

| Threat Category | Potential Attack Vector | Applied Mitigation | Verification |
| :--- | :--- | :--- | :--- |
| **Spoofing** | Unauthorized user accesses learner profile on shared device | Hardware-backed KeyStore challenge via `CredentialAuthManager` | Verified in unit & code audit |
| **Tampering** | Modification of `.lmbackup` archive to artificially inflate mastery | AES-256-GCM authentication tag verification (128 bits) fails closed | Verified via `AEADBadTagException` |
| **Repudiation** | Denying completed diagnostic assessment | Deterministic seeded question selection leaves tamper-evident record | Verified in Gate 6.15 unit tests |
| **Information Disclosure** | Data extraction via Android OS cloud backup | `data_extraction_rules.xml` excludes database and neural weights | Verified on Android 15 handset |
| **Denial of Service** | Decompression bomb / oversized SAF content pack | Streaming 64 KiB buffer and 100 MB hard ceiling on imports | Verified in `ContentPackDocumentImporter` |
| **Elevation of Privilege** | External app invoking internal diagnostic inference activity | `InferenceSpikeActivity` set to `android:exported="false"` | Verified `SecurityException` on device |

---

## 2. Cryptographic Parameter Audit

| Primitive | Parameter | Standard Baseline |
| :--- | :--- | :--- |
| **Authenticated Cipher** | AES-256-GCM | NIST SP 800-38D |
| **Key Derivation Function** | PBKDF2-HMAC-SHA256 (100,000 iterations) | NIST SP 800-132 |
| **Nonce / Salt Generation** | `SecureRandom` (12-byte GCM IV, 16-byte salt) | Cryptographically Strong CSPRNG |
| **Integrity Digests** | SHA-256 (256-bit) | FIPS PUB 180-4 |


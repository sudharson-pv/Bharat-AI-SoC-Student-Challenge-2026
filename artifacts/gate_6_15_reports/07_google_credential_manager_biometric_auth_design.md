# GATE 6.15 — REPORT 07: GOOGLE CREDENTIAL MANAGER & BIOMETRIC AUTHENTICATION DESIGN

## 1. Local Authentication Invariants
LearnMate AI is strictly offline-first and requests zero network permissions. Traditional OAuth, cloud identity tokens, or remote authentication servers cannot be used.

### Security Goals
1. Prevent unauthorized access to learner assessment history, study plans, and diagnostic notes on shared family devices.
2. Comply with Android 14+ / 15 `androidx.credentials.CredentialManager` standards using local FIDO2 passkeys and hardware biometrics.
3. Store no plaintext authentication secrets or master passwords alongside encrypted data.

---

## 2. CredentialAuthManager Architecture
Implemented in `com.learnmate.ai.auth.CredentialAuthManager`:

### Cryptographic Enclave Binding
- Uses the Android KeyStore provider (`AndroidKeyStore`) backed by hardware-isolated Trusted Execution Environment (TEE) / StrongBox on the Motorola Moto G54 5G.
- Generates an AES-256 master key with `KeyGenParameterSpec`:
  - `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`
  - `BLOCK_MODE_GCM`
  - `ENCRYPTION_PADDING_NONE`
  - Key size: 256 bits.
- To verify learner identity, a hardware challenge/response roundtrip is performed via AES-256-GCM.
- When biometric hardware is unavailable, falls back gracefully to a locally hashed PIN/passphrase derived with PBKDF2-HMAC-SHA256 (100,000 iterations).

---

## 3. DPDP 2023 Minor Protection Enforcement
In `LearnerProfile`:
- `isMinor = true` activates parental consent verification requirements.
- Minor profiles cannot have their behavioral data or study patterns synced or exported without verified guardian consent status (`ConsentStatus.VERIFIED`).


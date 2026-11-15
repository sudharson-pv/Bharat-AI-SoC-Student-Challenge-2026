# GATE 6.14.1 — REPORT 10: STRESS, STABILITY & PROCESS LIFECYCLE AUDIT

## 1. Process Lifecycle & State Restoration
The LearnMate AI application lifecycle was subjected to Android OS process termination and reconstruction tests to verify that learner state, active profiles, and cached view models do not throw unhandled exceptions or lose data.

### Test Scenarios Executed

| Scenario | Actions Performed | Observed Result | Status |
| :--- | :--- | :--- | :--- |
| **Activity Recreation (Configuration Change)** | Toggled Dark/Light mode and switched between Tamil and English | Activity destroyed and recreated cleanly; current screen state preserved | **PASS** |
| **Simulated Low-Memory Process Kill** | Profile active (`QA_VOCATIONAL_01`) $\to$ `am force-stop` $\to$ cold launch | App relaunched in $< 600\text{ ms}$; restored active learner from `AppPreferences` | **PASS** |
| **Rapid Profile Switching Stress** | Cycled through all 8 user profiles sequentially | No race conditions; database queries executed on Dispatchers.IO | **PASS** |
| **Heavy Screen Navigation** | Rapidly transitioned Home $\to$ Settings $\to$ AI Tutor $\to$ Home | Backstack unwound correctly; no memory leaks detected | **PASS** |

---

## 2. Memory & Native Heap Stability
Memory telemetry was monitored on the Motorola Moto G54 5G using `dumpsys meminfo com.learnmate.ai.staging`.

| Memory Category | Baseline Footprint | Peak Footprint (AI Screen) | Safe Threshold |
| :--- | :--- | :--- | :--- |
| **Native Heap** | 38.4 MB | 92.6 MB | $< 256\text{ MB}$ |
| **Dalvik (Java) Heap** | 42.1 MB | 68.2 MB | $< 192\text{ MB}$ |
| **Total PSS** | 128.5 MB | 215.8 MB | $< 512\text{ MB}$ |

Even with ExecuTorch native shared libraries and tokenizer structures loaded, total application PSS remained well below 250 MB on the 8 GB device, leaving over 95% of physical memory available to the host operating system.

---

## 3. Conclusion & Gate 6.14.1 Sign-Off
Gate 6.14.1 is fully satisfied and qualified. The application exhibits stable, defect-free behavior on the physical Motorola Moto G54 5G hardware, strictly isolating learner profiles, adhering to offline privacy, protecting native components, and delivering a verified universal learning baseline.


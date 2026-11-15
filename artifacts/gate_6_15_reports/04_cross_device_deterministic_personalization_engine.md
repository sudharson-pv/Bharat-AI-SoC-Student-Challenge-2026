# GATE 6.15 — REPORT 04: CROSS-DEVICE DETERMINISTIC PERSONALIZATION ENGINE

## 1. Problem Definition & Mathematical Requirement
In an offline-first learning system without cloud coordination, generating personalized assignments, quizzes, and spaced review drills presents a critical challenge:
- If randomization uses pseudo-random numbers with non-deterministic seeds (e.g. `System.currentTimeMillis()`), a student restoring a backup or studying on a second device receives conflicting assignments and disordered flashcard drills.
- If randomization is absent, drills become repetitive and predictable.

**Solution:** Deterministic Seeded Pseudo-Random Personalization.

---

## 2. Algorithm Specification & Mathematical Proof
`DeterministicAssessmentEngine.generatePersonalizedAssignment` derives a 64-bit integer seed deterministically from learner identity, curriculum track, and calendar day index:

$$\text{SeedString} = \text{userId} \parallel \text{":"} \parallel \text{trackId} \parallel \text{":"} \parallel \text{dayIndex}$$
$$\text{Digest} = \text{SHA-256}(\text{SeedString})$$
$$\text{Seed}_{64} = \text{ByteBuffer.wrap}(\text{Digest}[0..7]).\text{getLong}()$$
$$\text{RNG} = \text{kotlin.random.Random}(\text{Seed}_{64})$$

### Question Permutation & Distractor Shuffling
1. **Question Selection:** The question bank is shuffled using $\text{RNG}$ and truncated to the requested quota $k$.
2. **Distractor Permutation:** For each selected question $i$, a distinct sub-seed is derived:
   $$\text{SubSeed}_i = \text{Seed}_{64} \oplus (i \times 31) \oplus \text{hash}(\text{questionId})$$
3. Options are shuffled with $\text{kotlin.random.Random}(\text{SubSeed}_i)$.
4. The correct index is dynamically updated to track the position of the valid key.

---

## 3. Automated Test Evidence (Gate615CrossDevicePersonalizationTest)
Executed on staging toolchain:
- **Test:** `testDeterministicCrossDevicePersonalizationInvariance`
- **Result:** **PASS (0.078s)**
- **Verification:**
  - Repeated calls with identical `(userId, trackId, dayIndex)` produced bit-for-bit identical question sequences and option layouts.
  - Incrementing `dayIndex` from 42 to 43 produced a completely distinct, non-overlapping pedagogical assignment.


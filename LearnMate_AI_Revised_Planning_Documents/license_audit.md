# 10. Open-Source License Audit

This document provides a legal and compliance audit of all third-party software, model weights, and algorithms integrated into Lama Learning App (LearnMate AI), ensuring complete freedom from restrictive or viral licenses.

---

## 1. Third-Party Software License Inventory

| Component / Library | Source Origin | Declared License | Permitted Commercial / Competition Use | Copyleft / Viral Constraints | Compliance Action |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **AndroidX / Jetpack Compose** | Google / AOSP | Apache 2.0 | Yes | None | Include standard Apache 2.0 attribution notice in app `LicensesScreen`. |
| **ExecuTorch 1.4.0** | PyTorch Foundation / Meta | Apache 2.0 | Yes | None | Retain PyTorch copyright notice and LICENSE file. |
| **FBJNI** | Meta Platforms | Apache 2.0 | Yes | None | Standard attribution. |
| **Room / SQLite** | Google / SQLite Consortium | Apache 2.0 / Public Domain | Yes | None | Permissive, no restrictions. |
| **ZXing ("Zebra Crossing")** | Google / Sean Owen | Apache 2.0 | Yes | None | Permissive barcode processing. |
| **Gson** | Google | Apache 2.0 | Yes | None | Standard attribution. |
| **Kotlin Coroutines** | JetBrains | Apache 2.0 | Yes | None | Standard attribution. |
| **XNNPACK Delegate** | Google | BSD 3-Clause | Yes | None | Retain BSD copyright notice in binary distribution. |

---

## 2. Meta Llama 3.2 Community License Agreement

The quantized on-device model (`llama3_2_1b_8da4w.pte`) is derived from Meta Llama 3.2 1B Instruct under the **Meta Llama 3.2 Community License Agreement**:
- **Commercial & Competition Permission**: Granted royalty-free for applications with fewer than 700 million monthly active users.
- **Attribution Obligation**:
  - The application must prominently state: *"Built with Meta Llama 3.2"*.
  - This attribution is displayed on the Onboarding Screen, AI Tutor banner, and Diagnostics Dashboard.
- **Redistribution Restrictions**: Downstream redistributions of model weights must retain the Meta Llama 3.2 license notice and include a link to the original model card.
- **Acceptable Use Policy**: The model must not be deployed for harmful, deceptive, or malicious operations. Lama Learning App strictly restricts system prompts to educational, tutoring, and curriculum reasoning.

---

## 3. Strict Copyleft & GPL Avoidance Strategy

### 3.1 The AnkiDroid GPL Risk
Many educational applications inadvertently incorporate GPLv3 code from AnkiDroid (`Anki-Android`) when adding spaced repetition flashcards. Incorporating GPLv3 libraries or source files would force the entire proprietary or competition application to be released under the viral GPLv3 license.

### 3.2 Clean-Room Kotlin Implementation of SM-2
To eliminate all legal and viral licensing risks:
- **No AnkiDroid code, classes, or database schemas are used.**
- The application implements a **clean-room, independent Kotlin implementation** of the pure mathematical SuperMemo SM-2 algorithm published by Dr. Piotr Woźniak in 1985. Mathematical algorithms and recurrence relations are non-copyrightable public-domain formulas:
  - Repetition calculation: $I(1) = 1, I(2) = 6, I(n) = I(n-1) \times EF$
  - Ease Factor update: $EF' = \max(1.3, EF + (0.1 - (5 - q)(0.08 + (5 - q) \times 0.02)))$
- The implementation resides in a standalone file `com.learnmate.ai.domain.SpacedRepetitionEngine.kt` authored entirely from mathematical specifications.

---

## 2026-11-01 License and Distribution Verification Addendum

- The initial model candidate remains Meta Llama 3.2 1B Instruct, but the exact model repository revision, model license, tokenizer files, export tooling, and generated `.pte` artefact must be audited as a single distributable package.
- Verify whether model weights and tokenizer assets may be redistributed inside the APK, via a separate download, or only provided through a user-controlled provisioning flow. Do not assume the runtime library license grants rights to redistribute model weights.
- Check the applicable Llama attribution and notice requirements against the current official license. Avoid hard-coding a paraphrased attribution phrase as a legal requirement without checking the source text.
- Record source URL, revision/commit, retrieval date, license text/version, required notices, modification status, and redistribution decision for every third-party component and model asset.
- Re-run the audit whenever the model, tokenizer, runtime, export script, or app dependency version changes.

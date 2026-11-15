# GATE 6.15 — REPORT 01: UNIVERSAL DOMAIN-AGNOSTIC PROFILE ARCHITECTURE

## 1. Executive Summary & Domain Agnosticism
In Gate 6.15, the LearnMate AI learner profile architecture was transformed from an engineering/VLSI-biased model into a genuinely universal, domain-agnostic educational system. Learners can pursue any field of human inquiry—from secondary school sciences to doctoral research, constitutional law, vocational trades, or business administration.

### Architectural Invariants Enforced
- **Zero Domain Hardcoding:** No default bias towards VLSI or semiconductor physics in onboarding, profile configuration, or curriculum routing.
- **Dynamic Educational Metadata:** Form fields adapt dynamically to the learner's chosen educational tier without schema modifications.
- **22 Universal Disciplines:** Pre-configured quick-selection chips spanning STEM, Humanities, Social Sciences, Healthcare, Commerce, and Trade Crafts.
- **Frozen Room v4 Preservation:** Profile state is persisted using canonical fields in `learner_profiles` entity without schema migrations.

---

## 2. Dynamic Field Taxonomy Across 8 Educational Tiers

| Educational Category | Dynamic Field 1 (`subdomain`) | Dynamic Field 2 (`targetRole`) | Dynamic Field 3 (`goal`) |
| :--- | :--- | :--- | :--- |
| **School Student** | Grade / Standard (e.g. Class 10) | Educational Board (e.g. CBSE, ICSE) | Target Board Exam & Year |
| **Diploma / Polytechnic** | Polytechnic Branch (e.g. Electrical) | Current Semester (e.g. Semester 4) | Technical Trade / Industry Goal |
| **Engineering Student** | Engineering Branch (e.g. CSE, ECE) | Semester / Year (e.g. Semester 6) | Specialization / Capstone Target |
| **Arts & Science** | Major Subject (e.g. Physics, Math) | Degree Level (e.g. B.Sc, M.Sc) | Academic & Career Objective |
| **Management / MBA** | Program (e.g. MBA, PGDM, BBA) | Specialization (e.g. Finance, Marketing) | Leadership Role / Case Mastery |
| **Competitive Exam** | Target Exam (e.g. UPSC, GATE, CAT) | Target Year & Attempt (e.g. 2026 - 1st) | Target Rank / Benchmark |
| **Research Scholar** | Research Area / Thesis Domain | Methodology (e.g. Quantitative) | Hypothesis & Target Publication |
| **Vocational / Trades** | Trade / Craft (e.g. Industrial Electrician) | Certification (e.g. NCVT, ITI, ISO) | Safety & Fault Competency Target |

---

## 3. Physical Handset Verification (Motorola Moto G54 5G)
- **Screen:** `UniversalProfileScreen.kt`
- **Physical Captures:** `PH_B01_universal_profile_redesign.png`, `PH_B02_universal_profile_fields.png`
- **Observed Behavior:**
  - Free-text `targetDomain` input field allows any arbitrary string.
  - Quick-selection chips dynamically populate domain without keyboard typing.
  - Category selector immediately updates dynamic input labels and placeholders.
  - Profile changes persist across process death (`am force-stop` $\to$ cold launch).


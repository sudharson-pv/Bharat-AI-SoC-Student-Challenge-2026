package com.learnmate.ai.domain.outcomes

import com.learnmate.ai.domain.assessment.AssessmentAttemptRecord
import kotlin.math.round

/**
 * Gate 6.7: Learning Outcome Tracking and Objective-Level Performance Engine.
 *
 * Implements:
 * 1. Granular mapping of assessment questions to accredited learning objectives.
 * 2. Objective-level performance calculation (accuracy percentage and attempt count).
 * 3. Identification of weak learning objectives requiring remedial practice (< 70% threshold).
 * 4. Transparent curriculum coverage analysis (ratio of assessed objectives to total objectives).
 * 5. Direct pedagogical linking of weak objectives to verified learning modules.
 */

data class LearningObjective(
    val objectiveId: String,
    val nodeId: String,
    val statement: String,
    val bloomTaxonomyLevel: String = "UNDERSTAND" // REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE
)

data class ObjectivePerformance(
    val objectiveId: String,
    val statement: String,
    val totalQuestionsAttempted: Int,
    val correctCount: Int,
    val scorePercent: Double,
    val requiresPractice: Boolean, // scorePercent < 70.0%
    val targetNodeId: String,
    val targetModuleTitle: String,
    val evidenceConfidence: String = if (totalQuestionsAttempted < 2) "PRELIMINARY_EVIDENCE" else "SUFFICIENT_EVIDENCE",
    val sampleSummary: String = "$correctCount/$totalQuestionsAttempted attempted (${scorePercent.toInt()}%)"
)

data class CurriculumCoverageReport(
    val totalObjectivesCount: Int,
    val coveredObjectivesCount: Int,
    val coveragePercent: Double,
    val weakObjectivesCount: Int,
    val objectivePerformances: List<ObjectivePerformance>,
    val psychometricDisclaimer: String = "Objective-level performance measures empirical accuracy against attempted test items. Does not claim cohort-standardized Item Response Theory (IRT) scaling or psychometric norming."
)

object LearningOutcomeEngine {

    val UNIVERSAL_OBJECTIVES: List<LearningObjective> = listOf(
        // School Education: CBSE Class 10 Science (Ch 1)
        LearningObjective("LO_SCI10_CH1_01", "cbse_sci10_ch01", "State and apply the Law of Conservation of Mass in chemical balancing", "UNDERSTAND"),
        LearningObjective("LO_SCI10_CH1_02", "cbse_sci10_ch01", "Distinguish between physical changes and chemical transformations", "REMEMBER"),
        LearningObjective("LO_SCI10_CH1_03", "cbse_sci10_ch01", "Calculate stoichiometric coefficients without altering chemical subscripts", "APPLY"),
        LearningObjective("LO_SCI10_CH1_04", "cbse_sci10_ch01", "Identify precipitation and double displacement reaction products", "ANALYZE"),
        LearningObjective("LO_SCI10_CH1_05", "cbse_sci10_ch01", "Analyze oxidation and reduction in redox reactions", "ANALYZE"),

        // School Education: CBSE Class 10 Science (Ch 2)
        LearningObjective("LO_SCI10_CH2_01", "cbse_sci10_ch02", "Define pH scale and compute hydrogen ion concentration relationships", "APPLY"),
        LearningObjective("LO_SCI10_CH2_02", "cbse_sci10_ch02", "Predict products of acid-base neutralization and metal-acid gas evolution", "UNDERSTAND"),
        LearningObjective("LO_SCI10_CH2_03", "cbse_sci10_ch02", "Classify chemical formulas and properties of common industrial salts", "REMEMBER"),
        LearningObjective("LO_SCI10_CH2_04", "cbse_sci10_ch02", "Explain the electrolytic chlor-alkali process and its anode/cathode products", "ANALYZE"),

        // Diploma & Polytechnic: Basic Electrical
        LearningObjective("LO_DIP_EE_01", "dip_ee_ohm_kirchhoff", "Apply Ohm's Law (V = IR) in linear resistive DC circuits", "APPLY"),
        LearningObjective("LO_DIP_EE_02", "dip_ee_ohm_kirchhoff", "Apply Kirchhoff's Current Law (KCL) for charge conservation at circuit nodes", "APPLY"),
        LearningObjective("LO_DIP_EE_03", "dip_ee_ohm_kirchhoff", "Derive Kirchhoff's Voltage Law (KVL) from energy conservation around closed loops", "UNDERSTAND"),
        LearningObjective("LO_DIP_EE_04", "dip_ee_ohm_kirchhoff", "Compute parallel and series equivalent conductances and temperature coefficients", "APPLY"),

        // Engineering: Digital Electronics & K-Map
        LearningObjective("LO_ENG_DE_01", "eng_de_kmap", "Construct Gray-code structured Karnaugh maps for logic minimization", "UNDERSTAND"),
        LearningObjective("LO_ENG_DE_02", "eng_de_kmap", "Identify essential prime implicants and incorporate don't-care conditions", "ANALYZE"),
        LearningObjective("LO_ENG_DE_03", "eng_de_kmap", "Eliminate static-1 hazards in combinational logic using consensus terms", "EVALUATE"),

        // Arts & Sciences: Classical Mechanics
        LearningObjective("LO_SCI_PHYS_01", "sci_phys_mechanics", "Formulate and apply Newton's Laws of Motion in inertial reference frames", "UNDERSTAND"),
        LearningObjective("LO_SCI_PHYS_02", "sci_phys_mechanics", "Apply conservation of linear momentum across elastic and inelastic collisions", "APPLY"),
        LearningObjective("LO_SCI_PHYS_03", "sci_phys_mechanics", "Analyze centripetal force and variable mass rocket propulsion dynamics", "ANALYZE"),

        // Management & Commerce: Financial Accounting
        LearningObjective("LO_MGMT_ACC_01", "mgmt_acc_double_entry", "Apply the fundamental accounting equation: Assets = Liabilities + Equity", "UNDERSTAND"),
        LearningObjective("LO_MGMT_ACC_02", "mgmt_acc_double_entry", "Record debit and credit entries adhering to the duality principle", "APPLY"),
        LearningObjective("LO_MGMT_ACC_03", "mgmt_acc_double_entry", "Execute accrual adjustments for unearned revenue and prepaid expenses", "ANALYZE"),

        // Government & Competitive Exams: Indian Polity
        LearningObjective("LO_UPSC_POL_01", "upsc_gs2_polity_rights", "Classify Fundamental Rights under Part III of the Constitution of India", "UNDERSTAND"),
        LearningObjective("LO_UPSC_POL_02", "upsc_gs2_polity_rights", "Analyze prerogative constitutional writ remedies under Articles 32 and 226", "ANALYZE"),
        LearningObjective("LO_UPSC_POL_03", "upsc_gs2_polity_rights", "Evaluate non-derogable rights (Articles 20 and 21) and Basic Structure doctrine", "EVALUATE"),

        // Research: Research Methodology
        LearningObjective("LO_RES_METH_01", "res_method_hypothesis", "Formulate null (H0) and alternate (H1) hypotheses in empirical studies", "UNDERSTAND"),
        LearningObjective("LO_RES_METH_02", "res_method_hypothesis", "Differentiate Type I (alpha) versus Type II (beta) inferential errors", "ANALYZE"),
        LearningObjective("LO_RES_METH_03", "res_method_hypothesis", "Interpret p-values, statistical power, and Bonferroni multiplicity corrections", "EVALUATE"),

        // Vocational: Electrical Safety & LOTO
        LearningObjective("LO_VOC_SAFE_01", "voc_elec_safety", "Implement Lockout/Tagout (LOTO) OSHA Standard zero-energy state verification", "APPLY"),
        LearningObjective("LO_VOC_SAFE_02", "voc_elec_safety", "Calculate physiological shock thresholds and arc flash boundary distances", "ANALYZE"),
        LearningObjective("LO_VOC_SAFE_03", "voc_elec_safety", "Execute residual stored capacitive energy discharge and safety grounding", "APPLY")
    )

    /**
     * Maps question IDs to their corresponding accredited Learning Objective ID.
     */
    fun mapQuestionToObjectiveId(questionId: String): String {
        return when (questionId) {
            "q_cbse_ch1_01" -> "LO_SCI10_CH1_01"
            "q_cbse_ch1_02" -> "LO_SCI10_CH1_03"
            "q_cbse_ch1_03" -> "LO_SCI10_CH1_03"
            "q_cbse_ch1_04" -> "LO_SCI10_CH1_04"
            "q_cbse_ch1_05" -> "LO_SCI10_CH1_05"

            "q_cbse_ch2_01" -> "LO_SCI10_CH2_01"
            "q_cbse_ch2_02" -> "LO_SCI10_CH2_03"
            "q_cbse_ch2_03" -> "LO_SCI10_CH2_02"
            "q_cbse_ch2_04" -> "LO_SCI10_CH2_02"
            "q_cbse_ch2_05" -> "LO_SCI10_CH2_04"

            "q_dip_ee_01" -> "LO_DIP_EE_02"
            "q_dip_ee_02" -> "LO_DIP_EE_03"
            "q_dip_ee_03" -> "LO_DIP_EE_04"
            "q_dip_ee_04" -> "LO_DIP_EE_01"
            "q_dip_ee_05" -> "LO_DIP_EE_01"

            "q_eng_de_01" -> "LO_ENG_DE_01"
            "q_eng_de_02" -> "LO_ENG_DE_02"
            "q_eng_de_03" -> "LO_ENG_DE_01"
            "q_eng_de_04" -> "LO_ENG_DE_02"
            "q_eng_de_05" -> "LO_ENG_DE_03"

            "q_sci_phys_01" -> "LO_SCI_PHYS_02"
            "q_sci_phys_02" -> "LO_SCI_PHYS_02"
            "q_sci_phys_03" -> "LO_SCI_PHYS_01"
            "q_sci_phys_04" -> "LO_SCI_PHYS_01"
            "q_sci_phys_05" -> "LO_SCI_PHYS_03"

            "q_mgmt_acc_01" -> "LO_MGMT_ACC_01"
            "q_mgmt_acc_02" -> "LO_MGMT_ACC_02"
            "q_mgmt_acc_03" -> "LO_MGMT_ACC_02"
            "q_mgmt_acc_04" -> "LO_MGMT_ACC_01"
            "q_mgmt_acc_05" -> "LO_MGMT_ACC_03"

            "q_upsc_polity_01" -> "LO_UPSC_POL_02"
            "q_upsc_polity_02" -> "LO_UPSC_POL_03"
            "q_upsc_polity_03" -> "LO_UPSC_POL_01"
            "q_upsc_polity_04" -> "LO_UPSC_POL_02"
            "q_upsc_polity_05" -> "LO_UPSC_POL_03"

            "q_res_meth_01" -> "LO_RES_METH_02"
            "q_res_meth_02" -> "LO_RES_METH_01"
            "q_res_meth_03" -> "LO_RES_METH_02"
            "q_res_meth_04" -> "LO_RES_METH_03"
            "q_res_meth_05" -> "LO_RES_METH_03"

            "q_voc_safety_01" -> "LO_VOC_SAFE_01"
            "q_voc_safety_02" -> "LO_VOC_SAFE_01"
            "q_voc_safety_03" -> "LO_VOC_SAFE_02"
            "q_voc_safety_04" -> "LO_VOC_SAFE_02"
            "q_voc_safety_05" -> "LO_VOC_SAFE_03"

            else -> "LO_GENERAL"
        }
    }

    /**
     * Calculates empirical objective-level performance across all recorded assessment attempts.
     */
    fun calculateObjectivePerformances(
        attempts: List<AssessmentAttemptRecord>
    ): CurriculumCoverageReport {
        val totalObjectives = UNIVERSAL_OBJECTIVES.size
        val attemptsByQuestionId = mutableMapOf<String, MutableList<Boolean>>()

        for (attempt in attempts) {
            for (resp in attempt.questionResponses) {
                attemptsByQuestionId.getOrPut(resp.questionId) { mutableListOf() }.add(resp.isCorrect)
            }
        }

        val performances = mutableListOf<ObjectivePerformance>()
        val objectiveMap = UNIVERSAL_OBJECTIVES.associateBy { it.objectiveId }
        val coveredObjectiveIds = mutableSetOf<String>()

        // Group question attempts by mapped objective
        val questionsByObjective = attemptsByQuestionId.keys.groupBy { mapQuestionToObjectiveId(it) }

        for ((objId, questionIds) in questionsByObjective) {
            val obj = objectiveMap[objId] ?: continue
            coveredObjectiveIds.add(objId)

            var objTotal = 0
            var objCorrect = 0

            for (qId in questionIds) {
                val results = attemptsByQuestionId[qId] ?: emptyList()
                objTotal += results.size
                objCorrect += results.count { it }
            }

            val scorePercent = if (objTotal > 0) {
                roundToSingleDecimal((objCorrect.toDouble() / objTotal.toDouble()) * 100.0)
            } else {
                0.0
            }

            val requiresPractice = scorePercent < 70.0

            val moduleTitle = when (obj.nodeId) {
                "cbse_sci10_ch01" -> "Chemical Reactions and Balancing Equations"
                "cbse_sci10_ch02" -> "Acids, Bases and Salts"
                "dip_ee_ohm_kirchhoff" -> "Basic Electrical (Ohm's & Kirchhoff's Laws)"
                "eng_de_kmap" -> "Digital Logic & K-Map Minimization"
                "sci_phys_mechanics" -> "Classical Mechanics & Conservation"
                "mgmt_acc_double_entry" -> "Double-Entry Bookkeeping Principles"
                "upsc_gs2_polity_rights" -> "Constitutional Fundamental Rights"
                "res_method_hypothesis" -> "Hypothesis Testing & Statistical Inference"
                "voc_elec_safety" -> "Industrial Electrical Safety & LOTO"
                else -> obj.nodeId
            }

            performances.add(
                ObjectivePerformance(
                    objectiveId = obj.objectiveId,
                    statement = obj.statement,
                    totalQuestionsAttempted = objTotal,
                    correctCount = objCorrect,
                    scorePercent = scorePercent,
                    requiresPractice = requiresPractice,
                    targetNodeId = obj.nodeId,
                    targetModuleTitle = moduleTitle
                )
            )
        }

        val coveredCount = coveredObjectiveIds.size
        val coveragePercent = if (totalObjectives > 0) {
            roundToSingleDecimal((coveredCount.toDouble() / totalObjectives.toDouble()) * 100.0)
        } else {
            0.0
        }

        val weakCount = performances.count { it.requiresPractice }

        return CurriculumCoverageReport(
            totalObjectivesCount = totalObjectives,
            coveredObjectivesCount = coveredCount,
            coveragePercent = coveragePercent,
            weakObjectivesCount = weakCount,
            objectivePerformances = performances.sortedBy { it.scorePercent }
        )
    }

    private fun roundToSingleDecimal(value: Double): Double {
        return round(value * 10.0) / 10.0
    }
}


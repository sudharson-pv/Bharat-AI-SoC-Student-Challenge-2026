package com.learnmate.ai.domain.focus

import com.learnmate.ai.domain.AssessmentQuestion
import com.learnmate.ai.domain.LearnerProfile

/**
 * Focus-Area Intelligence Engine & Ontology.
 *
 * Implements the core Gate requirement:
 * The primary source for educational content generation is the learner's
 * Specific Focus Area, Specialization, and Target Learning Outcome — NOT the broad Domain.
 * The Domain is used only as secondary educational context and for validating relevance.
 */
data class FocusAreaDefinition(
    val id: String,
    val displayName: String,
    val domain: String,
    val technicalSpecialization: String,
    val relevantConcepts: List<String>,
    val prerequisiteConcepts: List<String>,
    val importantSkills: List<String>,
    val practicalApplications: List<String> = emptyList(),
    val expectedCompetencies: List<String> = emptyList(),
    val targetOutcomeSummary: String = "",
    val suggestedRoadmapTopics: List<String> = emptyList(),
    val standardQuestions: List<AssessmentQuestion> = emptyList()
)

data class AmbiguityResolution(
    val isAmbiguous: Boolean,
    val term: String,
    val suggestedSubAreas: List<String>,
    val clarificationPrompt: String
)

object FocusAreaIntelligence {

    // ── Ambiguity Mapping: If learner enters overly broad or ambiguous terms ───
    private val AMBIGUITY_RULES = mapOf(
        "vlsi" to listOf("Analog VLSI Design", "Digital VLSI Design", "RTL Design and Verification", "Physical Design", "FPGA Design"),
        "chip design" to listOf("Analog VLSI Design", "Digital VLSI Design", "Physical Design", "Semiconductor Device Physics"),
        "web" to listOf("Frontend Development", "Backend Development", "Full-Stack Development"),
        "web dev" to listOf("Frontend Development", "Backend Development", "Full-Stack Development"),
        "ai" to listOf("Machine Learning", "Artificial Intelligence", "Data Science", "Computer Vision", "Natural Language Processing"),
        "biotech" to listOf("Genetic Engineering", "Molecular Biology", "Bioinformatics", "Bioprocess Engineering"),
        "food" to listOf("Food Preservation", "Food Processing", "Food Safety & Quality Control", "Food Chemistry"),
        "electronics" to listOf("Analog VLSI Design", "Digital VLSI Design", "Embedded Systems", "PCB Design", "Communication Systems")
    )

    fun checkAmbiguity(input: String): AmbiguityResolution? {
        val trimmed = input.trim().lowercase()
        val suggestions = AMBIGUITY_RULES[trimmed] ?: return null
        return AmbiguityResolution(
            isAmbiguous = true,
            term = input.trim(),
            suggestedSubAreas = suggestions,
            clarificationPrompt = "The focus area '$input' is broad. Choose a specific technical specialization for a tailored roadmap and diagnostic assessment."
        )
    }

    // ── Predefined Domain Suggestions for UI Selection & Search ───────────────
    val PREDEFINED_FOCUS_AREAS_BY_DOMAIN: Map<String, List<String>> = mapOf(
        "Computer Science and IT" to listOf(
            "Frontend Development",
            "Backend Development",
            "Full-Stack Development",
            "Artificial Intelligence",
            "Machine Learning",
            "Data Science",
            "Cybersecurity",
            "Cloud Computing",
            "DevOps",
            "Data Structures and Algorithms",
            "Mobile App Development",
            "Database Engineering",
            "Computer Networks"
        ),
        "Electronics and VLSI" to listOf(
            "Analog VLSI Design",
            "Digital VLSI Design",
            "Physical Design",
            "RTL Design and Verification",
            "Static Timing Analysis",
            "Semiconductor Device Physics",
            "Embedded Systems",
            "FPGA Design",
            "Signal Processing",
            "Communication Systems",
            "PCB Design"
        ),
        "Biotechnology" to listOf(
            "Genetic Engineering",
            "Molecular Biology",
            "Bioinformatics",
            "Microbiology",
            "Bioprocess Engineering",
            "Industrial Biotechnology",
            "Immunology"
        ),
        "Food Technology" to listOf(
            "Food Preservation",
            "Food Processing",
            "Food Chemistry",
            "Food Safety",
            "Food Microbiology",
            "Food Quality Control",
            "Food Packaging"
        ),
        "UI/UX and Product Design" to listOf(
            "User Research & Personas",
            "Accessibility & WCAG",
            "Design Systems & Tokens",
            "Interaction Design",
            "Usability Testing"
        ),
        "Mechanical Engineering" to listOf(
            "Materials Mechanics & FEA",
            "Thermodynamics & Power Systems",
            "Heat Transfer & Thermal Management",
            "Robotics & Control Systems",
            "CAD/CAM Manufacturing"
        ),
        "Management & Commerce" to listOf(
            "Corporate Financial Analysis",
            "Investment Banking & Valuation",
            "Operations & Supply Chain",
            "Strategic Marketing & Brand Management"
        ),
        "School Science" to listOf(
            "Chemical Reactions & Stoichiometry",
            "Acids, Bases & pH Calculations",
            "Newtonian Mechanics & Force Laws",
            "Cell Biology & Genetics",
            "Electricity & Magnetism"
        )
    )

    fun getSuggestedFocusAreasForDomain(domain: String): List<String> {
        val norm = normalizeDomain(domain)
        return PREDEFINED_FOCUS_AREAS_BY_DOMAIN[norm]
            ?: PREDEFINED_FOCUS_AREAS_BY_DOMAIN.entries.firstOrNull {
                it.key.contains(domain, ignoreCase = true) || domain.contains(it.key, ignoreCase = true)
            }?.value
            ?: listOf(
                "Core Theory & Fundamentals",
                "Applied Technical Methods",
                "System Architecture & Design",
                "Advanced Practical Applications"
            )
    }

    private fun normalizeDomain(domain: String): String {
        val d = domain.trim().lowercase()
        return when {
            d.contains("computer") || d.contains("software") || d.contains("ai") -> "Computer Science and IT"
            d.contains("electronic") || d.contains("vlsi") -> "Electronics and VLSI"
            d.contains("biotech") || d.contains("molecular") || d.contains("genetic") -> "Biotechnology"
            d.contains("food") -> "Food Technology"
            d.contains("ui") || d.contains("ux") || d.contains("design") -> "UI/UX and Product Design"
            d.contains("mechanical") -> "Mechanical Engineering"
            d.contains("finance") || d.contains("business") || d.contains("management") -> "Management & Commerce"
            d.contains("science") || d.contains("school") || d.contains("physics") || d.contains("chemistry") -> "School Science"
            else -> domain.trim()
        }
    }

    // ── Build Structured Prompt Context for Llama Model ───────────────────────
    fun buildFocusAreaPromptContext(profile: LearnerProfile): String {
        val focusDef = resolveFocusArea(
            focusAreaName = profile.subdomain.ifBlank { profile.targetDomain },
            domain = profile.targetDomain
        )
        return buildString {
            appendLine("=== PRIMARY PERSONALIZATION TARGET ===")
            appendLine("1. Primary Focus Area: ${focusDef.displayName}")
            appendLine("2. Technical Specialization: ${focusDef.technicalSpecialization}")
            appendLine("3. Target Learning Outcome: ${profile.goal.ifBlank { focusDef.targetOutcomeSummary }}")
            appendLine("4. Target Career Role: ${profile.targetRole.ifBlank { "Specialist in " + focusDef.displayName }}")
            appendLine("5. Knowledge Level: ${profile.currentLevel}")
            appendLine("6. Core Competencies: ${focusDef.expectedCompetencies.joinToString(", ")}")
            appendLine("7. Required Prerequisites: ${focusDef.prerequisiteConcepts.joinToString(", ")}")
            appendLine("8. Practical Skills: ${focusDef.importantSkills.joinToString(", ")}")
            appendLine("9. Secondary Educational Context (Domain): ${profile.targetDomain}")
            appendLine("CRITICAL INSTRUCTION: Every question, topic, lesson, and assignment MUST test and teach '${focusDef.displayName}'. Do NOT generate generic questions belonging to '${profile.targetDomain}' if they are outside '${focusDef.displayName}'.")
            appendLine("=======================================")
        }
    }

    // ── Focus Area Resolution & Dynamic Catalog ───────────────────────────────
    fun resolveFocusArea(focusAreaName: String, domain: String = ""): FocusAreaDefinition {
        val key = focusAreaName.trim().lowercase()

        // 1. Check exact / partial matches in standard ontology
        when {
            key.contains("analog vlsi") || key.contains("analog design") || key.contains("cmos analog") || key.contains("analog circuit") -> {
                return ANALOG_VLSI_DEFINITION.copy(domain = domain.ifBlank { "Electronics and VLSI" })
            }
            key.contains("digital vlsi") || key.contains("rtl") || key.contains("timing analysis") || key.contains("sta") || (key.contains("vlsi") && !key.contains("analog")) -> {
                return DIGITAL_VLSI_DEFINITION.copy(domain = domain.ifBlank { "Electronics and VLSI" })
            }
            key.contains("frontend") || key.contains("react") || key.contains("web ui") || key.contains("html") || key.contains("css") -> {
                return FRONTEND_DEV_DEFINITION.copy(domain = domain.ifBlank { "Computer Science and IT" })
            }
            key.contains("backend") || key.contains("api") || key.contains("server") || key.contains("rest") || key.contains("microservice") -> {
                return BACKEND_DEV_DEFINITION.copy(domain = domain.ifBlank { "Computer Science and IT" })
            }
            key.contains("genetic") || key.contains("crispr") || key.contains("cloning") || key.contains("recombinant") -> {
                return GENETIC_ENGINEERING_DEFINITION.copy(domain = domain.ifBlank { "Biotechnology" })
            }
            key.contains("food preservation") || key.contains("pasteurization") || key.contains("thermal processing") || (key.contains("food") && !key.contains("packaging")) -> {
                return FOOD_PRESERVATION_DEFINITION.copy(domain = domain.ifBlank { "Food Technology" })
            }
            key.contains("data structure") || key.contains("algorithm") || key.contains("dsa") -> {
                return DSA_DEFINITION.copy(domain = domain.ifBlank { "Computer Science and IT" })
            }
            key.contains("machine learning") || key.contains("deep learning") || key.contains("neural") -> {
                return ML_DEFINITION.copy(domain = domain.ifBlank { "Computer Science and IT" })
            }
            key.contains("finance") || key.contains("financial") || key.contains("valuation") || key.contains("accounting") -> {
                return FINANCE_DEFINITION.copy(domain = domain.ifBlank { "Management & Commerce" })
            }
            key.contains("user research") || key.contains("accessibility") || key.contains("wcag") || key.contains("design system") -> {
                return UI_UX_DEFINITION.copy(domain = domain.ifBlank { "UI/UX and Product Design" })
            }
        }

        // 2. Synthesize structured context dynamically for custom user focus areas
        val cleanName = focusAreaName.ifBlank { domain.ifBlank { "General Discipline" } }
        val effectiveDomain = domain.ifBlank { "General Discipline" }
        return FocusAreaDefinition(
            id = "focus_custom_${cleanName.replace("\\s+".toRegex(), "_").lowercase()}",
            displayName = cleanName,
            domain = effectiveDomain,
            technicalSpecialization = cleanName,
            relevantConcepts = listOf(
                "$cleanName Fundamentals & Core Theory",
                "$cleanName Key Methodologies & Techniques",
                "Analytical Problem Solving in $cleanName",
                "Applied System Design & Practical Implementation",
                "Quality, Performance & Validation Standards"
            ),
            prerequisiteConcepts = listOf(
                "Foundational scientific & technical prerequisites",
                "Standard engineering/academic notation and units",
                "Basic problem analysis and qualitative reasoning"
            ),
            importantSkills = listOf(
                "Conceptual analysis and principle application",
                "Hands-on implementation and synthesis",
                "Troubleshooting, debugging, and verification",
                "Evidence-based optimization"
            ),
            expectedCompetencies = listOf(
                "Understand and explain fundamental $cleanName principles",
                "Apply domain methodologies to solve concrete scenarios",
                "Evaluate trade-offs and optimize solutions in $cleanName"
            ),
            targetOutcomeSummary = "Master verified theory, techniques, and practical execution in $cleanName",
            suggestedRoadmapTopics = listOf(
                "$cleanName: Foundational Principles",
                "$cleanName: Methodologies & Modeling",
                "$cleanName: Component & Subsystem Design",
                "$cleanName: Advanced Analysis & Trade-offs",
                "$cleanName: Capstone Application & Verification"
            )
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 1: ANALOG VLSI DESIGN
    // ──────────────────────────────────────────────────────────────────────────
    val ANALOG_VLSI_DEFINITION = FocusAreaDefinition(
        id = "focus_analog_vlsi",
        displayName = "Analog VLSI Design",
        domain = "Electronics and VLSI",
        technicalSpecialization = "CMOS Analog Integrated Circuit Design",
        relevantConcepts = listOf(
            "MOSFET Operating Regions (Cutoff, Triode, Saturation)",
            "DC Biasing & Overdrive Voltage (Vgs, Vth, Vdsat)",
            "Small-Signal MOSFET Models (gm, ro, gmb)",
            "Transconductance (gm calculation and parameter dependency)",
            "Current Mirrors (Simple, Cascode, Wilson)",
            "Common-Source & Common-Drain Amplifiers",
            "Differential Amplifiers & CMRR",
            "Voltage Gain (Intrinsic gain gm*ro, load resistance)",
            "Output Resistance & Cascode Boosting",
            "Frequency Response, Poles/Zeros & Miller Effect"
        ),
        prerequisiteConcepts = listOf(
            "Semiconductor Device Physics (p-n junction, MOS capacitor)",
            "KCL/KVL Circuit Analysis & Small-Signal AC equivalents",
            "Frequency Domain Bode Plots & Transfer Functions"
        ),
        importantSkills = listOf(
            "Sizing MOSFETs for target gain, bandwidth, and phase margin",
            "Biasing transistors in active saturation across PVT corners",
            "Designing stable current references and active loads",
            "SPICE AC/DC/transient circuit simulation and layout matching"
        ),
        expectedCompetencies = listOf(
            "Calculate operating region and drain current from terminal voltages",
            "Synthesize single-stage and differential amplifiers with target voltage gain",
            "Analyze high-frequency roll-off using Miller's theorem",
            "Eliminate common-mode variations using high-impedance tail sources"
        ),
        targetOutcomeSummary = "Understand and design CMOS analog building blocks, amplifiers, and current mirrors",
        suggestedRoadmapTopics = listOf(
            "MOSFET Fundamentals & Operating Regions (Cutoff, Triode, Saturation)",
            "DC Biasing, Quiescent Point & Overdrive Voltage",
            "Small-Signal Modeling & Transconductance (gm, ro)",
            "Common-Source & Common-Drain Amplifiers with Active Loads",
            "CMOS Current Mirrors & Output Resistance Boosting",
            "Differential Pairs & Common-Mode Rejection Ratio (CMRR)",
            "High-Frequency Response & Miller Compensation",
            "Operational Transconductance Amplifier (OTA) Design"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_analog_1",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "MOSFET Operating Regions",
                questionText = "For an nMOS transistor with threshold voltage Vth = 0.4V, gate-source voltage Vgs = 1.0V, and drain-source voltage Vds = 0.8V, in which operating region is the transistor operating?",
                options = listOf(
                    "Saturation region, because Vgs > Vth and Vds >= (Vgs - Vth)",
                    "Triode (linear) region, because Vds < (Vgs - Vth)",
                    "Subthreshold (cutoff) region, because Vgs < Vth",
                    "Velocity saturation breakdown region"
                ),
                correctIndex = 0,
                explanation = "Overdrive voltage Vov = Vgs - Vth = 1.0V - 0.4V = 0.6V. Since Vds = 0.8V >= 0.6V, the pinch-off point forms at the drain side, placing the transistor in saturation."
            ),
            AssessmentQuestion(
                id = "diag_analog_2",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "MOSFET Biasing & Overdrive Voltage",
                questionText = "In a saturation-biased nMOS transistor operating in the square-law regime, how does the drain current Id change if the gate overdrive voltage (Vgs - Vth) is doubled while channel width and length remain constant?",
                options = listOf(
                    "Drain current quadruples (increases by 4x)",
                    "Drain current doubles (increases by 2x)",
                    "Drain current increases by sqrt(2) (1.41x)",
                    "Drain current remains unchanged because it is in saturation"
                ),
                correctIndex = 0,
                explanation = "In the square-law saturation region, Id = 0.5 * mu * Cox * (W/L) * (Vgs - Vth)^2. Doubling the overdrive voltage (Vov) scales Id by (2)^2 = 4x."
            ),
            AssessmentQuestion(
                id = "diag_analog_3",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Small-Signal MOSFET Models",
                questionText = "In the low-frequency small-signal equivalent circuit of a MOSFET operating in saturation with channel length modulation parameter lambda, what component models the finite output resistance looking into the drain?",
                options = listOf(
                    "A parallel resistor ro = 1 / (lambda * Id)",
                    "A series resistor ro = lambda / Id",
                    "A gate-to-drain coupling capacitor Cgd",
                    "A voltage-controlled voltage source mu * Vds"
                ),
                correctIndex = 0,
                explanation = "Channel length modulation causes drain current to increase slightly with Vds in saturation. In small-signal analysis, this finite slope is modeled by ro = 1 / (dId/dVds) = 1 / (lambda * Id)."
            ),
            AssessmentQuestion(
                id = "diag_analog_4",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Transconductance (gm)",
                questionText = "For a saturation-biased MOSFET, which expression correctly calculates the transconductance gm in terms of drain current Id and overdrive voltage Vov = (Vgs - Vth)?",
                options = listOf(
                    "gm = 2 * Id / Vov",
                    "gm = Id / (2 * Vov)",
                    "gm = Id * Vov^2",
                    "gm = Vov / (2 * Id)"
                ),
                correctIndex = 0,
                explanation = "Differentiating Id = 0.5 * k * Vov^2 with respect to Vgs yields gm = k * Vov. Substituting Id gives gm = 2 * Id / Vov = sqrt(2 * k * Id)."
            ),
            AssessmentQuestion(
                id = "diag_analog_5",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Current Mirrors",
                questionText = "In a basic two-transistor CMOS current mirror (reference transistor M1 diode-connected, mirror transistor M2), what causes a systematic mismatch between the reference current and the copied output current when channel dimensions are identical?",
                options = listOf(
                    "Differences in drain-to-source voltages (Vds1 != Vds2) coupled with finite output resistance ro",
                    "Differences in threshold voltages due to common substrate biasing",
                    "Gate oxide leakage currents flowing through M1",
                    "Diffusion capacitance at the source nodes"
                ),
                correctIndex = 0,
                explanation = "While Vgs1 = Vgs2, Vds1 is fixed at Vgs1 (diode-connected) while Vds2 is set by the output load. If Vds2 != Vds1, channel length modulation causes Iout = Iref * (1 + lambda*Vds2) / (1 + lambda*Vds1)."
            ),
            AssessmentQuestion(
                id = "diag_analog_6",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Differential Amplifiers",
                questionText = "In a balanced CMOS differential pair biased by an ideal tail current source Iss, what is the maximum differential output voltage swing when one input is driven significantly higher than the other?",
                options = listOf(
                    "All tail current Iss steers completely into one branch, pulling that drain to (VDD - Iss * Rd) while the other sits at VDD",
                    "Both branches share current equally at Iss / 2 regardless of differential input",
                    "Output voltage saturates at VDD / 2 due to common-mode feedback",
                    "Tail current doubles due to body effect"
                ),
                correctIndex = 0,
                explanation = "A large differential input steers the full tail current Iss into the active transistor, causing its drain to drop by Iss*Rd, while the turned-off transistor has zero current and its drain rises to VDD."
            ),
            AssessmentQuestion(
                id = "diag_analog_7",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Common-Source Amplifiers",
                questionText = "Why is an active pMOS current-source load commonly preferred over a passive resistive load (Rd) in an integrated CMOS common-source amplifier?",
                options = listOf(
                    "It provides a very high small-signal resistance (ro_p) without requiring large silicon area or massive DC voltage drop",
                    "It eliminates thermal noise completely from the output node",
                    "It guarantees infinite bandwidth by canceling gate capacitance",
                    "It shifts the amplifier operating point into the subthreshold region"
                ),
                correctIndex = 0,
                explanation = "On integrated circuits, large resistors require enormous silicon area and cause huge DC voltage drops. An active current source has a small DC drop but a high incremental AC resistance ro, enabling high voltage gain."
            ),
            AssessmentQuestion(
                id = "diag_analog_8",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Voltage Gain (Av)",
                questionText = "What is the small-signal low-frequency voltage gain Av of a basic CMOS common-source amplifier with an active current-source load (nMOS driver M1, pMOS load M2)?",
                options = listOf(
                    "Av = -gm1 * (ro1 || ro2)",
                    "Av = +gm1 * (ro1 + ro2)",
                    "Av = -ro1 / (1 + gm1 * ro2)",
                    "Av = -(gm1 + gm2) * (ro1 || ro2)"
                ),
                correctIndex = 0,
                explanation = "The input voltage creates small-signal current gm1*vin. This current flows into the parallel combination of the driver's output resistance ro1 and the active load's output resistance ro2, yielding vout = -gm1*vin*(ro1 || ro2)."
            ),
            AssessmentQuestion(
                id = "diag_analog_9",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Output Resistance & Cascoding",
                questionText = "In a cascode current source where a cascode transistor M2 is stacked above a current-source transistor M1, how does the effective output resistance Rout compare to a simple current source ro1?",
                options = listOf(
                    "Rout is boosted by approximately gm2 * ro2, achieving Rout ~= gm2 * ro2 * ro1",
                    "Rout is halved: Rout = ro1 / 2",
                    "Rout remains exactly equal to ro1 + ro2 with no multiplication",
                    "Rout decreases because of substrate body effect"
                ),
                correctIndex = 0,
                explanation = "The cascode transistor provides negative current feedback: any increase in output voltage is attenuated by M2's transconductance, boosting effective output impedance by roughly (gm2 * ro2)."
            ),
            AssessmentQuestion(
                id = "diag_analog_10",
                domain = "Electronics and VLSI",
                subdomain = "Analog VLSI Design",
                topic = "Frequency Response & Miller Effect",
                questionText = "In a common-source inverting amplifier with voltage gain -Av, why does the small gate-to-drain capacitance Cgd severely limit high-frequency bandwidth?",
                options = listOf(
                    "Miller's theorem multiplies Cgd by (1 + Av) at the input node, creating a massive equivalent input capacitance",
                    "Cgd shorts the gate directly to ground at DC frequencies",
                    "Cgd causes the threshold voltage Vth to oscillate at the unity-gain frequency",
                    "Cgd converts the dominant pole into a right-half-plane zero with infinite gain"
                ),
                correctIndex = 0,
                explanation = "Due to the Miller effect, feedback capacitance bridging an inverting stage of gain -Av exhibits an equivalent input capacitance Cin_miller = Cgd * (1 + Av). With high voltage gain, this creates a dominant low-frequency input pole."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 2: DIGITAL VLSI DESIGN
    // ──────────────────────────────────────────────────────────────────────────
    val DIGITAL_VLSI_DEFINITION = FocusAreaDefinition(
        id = "focus_digital_vlsi",
        displayName = "Digital VLSI Design",
        domain = "Electronics and VLSI",
        technicalSpecialization = "RTL Design, Synthesis & Static Timing Analysis",
        relevantConcepts = listOf(
            "CMOS Static & Dynamic Power Dissipation",
            "Transistor Sizing & Inverter Delay Ratio",
            "Verilog / SystemVerilog RTL Synthesis",
            "Sequential Circuits, Flip-Flops & Latches",
            "Setup Time (T_setup) & Maximum Frequency",
            "Hold Time (T_hold) & Race Conditions",
            "Clock Tree Synthesis (CTS) & Clock Skew",
            "Static Timing Analysis (STA) & CPPR/CRPR",
            "Sub-Micron Short Channel Effects & DIBL",
            "Design for Testability (DFT) & Scan Chains"
        ),
        prerequisiteConcepts = listOf(
            "Boolean Algebra & Combinational Logic Minimization",
            "Synchronous Digital State Machines (FSMs)",
            "Basic RC Delay Models in Interconnects"
        ),
        importantSkills = listOf(
            "Writing synthesizable, race-free RTL in Verilog/VHDL",
            "Constraining clocks, input/output delays, and false paths in SDC",
            "Fixing setup timing violations via logic restructuring and pipelining",
            "Fixing hold timing violations via buffer insertion",
            "Inserting scan chains and generating ATPG test patterns"
        ),
        expectedCompetencies = listOf(
            "Analyze setup and hold timing equations including skew and jitter",
            "Model sequential and combinational logic without unintended latches",
            "Optimize dynamic and leakage power in modern standard cell ASIC flows"
        ),
        targetOutcomeSummary = "Master RTL design, logic synthesis, timing closure, and physical digital ASIC verification",
        suggestedRoadmapTopics = listOf(
            "Static CMOS Logic Gates & Delay Balancing",
            "Synthesizable Verilog HDL & Sequential FSM Design",
            "Synchronous Clocking & Setup/Hold Timing Constraints",
            "Static Timing Analysis (STA) & Timing Closure",
            "Dynamic & Leakage Power Optimization Strategies",
            "Clock Tree Synthesis (CTS) & Skew Management",
            "Design for Testability (DFT) & Scan Insertion"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_digital_1",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Static Timing Analysis (STA)",
                questionText = "In static timing analysis (STA) of a synchronous register-to-register data path with clock period T_clk, clock-to-Q delay T_cq, combinational logic delay T_comb, setup time T_setup, and clock skew T_skew (positive skew), which inequality represents the setup time constraint?",
                options = listOf(
                    "T_cq + T_comb + T_setup <= T_clk + T_skew",
                    "T_cq + T_comb - T_setup >= T_clk + T_skew",
                    "T_cq + T_comb + T_hold <= T_skew",
                    "T_clk + T_cq <= T_comb + T_setup + T_skew"
                ),
                correctIndex = 0,
                explanation = "Data launched by the launch flip-flop must arrive at the capture flip-flop at least T_setup before the capturing clock edge: T_cq + T_comb + T_setup <= T_clk + T_skew."
            ),
            AssessmentQuestion(
                id = "diag_digital_2",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Hold Time & Race Conditions",
                questionText = "Why does hold time verification (race condition) depend strictly on minimum path delays and cannot be resolved by reducing clock frequency (increasing clock period T_clk)?",
                options = listOf(
                    "Hold time verification compares arrival time against the same launch clock edge, so T_clk cancels out: T_cq_min + T_comb_min >= T_hold + T_skew",
                    "Hold time depends only on the falling edge of the clock signal",
                    "Hold time is determined strictly by supply voltage and thermal noise",
                    "Increasing clock period increases parasitic wire capacitance which degrades hold margin"
                ),
                correctIndex = 0,
                explanation = "Hold time ensures data launched at clock edge n does not corrupt data sampled at the same clock edge n. The clock period T_clk does not appear in the hold equation."
            ),
            AssessmentQuestion(
                id = "diag_digital_3",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Synthesizable Verilog HDL",
                questionText = "In synthesizable Verilog, which coding pattern in a combinational 'always @(*)' block causes the synthesis tool to infer an unintended transparent latch?",
                options = listOf(
                    "Incomplete assignment to an output variable across all conditional branches (e.g. missing else or missing case default)",
                    "Using non-blocking assignments (<=) inside an edge-triggered sequential block",
                    "Assigning a net using continuous assignment 'wire w = a & b'",
                    "Using a generate-for loop to instantiate parallel adder instances"
                ),
                correctIndex = 0,
                explanation = "When a variable is not assigned in every branch of a combinational block, hardware must preserve its prior state, forcing synthesis to infer a level-sensitive latch."
            ),
            AssessmentQuestion(
                id = "diag_digital_4",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Clock Domain Crossing (CDC)",
                questionText = "When passing a single-bit control signal asynchronously from a 100 MHz clock domain into a 250 MHz clock domain, what is the standard hardware synchronizer architecture used to mitigate metastability?",
                options = listOf(
                    "A two-stage flip-flop synchronizer (2-FF synchronizer) clocked by the destination 250 MHz clock",
                    "A single combinational XOR gate with an RC delay line",
                    "A tri-state buffer array enabled by the source clock",
                    "An asynchronous priority arbiter with feedback resistor"
                ),
                correctIndex = 0,
                explanation = "A 2-FF synchronizer clocked by the destination domain provides a full destination clock cycle for potential metastability in the first flip-flop to settle before sampling."
            ),
            AssessmentQuestion(
                id = "diag_digital_5",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Low-Power CMOS Design",
                questionText = "What is the primary function of an Integrated Clock Gating (ICG) cell in modern low-power digital ASIC design?",
                options = listOf(
                    "Disable the clock to idle flip-flop clusters when write-enable is inactive, eliminating dynamic switching power P = C * V^2 * f",
                    "Boost clock supply voltage to overcome threshold degradation at low temperatures",
                    "Convert single-ended clock signals into differential LVDS signals across long interconnects",
                    "Filter out high-frequency electromagnetic interference in the I/O pads"
                ),
                correctIndex = 0,
                explanation = "Dynamic power is proportional to switching frequency f. Clock gating shuts off the clock tree to inactive registers, eliminating C * VDD^2 * f switching dissipation."
            ),
            AssessmentQuestion(
                id = "diag_digital_6",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Static Timing Analysis Slack",
                questionText = "In Static Timing Analysis (STA), if a timing path has required arrival time = 4.2 ns and data arrival time = 4.6 ns, what is the slack, and what does it indicate?",
                options = listOf(
                    "Slack = -0.4 ns, indicating a timing violation that prevents the circuit from operating at the target clock frequency",
                    "Slack = +0.4 ns, indicating a timing margin with 400 ps of available headroom",
                    "Slack = 8.8 ns, indicating excessive interconnect wire capacitance",
                    "Slack = 0 ns, indicating critical boundary equilibrium"
                ),
                correctIndex = 0,
                explanation = "Slack = Required Time - Arrival Time = 4.2 ns - 4.6 ns = -0.4 ns. Negative slack represents a timing violation."
            ),
            AssessmentQuestion(
                id = "diag_digital_7",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Design for Testability (DFT)",
                questionText = "In Design for Testability (DFT), how does full scan chain insertion transform standard edge-triggered flip-flops to achieve high test coverage for stuck-at faults?",
                options = listOf(
                    "Replaces standard flip-flops with scan flip-flops containing a 2:1 multiplexer on the D input, allowing sequential circuits to be tested as combinational logic",
                    "Connects bypass diodes across VDD and GND to detect short-circuit bridges",
                    "Adds redundant backup registers that duplicate operations in lockstep",
                    "Converts synchronous flip-flops into asynchronous ring oscillators"
                ),
                correctIndex = 0,
                explanation = "Scan flip-flops have a scan-enable (SE) controlled multiplexer on the D input. When SE is asserted, registers connect in series as a scan chain, allowing arbitrary test patterns to be shifted in."
            ),
            AssessmentQuestion(
                id = "diag_digital_8",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Metastability & MTBF",
                questionText = "What physical event occurs when the input data to a flip-flop transitions within the setup/hold aperture (T_setup + T_hold) around the active clock edge?",
                options = listOf(
                    "The internal cross-coupled inverters enter a metastable state with intermediate voltage levels for an unpredictable duration",
                    "The silicon substrate suffers irreversible dielectric breakdown",
                    "The clock buffer inverts its polarity due to charge accumulation",
                    "The transistor gate oxide develops negative bias temperature instability (NBTI)"
                ),
                correctIndex = 0,
                explanation = "Violating setup or hold timing traps the internal bistable latch on its unstable equilibrium point, producing an indeterminate voltage level for a stochastic duration."
            ),
            AssessmentQuestion(
                id = "diag_digital_9",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Static & Subthreshold Leakage",
                questionText = "As CMOS technology scales below 16 nm FinFET and GAA nodes, which leakage mechanism represents a major component of static power dissipation when transistors are nominally turned off?",
                options = listOf(
                    "Subthreshold leakage current (Id_sub) and gate dielectric quantum tunneling leakage",
                    "Hot-carrier injection into the field oxide",
                    "Ohmic resistance in interconnect copper vias",
                    "Inductive kickback from switching bond wires"
                ),
                correctIndex = 0,
                explanation = "Subthreshold leakage flows between drain and source even when Vgs < Vth, and quantum mechanical tunneling occurs across ultra-thin gate oxides."
            ),
            AssessmentQuestion(
                id = "diag_digital_10",
                domain = "Electronics and VLSI",
                subdomain = "Digital VLSI Design",
                topic = "Clock Tree Synthesis & Clock Skew",
                questionText = "In Clock Tree Synthesis (CTS), what is clock skew, and how does positive clock skew affect setup and hold timing margins?",
                options = listOf(
                    "Clock skew is the difference in arrival time of the clock edge at different flip-flops; positive skew relaxes setup timing margin but degrades hold timing margin",
                    "Clock skew is cycle-to-cycle frequency jitter; positive skew degrades both setup and hold equally",
                    "Clock skew is voltage drop across power rails; positive skew only affects static leakage",
                    "Clock skew is phase inversion across inverters; positive skew relaxes hold margin and degrades setup margin"
                ),
                correctIndex = 0,
                explanation = "Positive skew means capture clock edge arrives later than launch clock edge. This provides additional time for data to arrive (improving setup), but reduces margin before next data arrives (worsening hold)."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 3: FRONTEND DEVELOPMENT
    // ──────────────────────────────────────────────────────────────────────────
    val FRONTEND_DEV_DEFINITION = FocusAreaDefinition(
        id = "focus_frontend_dev",
        displayName = "Frontend Development",
        domain = "Computer Science and IT",
        technicalSpecialization = "Modern Web UI & React Applications",
        relevantConcepts = listOf(
            "Semantic HTML5 & Accessibility (WCAG / ARIA)",
            "CSS Modern Layouts (Flexbox, Grid, Responsive Media Queries)",
            "JavaScript Core: Execution Context, Event Loop & Closures",
            "Asynchronous JavaScript (Promises, async/await, fetch)",
            "DOM Manipulation, Event Bubbling & Delegation",
            "React Component Architecture & Props/State Dataflow",
            "React Hooks (useState, useEffect, useMemo, useCallback)",
            "Client-Side State Management (Context, Redux, Zustand)",
            "Browser Rendering Lifecycle & Critical Rendering Path",
            "Frontend Build Tooling & Performance Optimization"
        ),
        prerequisiteConcepts = listOf(
            "Basic Computer Literacy & Web Browsing",
            "Introductory Programming Logic & Variables",
            "Basic Client-Server HTTP Architecture"
        ),
        importantSkills = listOf(
            "Building responsive, mobile-first web layouts from UI mockups",
            "Managing component lifecycle and side-effects in React",
            "Debugging asynchronous API data fetching and error states",
            "Optimizing Core Web Vitals (LCP, FID, CLS)"
        ),
        expectedCompetencies = listOf(
            "Construct responsive, accessible web interfaces using HTML, CSS, and modern JS",
            "Build dynamic single-page applications using React and declarative state",
            "Diagnose re-rendering bottlenecks and optimize browser runtime performance"
        ),
        targetOutcomeSummary = "Build responsive, accessible, high-performance web applications using modern JavaScript and React",
        suggestedRoadmapTopics = listOf(
            "Semantic HTML5 & Modern CSS (Flexbox & CSS Grid)",
            "Modern JavaScript (ES6+, Scoping, Closures & Event Loop)",
            "Asynchronous JS: Promises, Async/Await & API Fetching",
            "React Fundamentals: Components, Props & Declarative State",
            "React Hooks: useEffect, Side-effects & Custom Hooks",
            "State Management & Client-Side Routing",
            "Web Performance Optimization & Accessibility (WCAG)"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_fe_1",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "CSS Layouts: Flexbox vs Grid",
                questionText = "In modern CSS, which layout model is primarily intended for one-dimensional layouts (a row OR a column) rather than two-dimensional grid layouts?",
                options = listOf(
                    "CSS Flexible Box Layout (Flexbox)",
                    "CSS Grid Layout",
                    "CSS Float and Clearfix positioning",
                    "CSS Table display"
                ),
                correctIndex = 0,
                explanation = "Flexbox is designed for one-dimensional layouts (arranging elements along either the main axis or cross axis), whereas CSS Grid is explicitly two-dimensional (rows and columns simultaneously)."
            ),
            AssessmentQuestion(
                id = "diag_fe_2",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "JavaScript Event Loop",
                questionText = "In the JavaScript runtime event loop, in what order are Promise microtasks executed relative to setTimeout macrotasks?",
                options = listOf(
                    "All resolved microtasks in the microtask queue execute before the next macrotask is dequeued",
                    "setTimeout callbacks always execute before Promise then() callbacks",
                    "Microtasks and macrotasks are interleaved with equal priority in a single FIFO queue",
                    "Promises execute synchronously and never enter the event loop"
                ),
                correctIndex = 0,
                explanation = "The event loop drains the entire microtask queue (Promises, queueMicrotask) immediately after the current synchronous script finishes, before processing the next macrotask (setTimeout, setInterval)."
            ),
            AssessmentQuestion(
                id = "diag_fe_3",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "DOM Event Delegation",
                questionText = "What mechanism allows a single event listener attached to a parent <ul> element to handle click events on dynamically added <li> children?",
                options = listOf(
                    "Event bubbling (propagation up the DOM tree from target to ancestors)",
                    "Event capture phase suppression",
                    "Shadow DOM encapsulation",
                    "DocumentType inheritance"
                ),
                correctIndex = 0,
                explanation = "Event delegation relies on event bubbling: when a child element is clicked, the event bubbles up through its ancestors, allowing the parent listener to inspect event.target."
            ),
            AssessmentQuestion(
                id = "diag_fe_4",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "React State Immutability",
                questionText = "In React, why must state updates in useState be performed immutably (e.g. creating a new object/array copy rather than modifying array.push)?",
                options = listOf(
                    "React relies on shallow reference equality (Object.is) to detect state changes and schedule re-renders",
                    "Direct mutation throws an uncatchable JavaScript TypeError in strict mode",
                    "Mutable arrays automatically reset to length zero during reconciler passes",
                    "React state variables are allocated in read-only WebAssembly memory"
                ),
                correctIndex = 0,
                explanation = "React performs shallow comparison on state references. If an array or object is mutated directly in-place, its reference remains identical, so React skips re-rendering."
            ),
            AssessmentQuestion(
                id = "diag_fe_5",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "React useEffect Dependency Array",
                questionText = "In a React function component, what happens if an empty dependency array [] is passed as the second argument to useEffect?",
                options = listOf(
                    "The effect runs once after the initial component mount and never on subsequent re-renders",
                    "The effect runs after every single render pass",
                    "The effect is treated as a syntax error and prevents compilation",
                    "The effect runs only when component unmounts"
                ),
                correctIndex = 0,
                explanation = "An empty dependency array [] tells React that the effect does not depend on any props or state values, so it executes exactly once after the initial DOM mount."
            ),
            AssessmentQuestion(
                id = "diag_fe_6",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "Responsive Web Design: Viewport Meta Tag",
                questionText = "What is the primary function of the HTML tag '<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">' in mobile web development?",
                options = listOf(
                    "Sets the virtual viewport width to the device's physical screen width and establishes a 1:1 scale ratio",
                    "Automatically downloads mobile-optimized CSS style sheets",
                    "Compresses all webpage images to fit mobile bandwidth constraints",
                    "Disables user scrolling on small screen devices"
                ),
                correctIndex = 0,
                explanation = "Without this viewport meta tag, mobile browsers assume a desktop page width (~980px) and scale down the rendered page. The tag aligns viewport width with device physical pixels."
            ),
            AssessmentQuestion(
                id = "diag_fe_7",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "Browser Rendering: Reflow vs Repaint",
                questionText = "Which DOM modification triggers a browser Reflow (Layout recalculation) rather than just a simple Repaint?",
                options = listOf(
                    "Changing an element's 'width' or 'display' property, altering geometry",
                    "Changing an element's 'color' or 'background-color' property",
                    "Changing an element's 'visibility' from visible to hidden without altering dimensions",
                    "Applying an opacity change on an element with hardware acceleration"
                ),
                correctIndex = 0,
                explanation = "Reflow occurs whenever geometric properties (width, height, margin, padding, display) change, forcing the browser to recalculate element positions and sizes across the document."
            ),
            AssessmentQuestion(
                id = "diag_fe_8",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "Web Accessibility (WCAG)",
                questionText = "Under WCAG 2.1 AA standards, what is the minimum contrast ratio required between normal text and its background?",
                options = listOf(
                    "4.5:1",
                    "3.0:1",
                    "7.0:1",
                    "2.0:1"
                ),
                correctIndex = 0,
                explanation = "WCAG 2.1 Level AA requires a contrast ratio of at least 4.5:1 for normal body text and 3:1 for large text (18pt or 14pt bold)."
            ),
            AssessmentQuestion(
                id = "diag_fe_9",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "React Keys in Lists",
                questionText = "Why does React require a unique, stable 'key' prop when rendering dynamic collections of elements in a list?",
                options = listOf(
                    "Allows the Virtual DOM diffing algorithm to identify which items have changed, been added, or been removed efficiently",
                    "Encrypts list item data for cross-origin security",
                    "Binds CSS pseudo-selectors to individual list nodes",
                    "Assigns native database primary keys to the browser cache"
                ),
                correctIndex = 0,
                explanation = "Keys give elements a stable identity across renders, allowing React's reconciliation engine to reuse existing DOM nodes rather than recreating the entire subtree."
            ),
            AssessmentQuestion(
                id = "diag_fe_10",
                domain = "Computer Science and IT",
                subdomain = "Frontend Development",
                topic = "JavaScript Closures",
                questionText = "In JavaScript, what is a closure?",
                options = listOf(
                    "A function bundled together with references to its surrounding lexical environment",
                    "A method to close network sockets when an HTTP request finishes",
                    "An asynchronous callback that executes only when an error occurs",
                    "A syntax error caused by unmatched curly braces"
                ),
                correctIndex = 0,
                explanation = "A closure gives an inner function access to its outer enclosing function's scope (variables) even after the outer function has finished executing."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 4: BACKEND DEVELOPMENT
    // ──────────────────────────────────────────────────────────────────────────
    val BACKEND_DEV_DEFINITION = FocusAreaDefinition(
        id = "focus_backend_dev",
        displayName = "Backend Development",
        domain = "Computer Science and IT",
        technicalSpecialization = "REST APIs, Databases & Server Architecture",
        relevantConcepts = listOf(
            "RESTful API Design & HTTP Status Codes (200, 201, 400, 401, 403, 404, 500)",
            "Relational Database Design & Normalization (1NF, 2NF, 3NF)",
            "SQL Query Optimization & Database Indexing (B-Tree, Hash)",
            "ACID Transactions & Concurrency Isolation Levels",
            "User Authentication & Authorization (JWT, OAuth2, Session Cookies)",
            "Password Hashing & Salting (Bcrypt, Argon2)",
            "In-Memory Caching Strategies (Redis, Memcached)",
            "Microservices Architecture & API Gateways",
            "Database Migrations & Schema Evolution",
            "Rate Limiting & Defensive Backend Security"
        ),
        prerequisiteConcepts = listOf(
            "Computer Networking & TCP/IP, DNS, HTTP/HTTPS",
            "Data Structures (Hash Maps, Trees, Graphs)",
            "Basic Operating System Concepts (Processes, Threads, I/O)"
        ),
        importantSkills = listOf(
            "Designing clean RESTful API contracts and request/response payloads",
            "Writing parameterized SQL queries to prevent SQL injection vulnerabilities",
            "Managing database connection pools and transactions safely",
            "Implementing rate limiters and token-bucket protection"
        ),
        expectedCompetencies = listOf(
            "Build secure, scalable RESTful web services with database persistence",
            "Design normalized relational schemas and optimize slow database queries",
            "Implement robust token-based authentication and role-based access control"
        ),
        targetOutcomeSummary = "Build scalable, secure REST APIs, services, and database-backed backend architectures",
        suggestedRoadmapTopics = listOf(
            "HTTP Protocols & RESTful API Architecture",
            "Relational Databases: SQL, Schema Normalization & Joins",
            "Database Indexing, B-Trees & Query Optimization",
            "ACID Transactions, Locking & Concurrency Control",
            "Authentication & Security: JWT, OAuth2 & Password Hashing",
            "Caching Strategies with Redis & Connection Pooling",
            "Scalable Microservices, API Gateways & Message Queues"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_be_1",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "REST HTTP Semantics",
                questionText = "Under standard RESTful API conventions, which HTTP method should be used to create a new resource at a server-determined URI, and what status code indicates successful creation?",
                options = listOf(
                    "POST, returning HTTP 201 Created",
                    "GET, returning HTTP 200 OK",
                    "PUT, returning HTTP 204 No Content",
                    "PATCH, returning HTTP 302 Found"
                ),
                correctIndex = 0,
                explanation = "POST is standard for creating subordinate resources, returning HTTP 201 Created with a Location header pointing to the newly generated resource URI."
            ),
            AssessmentQuestion(
                id = "diag_be_2",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Relational Indexing",
                questionText = "How does creating a B-Tree index on a frequently filtered column (e.g. 'email') improve SQL SELECT query execution time?",
                options = listOf(
                    "Reduces lookup time from O(N) full table scan to O(log N) tree traversal",
                    "Compresses table rows into RAM cache permanently",
                    "Converts all string lookups into bitwise AND operations",
                    "Guarantees zero lock contention on concurrent writes"
                ),
                correctIndex = 0,
                explanation = "A B-Tree index maintains sorted key pointers, allowing the database query planner to binary search the index in O(log N) time rather than sequentially scanning all N rows."
            ),
            AssessmentQuestion(
                id = "diag_be_3",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "ACID Database Transactions",
                questionText = "In relational databases, which ACID property guarantees that all operations within a transaction either succeed completely or are completely rolled back upon error?",
                options = listOf(
                    "Atomicity (All-or-Nothing)",
                    "Consistency",
                    "Isolation",
                    "Durability"
                ),
                correctIndex = 0,
                explanation = "Atomicity ensures that a multi-step transaction is treated as a single indivisible unit of work; if any step fails, the database rolls back to the pre-transaction state."
            ),
            AssessmentQuestion(
                id = "diag_be_4",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Authentication with JWT",
                questionText = "Why should sensitive secrets (such as user passwords or cryptographic private keys) NEVER be placed inside a standard JSON Web Token (JWT) payload?",
                options = listOf(
                    "The payload is merely Base64Url-encoded and can be trivially decoded by anyone who intercepts the token",
                    "JWT headers automatically overwrite payload fields during transport",
                    "JWT tokens cannot exceed 64 bytes in total size",
                    "Browsers reject JWTs containing alphabetical characters"
                ),
                correctIndex = 0,
                explanation = "A standard JWT is digitally signed for tamper-detection, NOT encrypted. The payload is readable plaintext encoded with Base64Url, visible to anyone holding the token."
            ),
            AssessmentQuestion(
                id = "diag_be_5",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Password Security",
                questionText = "Why is applying a single iteration of a fast cryptographic hash (such as MD5 or SHA-256) considered insecure for storing user passwords?",
                options = listOf(
                    "Fast hashes enable attackers to execute billions of guesses per second via precomputed rainbow tables and GPU cracking; adaptive salted algorithms (like Bcrypt or Argon2) are required",
                    "SHA-256 hashes cannot be compared for equality in relational databases",
                    "MD5 hashes reverse themselves automatically after 30 days",
                    "Modern operating systems disallow hashing strings longer than 8 characters"
                ),
                correctIndex = 0,
                explanation = "General-purpose hashes are engineered for speed, allowing brute-force cracking at gigahash rates. Password hashing requires adaptive, computationally intensive functions like Bcrypt or Argon2 with unique salts."
            ),
            AssessmentQuestion(
                id = "diag_be_6",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Caching Strategies (Cache-Aside)",
                questionText = "In the Cache-Aside (Lazy Loading) caching pattern, what occurs when an application encounters a 'Cache Miss'?",
                options = listOf(
                    "The application queries the database, writes the result to the cache for future requests, and returns the data to the client",
                    "The application returns HTTP 404 to the client immediately",
                    "The cache automatically purges all remaining records",
                    "The database enters read-only lock mode"
                ),
                correctIndex = 0,
                explanation = "In Cache-Aside, the app first checks the cache. On a miss, it fetches data from the primary database, populates the cache with the fetched record, and returns the response."
            ),
            AssessmentQuestion(
                id = "diag_be_7",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "SQL Injection Prevention",
                questionText = "What is the primary mechanism that makes Parameterized Queries (Prepared Statements) immune to SQL Injection attacks?",
                options = listOf(
                    "The database engine compiles the SQL query structure first, treating user parameters strictly as literal data rather than executable code",
                    "User inputs are automatically converted to uppercase before execution",
                    "All single quotes are replaced with null bytes",
                    "Database tables are made read-only during query evaluation"
                ),
                correctIndex = 0,
                explanation = "Prepared statements separate SQL syntax compilation from data binding. User parameters are transmitted separately and treated purely as literals, rendering injected SQL syntax inert."
            ),
            AssessmentQuestion(
                id = "diag_be_8",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Idempotency in HTTP Methods",
                questionText = "Under standard HTTP specifications, which of the following HTTP methods is defined as Idempotent (making multiple identical requests produces the same side-effect as a single request)?",
                options = listOf(
                    "PUT",
                    "POST",
                    "PATCH (when appending to a collection)",
                    "CONNECT"
                ),
                correctIndex = 0,
                explanation = "PUT is idempotent: replacing a resource with a given payload 1 time or 10 times results in the exact same server resource state. POST is not idempotent."
            ),
            AssessmentQuestion(
                id = "diag_be_9",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Database Connection Pooling",
                questionText = "Why do production backend servers utilize a Database Connection Pool rather than opening a new TCP database connection for every incoming HTTP request?",
                options = listOf(
                    "Establishing TCP connections, SSL handshakes, and authentication credentials for every request incurs massive latency and memory overhead",
                    "Relational databases reject connections from clients that open more than 1 socket per day",
                    "Connection pools bypass SQL query parsing completely",
                    "OS sockets automatically corrupt database transaction logs without pooling"
                ),
                correctIndex = 0,
                explanation = "Creating a database connection involves TCP handshakes, TLS negotiation, and authentication. Connection pooling maintains pre-warmed connections, reducing request latency."
            ),
            AssessmentQuestion(
                id = "diag_be_10",
                domain = "Computer Science and IT",
                subdomain = "Backend Development",
                topic = "Rate Limiting Algorithms",
                questionText = "Which rate-limiting algorithm allows brief bursts of traffic while enforcing a strict steady-state consumption rate by accumulating tokens at a fixed interval?",
                options = listOf(
                    "Token Bucket algorithm",
                    "Fixed Window Counter algorithm",
                    "Least Recently Used (LRU) algorithm",
                    "Round Robin scheduling"
                ),
                correctIndex = 0,
                explanation = "The Token Bucket algorithm accumulates tokens up to a capacity limit. Requests consume tokens, permitting temporary traffic bursts while bounding long-term average throughput."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 5: GENETIC ENGINEERING (Biotechnology)
    // ──────────────────────────────────────────────────────────────────────────
    val GENETIC_ENGINEERING_DEFINITION = FocusAreaDefinition(
        id = "focus_genetic_eng",
        displayName = "Genetic Engineering",
        domain = "Biotechnology",
        technicalSpecialization = "Recombinant DNA, Cloning & Gene Editing",
        relevantConcepts = listOf(
            "Central Dogma & DNA Molecular Structure",
            "Type II Restriction Endonucleases & Palindromic Cleavage",
            "Plasmids, Cloning Vectors, MCS & Origins of Replication",
            "DNA Ligase & Phosphodiester Bond Formation",
            "Polymerase Chain Reaction (PCR) & Primer Design",
            "Agarose Gel Electrophoresis & DNA Band Sizing",
            "CRISPR-Cas9 System & Protospacer Adjacent Motif (PAM)",
            "Bacterial Transformation & Antibiotic Selection Markers",
            "Recombinant Protein Expression & Promoters (lac, T7)",
            "Western Blotting, SDS-PAGE & Epitope Tags (His-tag, FLAG)"
        ),
        prerequisiteConcepts = listOf(
            "General Chemistry & Hydrogen Bonding",
            "Cell Biology & Prokaryotic vs Eukaryotic Gene Structure",
            "Enzymology & Protein Conformation"
        ),
        importantSkills = listOf(
            "Designing PCR primers with appropriate melting temperatures (Tm)",
            "Planning restriction enzyme digests and ligation stoichiometry",
            "Screening recombinant colonies via blue-white selection or colony PCR",
            "Designing guide RNAs (gRNA) with target PAM constraints"
        ),
        expectedCompetencies = listOf(
            "Construct recombinant DNA cloning vectors with selective markers",
            "Optimize PCR amplification cycles and resolve nucleic acid fragments",
            "Explain CRISPR-Cas9 targeted double-strand break and repair mechanisms"
        ),
        targetOutcomeSummary = "Understand and apply genetic engineering fundamentals, recombinant cloning, and CRISPR gene editing",
        suggestedRoadmapTopics = listOf(
            "DNA Structure, Enzymes & Restriction Endonucleases",
            "Plasmid Vectors, Multiple Cloning Sites & Selection Markers",
            "PCR Kinetics, Primer Optimization & Gel Electrophoresis",
            "Bacterial Transformation & Recombinant Screening",
            "CRISPR-Cas9 Targeted Genome Editing & PAM Recognition",
            "Recombinant Protein Expression & Affinity Purification",
            "Downstream Processing & Quality Validation"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_gene_1",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Restriction Endonucleases",
                questionText = "What structural characteristic is shared by the target recognition sequences of Type II restriction endonucleases such as EcoRI (5'-GAATTC-3') and HindIII (5'-AAGCTT-3')?",
                options = listOf(
                    "They are inverted palindromic sequences possessing twofold rotational symmetry",
                    "They consist exclusively of repetitive adenine and thymine poly-A tracts",
                    "They contain a mandatory protospacer adjacent motif (PAM) at the 3' terminus",
                    "They are non-specific single-stranded overhangs of alternating purines"
                ),
                correctIndex = 0,
                explanation = "Type II restriction endonucleases recognize palindromic dsDNA sequences with twofold rotational symmetry, cleaving either staggered sticky ends or blunt ends."
            ),
            AssessmentQuestion(
                id = "diag_gene_2",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "DNA Ligase & Phosphodiester Bonds",
                questionText = "During recombinant plasmid construction, which biochemical bond is catalyzed by T4 DNA ligase between adjacent 5'-phosphate and 3'-hydroxyl groups of duplex DNA?",
                options = listOf(
                    "Covalent phosphodiester bond requiring ATP hydrolysis",
                    "Hydrogen bond between complementary purine-pyrimidine base pairs",
                    "Disulfide bridge between cysteine amino acid residues",
                    "Glycosidic bond between deoxyribose and nitrogenous base"
                ),
                correctIndex = 0,
                explanation = "DNA ligase joins adjacent 3'-OH and 5'-PO4 termini in duplex DNA by synthesizing a phosphodiester bond using an enzyme-AMP intermediate powered by ATP."
            ),
            AssessmentQuestion(
                id = "diag_gene_3",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Plasmid Cloning Vectors",
                questionText = "What essential functional element in a cloning plasmid ensures that transformed host bacteria harboring the recombinant plasmid can be selectively isolated from non-transformed cells?",
                options = listOf(
                    "A selectable antibiotic resistance marker gene (such as ampR or kanR)",
                    "A multiple cloning site (MCS) containing unique restriction sites",
                    "A high-copy origin of replication (oriC or pUC ori)",
                    "A polyadenylation signal sequence"
                ),
                correctIndex = 0,
                explanation = "Selectable markers like ampicillin resistance (beta-lactamase gene) allow only successfully transformed bacteria to survive and form colonies on antibiotic-supplemented agar plates."
            ),
            AssessmentQuestion(
                id = "diag_gene_4",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Polymerase Chain Reaction (PCR)",
                questionText = "In standard Polymerase Chain Reaction (PCR), what occurs during the annealing step typically performed at temperatures between 50°C and 65°C?",
                options = listOf(
                    "Sequence-specific synthetic oligonucleotide primers hybridize to their complementary flanking sequences on single-stranded template DNA",
                    "Taq DNA polymerase synthesizes nascent DNA strands at maximum extension velocity",
                    "Thermal denaturation breaks inter-strand hydrogen bonds to separate dsDNA into ssDNA",
                    "Magnesium cofactors bind to dNTP substrates to terminate polymerase elongation"
                ),
                correctIndex = 0,
                explanation = "The annealing step allows forward and reverse oligonucleotide primers to specifically base-pair with complementary target sequences on denatured ssDNA strands."
            ),
            AssessmentQuestion(
                id = "diag_gene_5",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Agarose Gel Electrophoresis",
                questionText = "In agarose gel electrophoresis under a uniform electric field, why do linear DNA fragments migrate toward the positive anode (red electrode)?",
                options = listOf(
                    "DNA possesses a uniform negative charge-to-mass ratio due to its repeating phosphate backbone",
                    "DNA is positively charged due to basic nitrogenous purine and pyrimidine rings",
                    "Ethidium bromide stains impart a strong negative net polarization to nucleic acids",
                    "Agarose fibers selectively pump uncharged biomolecules toward the anode"
                ),
                correctIndex = 0,
                explanation = "Each nucleotide contains a negatively charged phosphate group. DNA has a constant charge-to-mass ratio and migrates toward the positive anode, separated inversely by log of molecular size."
            ),
            AssessmentQuestion(
                id = "diag_gene_6",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "CRISPR-Cas9 & PAM Motifs",
                questionText = "What strict requirement must be adjacent to the target genomic DNA sequence for the Streptococcus pyogenes Cas9 nuclease to bind and induce a double-strand break?",
                options = listOf(
                    "A Protospacer Adjacent Motif (PAM) sequence with the consensus 5'-NGG-3' immediately downstream of the 20-nucleotide protospacer",
                    "A continuous 100-base-pair poly-A tail flanking the cleavage locus",
                    "A methylated adenine residue catalyzed by dam methyltransferase",
                    "An inverted terminal repeat sequence recognized by transposase"
                ),
                correctIndex = 0,
                explanation = "SpCas9 strictly requires a 5'-NGG-3' PAM motif immediately following the target protospacer on the non-target strand to enable R-loop formation and endonuclease activation."
            ),
            AssessmentQuestion(
                id = "diag_gene_7",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Bacterial Transformation",
                questionText = "In heat-shock chemical transformation of Escherichia coli using calcium chloride (CaCl2), what is the mechanistic role of divalent Ca2+ ions?",
                options = listOf(
                    "Ca2+ ions shield electrostatic repulsion between negatively charged DNA phosphate backbones and negatively charged cell membrane lipopolysaccharides",
                    "Ca2+ ions hydrolyze bacterial peptidoglycan cell walls to create permanent macro-pores",
                    "Ca2+ acts as an allosteric activator of endogenous DNA polymerase",
                    "Ca2+ activates the lac operon to initiate transcription of foreign plasmids"
                ),
                correctIndex = 0,
                explanation = "Both DNA and the bacterial outer membrane are negatively charged. Divalent Ca2+ cations coordinate and neutralize these charges, allowing DNA to enter during heat shock."
            ),
            AssessmentQuestion(
                id = "diag_gene_8",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Blue-White Colony Screening",
                questionText = "In alpha-complementation blue-white colony screening with pUC plasmids, why do recombinant colonies containing an insert in the multiple cloning site appear white on X-gal agar?",
                options = listOf(
                    "Foreign DNA insertion disrupts the lacZ-alpha open reading frame, preventing functional beta-galactosidase assembly and X-gal chromogenic cleavage",
                    "The foreign insert encodes an inhibitory peptide that blocks X-gal transport into the cell",
                    "White colonies have lost the ampicillin resistance gene due to homologous recombination",
                    "Recombinant bacteria secrete catalase which bleaches the indigo dye product"
                ),
                correctIndex = 0,
                explanation = "Inserting DNA into the MCS interrupts the lacZ-alpha peptide coding sequence. Without alpha-complementation, active beta-galactosidase is not produced and colonies remain white."
            ),
            AssessmentQuestion(
                id = "diag_gene_9",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Recombinant Expression Systems",
                questionText = "Why does the T7 RNA polymerase expression system (such as pET vectors in BL21(DE3) hosts) produce extraordinarily high yields of recombinant target protein?",
                options = listOf(
                    "T7 RNA polymerase is highly selective for its cognate T7 promoter and transcribes DNA several times faster than host bacterial RNA polymerase",
                    "T7 vectors eliminate eukaryotic introns via post-transcriptional spliceosomes",
                    "T7 polymerase suppresses all bacterial proteases through competitive inhibition",
                    "The T7 promoter incorporates rare tRNA codons into nascent polypeptide chains"
                ),
                correctIndex = 0,
                explanation = "Bacteriophage T7 RNA polymerase is extremely processive, specifically transcribing from the T7 promoter at high speed while host E. coli RNA polymerase ignores it."
            ),
            AssessmentQuestion(
                id = "diag_gene_10",
                domain = "Biotechnology",
                subdomain = "Genetic Engineering",
                topic = "Affinity Chromatography & Epitope Tags",
                questionText = "What is the biochemical principle behind isolating polyhistidine-tagged (6xHis) recombinant fusion proteins using Immobilized Metal Affinity Chromatography (IMAC)?",
                options = listOf(
                    "Histidine imidazole rings coordinate with transition metal ions (such as Ni2+ or Co2+) chelated on the agarose resin, eluted competitively by free imidazole",
                    "Histidine residues form covalent disulfide bonds with thiol sepharose matrices",
                    "Polyhistidine tags bind specifically to immobilized protein A on the column",
                    "The 6xHis tag is precipitated out of solution by ammonium sulfate fractionation"
                ),
                correctIndex = 0,
                explanation = "The electron-donor nitrogen atoms in histidine imidazole rings coordinate with chelated Ni2+ or Co2+ ions on the IMAC matrix. Free imidazole elutes the protein by competing for binding sites."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 6: FOOD PRESERVATION (Food Technology)
    // ──────────────────────────────────────────────────────────────────────────
    val FOOD_PRESERVATION_DEFINITION = FocusAreaDefinition(
        id = "focus_food_preservation",
        displayName = "Food Preservation",
        domain = "Food Technology",
        technicalSpecialization = "Thermal Kinetics, Water Activity & Packaging",
        relevantConcepts = listOf(
            "Thermal Processing & HTST Pasteurization (Alkaline Phosphatase test)",
            "Thermal Death Kinetics: Decimal Reduction Time (D-value)",
            "Thermal Resistance Constant (z-value) & Sterilization Value (F0)",
            "Water Activity (aw) Thresholds & Clostridium botulinum Control",
            "Modified Atmosphere Packaging (MAP) & CO2 Antimicrobial Action",
            "Low-Temperature Preservation & Cryogenic Fast Freezing",
            "Spray Drying, Amorphous States & Glass Transition Temperature (Tg)",
            "Non-Enzymatic Browning: Maillard Reaction Condensation",
            "Codex Alimentarius HACCP Principles & Critical Control Points (CCPs)",
            "Homofermentative vs Heterofermentative Lactic Acid Fermentation"
        ),
        prerequisiteConcepts = listOf(
            "Introductory Organic Chemistry & Biochemistry",
            "Microbiology & Bacterial Endospore Structure",
            "Basic Heat Transfer & Mass Balance"
        ),
        importantSkills = listOf(
            "Calculating microbial log reductions using D-values and thermal exposure",
            "Measuring and adjusting food water activity to prevent pathogen proliferation",
            "Establishing Critical Control Points (CCPs) in manufacturing HACCP plans",
            "Evaluating shelf-life stability and barrier packaging specifications"
        ),
        expectedCompetencies = listOf(
            "Apply thermal destruction kinetics to calculate pasteurization and canning cycles",
            "Prevent foodborne botulism and spoilage through water activity hurdle technology",
            "Design modified atmosphere packaging systems to inhibit aerobic mold growth"
        ),
        targetOutcomeSummary = "Master food preservation techniques, thermal inactivation kinetics, and HACCP quality systems",
        suggestedRoadmapTopics = listOf(
            "Thermal Processing Kinetics: D, z, and F0 Sterilization Values",
            "Water Activity (aw) Control & Microbial Growth Hurdle Technology",
            "Low-Temperature Preservation: Cryogenic Freezing & Ice Nucleation",
            "Modified Atmosphere Packaging (MAP) & Barrier Films",
            "HACCP System Design, Critical Limits & Monitoring",
            "Food Dehydration, Spray Drying & Glass Transition Physics",
            "Controlled Food Fermentation & Quality Assurance"
        ),
        standardQuestions = listOf(
            AssessmentQuestion(
                id = "diag_food_1",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Decimal Reduction Time (D-value)",
                questionText = "In thermal food processing kinetics, how is the Decimal Reduction Time (D-value) defined for a specific microorganism at a constant reference temperature?",
                options = listOf(
                    "The heating time in minutes required to reduce the viable microbial population by 90% (a 1-log cycle reduction) at that temperature",
                    "The temperature increase in degrees Celsius required to reduce thermal death time by 90%",
                    "The total exposure time required to achieve complete commercial sterility (100% destruction)",
                    "The time required for an endospore to germinate and double its cell mass"
                ),
                correctIndex = 0,
                explanation = "D-value is the heating time in minutes at temperature T required to reduce viable microbial numbers by 1 log (from N0 to 0.1*N0, a 90% destruction)."
            ),
            AssessmentQuestion(
                id = "diag_food_2",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Thermal Resistance Constant (z-value)",
                questionText = "If Clostridium botulinum endospores have a D121°C value of 0.25 minutes and a z-value of 10°C, what is the D-value when processing temperature is reduced to 111°C?",
                options = listOf(
                    "2.5 minutes (D-value increases by a factor of 10 for every z-value decrease in temperature)",
                    "0.025 minutes (D-value decreases by a factor of 10)",
                    "1.25 minutes (D-value increases proportionally by 5x)",
                    "0.50 minutes (D-value doubles)"
                ),
                correctIndex = 0,
                explanation = "The z-value is the temperature change that causes a 10-fold change in D-value. Dropping temperature by one z-value (121°C - 10°C = 111°C) increases D by 10x: 0.25 * 10 = 2.5 minutes."
            ),
            AssessmentQuestion(
                id = "diag_food_3",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "12D Botulinum Cook (F0 Value)",
                questionText = "In industrial canning of low-acid foods (pH > 4.6), why is the mandatory '12D concept' applied targeting Clostridium botulinum endospores (F0 ≈ 2.5 to 3.0 min)?",
                options = listOf(
                    "To reduce the probability of spore survival to less than 1 in 10^12 containers, ensuring public health safety against deadly botulinum neurotoxin",
                    "To prevent non-enzymatic Maillard browning during room temperature warehouse storage",
                    "To completely deactivate thermophilic flat-sour sporeformers such as Geobacillus stearothermophilus",
                    "To dissolve connective collagen tissue in canned meat products"
                ),
                correctIndex = 0,
                explanation = "Low-acid canned foods can support Clostridium botulinum germination and toxin production. A 12D process provides a public health safety margin ensuring a 10^-12 survival probability."
            ),
            AssessmentQuestion(
                id = "diag_food_4",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Water Activity (aw) Thresholds",
                questionText = "What is the critical minimum water activity (aw) threshold below which Clostridium botulinum cannot germinate or produce neurotoxin in food products?",
                options = listOf(
                    "aw = 0.935 (under standard conditions; aw < 0.85 halts all pathogenic bacterial growth including Staphylococcus aureus)",
                    "aw = 0.600 (the survival limit for xerophilic molds and osmophilic yeasts)",
                    "aw = 0.990 (the moisture level of pure water)",
                    "aw = 0.750 (the limit for halophilic bacteria in brine)"
                ),
                correctIndex = 0,
                explanation = "Clostridium botulinum growth is inhibited at water activity below 0.935 (Group I proteolytic) and 0.97 (Group II non-proteolytic). S. aureus can grow down to 0.85 aw."
            ),
            AssessmentQuestion(
                id = "diag_food_5",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "HTST Milk Pasteurization",
                questionText = "In continuous commercial milk pasteurization (HTST), which heat-sensitive endogenous enzyme's complete inactivation is verified as the legal benchmark indicator for adequate pasteurization?",
                options = listOf(
                    "Alkaline Phosphatase (ALP)",
                    "Alpha-Amylase",
                    "Lipoxygenase",
                    "Polyphenol Oxidase"
                ),
                correctIndex = 0,
                explanation = "Alkaline phosphatase has thermal heat resistance slightly greater than Mycobacterium tuberculosis and Coxiella burnetii. Absence of ALP activity confirms successful pasteurization."
            ),
            AssessmentQuestion(
                id = "diag_food_6",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Modified Atmosphere Packaging (MAP)",
                questionText = "In Modified Atmosphere Packaging (MAP) of fresh refrigerated foods, what is the primary antimicrobial mechanism of elevated carbon dioxide (CO2, typically 20-40%) concentrations?",
                options = listOf(
                    "CO2 dissolves into the aqueous phase forming carbonic acid, lowering intracellular pH and altering cell membrane permeability of Gram-negative aerobic psychrotrophs",
                    "CO2 acts as an antioxidant by reducing ferric iron to ferrous iron in myoglobin",
                    "CO2 catalyzes the cross-linking of packaging polymer barriers",
                    "CO2 accelerates ethylene gas emission to retard plant senescence"
                ),
                correctIndex = 0,
                explanation = "Dissolved CO2 forms H2CO3, penetrating bacterial membranes, acidifying internal pH, and specifically inhibiting aerobic spoilage bacteria like Pseudomonas."
            ),
            AssessmentQuestion(
                id = "diag_food_7",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Cryogenic Fast Freezing",
                questionText = "Why does cryogenic fast freezing using liquid nitrogen (-196°C) preserve food texture and minimize drip loss upon thawing significantly better than slow blast freezing?",
                options = listOf(
                    "Rapid heat extraction promotes rapid ice crystal nucleation across intracellular and extracellular compartments, producing microscopic ice crystals that do not rupture cell walls",
                    "Cryogenic temperatures denature pectin methylesterase to stabilize fruit cell walls",
                    "Liquid nitrogen chemically combines with free water to form stable clathrate hydrates",
                    "Slow blast freezing causes glass transitions that prevent water molecule mobility"
                ),
                correctIndex = 0,
                explanation = "Slow freezing creates large extracellular ice crystals that draw water out by osmosis and rupture cell walls. Ultra-fast freezing forms tiny, uniformly distributed ice crystals, preserving cellular integrity."
            ),
            AssessmentQuestion(
                id = "diag_food_8",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Hurdle Technology Synergy",
                questionText = "How does Leistner's 'Hurdle Technology' achieve microbiologically stable and shelf-stable foods without severe sensory and nutritional degradation from extreme single treatments?",
                options = listOf(
                    "Combines multiple sub-lethal preservation parameters (e.g. mild heat, pH reduction, aw reduction, redox potential Eh, and packaging) which act synergistically against microbial homeostasis",
                    "Alternates between ionizing gamma radiation and high hydrostatic pressure processing",
                    "Exposes food to 100°C steam pulses followed by vacuum centrifugation",
                    "Eliminates all food carbohydrates to starve spoilage microorganisms"
                ),
                correctIndex = 0,
                explanation = "Hurdle technology applies intelligent combinations of mild hurdles (e.g. F, t, aw, pH, Eh, preservatives) that exhaust microbial energy reserves trying to maintain homeostasis, ensuring safety with minimal quality loss."
            ),
            AssessmentQuestion(
                id = "diag_food_9",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "Blanching Enzyme Inactivation",
                questionText = "Before freezing or dehydrating vegetables, what is the primary objective of the short thermal blanching pre-treatment (typically 70°C to 100°C for 1 to 5 minutes)?",
                options = listOf(
                    "Inactivate endogenous quality-degrading enzymes (such as peroxidase, catalase, and polyphenol oxidase) that cause off-flavors, discoloration, and nutrient loss during frozen storage",
                    "Completely sterilize the interior tissue against all bacterial endospores",
                    "Extract soluble vitamins and starches to reduce calorie density",
                    "Gelatinize cellulose microfibrils to harden the plant cuticle"
                ),
                correctIndex = 0,
                explanation = "Vegetables contain endogenous enzymes (catalase, peroxidase, lipoxygenase) that remain active even at freezing temperatures (-18°C), causing unpalatable off-flavors and browning unless inactivated by blanching."
            ),
            AssessmentQuestion(
                id = "diag_food_10",
                domain = "Food Technology",
                subdomain = "Food Preservation",
                topic = "HACCP Critical Control Points (CCPs)",
                questionText = "Under Codex Alimentarius HACCP principles, how is a Critical Control Point (CCP) distinguished from a standard prerequisite Sanitation Standard Operating Procedure (SSOP)?",
                options = listOf(
                    "A CCP is a specific operational step at which control can be applied and is essential to prevent, eliminate, or reduce an identified food safety hazard to an acceptable critical limit",
                    "A CCP applies only to non-food contact surfaces and employee hygiene stations",
                    "A CCP is a voluntary marketing certification renewed annually by retail distributors",
                    "A CCP is any step where food temperature exceeds 100°C for more than 10 minutes"
                ),
                correctIndex = 0,
                explanation = "Principle 2 of HACCP defines a CCP as a step where control is essential to prevent or eliminate a food safety hazard or reduce it to an acceptable level, guarded by measurable critical limits."
            )
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 7: DATA STRUCTURES & ALGORITHMS (CS)
    // ──────────────────────────────────────────────────────────────────────────
    val DSA_DEFINITION = FocusAreaDefinition(
        id = "focus_dsa",
        displayName = "Data Structures and Algorithms",
        domain = "Computer Science and IT",
        technicalSpecialization = "Algorithmic Efficiency & Complex Problem Solving",
        relevantConcepts = listOf(
            "Asymptotic Complexity & Big-O Notation",
            "Arrays, Linked Lists & Dynamic Resizing",
            "Hash Tables, Hash Functions & Collision Resolution",
            "Binary Trees, BSTs & Self-Balancing Trees (AVL, Red-Black)",
            "Graph Representations (Adjacency List/Matrix)",
            "Graph Traversal: BFS, DFS & Topological Sort",
            "Shortest Path Algorithms: Dijkstra, Bellman-Ford",
            "Dynamic Programming: Memoization & Tabulation",
            "Greedy Algorithms & Divide-and-Conquer",
            "Sorting Algorithms: QuickSort, MergeSort, HeapSort"
        ),
        prerequisiteConcepts = listOf(
            "Basic Programming Syntax, Loops & Recursion",
            "Discrete Mathematics (Sets, Functions, Logic)"
        ),
        importantSkills = listOf(
            "Analyzing time and space complexity of novel code",
            "Choosing optimal data structures based on access/insert trade-offs",
            "Formulating recurrence relations for dynamic programming"
        ),
        expectedCompetencies = listOf(
            "Design efficient algorithms with optimal Big-O bounds",
            "Traverse and manipulate non-linear graph and tree structures"
        ),
        targetOutcomeSummary = "Master core data structures, algorithmic design patterns, and complexity optimization",
        suggestedRoadmapTopics = listOf(
            "Algorithm Analysis (Big-O) & Fundamental Linear Data Structures",
            "Divide & Conquer, Sorting & Binary Search Algorithms",
            "Hash Tables & Hash-Based Problem Solving",
            "Trees: BSTs, Traversal & Balanced Trees",
            "Graphs: BFS, DFS & Topological Sorting",
            "Shortest Path & Minimum Spanning Trees",
            "Dynamic Programming: State Formulations & Optimizations"
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 8: MACHINE LEARNING (CS / AI)
    // ──────────────────────────────────────────────────────────────────────────
    val ML_DEFINITION = FocusAreaDefinition(
        id = "focus_ml",
        displayName = "Machine Learning",
        domain = "Computer Science and IT",
        technicalSpecialization = "Predictive Modeling & Statistical Learning",
        relevantConcepts = listOf(
            "Supervised vs Unsupervised vs Reinforcement Learning",
            "Linear & Logistic Regression, Cost Functions",
            "Gradient Descent & Optimization (SGD, Adam)",
            "Overfitting, Underfitting & Bias-Variance Trade-off",
            "Regularization Techniques (L1 Lasso, L2 Ridge)",
            "Decision Trees, Random Forests & Gradient Boosting",
            "Model Evaluation Metrics (Precision, Recall, F1, ROC-AUC)",
            "Neural Network Fundamentals & Backpropagation",
            "Dimensionality Reduction (PCA, t-SNE)",
            "On-Device Quantization & Model Pruning"
        ),
        prerequisiteConcepts = listOf(
            "Multivariable Calculus (Partial Derivatives, Gradients)",
            "Linear Algebra (Vectors, Matrices, Dot Products)",
            "Probability & Statistics (Distributions, Expectation)"
        ),
        importantSkills = listOf(
            "Feature engineering and data preprocessing pipelines",
            "Training and tuning hyperparameters with cross-validation",
            "Evaluating model performance and diagnosing overfitting"
        ),
        expectedCompetencies = listOf(
            "Build and evaluate statistical models for classification and regression",
            "Optimize neural network parameters using gradient descent and backpropagation"
        ),
        targetOutcomeSummary = "Build, train, evaluate, and optimize machine learning models and neural networks",
        suggestedRoadmapTopics = listOf(
            "Linear Algebra & Gradient Descent Optimization",
            "Supervised Learning: Regression, Classification & Regularization",
            "Tree-Based Ensembles: Random Forests & XGBoost",
            "Unsupervised Learning & Dimensionality Reduction (PCA)",
            "Neural Networks, Loss Functions & Backpropagation",
            "Model Deployment, Quantization & Edge Inference"
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 9: CORPORATE FINANCE (Management)
    // ──────────────────────────────────────────────────────────────────────────
    val FINANCE_DEFINITION = FocusAreaDefinition(
        id = "focus_finance",
        displayName = "Corporate Financial Analysis",
        domain = "Management & Commerce",
        technicalSpecialization = "Valuation, Capital Budgeting & Financial Modeling",
        relevantConcepts = listOf(
            "Time Value of Money (PV, FV, Discounting)",
            "Financial Statement Analysis (Balance Sheet, Income, Cash Flow)",
            "Capital Budgeting Metrics (NPV, IRR, Payback)",
            "Weighted Average Cost of Capital (WACC)",
            "DuPont Return on Equity (ROE) Decomposition",
            "Capital Asset Pricing Model (CAPM) & Beta",
            "Discounted Cash Flow (DCF) Valuation & Free Cash Flow (FCFF)",
            "Working Capital Ratios & Liquidity Analysis",
            "Market Multiples (EV/EBITDA, P/E ratio)",
            "Financial Hedging with Forwards and Futures"
        ),
        prerequisiteConcepts = listOf(
            "Basic Accounting Principles & Debit/Credit Logic",
            "Microeconomics (Supply, Demand, Elasticity)"
        ),
        importantSkills = listOf(
            "Constructing multi-year discounted cash flow models",
            "Evaluating capital project profitability using NPV criteria",
            "Decomposing operational profitability drivers via DuPont analysis"
        ),
        expectedCompetencies = listOf(
            "Perform enterprise DCF and comparable multiple corporate valuations",
            "Calculate after-tax cost of capital and optimize financing mix"
        ),
        targetOutcomeSummary = "Master corporate valuation, financial statement modeling, and capital budgeting",
        suggestedRoadmapTopics = listOf(
            "Time Value of Money & Discounted Cash Flows",
            "Financial Statement Modeling & DuPont Analysis",
            "Capital Budgeting: NPV & Investment Decision Rules",
            "Cost of Capital (WACC) & Capital Structure Decisions",
            "Enterprise DCF Valuation & Free Cash Flow Forecasting",
            "Comparable Market Multiples & Transaction Analysis"
        )
    )

    // ──────────────────────────────────────────────────────────────────────────
    // DEFINITION 10: UI/UX & PRODUCT DESIGN
    // ──────────────────────────────────────────────────────────────────────────
    val UI_UX_DEFINITION = FocusAreaDefinition(
        id = "focus_ui_ux",
        displayName = "User Research & Interaction Design",
        domain = "UI/UX and Product Design",
        technicalSpecialization = "Usability, Accessibility & Design Systems",
        relevantConcepts = listOf(
            "User Research Methodologies & Persona Synthesis",
            "Customer Journey Mapping & Empathy Maps",
            "Information Architecture & Wireframing",
            "WCAG 2.1 Accessibility & Color Contrast Standards",
            "Design Tokens (Color, Typography, Spacing)",
            "Component Libraries & Reusable UI Systems",
            "Interactive Prototyping & Micro-interactions",
            "Usability Testing & Task Completion Metrics",
            "Heuristic Evaluation (Nielsen Norman 10 Heuristics)",
            "Design-to-Engineering Handoff & Specifications"
        ),
        prerequisiteConcepts = listOf(
            "Visual Hierarchy & Basic Composition Principles",
            "Empathy & Human-Centered Design Thinking"
        ),
        importantSkills = listOf(
            "Conducting qualitative user interviews and synthesizing insights",
            "Auditing web interfaces for WCAG 2.1 AA accessibility compliance",
            "Architecting scalable design token systems for design handoff"
        ),
        expectedCompetencies = listOf(
            "Transform user research into accessible, high-fidelity UI systems",
            "Conduct formal heuristic reviews and usability testing sessions"
        ),
        targetOutcomeSummary = "Design accessible, user-centered digital interfaces and scalable component systems",
        suggestedRoadmapTopics = listOf(
            "Human-Centered Design Thinking & User Research Methods",
            "Information Architecture, User Flows & Wireframing",
            "WCAG 2.1 Accessibility Principles & Contrast Standards",
            "Design Systems, Tokens & Component Architecture",
            "Interactive Prototyping & Usability Testing Metrics"
        )
    )
}

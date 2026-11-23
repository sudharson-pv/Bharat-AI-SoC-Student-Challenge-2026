package com.learnmate.ai.data.taxonomy

import com.learnmate.ai.data.db.AssessmentQuestionEntity
import com.learnmate.ai.data.db.VerifiedTopicEntity
import com.learnmate.ai.domain.DomainCategory

/**
 * Domain & Topic Taxonomy containing verified educational curriculum.
 * Safeguard: Provides authoritative textbook facts cleanly separated from AI model outputs.
 * All entries carry traceable academic citations (author, title, edition, chapter, ISBN/standard).
 */
object TopicTaxonomy {

    fun normalizeDomain(domain: String): String {
        val d = domain.trim().lowercase()
        return when {
            d.contains("computer") || d.contains("software") || d.contains("ai") || d.contains("algorithm") || d == "cs_it" -> "Computer Science and IT"
            d.contains("electronic") || d.contains("vlsi") || d == "electronics_vlsi" -> "Electronics and VLSI"
            d.contains("biotech") || d.contains("molecular") || d.contains("genetic") || d == "biotech" -> "Biotechnology"
            d.contains("food") || d == "food_tech" -> "Food Technology"
            d.contains("ui") || d.contains("ux") || d.contains("product design") || d == "ui_ux" -> "UI/UX and Product Design"
            d.contains("mechanical") || d == "mech_eng" -> "Mechanical Engineering"
            (d.contains("science") || d.contains("school") || d.contains("physics") || d.contains("chemistry") || d.contains("secondary")) &&
                !d.contains("computer") && !d.contains("data") -> "School Science"
            else -> domain.trim()
        }
    }

    val DOMAIN_CATEGORIES: List<DomainCategory> = listOf(
        DomainCategory(
            id = "electronics_vlsi",
            title = "Electronics and VLSI",
            description = "Semiconductors, digital logic synthesis, CMOS circuits, and ASIC design",
            iconName = "memory",
            subdomains = listOf("Digital VLSI & RTL", "Semiconductor Physics", "Physical Design"),
            coreTopics = listOf(
                "PN Junction Physics & Diodes",
                "CMOS Inverter & Switching Characteristics",
                "Static Timing Analysis (Setup & Hold)"
            )
        ),
        DomainCategory(
            id = "cs_it",
            title = "Computer Science and IT",
            description = "Algorithms, software engineering, databases, and on-device AI",
            iconName = "code",
            subdomains = listOf("Software Engineering", "Data Structures & Algorithms", "Edge AI"),
            coreTopics = listOf(
                "Algorithmic Complexity & Big-O",
                "Binary Search & Hash Tables",
                "On-Device Model Quantization"
            )
        ),
        DomainCategory(
            id = "ui_ux",
            title = "UI/UX and Product Design",
            description = "User research, accessibility standards, design tokens, and usability",
            iconName = "palette",
            subdomains = listOf("User Research", "Accessibility & WCAG", "Design Systems"),
            coreTopics = listOf(
                "User Personas & Journey Mapping",
                "WCAG Accessibility & Color Contrast",
                "Design Tokens & Component Libraries"
            )
        ),
        DomainCategory(
            id = "mech_eng",
            title = "Mechanical Engineering",
            description = "Thermodynamics, mechanics of materials, heat transfer, and manufacturing",
            iconName = "settings",
            subdomains = listOf("Materials Mechanics", "Thermodynamics", "Heat Transfer"),
            coreTopics = listOf(
                "Stress, Strain & Hooke's Law",
                "First & Second Laws of Thermodynamics",
                "Heat Exchangers & Conduction"
            )
        ),
        DomainCategory(
            id = "biotechnology",
            title = "Biotechnology",
            description = "Molecular biology, recombinant DNA, PCR, genome editing, and bioprocess engineering",
            iconName = "biotech",
            subdomains = listOf("Molecular Biology", "Genetic Engineering", "Bioprocess Engineering"),
            coreTopics = listOf(
                "Recombinant DNA Technology & Restriction Cloning",
                "PCR Amplification & Gel Electrophoresis",
                "CRISPR-Cas9 Targeted Genome Editing"
            )
        ),
        DomainCategory(
            id = "food_tech",
            title = "Food Technology",
            description = "Food processing, preservation kinetics, water activity, and HACCP quality assurance",
            iconName = "restaurant",
            subdomains = listOf("Food Preservation", "Food Safety & Quality", "Food Engineering"),
            coreTopics = listOf(
                "Thermal Processing & Pasteurization (D & z Values)",
                "Water Activity (aw) Control & Microbial Stability",
                "HACCP Food Safety Principles & Critical Limits"
            )
        ),
        DomainCategory(
            id = "school_science",
            title = "School Science",
            description = "Secondary chemistry, Newtonian physics, stoichiometry, and foundational science",
            iconName = "science",
            subdomains = listOf("Secondary Chemistry", "Secondary Physics", "Cellular Biology"),
            coreTopics = listOf(
                "Chemical Reactions, Balancing & Stoichiometry",
                "Newtonian Mechanics & Force Equilibrium",
                "Acids, Bases, Salts and pH Calculation"
            )
        )
    )

    fun getVerifiedTopics(): List<VerifiedTopicEntity> = listOf(
        // ── Electronics and VLSI ─────────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_pn_junction",
            domain = "Electronics and VLSI",
            subdomain = "Semiconductor Physics",
            title = "PN Junction Physics & Diodes",
            description = "Principles of depletion regions, carrier diffusion, built-in potential, and forward/reverse bias.",
            verifiedExplanation = "A p-n junction diode is formed by joining p-type and n-type semiconductor materials within a single crystal. At equilibrium, free carriers diffuse across the metallurgical junction (holes from p to n, electrons from n to p), leaving behind uncompensated ionized dopant atoms. This forms an electrically neutral space-charge depletion region with a built-in electric field and potential barrier (typically ~0.7V for Silicon at 300K). Under forward bias (p-side positive), the barrier height is reduced, enabling exponential diffusion current flow. Under reverse bias, the barrier height increases, restricting current to a negligible minority carrier drift saturation current until avalanche or Zener breakdown occurs.",
            coreKeywordsJson = "[\"depletion region\", \"built-in potential\", \"forward bias\", \"diffusion current\", \"drift current\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Physics of Semiconductor Devices",
            referenceAuthor = "S.M. Sze & Kwok K. Ng",
            referenceEdition = "3rd Edition",
            referenceChapter = "2",
            referenceSection = "2.1-2.4",
            referenceIdentifier = "ISBN: 978-0471143239"
        ),
        VerifiedTopicEntity(
            topicId = "topic_cmos_inverter",
            domain = "Electronics and VLSI",
            subdomain = "Digital VLSI & RTL",
            title = "CMOS Inverter & Switching Characteristics",
            description = "Complementary MOS transistor pairing, static power consumption, and voltage transfer curve.",
            verifiedExplanation = "A CMOS inverter pairs an nMOS pull-down transistor with a pMOS pull-up transistor. When the input is high (VDD), the nMOS is on and pMOS is off, pulling the output cleanly to Ground (0V). When the input is low (GND), the pMOS is on and nMOS is off, pulling the output to VDD. Static power dissipation is virtually zero, occurring only through sub-threshold leakage, while dynamic power is consumed during switching due to capacitive charging/discharging and short-circuit crowbar current.",
            coreKeywordsJson = "[\"nMOS\", \"pMOS\", \"voltage transfer characteristic\", \"noise margins\", \"dynamic power\"]",
            prerequisiteTopicIdsJson = "[\"topic_pn_junction\"]",
            estimatedMinutes = 40,
            referenceTitle = "CMOS VLSI Design: A Circuits and Systems Perspective",
            referenceAuthor = "Neil H.E. Weste & David Money Harris",
            referenceEdition = "4th Edition",
            referenceChapter = "1",
            referenceSection = "1.4-1.6",
            referenceIdentifier = "ISBN: 978-0321547743"
        ),
        VerifiedTopicEntity(
            topicId = "topic_sta",
            domain = "Electronics and VLSI",
            subdomain = "Physical Design",
            title = "Static Timing Analysis (Setup & Hold)",
            description = "Propagation delay calculations, clock skew, setup time, and hold time verification without dynamic simulation.",
            verifiedExplanation = "Static Timing Analysis (STA) computes the delays across all electrical paths in a synchronous digital circuit without dynamic input vector simulation. Setup time is the minimum duration the data input must remain stable before the active clock edge; a setup violation occurs if data arrives too late, causing timing failure at high clock frequency. Hold time is the minimum duration the data must remain stable after the active clock edge; a hold violation occurs if data arrives too quickly and contaminates the register during the latching window, independent of clock frequency.",
            coreKeywordsJson = "[\"setup slack\", \"hold slack\", \"clock skew\", \"jitter\", \"critical path\"]",
            prerequisiteTopicIdsJson = "[\"topic_cmos_inverter\"]",
            estimatedMinutes = 45,
            referenceTitle = "Static Timing Analysis for Nanometer Designs",
            referenceAuthor = "J. Bhasker & Rakesh Chadha",
            referenceEdition = "1st Edition",
            referenceChapter = "2",
            referenceSection = "2.2-2.5",
            referenceIdentifier = "ISBN: 978-0387938196"
        ),

        // ── Computer Science and IT ──────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_big_o",
            domain = "Computer Science and IT",
            subdomain = "Data Structures & Algorithms",
            title = "Algorithmic Complexity & Big-O",
            description = "Asymptotic analysis, worst-case upper bounds, and memory footprint scaling.",
            verifiedExplanation = "Big-O notation describes the limiting behavior of an algorithm's running time or memory requirements as the input size n approaches infinity. It characterizes the asymptotic upper bound: f(n) = O(g(n)) if there exist positive constants c and n0 such that |f(n)| <= c*|g(n)| for all n >= n0. Common complexity classes include O(1) constant, O(log n) logarithmic, O(n) linear, O(n log n) linearithmic, and O(n^2) quadratic.",
            coreKeywordsJson = "[\"asymptotic\", \"upper bound\", \"worst-case\", \"space complexity\", \"logarithmic\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 30,
            referenceTitle = "Introduction to Algorithms",
            referenceAuthor = "Thomas H. Cormen, Charles E. Leiserson, Ronald L. Rivest, Clifford Stein",
            referenceEdition = "3rd Edition",
            referenceChapter = "3",
            referenceSection = "3.1",
            referenceIdentifier = "ISBN: 978-0262033848"
        ),
        VerifiedTopicEntity(
            topicId = "topic_binary_search",
            domain = "Computer Science and IT",
            subdomain = "Data Structures & Algorithms",
            title = "Binary Search & Hash Tables",
            description = "Divide-and-conquer search guarantees, hash collision resolution, and load factor dynamics.",
            verifiedExplanation = "Binary search operates on sorted arrays by repeatedly halving the search interval, comparing the target value to the middle element. It provides an optimal worst-case search complexity of O(log n). Hash tables store key-value pairs using a hash function that maps keys to array buckets, achieving O(1) average-time lookups and insertions. Collisions are handled via separate chaining (linked lists or trees per bucket) or open addressing (linear/quadratic probing), with performance degrading toward O(n) when the load factor exceeds capacity thresholds.",
            coreKeywordsJson = "[\"divide and conquer\", \"logarithmic search\", \"hash function\", \"collision resolution\", \"load factor\"]",
            prerequisiteTopicIdsJson = "[\"topic_big_o\"]",
            estimatedMinutes = 35,
            referenceTitle = "The Art of Computer Programming, Vol. 3: Sorting and Searching",
            referenceAuthor = "Donald E. Knuth",
            referenceEdition = "2nd Edition",
            referenceChapter = "6",
            referenceSection = "6.2-6.4",
            referenceIdentifier = "ISBN: 978-0201896855"
        ),
        VerifiedTopicEntity(
            topicId = "topic_edge_ai",
            domain = "Computer Science and IT",
            subdomain = "Edge AI",
            title = "On-Device Model Quantization",
            description = "Integer quantization (8da4w), KV-cache allocation, and edge runtime delegates.",
            verifiedExplanation = "On-device model quantization maps continuous floating-point weights and activation tensors (e.g. FP32 or BF16) to lower-bitwidth integer representations (e.g. INT8 or INT4) to minimize memory bandwidth and memory footprint on mobile devices. In 8da4w quantization, activations are dynamically quantized to 8 bits during inference while linear weights are pre-quantized to 4-bit grouped integers. This enables multi-gigabyte models to execute on edge smartphone CPUs via delegates such as XNNPACK without exceeding mobile RAM ceilings.",
            coreKeywordsJson = "[\"quantization\", \"8da4w\", \"group size\", \"XNNPACK\", \"memory bandwidth\"]",
            prerequisiteTopicIdsJson = "[\"topic_big_o\"]",
            estimatedMinutes = 45,
            referenceTitle = "Efficient Processing of Deep Neural Networks",
            referenceAuthor = "Vivienne Sze, Yu-Hsin Chen, Tien-Ju Yang, Joel S. Emer",
            referenceEdition = "1st Edition",
            referenceChapter = "7",
            referenceSection = "7.1-7.3",
            referenceIdentifier = "ISBN: 978-1681738314"
        ),

        // ── UI/UX and Product Design ─────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_personas",
            domain = "UI/UX and Product Design",
            subdomain = "User Research",
            title = "User Personas & Journey Mapping",
            description = "Goal-directed design, composite user archetypes, pain points, and lifecycle touchpoints.",
            verifiedExplanation = "User personas are detailed, research-grounded composite archetypes that represent key user segments sharing common behavioral patterns, motivations, and technical constraints. Rather than vague demographics, effective personas define concrete end-goals, mental models, and environmental pain points. Journey mapping charts the sequential stages a user traverses to accomplish a specific objective, documenting their emotional states, decision forks, and friction areas across interaction touchpoints.",
            coreKeywordsJson = "[\"user personas\", \"journey mapping\", \"mental models\", \"pain points\", \"goal-directed design\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 30,
            referenceTitle = "The Inmates Are Running the Asylum: Why High Tech Products Drive Us Crazy",
            referenceAuthor = "Alan Cooper",
            referenceEdition = "1st Edition",
            referenceChapter = "9",
            referenceSection = "9.1-9.3",
            referenceIdentifier = "ISBN: 978-0672316494"
        ),
        VerifiedTopicEntity(
            topicId = "topic_wcag",
            domain = "UI/UX and Product Design",
            subdomain = "Accessibility & WCAG",
            title = "WCAG Accessibility & Color Contrast",
            description = "Perceivable, Operable, Understandable, and Robust (POUR) digital compliance guidelines.",
            verifiedExplanation = "Web Content Accessibility Guidelines (WCAG) 2.2 defines the international benchmark for accessible digital experiences based on four foundational principles (POUR): Perceivable, Operable, Understandable, and Robust. Level AA compliance mandates minimum visual contrast ratios of 4.5:1 for normal body text and 3:1 for large text (>=18pt or bold >=14pt) against adjacent backgrounds. In mobile interfaces, interactive touch targets must meet a minimum bounding dimension of 48x48dp to accommodate motor impairment and diverse thumb geometries.",
            coreKeywordsJson = "[\"WCAG 2.2\", \"contrast ratio\", \"POUR principles\", \"touch targets\", \"assistive technology\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Web Content Accessibility Guidelines (WCAG) 2.2",
            referenceAuthor = "World Wide Web Consortium (W3C)",
            referenceEdition = "W3C Recommendation 2023",
            referenceChapter = "Guideline 1.4",
            referenceSection = "1.4.3 Contrast (Minimum)",
            referenceIdentifier = "W3C TR/WCAG22"
        ),
        VerifiedTopicEntity(
            topicId = "topic_design_tokens",
            domain = "UI/UX and Product Design",
            subdomain = "Design Systems",
            title = "Design Tokens & Component Libraries",
            description = "Systematic semantic abstractions for color, spacing, typography, and theme switching.",
            verifiedExplanation = "Design tokens are atomic, platform-agnostic design decisions represented as structured key-value data (e.g., color, spacing, corner radius, elevation, typography scales). They decouple abstract design intent from concrete CSS or Android Compose code. In modern design systems, tokens are structured in tiers: Global tokens (raw values like 'blue-500 = #3B82F6'), Semantic tokens (purpose-driven like 'color-primary = blue-500'), and Component tokens (scoped like 'btn-bg = color-primary'). This structure enables dynamic runtime theming, dark mode switching, and localization.",
            coreKeywordsJson = "[\"design tokens\", \"design system\", \"semantic tokens\", \"theming\", \"Material 3\"]",
            prerequisiteTopicIdsJson = "[\"topic_wcag\"]",
            estimatedMinutes = 40,
            referenceTitle = "Design Systems: A Practical Guide to Creating Design Languages",
            referenceAuthor = "Alla Kholmatova",
            referenceEdition = "1st Edition",
            referenceChapter = "3",
            referenceSection = "3.2",
            referenceIdentifier = "ISBN: 978-1937557645"
        ),

        // ── Mechanical Engineering ───────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_stress_strain",
            domain = "Mechanical Engineering",
            subdomain = "Materials Mechanics",
            title = "Stress, Strain & Hooke's Law",
            description = "Tensile loading, elastic deformation, Modulus of Elasticity, yield strength, and plastic flow.",
            verifiedExplanation = "Stress (sigma) represents the internal resisting force per unit cross-sectional area (F/A, measured in Pascals or N/m^2), while strain (epsilon) is the fractional elongation or dimensional change (delta L / L, dimensionless). Hooke's Law states that within the elastic limit of an isotropic material, stress is directly proportional to strain: sigma = E * epsilon, where E is Young's Modulus of Elasticity. Beyond the yield point, permanent plastic deformation occurs until ultimate tensile strength and rupture.",
            coreKeywordsJson = "[\"stress\", \"strain\", \"Hooke's law\", \"Young's modulus\", \"yield strength\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Shigley's Mechanical Engineering Design",
            referenceAuthor = "Richard G. Budynas & J. Keith Nisbett",
            referenceEdition = "11th Edition",
            referenceChapter = "3",
            referenceSection = "3.1-3.6",
            referenceIdentifier = "ISBN: 978-0073398204"
        ),
        VerifiedTopicEntity(
            topicId = "topic_thermo_laws",
            domain = "Mechanical Engineering",
            subdomain = "Thermodynamics",
            title = "First & Second Laws of Thermodynamics",
            description = "Conservation of energy, enthalpy, entropy generation, and Carnot cycle thermal efficiency bounds.",
            verifiedExplanation = "The First Law of Thermodynamics asserts the conservation of energy: the change in internal energy (Delta U) of a closed system equals the heat added to the system (Q) minus work done by the system (W): Delta U = Q - W. The Second Law dictates that natural processes are irreversible, stating that the total entropy of an isolated system always increases over time (Delta S >= 0). It establishes that no thermal heat engine can convert heat entirely into mechanical work, bounding thermal efficiency by the Carnot limit: eta = 1 - (T_cold / T_hot).",
            coreKeywordsJson = "[\"first law\", \"second law\", \"entropy\", \"Carnot efficiency\", \"internal energy\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 40,
            referenceTitle = "Fundamentals of Engineering Thermodynamics",
            referenceAuthor = "Michael J. Moran, Howard N. Shapiro, Daisie D. Boettner, Margaret B. Bailey",
            referenceEdition = "9th Edition",
            referenceChapter = "2 & 5",
            referenceSection = "2.1-2.5, 5.1-5.3",
            referenceIdentifier = "ISBN: 978-1119391388"
        ),
        VerifiedTopicEntity(
            topicId = "topic_heat_conduction",
            domain = "Mechanical Engineering",
            subdomain = "Heat Transfer",
            title = "Heat Exchangers & Conduction",
            description = "Fourier's Law of thermal conduction, overall heat transfer coefficients, and LMTD thermal analysis.",
            verifiedExplanation = "Thermal conduction is the transfer of internal kinetic energy between particles down a temperature gradient. Fourier's Law governs one-dimensional steady conduction: q = -k * A * (dT/dx), where q is heat transfer rate, k is thermal conductivity, and A is surface area. Heat exchangers transfer thermal energy between fluids separated by conductive walls. Their performance is governed by q = U * A * Delta T_lm, where U is the overall heat transfer coefficient and Delta T_lm is the Logarithmic Mean Temperature Difference.",
            coreKeywordsJson = "[\"Fourier's law\", \"conduction\", \"thermal conductivity\", \"heat exchanger\", \"LMTD\"]",
            prerequisiteTopicIdsJson = "[\"topic_thermo_laws\"]",
            estimatedMinutes = 40,
            referenceTitle = "Fundamentals of Heat and Mass Transfer",
            referenceAuthor = "Frank P. Incropera, David P. DeWitt, Theodore L. Bergman, Adrienne S. Lavine",
            referenceEdition = "7th Edition",
            referenceChapter = "1 & 11",
            referenceSection = "1.2, 11.1-11.3",
            referenceIdentifier = "ISBN: 978-0470501979"
        ),

        // ── Biotechnology ────────────────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_dna_cloning",
            domain = "Biotechnology",
            subdomain = "Molecular Biology",
            title = "Recombinant DNA Technology & Restriction Cloning",
            description = "Restriction enzymes, plasmid cloning vectors, palindromic cleavage, and DNA ligase.",
            verifiedExplanation = "Recombinant DNA technology combines genetic material from multiple sources. Type II restriction endonucleases recognize specific palindromic sequences (e.g. 5'-GAATTC-3' for EcoRI) and hydrolyze phosphodiester bonds to produce complementary sticky or blunt ends. DNA ligase subsequently seals the nicks in the phosphodiester backbone by forming covalent ester bonds between 5'-phosphate and 3'-hydroxyl groups.",
            coreKeywordsJson = "[\"restriction enzyme\", \"plasmid\", \"sticky ends\", \"DNA ligase\", \"cloning vector\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Molecular Biology of the Gene",
            referenceAuthor = "James D. Watson, Tania A. Baker, Stephen P. Bell",
            referenceEdition = "7th Edition",
            referenceChapter = "5 & 7",
            referenceSection = "5.3, 7.1-7.4",
            referenceIdentifier = "ISBN: 978-0321762436"
        ),
        VerifiedTopicEntity(
            topicId = "topic_pcr_amplification",
            domain = "Biotechnology",
            subdomain = "Molecular Biology",
            title = "PCR Amplification & Gel Electrophoresis",
            description = "Three-step thermal cycling, thermostable Taq polymerase, and agarose matrix sieving.",
            verifiedExplanation = "The Polymerase Chain Reaction exponentially amplifies target DNA sequences through repeated cycles of thermal denaturation (~95°C), primer annealing (~55°C), and primer extension (~72°C) catalyzed by thermostable Taq DNA polymerase. Following amplification, fragments are resolved on an agarose gel matrix under an electric field, migrating toward the positive anode inversely proportional to the log of their molecular weight.",
            coreKeywordsJson = "[\"PCR\", \"Taq polymerase\", \"primer annealing\", \"agarose gel\", \"electrophoresis\"]",
            prerequisiteTopicIdsJson = "[\"topic_dna_cloning\"]",
            estimatedMinutes = 40,
            referenceTitle = "Principles of Gene Manipulation and Genomics",
            referenceAuthor = "Sandy B. Primrose & Richard Twyman",
            referenceEdition = "7th Edition",
            referenceChapter = "2",
            referenceSection = "2.2-2.5",
            referenceIdentifier = "ISBN: 978-1405135443"
        ),
        VerifiedTopicEntity(
            topicId = "topic_crispr_cas9",
            domain = "Biotechnology",
            subdomain = "Genetic Engineering",
            title = "CRISPR-Cas9 Targeted Genome Editing",
            description = "Single guide RNA (sgRNA), Cas9 endonuclease, Protospacer Adjacent Motif (PAM), and DSB repair.",
            verifiedExplanation = "CRISPR-Cas9 provides RNA-programmable targeted genome editing. A chimeric single-guide RNA (sgRNA) directs the Cas9 endonuclease to a complementary 20-nucleotide genomic target sequence immediately adjacent to a 5'-NGG-3' Protospacer Adjacent Motif (PAM). Cas9 introduces a site-specific double-strand break (DSB) 3-4 base pairs upstream of the PAM, which is repaired via Non-Homologous End Joining (NHEJ) for gene knockout or Homology-Directed Repair (HDR) for precise gene editing.",
            coreKeywordsJson = "[\"CRISPR\", \"Cas9\", \"sgRNA\", \"PAM motif\", \"double-strand break\", \"NHEJ\"]",
            prerequisiteTopicIdsJson = "[\"topic_pcr_amplification\"]",
            estimatedMinutes = 45,
            referenceTitle = "Genomes 4",
            referenceAuthor = "T.A. Brown",
            referenceEdition = "4th Edition",
            referenceChapter = "19",
            referenceSection = "19.3",
            referenceIdentifier = "ISBN: 978-0815345084"
        ),

        // ── Food Technology ──────────────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_food_thermal_preservation",
            domain = "Food Technology",
            subdomain = "Food Preservation",
            title = "Thermal Processing & Pasteurization (D & z Values)",
            description = "Thermal death kinetics, decimal reduction time (D-value), z-value temperature sensitivity, and commercial sterility.",
            verifiedExplanation = "Thermal processing destroys pathogenic and spoilage microorganisms in food systems. Microbial destruction follows first-order kinetics: the Decimal Reduction Time (D-value) is the time in minutes at a specified temperature to destroy 90% (1 log cycle) of a target organism. The z-value is the temperature increase required to reduce the D-value by 90% (1 log cycle). In commercial canning of low-acid foods, a 12D process against Clostridium botulinum spores ensures public health safety.",
            coreKeywordsJson = "[\"pasteurization\", \"D-value\", \"z-value\", \"thermal death time\", \"Clostridium botulinum\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Food Processing Technology: Principles and Practice",
            referenceAuthor = "P.J. Fellows",
            referenceEdition = "4th Edition",
            referenceChapter = "13 & 14",
            referenceSection = "13.1-13.4",
            referenceIdentifier = "ISBN: 978-0081019078"
        ),
        VerifiedTopicEntity(
            topicId = "topic_food_water_activity",
            domain = "Food Technology",
            subdomain = "Food Engineering",
            title = "Water Activity (aw) Control & Microbial Stability",
            description = "Water activity thermodynamic definition, moisture sorption isotherms, and bacterial growth limits.",
            verifiedExplanation = "Water activity (aw = p / p0) represents the ratio of water vapor pressure in food to that of pure water at the same temperature. It measures the free, unbound water available to participate in chemical reactions and sustain microbial growth. Most bacteria are inhibited below aw 0.91, C. botulinum is arrested below 0.93, yeasts below 0.88, and molds below 0.80. Preservation methods such as salting, sugar addition, and dehydration depress water activity into shelf-stable regimes.",
            coreKeywordsJson = "[\"water activity\", \"moisture sorption isotherm\", \"microbial stability\", \"preservation\"]",
            prerequisiteTopicIdsJson = "[\"topic_food_thermal_preservation\"]",
            estimatedMinutes = 40,
            referenceTitle = "Introduction to Food Engineering",
            referenceAuthor = "R. Paul Singh & Dennis R. Heldman",
            referenceEdition = "5th Edition",
            referenceChapter = "11",
            referenceSection = "11.1-11.4",
            referenceIdentifier = "ISBN: 978-0123849052"
        ),
        VerifiedTopicEntity(
            topicId = "topic_food_haccp",
            domain = "Food Technology",
            subdomain = "Food Safety & Quality",
            title = "HACCP Food Safety Principles & Critical Limits",
            description = "Hazard analysis, critical control points (CCPs), critical limits, monitoring, and corrective actions.",
            verifiedExplanation = "Hazard Analysis and Critical Control Point (HACCP) is a systematic, preventive approach to food safety. The 7 principles comprise: (1) Conduct hazard analysis, (2) Determine Critical Control Points (CCPs), (3) Establish critical limits for preventative measures, (4) Establish monitoring procedures, (5) Establish corrective actions, (6) Establish verification procedures, and (7) Establish record-keeping and documentation.",
            coreKeywordsJson = "[\"HACCP\", \"critical control point\", \"critical limit\", \"food safety\", \"verification\"]",
            prerequisiteTopicIdsJson = "[\"topic_food_water_activity\"]",
            estimatedMinutes = 35,
            referenceTitle = "Food Safety Management: A Practical Guide for the Food Industry",
            referenceAuthor = "Yasmine Motarjemi & Huub Lelieveld",
            referenceEdition = "1st Edition",
            referenceChapter = "24",
            referenceSection = "24.2-24.6",
            referenceIdentifier = "ISBN: 978-0123815040"
        ),

        // ── School Science ───────────────────────────────────────────────────────
        VerifiedTopicEntity(
            topicId = "topic_cbse_chemical_reactions",
            domain = "School Science",
            subdomain = "Secondary Chemistry",
            title = "Chemical Reactions, Balancing & Stoichiometry",
            description = "Law of conservation of mass, balancing chemical equations, and redox reactions.",
            verifiedExplanation = "Chemical reactions involve the breaking and forming of chemical bonds without creating or destroying matter, adhering to the Law of Conservation of Mass. A balanced chemical equation has identical numbers of each type of atom on both reactant and product sides. Primary reaction classes include combination, decomposition, single displacement, double displacement, and oxidation-reduction (redox) reactions.",
            coreKeywordsJson = "[\"chemical equation\", \"balancing\", \"stoichiometry\", \"conservation of mass\", \"redox\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 30,
            referenceTitle = "Science: Textbook for Class X",
            referenceAuthor = "NCERT Curriculum Framework",
            referenceEdition = "Revised Edition",
            referenceChapter = "1",
            referenceSection = "1.1-1.3",
            referenceIdentifier = "CBSE/NCERT-SCI-X-CH01"
        ),
        VerifiedTopicEntity(
            topicId = "topic_cbse_acids_bases",
            domain = "School Science",
            subdomain = "Secondary Chemistry",
            title = "Acids, Bases, Salts and pH Calculation",
            description = "Arrhenius and Bronsted-Lowry acid-base theories, logarithmic pH scale, and neutralization reactions.",
            verifiedExplanation = "Acids donate protons (H+) in aqueous solution, yielding hydronium ions (H3O+), whereas bases generate hydroxide ions (OH-). The pH scale is logarithmic: pH = -log10[H+]. A decrease of 1 pH unit represents a tenfold increase in hydrogen ion concentration. Neutralization between an acid and a base produces a salt and water (e.g. HCl + NaOH -> NaCl + H2O).",
            coreKeywordsJson = "[\"acid\", \"base\", \"pH scale\", \"neutralization\", \"Arrhenius\"]",
            prerequisiteTopicIdsJson = "[\"topic_cbse_chemical_reactions\"]",
            estimatedMinutes = 30,
            referenceTitle = "Science: Textbook for Class X",
            referenceAuthor = "NCERT Curriculum Framework",
            referenceEdition = "Revised Edition",
            referenceChapter = "2",
            referenceSection = "2.1-2.4",
            referenceIdentifier = "CBSE/NCERT-SCI-X-CH02"
        ),
        VerifiedTopicEntity(
            topicId = "topic_cbse_newton_mechanics",
            domain = "School Science",
            subdomain = "Secondary Physics",
            title = "Newtonian Mechanics & Force Equilibrium",
            description = "Newton's Three Laws of Motion, momentum conservation, and gravitational attraction.",
            verifiedExplanation = "Classical mechanics describes the motion of macroscopic bodies under applied forces. Newton's First Law states bodies remain in uniform motion unless acted upon by a net external force (inertia). Newton's Second Law establishes F_net = m * a. Newton's Third Law dictates that every action produces an equal and opposite reaction force. Linear momentum (p = m * v) is strictly conserved in isolated systems.",
            coreKeywordsJson = "[\"Newton's laws\", \"inertia\", \"force\", \"momentum conservation\", \"acceleration\"]",
            prerequisiteTopicIdsJson = "[]",
            estimatedMinutes = 35,
            referenceTitle = "Conceptual Physics",
            referenceAuthor = "Paul G. Hewitt",
            referenceEdition = "12th Edition",
            referenceChapter = "2-5",
            referenceSection = "2.1-5.6",
            referenceIdentifier = "ISBN: 978-0321909107"
        )
    )

    fun getDiagnosticQuestions(): List<AssessmentQuestionEntity> = listOf(
        // ── Electronics and VLSI ─────────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_pn_01",
            domain = "Electronics and VLSI",
            subdomain = "Semiconductor Physics",
            topic = "PN Junction Physics & Diodes",
            questionText = "What physical mechanism creates the depletion region in an unbiased p-n junction?",
            optionsJson = "[\"Majority carrier diffusion leaving behind fixed ionized dopant atoms\", \"Direct ohmic conduction between metal wires\", \"Thermal ionization of intrinsic silicon creating continuous current\", \"External battery bias injecting excess electrons\"]",
            correctIndex = 0,
            verifiedExplanation = "Majority carriers diffuse across the junction down their concentration gradient (holes from p to n, electrons from n to p). When they recombine, they unmask fixed ionized donor and acceptor atoms, establishing the space-charge depletion region."
        ),
        AssessmentQuestionEntity(
            questionId = "q_pn_02",
            domain = "Electronics and VLSI",
            subdomain = "Semiconductor Physics",
            topic = "PN Junction Physics & Diodes",
            questionText = "How does applying a forward bias affect the built-in potential barrier of a p-n junction?",
            optionsJson = "[\"Decreases the potential barrier height, allowing carrier diffusion\", \"Increases the potential barrier height, blocking all current\", \"Inverts the doping type of the substrate\", \"Eliminates all resistance permanently\"]",
            correctIndex = 0,
            verifiedExplanation = "Forward bias (p-side positive relative to n-side) applies an electric field opposite to the built-in field, reducing the barrier height and allowing majority carriers to diffuse across the junction exponentially."
        ),
        AssessmentQuestionEntity(
            questionId = "q_cmos_01",
            domain = "Electronics and VLSI",
            subdomain = "Digital VLSI & RTL",
            topic = "CMOS Inverter & Switching Characteristics",
            questionText = "Why is static power dissipation exceptionally low in an ideal CMOS inverter?",
            optionsJson = "[\"At any valid logic level, either the pull-up pMOS or pull-down nMOS network is completely turned off\", \"Both transistors conduct continuously with zero resistance\", \"It utilizes passive inductors instead of semiconductors\", \"The power supply voltage is completely switched off between clock cycles\"]",
            correctIndex = 0,
            verifiedExplanation = "In CMOS logic, the pMOS and nMOS transistors operate in a complementary arrangement. When the input is steady High or Low, one transistor is off, breaking the direct DC path from VDD to GND."
        ),
        AssessmentQuestionEntity(
            questionId = "q_sta_01",
            domain = "Electronics and VLSI",
            subdomain = "Physical Design",
            topic = "Static Timing Analysis (Setup & Hold)",
            questionText = "Which timing violation cannot be resolved by reducing the circuit clock frequency?",
            optionsJson = "[\"Hold time violation\", \"Setup time violation\", \"Maximum transition violation\", \"Clock period slack deficit\"]",
            correctIndex = 0,
            verifiedExplanation = "Hold time is an internal race condition between data delay and clock skew. It requires data to remain stable for a short duration after the clock edge, completely independent of how long the clock period is."
        ),

        // ── Computer Science and IT ──────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_algo_01",
            domain = "Computer Science and IT",
            subdomain = "Data Structures & Algorithms",
            topic = "Algorithmic Complexity & Big-O",
            questionText = "What is the worst-case time complexity of binary search on a sorted array of n elements?",
            optionsJson = "[\"O(log n)\", \"O(n)\", \"O(n log n)\", \"O(1)\"]",
            correctIndex = 0,
            verifiedExplanation = "Binary search repeatedly halves the search space at each iteration (n, n/2, n/4, ..., 1), requiring at most ceil(log2(n)) + 1 comparisons."
        ),
        AssessmentQuestionEntity(
            questionId = "q_search_01",
            domain = "Computer Science and IT",
            subdomain = "Data Structures & Algorithms",
            topic = "Binary Search & Hash Tables",
            questionText = "What is the average time complexity of a key lookup in a properly dimensioned hash table?",
            optionsJson = "[\"O(1) constant time\", \"O(log n) logarithmic time\", \"O(n) linear time\", \"O(n^2) quadratic time\"]",
            correctIndex = 0,
            verifiedExplanation = "With a uniform hash function and low load factor, hash table buckets contain O(1) elements on average, resulting in constant-time average lookups."
        ),
        AssessmentQuestionEntity(
            questionId = "q_edge_01",
            domain = "Computer Science and IT",
            subdomain = "Edge AI",
            topic = "On-Device Model Quantization",
            questionText = "In 8da4w quantized neural networks, what does '8da' specifically refer to?",
            optionsJson = "[\"8-bit dynamic activation quantization computed during runtime inference\", \"8-bit static weights stored in the PTE flatbuffer\", \"8 deep attention heads in the transformer backbone\", \"8 decibel audio frequency filtering\"]",
            correctIndex = 0,
            verifiedExplanation = "'8da' denotes 8-bit dynamic activations, where floating-point activation values are quantized to 8-bit integers on-the-fly during inference to accelerate tensor dot products while preserving dynamic range."
        ),

        // ── UI/UX and Product Design ─────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_ux_01",
            domain = "UI/UX and Product Design",
            subdomain = "User Research",
            topic = "User Personas & Journey Mapping",
            questionText = "In goal-directed design, what primary purpose do user personas serve?",
            optionsJson = "[\"They provide research-grounded composite archetypes to align product decisions around user goals\", \"They serve as legal documents for user privacy agreements\", \"They calculate financial advertising revenues\", \"They replace usability testing with automated scripts\"]",
            correctIndex = 0,
            verifiedExplanation = "Personas synthesize qualitative user research into concrete user archetypes, preventing team bias and keeping feature design focused on real end-user goals and friction points."
        ),
        AssessmentQuestionEntity(
            questionId = "q_ux_02",
            domain = "UI/UX and Product Design",
            subdomain = "Accessibility & WCAG",
            topic = "WCAG Accessibility & Color Contrast",
            questionText = "Under WCAG 2.2 Level AA, what is the minimum contrast ratio required for normal body text?",
            optionsJson = "[\"4.5:1 against the background\", \"2:1 against the background\", \"1:1 matching the background\", \"10:1 pure black and white only\"]",
            correctIndex = 0,
            verifiedExplanation = "WCAG Level AA Success Criterion 1.4.3 requires a visual contrast ratio of at least 4.5:1 for standard text (under 18pt or under 14pt bold) to ensure readability for users with moderate visual acuity loss."
        ),
        AssessmentQuestionEntity(
            questionId = "q_ux_03",
            domain = "UI/UX and Product Design",
            subdomain = "Design Systems",
            topic = "Design Tokens & Component Libraries",
            questionText = "What architectural advantage do semantic design tokens provide over hardcoded color values?",
            optionsJson = "[\"They map design intent to context-aware roles, enabling seamless dark mode and multi-theme switching\", \"They eliminate the need for Kotlin code entirely\", \"They increase the mobile APK file size significantly\", \"They disable all animations in the operating system\"]",
            correctIndex = 0,
            verifiedExplanation = "Semantic tokens (like 'color-surface-elevated') decouple visual roles from literal hex codes (like '#FFFFFF'), allowing the runtime to substitute dark/light or brand palettes automatically."
        ),

        // ── Mechanical Engineering ───────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_mech_01",
            domain = "Mechanical Engineering",
            subdomain = "Materials Mechanics",
            topic = "Stress, Strain & Hooke's Law",
            questionText = "What physical property is defined by the slope of the linear region on a tensile stress-strain curve?",
            optionsJson = "[\"Young's Modulus of Elasticity\", \"Ultimate Tensile Strength\", \"Poisson's Ratio\", \"Thermal Expansion Coefficient\"]",
            correctIndex = 0,
            verifiedExplanation = "According to Hooke's Law (sigma = E * epsilon), the linear proportionality constant between tensile stress and strain in the elastic regime is Young's Modulus (E)."
        ),
        AssessmentQuestionEntity(
            questionId = "q_mech_02",
            domain = "Mechanical Engineering",
            subdomain = "Thermodynamics",
            topic = "First & Second Laws of Thermodynamics",
            questionText = "Which thermodynamic law states that the entropy of an isolated system never decreases over time?",
            optionsJson = "[\"The Second Law of Thermodynamics\", \"The First Law of Thermodynamics\", \"The Zeroth Law of Thermodynamics\", \"The Law of Conservation of Mass\"]",
            correctIndex = 0,
            verifiedExplanation = "The Second Law dictates that in any spontaneous natural process, the total entropy of the system and its surroundings must increase or remain constant for reversible processes (Delta S >= 0)."
        ),
        AssessmentQuestionEntity(
            questionId = "q_mech_03",
            domain = "Mechanical Engineering",
            subdomain = "Heat Transfer",
            topic = "Heat Exchangers & Conduction",
            questionText = "According to Fourier's Law of thermal conduction, what is heat flux directly proportional to?",
            optionsJson = "[\"The negative temperature gradient (-dT/dx)\", \"The square of the fluid velocity\", \"The atmospheric pressure\", \"The magnetic permeability of the metal\"]",
            correctIndex = 0,
            verifiedExplanation = "Fourier's Law specifies that conduction heat flux (q/A) equals -k * (dT/dx), indicating that heat spontaneously flows down a negative temperature gradient."
        ),

        // ── Biotechnology ────────────────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_bio_01",
            domain = "Biotechnology",
            subdomain = "Molecular Biology",
            topic = "Recombinant DNA Technology & Restriction Cloning",
            questionText = "What type of nucleotide recognition sequence is characteristically cleaved by Type II restriction endonucleases (such as EcoRI)?",
            optionsJson = "[\"Palindromic sequences with two-fold rotational symmetry\", \"Homopolymeric poly-A repeat tracts\", \"Telomeric hexanucleotide repeat sequences\", \"Unpaired single-stranded hairpin loops\"]",
            correctIndex = 0,
            verifiedExplanation = "Type II restriction endonucleases typically recognize short palindromic sequences (e.g. 5'-GAATTC-3') and cleave both strands symmetrically to generate sticky or blunt ends."
        ),
        AssessmentQuestionEntity(
            questionId = "q_bio_02",
            domain = "Biotechnology",
            subdomain = "Molecular Biology",
            topic = "PCR Amplification & Gel Electrophoresis",
            questionText = "In standard three-step PCR thermal cycling, what is the primary biochemical purpose of the 72°C extension step?",
            optionsJson = "[\"Optimal enzymatic synthesis of DNA by thermostable Taq polymerase\", \"Complete thermal denaturation of double-stranded DNA template\", \"Annealing of forward and reverse oligonucleotide primers to template\", \"Inactivation of exonucleases and proteases\"]",
            correctIndex = 0,
            verifiedExplanation = "Taq polymerase exhibits peak catalytic activity around 72°C, extending primers along the single-stranded template in the 5' to 3' direction."
        ),
        AssessmentQuestionEntity(
            questionId = "q_bio_03",
            domain = "Biotechnology",
            subdomain = "Genetic Engineering",
            topic = "CRISPR-Cas9 Targeted Genome Editing",
            questionText = "In the CRISPR-Cas9 genome editing system, what adjacent sequence is required on the target DNA immediately following the protospacer for Cas9 binding and cleavage?",
            optionsJson = "[\"Protospacer Adjacent Motif (PAM, typically 5'-NGG-3')\", \"TATA box promoter element\", \"Polyadenylation consensus sequence\", \"Shine-Dalgarno ribosomal binding site\"]",
            correctIndex = 0,
            verifiedExplanation = "SpCas9 strictly requires a Protospacer Adjacent Motif (PAM; 5'-NGG-3') downstream of the 20-nucleotide guide RNA target sequence to initiate DNA unwinding and R-loop formation."
        ),

        // ── Food Technology ──────────────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_food_01",
            domain = "Food Technology",
            subdomain = "Food Preservation",
            topic = "Thermal Processing & Pasteurization (D & z Values)",
            questionText = "In food thermal sterilization, what does the Decimal Reduction Time (D-value) represent?",
            optionsJson = "[\"Time in minutes at a given temperature required to destroy 90% (1 log reduction) of a microbial population\", \"Temperature increase required to reduce the thermal death time by 90%\", \"Total time required to achieve commercial sterility at 121.1°C\", \"Number of spores surviving a 12D botulinum cook\"]",
            correctIndex = 0,
            verifiedExplanation = "The D-value is the exposure time at a constant temperature required to reduce the surviving microbial population by 90% (1 log cycle)."
        ),
        AssessmentQuestionEntity(
            questionId = "q_food_02",
            domain = "Food Technology",
            subdomain = "Food Engineering",
            topic = "Water Activity (aw) Control & Microbial Stability",
            questionText = "What is the recognized minimum water activity (aw) limit below which Clostridium botulinum cannot produce neurotoxin?",
            optionsJson = "[\"0.93\", \"0.85\", \"0.75\", \"0.60\"]",
            correctIndex = 0,
            verifiedExplanation = "Clostridium botulinum growth and toxin production are completely inhibited at water activity below 0.93 (equivalent to ~10% NaCl in water phase)."
        ),
        AssessmentQuestionEntity(
            questionId = "q_food_03",
            domain = "Food Technology",
            subdomain = "Food Safety & Quality",
            topic = "HACCP Food Safety Principles & Critical Limits",
            questionText = "Under Codex Alimentarius HACCP guidelines, what is the critical step defined to prevent, eliminate, or reduce a food safety hazard to an acceptable level?",
            optionsJson = "[\"Critical Control Point (CCP)\", \"Standard Operating Procedure (SOP)\", \"Good Manufacturing Practice (GMP)\", \"Corrective Action Log (CAL)\"]",
            correctIndex = 0,
            verifiedExplanation = "A Critical Control Point (CCP) is a specific operational step where control can be applied to prevent or eliminate a hazard or reduce it to an acceptable level."
        ),

        // ── School Science ───────────────────────────────────────────────────────
        AssessmentQuestionEntity(
            questionId = "q_sci_01",
            domain = "School Science",
            subdomain = "Secondary Chemistry",
            topic = "Chemical Reactions, Balancing & Stoichiometry",
            questionText = "What does the Law of Conservation of Mass dictate for any closed chemical reaction?",
            optionsJson = "[\"Total mass of reactants equals the total mass of products\", \"Energy increases exponentially during exothermic changes\", \"Gas volume remains constant regardless of pressure\", \"Number of molecules must be identical on both sides\"]",
            correctIndex = 0,
            verifiedExplanation = "In a closed chemical system, matter is neither created nor destroyed during a chemical reaction; hence the total mass of the products must equal the total mass of reactants."
        ),
        AssessmentQuestionEntity(
            questionId = "q_sci_02",
            domain = "School Science",
            subdomain = "Secondary Chemistry",
            topic = "Acids, Bases, Salts and pH Calculation",
            questionText = "If a solution's hydrogen ion concentration [H+] increases by a factor of 100, how does its pH value change?",
            optionsJson = "[\"The pH decreases by 2 units\", \"The pH increases by 2 units\", \"The pH decreases by 100 units\", \"The pH increases by 10 units\"]",
            correctIndex = 0,
            verifiedExplanation = "pH is defined as -log10[H+]. Increasing [H+] by 100x (10^2) decreases the pH by exactly 2 units."
        ),
        AssessmentQuestionEntity(
            questionId = "q_sci_03",
            domain = "School Science",
            subdomain = "Secondary Physics",
            topic = "Newtonian Mechanics & Force Equilibrium",
            questionText = "According to Newton's Second Law of Motion, what is the acceleration of an object directly proportional to?",
            optionsJson = "[\"The net force acting upon it\", \"The object's total mass\", \"The square of its velocity\", \"The gravitational potential energy\"]",
            correctIndex = 0,
            verifiedExplanation = "F = m * a implies acceleration a = F_net / m, meaning acceleration is directly proportional to net force and inversely proportional to mass."
        )
    )
}

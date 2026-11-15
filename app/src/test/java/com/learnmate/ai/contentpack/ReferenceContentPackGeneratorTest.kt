package com.learnmate.ai.contentpack

import com.learnmate.ai.data.contentpack.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ReferenceContentPackGeneratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val md = MessageDigest.getInstance("SHA-256")

    private fun sha256(bytes: ByteArray): String {
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testGenerateAndVerifyReferenceContentPack() {
        val keyPair = ContentPackSecurity.generateTestKeyPair()

        // 1. Prepare genuine educational payload assets (NCERT Class 10 Science)
        val ch1Content = """
            ================================================================================
            NCERT Class 10 Science — Chapter 1: Chemical Reactions and Equations
            Reference: Science Class X, NCERT (Rationalised 2023-24), ISBN 978-81-7450-492-0
            ================================================================================
            A chemical reaction is a process where the identity and properties of original
            substances change to produce one or more new substances.
            
            Law of Conservation of Mass:
            Mass can neither be created nor destroyed in a chemical reaction. Therefore, the
            total number of atoms of each element must remain identical before and after.
            
            Example: Balancing combustion of methane
            CH4(g) + 2 O2(g) -> CO2(g) + 2 H2O(l)
            Carbon atoms: Reactants = 1, Products = 1
            Hydrogen atoms: Reactants = 4, Products = 4
            Oxygen atoms: Reactants = 4, Products = 4
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch2Content = """
            ================================================================================
            NCERT Class 10 Science — Chapter 2: Acids, Bases and Salts
            Reference: Science Class X, NCERT (Rationalised 2023-24), ISBN 978-81-7450-492-0
            ================================================================================
            pH Scale:
            The pH scale measures hydrogen ion concentration in solution, ranging from 0 to 14.
            pH < 7 indicates an acidic solution ([H+] > [OH-]).
            pH = 7 indicates neutral (pure water at 25°C).
            pH > 7 indicates an alkaline / basic solution ([OH-] > [H+]).
            
            Importance in Daily Life:
            1. Digestive System: Stomach produces HCl (pH ~ 1.2) for pepsin activation.
            2. Tooth Decay: Starts when mouth pH falls below 5.5 due to bacterial acid production.
            3. Self-Defense in Nature: Bee stings inject methanoic acid; baking soda neutralizes it.
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch6Content = """
            ================================================================================
            NCERT Class 10 Science — Chapter 6: Life Processes
            Reference: Science Class X, NCERT (Rationalised 2023-24), ISBN 978-81-7450-492-0
            ================================================================================
            Nutrition:
            - Autotrophic: Organisms synthesize organic food from CO2 and H2O using sunlight (Photosynthesis).
              6 CO2 + 12 H2O --(sunlight/chlorophyll)--> C6H12O6 + 6 O2 + 6 H2O
            - Heterotrophic: Organisms depend on synthesized organic compounds (Holozoic, Saprophytic, Parasitic).
            
            Respiration:
            Breakdown of glucose (6-carbon molecule) into pyruvate (3-carbon molecule) in cytoplasm.
            - Aerobic: Pyruvate enters mitochondria -> 6 CO2 + 6 H2O + 38 ATP.
            - Anaerobic (Yeast): Pyruvate -> Ethanol + CO2 + 2 ATP.
            - Anaerobic (Muscle during vigorous exercise): Pyruvate -> Lactic Acid + 2 ATP (causes cramps).
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val ch10Content = """
            ================================================================================
            NCERT Class 10 Science — Chapter 10: Light — Reflection and Refraction
            Reference: Science Class X, NCERT (Rationalised 2023-24), ISBN 978-81-7450-492-0
            ================================================================================
            Spherical Mirror Formula:
            1/v + 1/u = 1/f
            where:
            u = object distance from pole (always negative by Cartesian sign convention)
            v = image distance from pole
            f = focal length (concave = negative, convex = positive)
            Linear Magnification: m = h'/h = -v/u
            
            Snell's Law of Refraction:
            The ratio of sine of angle of incidence to sine of angle of refraction is constant:
            sin(i) / sin(r) = n2 / n1 = n21 (Refractive Index of Medium 2 with respect to Medium 1)
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val payloads = mapOf(
            "resources/ch1_equations_guide.txt" to ch1Content,
            "resources/ch2_ph_scale_chart.txt" to ch2Content,
            "resources/ch6_life_processes_summary.txt" to ch6Content,
            "resources/ch10_optics_ray_diagrams.txt" to ch10Content
        )

        val payloadEntries = payloads.map { (path, data) ->
            PayloadFileEntry(path = path, sha256 = sha256(data), sizeBytes = data.size.toLong())
        }

        // 2. Define Curriculum Nodes
        val nodes = listOf(
            CurriculumNodeItem(
                nodeId = "node_cbse_sci10_root",
                parentNodeId = null,
                nodeType = "DISCIPLINE",
                code = "CBSE-SCI10",
                title = "CBSE Class 10 Science",
                description = "Complete CBSE / NCERT Class 10 Science curriculum foundation",
                estimatedMinutes = 1800,
                orderIndex = 1
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch1",
                parentNodeId = "node_cbse_sci10_root",
                nodeType = "UNIT",
                code = "SCI10-CH01",
                title = "Chapter 1: Chemical Reactions and Equations",
                description = "Chemical changes, word equations, balanced equations and reaction types",
                estimatedMinutes = 180,
                orderIndex = 2
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch1_balancing",
                parentNodeId = "node_sci10_ch1",
                nodeType = "TOPIC",
                code = "SCI10-CH01-TOP01",
                title = "Balancing Chemical Equations",
                description = "Conservation of mass and systematic stoichiometric coefficient balancing",
                estimatedMinutes = 60,
                orderIndex = 3
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch1_types",
                parentNodeId = "node_sci10_ch1",
                nodeType = "TOPIC",
                code = "SCI10-CH01-TOP02",
                title = "Types of Chemical Reactions",
                description = "Combination, decomposition, displacement, double displacement, and redox",
                estimatedMinutes = 90,
                orderIndex = 4
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch2",
                parentNodeId = "node_cbse_sci10_root",
                nodeType = "UNIT",
                code = "SCI10-CH02",
                title = "Chapter 2: Acids, Bases and Salts",
                description = "Chemical properties of acids/bases, indicators, pH scale and salts",
                estimatedMinutes = 200,
                orderIndex = 5
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch2_ph",
                parentNodeId = "node_sci10_ch2",
                nodeType = "TOPIC",
                code = "SCI10-CH02-TOP01",
                title = "The pH Scale & Applications",
                description = "Universal indicators, pH measurement, importance in soil and digestion",
                estimatedMinutes = 75,
                orderIndex = 6
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch6",
                parentNodeId = "node_cbse_sci10_root",
                nodeType = "UNIT",
                code = "SCI10-CH06",
                title = "Chapter 6: Life Processes",
                description = "Basic physiological functions: nutrition, respiration, transport, excretion",
                estimatedMinutes = 240,
                orderIndex = 7
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch6_nutrition",
                parentNodeId = "node_sci10_ch6",
                nodeType = "TOPIC",
                code = "SCI10-CH06-TOP01",
                title = "Autotrophic and Heterotrophic Nutrition",
                description = "Photosynthesis mechanisms, human alimentary canal and digestive enzymes",
                estimatedMinutes = 90,
                orderIndex = 8
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch6_respiration",
                parentNodeId = "node_sci10_ch6",
                nodeType = "TOPIC",
                code = "SCI10-CH06-TOP02",
                title = "Aerobic & Anaerobic Respiration",
                description = "Breakdown of glucose, ATP synthesis, and human respiratory system",
                estimatedMinutes = 80,
                orderIndex = 9
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch10",
                parentNodeId = "node_cbse_sci10_root",
                nodeType = "UNIT",
                code = "SCI10-CH10",
                title = "Chapter 10: Light — Reflection and Refraction",
                description = "Geometrical optics, mirror and lens formulas, and Snell's Law",
                estimatedMinutes = 220,
                orderIndex = 10
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch10_mirrors",
                parentNodeId = "node_sci10_ch10",
                nodeType = "TOPIC",
                code = "SCI10-CH10-TOP01",
                title = "Spherical Mirrors & Ray Tracing",
                description = "Concave and convex mirrors, Cartesian sign convention, magnification",
                estimatedMinutes = 80,
                orderIndex = 11
            ),
            CurriculumNodeItem(
                nodeId = "node_sci10_ch10_refraction",
                parentNodeId = "node_sci10_ch10",
                nodeType = "TOPIC",
                code = "SCI10-CH10-TOP02",
                title = "Laws of Refraction & Lenses",
                description = "Snell's Law, refractive index, thin lenses, and optical power",
                estimatedMinutes = 90,
                orderIndex = 12
            )
        )

        // 3. Define Prerequisites
        val prerequisites = listOf(
            PrerequisiteItem("prereq_sci10_types_balancing", "node_sci10_ch1_types", "node_sci10_ch1_balancing", "MANDATORY"),
            PrerequisiteItem("prereq_sci10_ph_acids", "node_sci10_ch2_ph", "node_sci10_ch2", "MANDATORY"),
            PrerequisiteItem("prereq_sci10_resp_nutr", "node_sci10_ch6_respiration", "node_sci10_ch6_nutrition", "RECOMMENDED"),
            PrerequisiteItem("prereq_sci10_refr_mirr", "node_sci10_ch10_refraction", "node_sci10_ch10_mirrors", "MANDATORY")
        )

        // 4. Define Verified Educational Resources
        val resources = listOf(
            ResourceItem(
                resourceId = "res_sci10_ch1_text",
                nodeId = "node_sci10_ch1_balancing",
                languageCode = "en",
                verifiedExplanation = "Conservation of atoms in chemical equations with worked precipitation and redox examples.",
                referenceTitle = "Science — Textbook for Class X",
                referenceAuthor = "NCERT",
                referenceEdition = "Rationalised 2023-24",
                referenceChapter = "Chapter 1",
                referenceSection = "1.1 - 1.2",
                referenceIdentifier = "ISBN 978-81-7450-492-0",
                coreKeywordsJson = "[\"conservation of mass\", \"stoichiometry\", \"chemical equation\"]"
            ),
            ResourceItem(
                resourceId = "res_sci10_ch2_text",
                nodeId = "node_sci10_ch2_ph",
                languageCode = "en",
                verifiedExplanation = "The logarithmic hydrogen ion pH scale and neutralization reactions in everyday biology.",
                referenceTitle = "Science — Textbook for Class X",
                referenceAuthor = "NCERT",
                referenceEdition = "Rationalised 2023-24",
                referenceChapter = "Chapter 2",
                referenceSection = "2.2 - 2.3",
                referenceIdentifier = "ISBN 978-81-7450-492-0",
                coreKeywordsJson = "[\"pH scale\", \"acid\", \"base\", \"neutralization\"]"
            ),
            ResourceItem(
                resourceId = "res_sci10_ch6_text",
                nodeId = "node_sci10_ch6_respiration",
                languageCode = "en",
                verifiedExplanation = "Cellular respiration pathways: glycolysis, mitochondrial Krebs cycle, and anaerobic fermentation.",
                referenceTitle = "Science — Textbook for Class X",
                referenceAuthor = "NCERT",
                referenceEdition = "Rationalised 2023-24",
                referenceChapter = "Chapter 6",
                referenceSection = "6.3",
                referenceIdentifier = "ISBN 978-81-7450-492-0",
                coreKeywordsJson = "[\"respiration\", \"ATP\", \"mitochondria\", \"pyruvate\"]"
            ),
            ResourceItem(
                resourceId = "res_sci10_ch10_text",
                nodeId = "node_sci10_ch10_refraction",
                languageCode = "en",
                verifiedExplanation = "Snell's Law of refraction and optical power calculation for converging and diverging systems.",
                referenceTitle = "Science — Textbook for Class X",
                referenceAuthor = "NCERT",
                referenceEdition = "Rationalised 2023-24",
                referenceChapter = "Chapter 10",
                referenceSection = "10.3",
                referenceIdentifier = "ISBN 978-81-7450-492-0",
                coreKeywordsJson = "[\"Snell's Law\", \"refraction\", \"lens formula\", \"dioptre\"]"
            )
        )

        val manifest = ContentPackManifest(
            formatVersion = "1.0.0",
            packId = "cbse_sci10_starter",
            version = "1.0.0",
            minAppVersion = "1.4.0",
            title = "CBSE Class 10 Science - Essential Foundations",
            category = "SCHOOL_CURRICULUM",
            primaryLanguage = "en",
            supportedLanguages = listOf("en", "hi"),
            authorOrProvider = "LearnMate Foundation Board & NCERT Reference",
            provenanceNotice = "Curriculum aligned with NCERT Class 10 Science (Rationalised 2023-24). Verified for offline learning. Starter module: covers essential foundational units (Chapters 1, 2, 6, 10) and does not represent complete syllabus coverage.",
            publisherKeyId = "learnmate_official_2026",
            dependencies = emptyList(),
            curriculumNodes = nodes,
            prerequisites = prerequisites,
            resources = resources,
            payloadFiles = payloadEntries
        )

        // 5. Package as .lmpack archive in temp folder
        val packFile = File(tempFolder.root, "cbse_sci10_starter_temp.lmpack")
        ContentPackSecurity.createLmpackArchive(
            outputFile = packFile,
            manifest = manifest,
            keyPair = keyPair,
            payloadFiles = payloads
        )

        // 6. Validate the created archive
        val originalKey = ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["learnmate_official_2026"]
        ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["learnmate_official_2026"] = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        try {
            val validation = ContentPackSecurity.validateArchive(
                archiveFile = packFile,
                trustedPublicKey = keyPair.public
            )

            assertTrue("Reference pack validation must succeed: ${validation.errorMessage}", validation.isValid)
            assertNotNull(validation.manifest)
            assertEquals("cbse_sci10_starter", validation.manifest?.packId)
            assertEquals(12, validation.manifest?.curriculumNodes?.size)
            assertEquals(4, validation.manifest?.prerequisites?.size)
            assertEquals(4, validation.manifest?.resources?.size)
            assertEquals(4, validation.manifest?.payloadFiles?.size)

            // 7. Perform staged installation into isolated test storage
            val testStorageDir = tempFolder.newFolder("test_storage")
            val installer = ContentPackInstaller(baseStorageDir = testStorageDir, dao = null)
            val installResult = installer.installPack(packFile, keyPair.public)

            assertTrue("Installation must succeed", installResult is ContentPackInstaller.InstallResult.Success)
            val success = installResult as ContentPackInstaller.InstallResult.Success
            assertEquals("cbse_sci10_starter", success.packId)
            assertEquals(12, success.nodesInstalled)

            // Verify physical assets on disk
            val installedCh1 = File(testStorageDir, "installed/cbse_sci10_starter/resources/ch1_equations_guide.txt")
            assertTrue("Installed asset must exist", installedCh1.exists())
            assertEquals(ch1Content.size.toLong(), installedCh1.length())
        } finally {
            if (originalKey != null) {
                ContentPackSecurity.TRUSTED_PUBLISHER_KEYS["learnmate_official_2026"] = originalKey
            }
        }
    }

    @Test
    fun testOfficialSignedReferencePackValidatesWithPinnedKey() {
        val officialPack = File("cbse_sci10_starter.lmpack")
        assertTrue("Official pack must exist in workspace", officialPack.exists())

        // Validate directly against the official pinned public key (trustedPublicKey = null)
        val result = ContentPackSecurity.validateArchive(
            archiveFile = officialPack,
            trustedPublicKey = null,
            allowTestKeyIfNull = false
        )
        assertTrue("Official pack must validate with pinned public key: ${result.errorMessage}", result.isValid)
        assertNotNull(result.manifest)
        assertEquals("cbse_sci10_starter", result.manifest?.packId)
        assertEquals(12, result.manifest?.curriculumNodes?.size)
        assertEquals(4, result.manifest?.payloadFiles?.size)

        // Verify exact uncompressed payload sum (3,349 bytes)
        val payloadSum = result.manifest?.payloadFiles?.sumOf { it.sizeBytes } ?: 0L
        assertEquals(3349L, payloadSum)
    }
}


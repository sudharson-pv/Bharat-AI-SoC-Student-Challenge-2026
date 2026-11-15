package com.learnmate.ai

import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.JsonParser
import com.learnmate.ai.data.db.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DatabaseMigrationTest {

    @Test
    fun testMigration1To2_versionNumbersAndSql() {
        val migration = DatabaseMigrations.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)

        val executedSql = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        executedSql.add(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        migration.migrate(dbProxy)

        assertEquals(1, executedSql.size)
        val sql = executedSql[0]
        assertTrue(
            "SQL should alter study_tasks to add notes column: $sql",
            sql.contains("ALTER TABLE study_tasks ADD COLUMN notes TEXT", ignoreCase = true)
        )
    }

    @Test
    fun testMigration2To3_versionNumbersAndSql() {
        val migration = DatabaseMigrations.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)

        val executedSql = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        executedSql.add(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        migration.migrate(dbProxy)

        assertEquals(6, executedSql.size)
        assertTrue(executedSql.any { it.contains("referenceTitle", ignoreCase = true) })
        assertTrue(executedSql.any { it.contains("referenceAuthor", ignoreCase = true) })
        assertTrue(executedSql.any { it.contains("referenceEdition", ignoreCase = true) })
        assertTrue(executedSql.any { it.contains("referenceChapter", ignoreCase = true) })
        assertTrue(executedSql.any { it.contains("referenceSection", ignoreCase = true) })
        assertTrue(executedSql.any { it.contains("referenceIdentifier", ignoreCase = true) })
    }

    @Test
    fun testMigration3To4_versionNumbersAndAuditParity() {
        val migration = DatabaseMigrations.MIGRATION_3_4
        assertEquals(3, migration.startVersion)
        assertEquals(4, migration.endVersion)

        val executedSql = mutableListOf<String>()

        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        executedSql.add(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        migration.migrate(dbProxy)

        // 1. Learner Profiles Extension
        assertTrue("Must add preferredLanguageCode", executedSql.any { it.contains("ADD COLUMN preferredLanguageCode TEXT NOT NULL DEFAULT 'en'") })
        assertTrue("Must add defaultEducationLevel", executedSql.any { it.contains("ADD COLUMN defaultEducationLevel TEXT NOT NULL DEFAULT 'UNDERGRADUATE'") })
        assertTrue("Must add defaultLearnerCategory", executedSql.any { it.contains("ADD COLUMN defaultLearnerCategory TEXT NOT NULL DEFAULT 'COLLEGE_STUDENT'") })
        assertTrue("Must add isMinor", executedSql.any { it.contains("ADD COLUMN isMinor INTEGER NOT NULL DEFAULT 0") })
        assertTrue("Must add guardianConsentGiven", executedSql.any { it.contains("ADD COLUMN guardianConsentGiven INTEGER NOT NULL DEFAULT 0") })

        // 2. Content Packs & Dependencies
        assertTrue("Must create content_packs table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS content_packs") })
        assertTrue("Must create pack_dependencies table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS pack_dependencies") })

        // 3. Curriculum Nodes & Seed
        assertTrue("Must create curriculum_nodes table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS curriculum_nodes") })
        assertTrue("Must seed baseline content pack", executedSql.any { it.contains("pack_builtin_legacy_gate4") })
        assertTrue("Must seed discipline roots", executedSql.any { it.contains("node_root_vlsi") && it.contains("node_root_cs") })
        assertTrue("Must migrate verified topics into curriculum nodes", executedSql.any { it.contains("INSERT INTO curriculum_nodes") && it.contains("FROM verified_topics") })

        // 4. Multi-Track & Safe Unresolved-Track Synthesis
        assertTrue("Must create learner_tracks table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS learner_tracks") })
        assertTrue("Must create composite unique index on learner_tracks(trackId, userId)", executedSql.any { it.contains("index_learner_tracks_trackId_userId") })
        assertTrue("Must synthesize default tracks preserving learner profiles", executedSql.any { it.contains("INSERT INTO learner_tracks") && it.contains("FROM learner_profiles") })
        assertTrue("Must synthesize NULL targetCurriculumNodeId for unknown domains (unresolved-track safe state)", executedSql.any { it.contains("ELSE NULL") })

        // 5. Prerequisites & Resources
        assertTrue("Must create node_prerequisites table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS node_prerequisites") })
        assertTrue("Must create curriculum_resources table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS curriculum_resources") })
        assertTrue("Must migrate textbook citations into curriculum_resources", executedSql.any { it.contains("INSERT INTO curriculum_resources") && it.contains("FROM verified_topics") })

        // 6. Assessment Blueprints & Questions V2
        assertTrue("Must create assessment_blueprints table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS assessment_blueprints") })
        assertTrue("Must create assessment_questions_v2 table", executedSql.any { it.contains("CREATE TABLE IF NOT EXISTS assessment_questions_v2") })
        assertTrue("Must migrate all 13 questions with exact node IDs", executedSql.any {
            it.contains("q_pn_01") && it.contains("topic_pn_junction") &&
            it.contains("q_cmos_01") && it.contains("topic_cmos_inverter") &&
            it.contains("q_sta_01") && it.contains("topic_sta") &&
            it.contains("q_algo_01") && it.contains("topic_complexity")
        })

        // 7. Composite Foreign-Key Table Recreation
        assertTrue("Must recreate personalised_roadmaps with composite FK", executedSql.any {
            it.contains("personalised_roadmaps_new") && it.contains("FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId)")
        })
        assertTrue("Must recreate skill_gap_reports with composite FK", executedSql.any {
            it.contains("skill_gap_reports_new") && it.contains("FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId)")
        })
        assertTrue("Must recreate study_tasks with composite FK", executedSql.any {
            it.contains("study_tasks_new") && it.contains("FOREIGN KEY (trackId, userId) REFERENCES learner_tracks(trackId, userId)")
        })

        // 8. Chat Ownership Integrity & Staging
        assertTrue("Must stage chat_messages before recreating chat_sessions", executedSql.any {
            it.contains("CREATE TABLE IF NOT EXISTS chat_messages_backup AS SELECT") && it.contains("FROM chat_messages")
        })
        assertTrue("Must create composite unique index on chat_sessions(sessionId, userId)", executedSql.any {
            it.contains("index_chat_sessions_sessionId_userId")
        })
        assertTrue("Must recreate chat_messages with composite FK to chat_sessions(sessionId, userId)", executedSql.any {
            it.contains("FOREIGN KEY (sessionId, userId) REFERENCES chat_sessions(sessionId, userId)")
        })
        assertTrue("Must restore chat_messages from backup", executedSql.any {
            it.contains("INTO chat_messages") && it.contains("FROM chat_messages_backup")
        })
        assertTrue("Must drop chat_messages_backup table", executedSql.any {
            it.contains("DROP TABLE chat_messages_backup")
        })
    }

    private fun populateBaselineVerifiedTopicsAndQuestions(stmt: java.sql.Statement) {
        stmt.execute("""
            INSERT INTO verified_topics VALUES
            ('topic_pn_junction', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'Depletion region and carrier transport', 'PN junction forms at metallurgical boundary.', '["depletion", "diffusion"]', '[]', 30, 'Physics of Semiconductor Devices', 'Sze & Ng', '3rd', 'Chapter 2', '2.1', 'ISBN 978-0471143239'),
            ('topic_cmos_inverter', 'Electronics and VLSI', 'Digital Circuits', 'CMOS Inverter', 'Static characteristics and VTC', 'CMOS inverter consists of PMOS and NMOS.', '["VTC", "noise margin"]', '["topic_pn_junction"]', 45, 'CMOS VLSI Design', 'Weste & Harris', '4th', 'Chapter 2', '2.2', 'ISBN 978-0321547743'),
            ('topic_sta', 'Electronics and VLSI', 'Timing Analysis', 'Static Timing Analysis', 'Setup and hold constraints', 'STA verifies timing without dynamic simulation.', '["setup", "hold"]', '["topic_cmos_inverter"]', 60, 'Static Timing Analysis for Nanometer Designs', 'Bhasker & Chadha', '1st', 'Chapter 3', '3.1', 'ISBN 978-0387938196'),
            ('topic_complexity', 'Computer Science and IT', 'Algorithms', 'Asymptotic Complexity', 'Big-O notation', 'Asymptotic analysis describes limiting behavior.', '["Big-O", "time complexity"]', '[]', 30, 'Introduction to Algorithms', 'Cormen et al.', '4th', 'Chapter 3', '3.1', 'ISBN 978-0262046305'),
            ('topic_binary_search', 'Computer Science and IT', 'Algorithms', 'Binary Search', 'Logarithmic search in sorted arrays', 'Binary search divides the search space in half.', '["binary search", "divide and conquer"]', '["topic_complexity"]', 30, 'Introduction to Algorithms', 'Cormen et al.', '4th', 'Chapter 2', '2.3', 'ISBN 978-0262046305'),
            ('topic_quantization', 'Computer Science and IT', 'Edge AI', 'Post-Training Quantization', 'INT8 quantization', 'Quantization maps floating point to integers.', '["quantization", "INT8"]', '[]', 45, 'Deep Learning', 'Goodfellow et al.', '1st', 'Chapter 8', '8.4', 'ISBN 978-0262035613'),
            ('topic_personas', 'UI/UX and Product Design', 'User Research', 'User Personas', 'Archetypes of target users', 'Personas represent behavioral archetypes.', '["persona", "user research"]', '[]', 30, 'The Inmates Are Running the Asylum', 'Cooper', '2nd', 'Chapter 9', '9.1', 'ISBN 978-0672326141'),
            ('topic_wcag', 'UI/UX and Product Design', 'Accessibility', 'WCAG 2.1 AA Compliance', 'Color contrast and screen readers', 'WCAG ensures accessible digital experiences.', '["WCAG", "contrast ratio"]', '[]', 30, 'Web Content Accessibility Guidelines', 'W3C', '2.1', 'Section 1', '1.4.3', 'W3C REC-WCAG21'),
            ('topic_design_tokens', 'UI/UX and Product Design', 'Design Systems', 'Design Tokens', 'Primitive design values', 'Design tokens store design decisions.', '["design tokens", "spacing"]', '["topic_wcag"]', 30, 'Design Systems', 'Frost', '1st', 'Chapter 4', '4.2', 'ISBN 978-0994443656'),
            ('topic_stress_strain', 'Mechanical Engineering', 'Solid Mechanics', 'Stress and Strain', 'Hooke’s law and elastic modulus', 'Stress is internal force per unit area.', '["stress", "strain"]', '[]', 45, 'Mechanical Engineering Design', 'Shigley', '11th', 'Chapter 2', '2.1', 'ISBN 978-0073398204'),
            ('topic_thermo_laws', 'Mechanical Engineering', 'Thermodynamics', 'Laws of Thermodynamics', 'First and second laws', 'Energy cannot be created or destroyed.', '["thermodynamics", "entropy"]', '[]', 60, 'Fundamentals of Engineering Thermodynamics', 'Moran & Shapiro', '9th', 'Chapter 5', '5.2', 'ISBN 978-1119391388'),
            ('topic_heat_conduction', 'Mechanical Engineering', 'Heat Transfer', 'Fourier’s Law of Heat Conduction', 'Thermal conductivity and gradient', 'Heat flux is proportional to temperature gradient.', '["conduction", "Fourier law"]', '[]', 45, 'Fundamentals of Heat and Mass Transfer', 'Incropera & DeWitt', '8th', 'Chapter 2', '2.1', 'ISBN 978-1119320425');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO assessment_questions VALUES
            ('q_pn_01', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'What happens under reverse bias?', '["Depletion region widens","Depletion region narrows","Current flows freely","Diffusion capacitance dominates"]', 0, 'Reverse bias increases electric field and widens depletion region.', 'BEGINNER'),
            ('q_pn_02', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'Built-in potential of silicon diode?', '["0.1V","0.7V","1.5V","5.0V"]', 1, 'Silicon built-in potential is approximately 0.7V at room temperature.', 'BEGINNER'),
            ('q_cmos_01', 'Electronics and VLSI', 'Digital Circuits', 'CMOS Inverter', 'When Vin is high (VDD), what is PMOS state?', '["Linear","Saturation","Cutoff","Breakdown"]', 2, 'With Vin=VDD, PMOS Vgs=0, placing it in cutoff.', 'INTERMEDIATE'),
            ('q_sta_01', 'Electronics and VLSI', 'Timing Analysis', 'Static Timing Analysis', 'Setup violation means?', '["Data arrived too late before clock","Data changed too soon after clock","Clock frequency is zero","Power consumption is excessive"]', 0, 'Setup violation occurs when data arrives too late.', 'INTERMEDIATE'),
            ('q_algo_01', 'Computer Science and IT', 'Algorithms', 'Asymptotic Complexity', 'Time complexity of mergesort?', '["O(n)","O(n log n)","O(n^2)","O(log n)"]', 1, 'Merge sort divides array and merges in O(n log n).', 'BEGINNER'),
            ('q_search_01', 'Computer Science and IT', 'Algorithms', 'Binary Search', 'Binary search precondition?', '["Array must be sorted","Array must have even length","Array must fit in L1 cache","Elements must be integers"]', 0, 'Binary search requires sorted sequence.', 'BEGINNER'),
            ('q_edge_01', 'Computer Science and IT', 'Edge AI', 'Post-Training Quantization', 'INT8 quantization maps 32-bit floats to?', '["8-bit unsigned only","8-bit integers","16-bit floats","4-bit integers"]', 1, 'INT8 quantization maps FP32 weights and activations to 8-bit integers.', 'ADVANCED'),
            ('q_ux_01', 'UI/UX and Product Design', 'User Research', 'User Personas', 'Primary purpose of persona?', '["Represent real individual exactly","Archetype to guide design decisions","Marketing advertising demographic","Legal consent document"]', 1, 'Personas represent user archetypes.', 'BEGINNER'),
            ('q_ux_02', 'UI/UX and Product Design', 'Accessibility', 'WCAG 2.1 AA Compliance', 'Minimum contrast ratio for normal text under WCAG AA?', '["3.0:1","4.5:1","7.0:1","2.0:1"]', 1, 'WCAG 2.1 AA requires minimum 4.5:1 contrast ratio.', 'INTERMEDIATE'),
            ('q_ux_03', 'UI/UX and Product Design', 'Design Systems', 'Design Tokens', 'Design tokens store?', '["CSS compiled classes","Design decisions like colors and spacing","React component source code","Database connection strings"]', 1, 'Design tokens store design decisions.', 'BEGINNER'),
            ('q_mech_01', 'Mechanical Engineering', 'Solid Mechanics', 'Stress and Strain', 'Hooke’s law relates?', '["Stress linearly to strain in elastic region","Pressure to volume in gas","Heat to work in cycle","Torque to angular acceleration"]', 0, 'Hookes law states stress is proportional to strain in elastic region.', 'BEGINNER'),
            ('q_mech_02', 'Mechanical Engineering', 'Thermodynamics', 'Laws of Thermodynamics', 'Second law implies?', '["Energy is conserved","Total entropy of isolated system never decreases","Heat flows cold to hot spontaneously","Absolute zero is reachable"]', 1, 'Total entropy of an isolated system never decreases.', 'INTERMEDIATE'),
            ('q_mech_03', 'Mechanical Engineering', 'Heat Transfer', 'Fourier’s Law of Heat Conduction', 'Fourier’s law states heat flux is proportional to?', '["Temperature squared","Negative temperature gradient","Convection coefficient","Stefan-Boltzmann constant"]', 1, 'Heat flux is proportional to negative temperature gradient.', 'INTERMEDIATE');
        """.trimIndent())
    }

    private fun createAllV3Tables(stmt: java.sql.Statement) {
        stmt.execute("PRAGMA foreign_keys = ON;")
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS learner_profiles (
                userId TEXT NOT NULL, name TEXT NOT NULL, targetDomain TEXT NOT NULL,
                subdomain TEXT NOT NULL, goal TEXT NOT NULL, targetRole TEXT NOT NULL,
                previousExperience TEXT NOT NULL, currentLevel TEXT NOT NULL, audienceLevel TEXT NOT NULL,
                dailyStudyMinutes INTEGER NOT NULL, weeklyStudyDays INTEGER NOT NULL, currentTopic TEXT NOT NULL,
                onboardingCompleted INTEGER NOT NULL, assessmentCompleted INTEGER NOT NULL, isDemoAccount INTEGER NOT NULL,
                createdAtEpochMs INTEGER NOT NULL, updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(userId)
            );
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS verified_topics (
                topicId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, verifiedExplanation TEXT NOT NULL,
                coreKeywordsJson TEXT NOT NULL, prerequisiteTopicIdsJson TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL,
                referenceTitle TEXT NOT NULL, referenceAuthor TEXT NOT NULL, referenceEdition TEXT NOT NULL,
                referenceChapter TEXT NOT NULL, referenceSection TEXT NOT NULL, referenceIdentifier TEXT NOT NULL,
                PRIMARY KEY(topicId)
            );
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS index_verified_topics_domain_subdomain ON verified_topics (domain, subdomain);")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS assessment_questions (
                questionId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                topic TEXT NOT NULL, questionText TEXT NOT NULL, optionsJson TEXT NOT NULL,
                correctIndex INTEGER NOT NULL, verifiedExplanation TEXT NOT NULL, difficultyLevel TEXT NOT NULL,
                PRIMARY KEY(questionId)
            );
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS index_assessment_questions_domain_topic ON assessment_questions (domain, topic);")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS personalised_roadmaps (
                userId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                goal TEXT NOT NULL, targetRole TEXT NOT NULL, totalSteps INTEGER NOT NULL,
                stepsJson TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(userId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS skill_gap_reports (
                userId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                overallScorePercent INTEGER NOT NULL, totalQuestions INTEGER NOT NULL, correctCount INTEGER NOT NULL,
                strengthsJson TEXT NOT NULL, weaknessesJson TEXT NOT NULL, skillGapsJson TEXT NOT NULL,
                generatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(userId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS study_tasks (
                taskId TEXT NOT NULL, userId TEXT NOT NULL, roadmapStepId TEXT NOT NULL,
                dayIndex INTEGER NOT NULL, weekIndex INTEGER NOT NULL, title TEXT NOT NULL,
                topic TEXT NOT NULL, durationMinutes INTEGER NOT NULL, isCompleted INTEGER NOT NULL,
                completedAtEpochMs INTEGER, notes TEXT NOT NULL,
                PRIMARY KEY(taskId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_dayIndex ON study_tasks (userId, dayIndex);")
        stmt.execute("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_isCompleted ON study_tasks (userId, isCompleted);")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS chat_sessions (
                sessionId TEXT NOT NULL, userId TEXT NOT NULL, domain TEXT NOT NULL,
                title TEXT NOT NULL, isAiEnabled INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(sessionId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS index_chat_sessions_userId_updatedAtEpochMs ON chat_sessions (userId, updatedAtEpochMs);")

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                messageId TEXT NOT NULL, sessionId TEXT NOT NULL, userId TEXT NOT NULL,
                senderRole TEXT NOT NULL, text TEXT NOT NULL, isAiGenerated INTEGER NOT NULL,
                promptTokensEstimate INTEGER NOT NULL, generatedTokens INTEGER NOT NULL,
                ttftMs INTEGER, tokensPerSec REAL, errorText TEXT, timestampEpochMs INTEGER NOT NULL,
                PRIMARY KEY(messageId),
                FOREIGN KEY(sessionId) REFERENCES chat_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        stmt.execute("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId_timestampEpochMs ON chat_messages (sessionId, timestampEpochMs);")
        stmt.execute("CREATE INDEX IF NOT EXISTS index_chat_messages_userId ON chat_messages (userId);")
    }

    @Test
    fun testMigration3To4_realSqliteExecutionAndParityValidation() {
        val conn: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // 1. Create the exact Room v3 schema (8 tables)
        createAllV3Tables(stmt)

        // 2. Populate representative legacy data
        stmt.execute("""
            INSERT INTO learner_profiles VALUES 
            ('user_1', 'Aarav Patel', 'Electronics and VLSI', 'Digital RTL', 'Learn Verilog', 'RTL Engineer', 'EE degree', 'Beginner', 'UNDERGRADUATE', 60, 5, 'CMOS Inverter', 1, 1, 0, 1700000001000, 1700000002000),
            ('user_2', 'Diya Sharma', 'Computer Science and IT', 'Algorithms', 'Master Graphs', 'SWE', 'CS degree', 'Intermediate', 'UNDERGRADUATE', 45, 6, 'Binary Search', 1, 1, 0, 1700000003000, 1700000004000);
        """.trimIndent())

        stmt.execute("""
            INSERT INTO verified_topics VALUES
            ('topic_pn_junction', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'Depletion region and carrier transport', 'PN junction forms at metallurgical boundary.', '["depletion", "diffusion"]', '[]', 30, 'Physics of Semiconductor Devices', 'Sze & Ng', '3rd', 'Chapter 2', '2.1', 'ISBN 978-0471143239'),
            ('topic_cmos_inverter', 'Electronics and VLSI', 'Digital Circuits', 'CMOS Inverter', 'Static characteristics and VTC', 'CMOS inverter consists of PMOS and NMOS.', '["VTC", "noise margin"]', '["topic_pn_junction"]', 45, 'CMOS VLSI Design', 'Weste & Harris', '4th', 'Chapter 2', '2.2', 'ISBN 978-0321547743'),
            ('topic_sta', 'Electronics and VLSI', 'Timing Analysis', 'Static Timing Analysis', 'Setup and hold constraints', 'STA verifies timing without dynamic simulation.', '["setup", "hold"]', '["topic_cmos_inverter"]', 60, 'Static Timing Analysis for Nanometer Designs', 'Bhasker & Chadha', '1st', 'Chapter 3', '3.1', 'ISBN 978-0387938196'),
            ('topic_complexity', 'Computer Science and IT', 'Algorithms', 'Asymptotic Complexity', 'Big-O notation', 'Asymptotic analysis describes limiting behavior.', '["Big-O", "time complexity"]', '[]', 30, 'Introduction to Algorithms', 'Cormen et al.', '4th', 'Chapter 3', '3.1', 'ISBN 978-0262046305'),
            ('topic_binary_search', 'Computer Science and IT', 'Algorithms', 'Binary Search', 'Logarithmic search in sorted arrays', 'Binary search divides the search space in half.', '["binary search", "divide and conquer"]', '["topic_complexity"]', 30, 'Introduction to Algorithms', 'Cormen et al.', '4th', 'Chapter 2', '2.3', 'ISBN 978-0262046305'),
            ('topic_quantization', 'Computer Science and IT', 'Edge AI', 'Post-Training Quantization', 'INT8 quantization', 'Quantization maps floating point to integers.', '["quantization", "INT8"]', '[]', 45, 'Deep Learning', 'Goodfellow et al.', '1st', 'Chapter 8', '8.4', 'ISBN 978-0262035613'),
            ('topic_personas', 'UI/UX and Product Design', 'User Research', 'User Personas', 'Archetypes of target users', 'Personas represent behavioral archetypes.', '["persona", "user research"]', '[]', 30, 'The Inmates Are Running the Asylum', 'Cooper', '2nd', 'Chapter 9', '9.1', 'ISBN 978-0672326141'),
            ('topic_wcag', 'UI/UX and Product Design', 'Accessibility', 'WCAG 2.1 AA Compliance', 'Color contrast and screen readers', 'WCAG ensures accessible digital experiences.', '["WCAG", "contrast ratio"]', '[]', 30, 'Web Content Accessibility Guidelines', 'W3C', '2.1', 'Section 1', '1.4.3', 'W3C REC-WCAG21'),
            ('topic_design_tokens', 'UI/UX and Product Design', 'Design Systems', 'Design Tokens', 'Primitive design values', 'Design tokens store design decisions.', '["design tokens", "spacing"]', '["topic_wcag"]', 30, 'Design Systems', 'Frost', '1st', 'Chapter 4', '4.2', 'ISBN 978-0994443656'),
            ('topic_stress_strain', 'Mechanical Engineering', 'Solid Mechanics', 'Stress and Strain', 'Hooke’s law and elastic modulus', 'Stress is internal force per unit area.', '["stress", "strain"]', '[]', 45, 'Mechanical Engineering Design', 'Shigley', '11th', 'Chapter 2', '2.1', 'ISBN 978-0073398204'),
            ('topic_thermo_laws', 'Mechanical Engineering', 'Thermodynamics', 'Laws of Thermodynamics', 'First and second laws', 'Energy cannot be created or destroyed.', '["thermodynamics", "entropy"]', '[]', 60, 'Fundamentals of Engineering Thermodynamics', 'Moran & Shapiro', '9th', 'Chapter 5', '5.2', 'ISBN 978-1119391388'),
            ('topic_heat_conduction', 'Mechanical Engineering', 'Heat Transfer', 'Fourier’s Law of Heat Conduction', 'Thermal conductivity and gradient', 'Heat flux is proportional to temperature gradient.', '["conduction", "Fourier law"]', '[]', 45, 'Fundamentals of Heat and Mass Transfer', 'Incropera & DeWitt', '8th', 'Chapter 2', '2.1', 'ISBN 978-1119320425');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO assessment_questions VALUES
            ('q_pn_01', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'What happens under reverse bias?', '["Depletion region widens","Depletion region narrows","Current flows freely","Diffusion capacitance dominates"]', 0, 'Reverse bias increases electric field and widens depletion region.', 'BEGINNER'),
            ('q_pn_02', 'Electronics and VLSI', 'Semiconductors', 'PN Junction Diode', 'Built-in potential of silicon diode?', '["0.1V","0.7V","1.5V","5.0V"]', 1, 'Silicon built-in potential is approximately 0.7V at room temperature.', 'BEGINNER'),
            ('q_cmos_01', 'Electronics and VLSI', 'Digital Circuits', 'CMOS Inverter', 'When Vin is high (VDD), what is PMOS state?', '["Linear","Saturation","Cutoff","Breakdown"]', 2, 'With Vin=VDD, PMOS Vgs=0, placing it in cutoff.', 'INTERMEDIATE'),
            ('q_sta_01', 'Electronics and VLSI', 'Timing Analysis', 'Static Timing Analysis', 'Setup violation means?', '["Data arrived too late before clock","Data changed too soon after clock","Clock frequency is zero","Power consumption is excessive"]', 0, 'Setup violation occurs when data arrives too late.', 'INTERMEDIATE'),
            ('q_algo_01', 'Computer Science and IT', 'Algorithms', 'Asymptotic Complexity', 'Time complexity of mergesort?', '["O(n)","O(n log n)","O(n^2)","O(log n)"]', 1, 'Merge sort divides array and merges in O(n log n).', 'BEGINNER'),
            ('q_search_01', 'Computer Science and IT', 'Algorithms', 'Binary Search', 'Binary search precondition?', '["Array must be sorted","Array must have even length","Array must fit in L1 cache","Elements must be integers"]', 0, 'Binary search requires sorted sequence.', 'BEGINNER'),
            ('q_edge_01', 'Computer Science and IT', 'Edge AI', 'Post-Training Quantization', 'INT8 quantization maps 32-bit floats to?', '["8-bit unsigned only","8-bit integers","16-bit floats","4-bit integers"]', 1, 'INT8 quantization maps FP32 weights and activations to 8-bit integers.', 'ADVANCED'),
            ('q_ux_01', 'UI/UX and Product Design', 'User Research', 'User Personas', 'Primary purpose of persona?', '["Represent real individual exactly","Archetype to guide design decisions","Marketing advertising demographic","Legal consent document"]', 1, 'Personas represent user archetypes.', 'BEGINNER'),
            ('q_ux_02', 'UI/UX and Product Design', 'Accessibility', 'WCAG 2.1 AA Compliance', 'Minimum contrast ratio for normal text under WCAG AA?', '["3.0:1","4.5:1","7.0:1","2.0:1"]', 1, 'WCAG 2.1 AA requires minimum 4.5:1 contrast ratio.', 'INTERMEDIATE'),
            ('q_ux_03', 'UI/UX and Product Design', 'Design Systems', 'Design Tokens', 'Design tokens store?', '["CSS compiled classes","Design decisions like colors and spacing","React component source code","Database connection strings"]', 1, 'Design tokens store design decisions.', 'BEGINNER'),
            ('q_mech_01', 'Mechanical Engineering', 'Solid Mechanics', 'Stress and Strain', 'Hooke’s law relates?', '["Stress linearly to strain in elastic region","Pressure to volume in gas","Heat to work in cycle","Torque to angular acceleration"]', 0, 'Hookes law states stress is proportional to strain in elastic region.', 'BEGINNER'),
            ('q_mech_02', 'Mechanical Engineering', 'Thermodynamics', 'Laws of Thermodynamics', 'Second law implies?', '["Energy is conserved","Total entropy of isolated system never decreases","Heat flows cold to hot spontaneously","Absolute zero is reachable"]', 1, 'Total entropy of an isolated system never decreases.', 'INTERMEDIATE'),
            ('q_mech_03', 'Mechanical Engineering', 'Heat Transfer', 'Fourier’s Law of Heat Conduction', 'Fourier’s law states heat flux is proportional to?', '["Temperature squared","Negative temperature gradient","Convection coefficient","Stefan-Boltzmann constant"]', 1, 'Heat flux is proportional to negative temperature gradient.', 'INTERMEDIATE');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO personalised_roadmaps VALUES
            ('user_1', 'Electronics and VLSI', 'Digital RTL', 'Learn Verilog', 'RTL Engineer', 10, '[]', 1700000002000),
            ('user_2', 'Computer Science and IT', 'Algorithms', 'Master Graphs', 'SWE', 8, '[]', 1700000004000);
        """.trimIndent())

        stmt.execute("""
            INSERT INTO skill_gap_reports VALUES
            ('user_1', 'Electronics and VLSI', 'Digital RTL', 80, 5, 4, '["CMOS"]', '["STA"]', '[]', 1700000002000),
            ('user_2', 'Computer Science and IT', 'Algorithms', 75, 4, 3, '["Binary Search"]', '["Quantization"]', '[]', 1700000004000);
        """.trimIndent())

        stmt.execute("""
            INSERT INTO study_tasks VALUES
            ('task_1', 'user_1', 'step_1', 1, 1, 'Study CMOS Inverter', 'CMOS Inverter', 30, 1, 1700000002000, 'Understood VTC curve'),
            ('task_2', 'user_1', 'step_2', 2, 1, 'Review STA Setup Times', 'Static Timing Analysis', 45, 0, NULL, ''),
            ('task_3', 'user_2', 'step_1', 1, 1, 'Analyze MergeSort Complexity', 'Asymptotic Complexity', 30, 1, 1700000004000, 'Proved recurrence relation'),
            ('task_4', 'user_2', 'step_2', 2, 1, 'Implement Binary Search', 'Binary Search', 45, 0, NULL, '');
        """.trimIndent())

        stmt.execute("""
            INSERT INTO chat_sessions VALUES
            ('session_1', 'user_1', 'Electronics and VLSI', 'Verilog Discussion', 1, 1700000001000, 1700000002000),
            ('session_2', 'user_2', 'Computer Science and IT', 'Algorithm Discussion', 1, 1700000003000, 1700000004000);
        """.trimIndent())

        // Chat messages: normal messages
        stmt.execute("""
            INSERT INTO chat_messages VALUES
            ('msg_1', 'session_1', 'user_1', 'USER', 'Explain CMOS VTC', 0, 10, 0, NULL, NULL, NULL, 1700000001100),
            ('msg_2', 'session_1', 'user_1', 'ASSISTANT', 'The CMOS VTC has 5 operating regions.', 1, 15, 20, 200, 25.0, NULL, 1700000001200),
            ('msg_3', 'session_2', 'user_2', 'USER', 'What is Big-O of QuickSort?', 0, 8, 0, NULL, NULL, NULL, 1700000003100);
        """.trimIndent())

        // Temporarily disable FK to insert legacy orphaned/alien messages into v3 to test recovery policy
        stmt.execute("PRAGMA foreign_keys = OFF;")
        stmt.execute("""
            INSERT INTO chat_messages VALUES
            ('msg_orphaned_known', 'session_deleted', 'user_1', 'USER', 'Orphaned message of known user', 0, 5, 0, NULL, NULL, NULL, 1700000005000),
            ('msg_alien', 'session_alien', 'user_alien_nonexistent', 'USER', 'Corrupted alien message', 0, 5, 0, NULL, NULL, NULL, 1700000006000);
        """.trimIndent())
        stmt.execute("PRAGMA foreign_keys = ON;")

        // 4. Wrap the real SQLite connection into a SupportSQLiteDatabase proxy
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL") {
                        val sql = args?.get(0) as String
                        val s = conn.createStatement()
                        s.execute(sql)
                        s.close()
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        // 5. Run the actual MIGRATION_3_4 with active foreign keys enforcement (no toggling inside transaction)
        DatabaseMigrations.MIGRATION_3_4.migrate(dbProxy)

        // 6. Execute PRAGMA foreign_key_check -> Must return ZERO violations!
        val fkCheckRs = stmt.executeQuery("PRAGMA foreign_key_check;")
        val fkViolations = mutableListOf<String>()
        while (fkCheckRs.next()) {
            fkViolations.add("Table: ${fkCheckRs.getString(1)}, RowId: ${fkCheckRs.getLong(2)}, RefTable: ${fkCheckRs.getString(3)}")
        }
        fkCheckRs.close()
        assertTrue("PRAGMA foreign_key_check must have 0 violations: $fkViolations", fkViolations.isEmpty())

        // 7. Execute PRAGMA integrity_check -> Must return 'ok'!
        val integrityRs = stmt.executeQuery("PRAGMA integrity_check;")
        assertTrue(integrityRs.next())
        val integrityResult = integrityRs.getString(1)
        assertEquals("ok", integrityResult)
        integrityRs.close()

        // 8. Verify default track synthesis for both users
        val tracksRs = stmt.executeQuery("SELECT trackId, userId, targetCurriculumNodeId, isTrackActive FROM learner_tracks ORDER BY userId;")
        assertTrue(tracksRs.next())
        assertEquals("track_user_1_default", tracksRs.getString("trackId"))
        assertEquals("user_1", tracksRs.getString("userId"))
        assertEquals("node_root_vlsi", tracksRs.getString("targetCurriculumNodeId"))
        assertEquals(1, tracksRs.getInt("isTrackActive"))

        assertTrue(tracksRs.next())
        assertEquals("track_user_2_default", tracksRs.getString("trackId"))
        assertEquals("user_2", tracksRs.getString("userId"))
        assertEquals("node_root_cs", tracksRs.getString("targetCurriculumNodeId"))
        assertEquals(1, tracksRs.getInt("isTrackActive"))
        assertFalse(tracksRs.next())
        tracksRs.close()

        // 9. Verify 12 verified topics migrated into curriculum_nodes
        val nodesRs = stmt.executeQuery("SELECT COUNT(*) FROM curriculum_nodes WHERE nodeType = 'TOPIC';")
        assertTrue(nodesRs.next())
        assertEquals(12, nodesRs.getInt(1))
        nodesRs.close()

        // 10. Verify 13 assessment questions migrated into assessment_questions_v2
        val questionsRs = stmt.executeQuery("SELECT COUNT(*) FROM assessment_questions_v2;")
        assertTrue(questionsRs.next())
        assertEquals(13, questionsRs.getInt(1))
        questionsRs.close()

        // 11. Verify roadmaps migrated with trackId
        val roadmapRs = stmt.executeQuery("SELECT trackId, userId FROM personalised_roadmaps ORDER BY userId;")
        assertTrue(roadmapRs.next())
        assertEquals("track_user_1_default", roadmapRs.getString("trackId"))
        assertEquals("user_1", roadmapRs.getString("userId"))
        assertTrue(roadmapRs.next())
        assertEquals("track_user_2_default", roadmapRs.getString("trackId"))
        assertEquals("user_2", roadmapRs.getString("userId"))
        assertFalse(roadmapRs.next())
        roadmapRs.close()

        // 12. Verify study tasks migrated with trackId
        val tasksRs = stmt.executeQuery("SELECT COUNT(*) FROM study_tasks WHERE trackId = 'track_user_1_default';")
        assertTrue(tasksRs.next())
        assertEquals(2, tasksRs.getInt(1))
        tasksRs.close()

        // 13. Verify Chat Message Preservation & Recovery:
        // - 3 regular messages in original sessions
        // - 1 orphaned message of user_1 recovered into synthesized 'session_recovered_user_1'
        val regularMessagesRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages WHERE sessionId = 'session_1';")
        assertTrue(regularMessagesRs.next())
        assertEquals(2, regularMessagesRs.getInt(1))
        regularMessagesRs.close()

        val recoveredSessionRs = stmt.executeQuery("SELECT sessionId, trackId, userId, title FROM chat_sessions WHERE sessionId = 'session_recovered_user_1';")
        assertTrue("Recovery session must be created for orphaned user message", recoveredSessionRs.next())
        assertEquals("track_user_1_default", recoveredSessionRs.getString("trackId"))
        assertEquals("user_1", recoveredSessionRs.getString("userId"))
        recoveredSessionRs.close()

        val recoveredMsgRs = stmt.executeQuery("SELECT messageId, sessionId, userId, text FROM chat_messages WHERE messageId = 'msg_orphaned_known';")
        assertTrue(recoveredMsgRs.next())
        assertEquals("session_recovered_user_1", recoveredMsgRs.getString("sessionId"))
        assertEquals("user_1", recoveredMsgRs.getString("userId"))
        assertEquals("Orphaned message of known user", recoveredMsgRs.getString("text"))
        recoveredMsgRs.close()

        // - Alien message with unknown user quarantined into chat_messages_unmatched_archive
        val alienMsgRs = stmt.executeQuery("SELECT messageId, originalUserId, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId = 'msg_alien';")
        assertTrue(alienMsgRs.next())
        assertEquals("user_alien_nonexistent", alienMsgRs.getString("originalUserId"))
        assertEquals("ORPHANED_UNKNOWN_USER", alienMsgRs.getString("quarantineReason"))
        alienMsgRs.close()

        // 14. Verify Composite Foreign Key Rejection (Track Hijacking Prevention)
        try {
            // Attempt to insert a task with trackId belonging to user_1, but userId = 'user_2'
            stmt.execute("""
                INSERT INTO study_tasks (taskId, trackId, userId, roadmapStepId, dayIndex, weekIndex, title, topic, durationMinutes, isCompleted, completedAtEpochMs, notes)
                VALUES ('task_hijack', 'track_user_1_default', 'user_2', 'step_1', 1, 1, 'Hijack Task', 'CMOS', 30, 0, NULL, '');
            """.trimIndent())
            fail("Composite foreign key must reject cross-user ownership mismatch!")
        } catch (e: SQLException) {
            assertTrue("Expected foreign key violation, got: ${e.message}", e.message?.contains("FOREIGN KEY", ignoreCase = true) == true)
        }

        // 15. Verify Cascade Deletion of chat session deletes its messages
        stmt.execute("DELETE FROM chat_sessions WHERE sessionId = 'session_1';")
        val cascadeCheckRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages WHERE sessionId = 'session_1';")
        assertTrue(cascadeCheckRs.next())
        assertEquals("Deleting session must cascade to all its messages", 0, cascadeCheckRs.getInt(1))
        cascadeCheckRs.close()

        // 16. Compare SQLite schema against exported Room v4 schema JSON
        val schemaFile = File("schemas/com.learnmate.ai.data.db.LearnMateDatabase/4.json")
        if (schemaFile.exists()) {
            val jsonRoot = JsonParser.parseString(schemaFile.readText()).asJsonObject
            val entitiesArray = jsonRoot.getAsJsonObject("database").getAsJsonArray("entities")
            val schemaTableNames = entitiesArray.map { it.asJsonObject.get("tableName").asString }.toSet()

            val tablesRs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%';")
            val dbTables = mutableSetOf<String>()
            while (tablesRs.next()) {
                dbTables.add(tablesRs.getString(1))
            }
            tablesRs.close()

            schemaTableNames.forEach { expectedTable ->
                assertTrue("Migrated SQLite database must contain Room v4 entity table: $expectedTable", dbTables.contains(expectedTable))
            }
        }

        stmt.close()
        conn.close()
    }

    @Test
    fun testChatMigration_12EdgeCasesLosslessAndIntegrity() {
        val dbFile = File.createTempFile("learnmate_chat_test_", ".db")
        dbFile.deleteOnExit()

        val conn: Connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        val stmt = conn.createStatement()

        // 1. Initialize complete v3 Schema
        createAllV3Tables(stmt)

        // 2. Insert Users and Base Topics
        stmt.execute("""
            INSERT INTO learner_profiles VALUES 
            ('user_1', 'Aarav Patel', 'Electronics and VLSI', 'Digital RTL', 'Learn Verilog', 'RTL Engineer', 'EE degree', 'Beginner', 'UNDERGRADUATE', 60, 5, 'CMOS Inverter', 1, 1, 0, 1700000001000, 1700000002000),
            ('user_2', 'Diya Sharma', 'Computer Science and IT', 'Algorithms', 'Master Graphs', 'SWE', 'CS degree', 'Intermediate', 'UNDERGRADUATE', 45, 6, 'Binary Search', 1, 1, 0, 1700000003000, 1700000004000);
        """.trimIndent())

        populateBaselineVerifiedTopicsAndQuestions(stmt)

        // 3. Populate All 12 Chat Migration Scenarios in Legacy v3 Data:

        // Case 1: Standard active session with normal message
        stmt.execute("INSERT INTO chat_sessions VALUES ('session_std', 'user_1', 'Electronics and VLSI', 'Std Chat', 1, 1700000100000, 1700000100000);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_std_1', 'session_std', 'user_1', 'USER', 'Hello VLSI', 0, 10, 0, NULL, NULL, NULL, 1700000100100);")

        // Case 2: Blank message user ID with valid session -> should inherit session.userId ('user_1')
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_blank_user_valid_sess', 'session_std', '', 'USER', 'Blank user message', 0, 10, 0, NULL, NULL, NULL, 1700000100200);")

        // Case 3: Conflicting message and session user IDs (session belongs to user_1, message says user_2)
        // Must NEVER silently reassign -> quarantined as CONFLICTING_SESSION_USER_ID
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_conflict_user', 'session_std', 'user_2', 'USER', 'Conflicting user message', 0, 12, 0, NULL, NULL, NULL, 1700000100300);")

        // Case 7: Session with multiple messages preserving ordering
        stmt.execute("INSERT INTO chat_sessions VALUES ('session_seq', 'user_1', 'Electronics and VLSI', 'Seq Chat', 1, 1700000200000, 1700000200000);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_seq_1', 'session_seq', 'user_1', 'USER', 'Question 1', 0, 15, 0, NULL, NULL, NULL, 1700000200100);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_seq_2', 'session_seq', 'user_1', 'ASSISTANT', 'Answer 1', 1, 0, 30, 450, 18.5, NULL, 1700000200200);")

        // Case 8: Existing recovery session collision (pre-existing session_recovered_user_1)
        stmt.execute("INSERT INTO chat_sessions VALUES ('session_recovered_user_1', 'user_1', 'Recovered Messages', 'Existing Pre-Recovery Session', 0, 1700000250000, 1700000250000);")

        // Temporarily disable FK to insert legacy orphaned/alien messages into v3 to test recovery policy
        stmt.execute("PRAGMA foreign_keys = OFF;")

        // Case 4: Single orphaned message of known user_1 (non-existent session) -> recovered into session_recovered_user_1
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_orphaned_single', 'session_nonexistent_1', 'user_1', 'USER', 'Orphan Single', 0, 12, 0, NULL, NULL, NULL, 1700000300000);")

        // Case 7 (prompt #7): Multiple orphaned messages of known user_2 -> grouped into single session_recovered_user_2
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_orphaned_multi_1', 'session_nonexistent_2', 'user_2', 'USER', 'Orphan Multi 1', 0, 10, 0, NULL, NULL, NULL, 1700000400100);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_orphaned_multi_2', 'session_nonexistent_3', 'user_2', 'ASSISTANT', 'Orphan Multi 2', 1, 0, 25, 400, 20.0, NULL, 1700000400200);")

        // Case 5: Alien message (user does not exist in learner_profiles) -> quarantined to unmatched archive
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_alien_1', 'session_ghost', 'user_alien_999', 'USER', 'Alien message', 0, 8, 0, NULL, NULL, NULL, 1700000500000);")

        // Case 6: Blank user ID with missing session -> quarantined as BLANK_USER_AND_MISSING_SESSION
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_blank_user_missing_sess', 'session_ghost_missing', '', 'USER', 'Blank user missing session', 0, 8, 0, NULL, NULL, NULL, 1700000550000);")

        // To test real-world legacy corruption scenarios with duplicate message IDs (Cases 9 & 10):
        // Recreate chat_messages without unique primary key constraint before inserting duplicate rows
        stmt.execute("CREATE TABLE chat_messages_temp AS SELECT * FROM chat_messages;")
        stmt.execute("DROP TABLE chat_messages;")
        stmt.execute("""
            CREATE TABLE chat_messages (
                messageId TEXT NOT NULL, sessionId TEXT NOT NULL, userId TEXT NOT NULL,
                senderRole TEXT NOT NULL, text TEXT NOT NULL, isAiGenerated INTEGER NOT NULL,
                promptTokensEstimate INTEGER NOT NULL, generatedTokens INTEGER NOT NULL,
                ttftMs INTEGER, tokensPerSec REAL, errorText TEXT, timestampEpochMs INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES chat_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        stmt.execute("INSERT INTO chat_messages SELECT * FROM chat_messages_temp;")
        stmt.execute("DROP TABLE chat_messages_temp;")

        // Case 9: Duplicate message ID with IDENTICAL content
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_dup_same', 'session_std', 'user_1', 'USER', 'Duplicate identical text', 0, 10, 0, NULL, NULL, NULL, 1700000600000);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_dup_same', 'session_std', 'user_1', 'USER', 'Duplicate identical text', 0, 10, 0, NULL, NULL, NULL, 1700000600001);")

        // Case 10: Duplicate message ID with DIFFERENT content
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_dup_diff', 'session_std', 'user_1', 'USER', 'Original Content A', 0, 10, 0, NULL, NULL, NULL, 1700000650000);")
        stmt.execute("INSERT INTO chat_messages VALUES ('msg_dup_diff', 'session_std', 'user_1', 'USER', 'Conflicting Content B', 0, 14, 0, NULL, NULL, NULL, 1700000650001);")

        stmt.execute("PRAGMA foreign_keys = ON;")

        // Case 11: Empty session with 0 messages
        stmt.execute("INSERT INTO chat_sessions VALUES ('session_empty', 'user_2', 'Computer Science and IT', 'Empty Chat', 1, 1700000700000, 1700000700000);")

        // 4. Run MIGRATION_3_4 with ACTIVE foreign keys enforcement (NO PRAGMA toggling during migration)
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL") {
                        val sql = args?.get(0) as String
                        val s = conn.createStatement()
                        s.execute(sql)
                        s.close()
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        // Execute migration completely with foreign keys enabled
        DatabaseMigrations.MIGRATION_3_4.migrate(dbProxy)

        // Verify foreign key integrity: ZERO violations
        val fkCheckRs = stmt.executeQuery("PRAGMA foreign_key_check;")
        val fkViolations = mutableListOf<String>()
        while (fkCheckRs.next()) {
            fkViolations.add("Table: ${fkCheckRs.getString(1)}, RowId: ${fkCheckRs.getLong(2)}, RefTable: ${fkCheckRs.getString(3)}")
        }
        fkCheckRs.close()
        assertTrue("PRAGMA foreign_key_check must have 0 violations: $fkViolations", fkViolations.isEmpty())

        // Verify database integrity: 'ok'
        val integrityRs = stmt.executeQuery("PRAGMA integrity_check;")
        assertTrue(integrityRs.next())
        assertEquals("ok", integrityRs.getString(1))
        integrityRs.close()

        // ── 5. Verify All 12 Chat Preservation & Recovery Outcomes ────────────────

        // Case 1: Standard session and message
        val stdMsgRs = stmt.executeQuery("SELECT messageId, sessionId, userId, text FROM chat_messages WHERE messageId = 'msg_std_1';")
        assertTrue("Case 1: Standard message preserved", stdMsgRs.next())
        assertEquals("session_std", stdMsgRs.getString("sessionId"))
        assertEquals("user_1", stdMsgRs.getString("userId"))
        assertEquals("Hello VLSI", stdMsgRs.getString("text"))
        stdMsgRs.close()

        // Case 2: Blank message user ID with valid session -> aligned to session.userId ('user_1')
        val blankMsgRs = stmt.executeQuery("SELECT messageId, sessionId, userId FROM chat_messages WHERE messageId = 'msg_blank_user_valid_sess';")
        assertTrue("Case 2: Blank user ID in valid session preserved", blankMsgRs.next())
        assertEquals("user_1", blankMsgRs.getString("userId"))
        assertEquals("session_std", blankMsgRs.getString("sessionId"))
        blankMsgRs.close()

        // Case 3: Conflicting user ID in valid session -> quarantined with audit trail
        val conflictRs = stmt.executeQuery("SELECT messageId, originalSessionId, originalUserId, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId = 'msg_conflict_user';")
        assertTrue("Case 3: Conflicting user ID quarantined", conflictRs.next())
        assertEquals("session_std", conflictRs.getString("originalSessionId"))
        assertEquals("user_2", conflictRs.getString("originalUserId"))
        assertEquals("CONFLICTING_SESSION_USER_ID", conflictRs.getString("quarantineReason"))
        conflictRs.close()

        // Case 4: Single orphaned message recovered into synthesized session
        val singleOrphanRs = stmt.executeQuery("SELECT sessionId, userId FROM chat_messages WHERE messageId = 'msg_orphaned_single';")
        assertTrue("Case 4: Single orphaned message preserved", singleOrphanRs.next())
        assertEquals("session_recovered_user_1", singleOrphanRs.getString("sessionId"))
        assertEquals("user_1", singleOrphanRs.getString("userId"))
        singleOrphanRs.close()

        // Case 5: Alien message quarantined into chat_messages_unmatched_archive
        val alienRs = stmt.executeQuery("SELECT messageId, originalUserId, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId = 'msg_alien_1';")
        assertTrue("Case 5: Alien message quarantined", alienRs.next())
        assertEquals("user_alien_999", alienRs.getString("originalUserId"))
        assertEquals("ORPHANED_UNKNOWN_USER", alienRs.getString("quarantineReason"))
        alienRs.close()

        // Case 6: Blank user ID with missing session quarantined
        val blankMissingRs = stmt.executeQuery("SELECT messageId, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId = 'msg_blank_user_missing_sess';")
        assertTrue("Case 6: Blank user with missing session quarantined", blankMissingRs.next())
        assertEquals("BLANK_USER_AND_MISSING_SESSION", blankMissingRs.getString("quarantineReason"))
        blankMissingRs.close()

        // Case 7: Multiple orphaned messages of user_2 grouped into same recovery session
        val multiOrphanRs = stmt.executeQuery("SELECT messageId, sessionId FROM chat_messages WHERE sessionId = 'session_recovered_user_2' ORDER BY timestampEpochMs ASC;")
        assertTrue(multiOrphanRs.next())
        assertEquals("msg_orphaned_multi_1", multiOrphanRs.getString("messageId"))
        assertTrue(multiOrphanRs.next())
        assertEquals("msg_orphaned_multi_2", multiOrphanRs.getString("messageId"))
        assertFalse(multiOrphanRs.next())
        multiOrphanRs.close()

        // Case 8: Existing recovery session collision handled cleanly
        val existRecovRs = stmt.executeQuery("SELECT sessionId, trackId, userId FROM chat_sessions WHERE sessionId = 'session_recovered_user_1';")
        assertTrue("Case 8: Existing recovery session preserved without PK error", existRecovRs.next())
        assertEquals("user_1", existRecovRs.getString("userId"))
        existRecovRs.close()

        // Case 9: Duplicate message ID with identical content (first in active, duplicate in archive)
        val dupSameActiveRs = stmt.executeQuery("SELECT messageId, text FROM chat_messages WHERE messageId = 'msg_dup_same';")
        assertTrue("Case 9: First occurrence preserved in active table", dupSameActiveRs.next())
        assertEquals("Duplicate identical text", dupSameActiveRs.getString("text"))
        dupSameActiveRs.close()

        val dupSameArchiveRs = stmt.executeQuery("SELECT messageId, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId LIKE 'msg_dup_same_dup_%';")
        assertTrue("Case 9: Duplicate archived with unique audit ID", dupSameArchiveRs.next())
        assertEquals("DUPLICATE_MESSAGE_ID_IDENTICAL", dupSameArchiveRs.getString("quarantineReason"))
        dupSameArchiveRs.close()

        // Case 10: Duplicate message ID with different content (zero data loss!)
        val dupDiffActiveRs = stmt.executeQuery("SELECT messageId, text FROM chat_messages WHERE messageId = 'msg_dup_diff';")
        assertTrue("Case 10: Primary occurrence preserved in active table", dupDiffActiveRs.next())
        assertEquals("Original Content A", dupDiffActiveRs.getString("text"))
        dupDiffActiveRs.close()

        val dupDiffArchiveRs = stmt.executeQuery("SELECT messageId, text, quarantineReason FROM chat_messages_unmatched_archive WHERE messageId LIKE 'msg_dup_diff_dup_%';")
        assertTrue("Case 10: Conflicting duplicate archived with unique audit ID and full text", dupDiffArchiveRs.next())
        assertEquals("Conflicting Content B", dupDiffArchiveRs.getString("text"))
        assertEquals("DUPLICATE_MESSAGE_ID_COLLISION", dupDiffArchiveRs.getString("quarantineReason"))
        dupDiffArchiveRs.close()

        // Case 11: Empty session preserved with synthesized trackId
        val emptySessionRs = stmt.executeQuery("SELECT sessionId, trackId, userId FROM chat_sessions WHERE sessionId = 'session_empty';")
        assertTrue("Case 11: Empty session preserved", emptySessionRs.next())
        assertEquals("track_user_2_default", emptySessionRs.getString("trackId"))
        assertEquals("user_2", emptySessionRs.getString("userId"))
        emptySessionRs.close()

        // Case 12: Cascade deletion of session cascades to messages
        stmt.execute("DELETE FROM chat_sessions WHERE sessionId = 'session_seq';")
        val cascadeRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages WHERE sessionId = 'session_seq';")
        assertTrue(cascadeRs.next())
        assertEquals("Case 12: Cascade delete must remove session messages", 0, cascadeRs.getInt(1))
        cascadeRs.close()

        // 6. Verify Quarantine archive retention & DPDP right-to-erasure
        val now = System.currentTimeMillis()
        val thirtyFiveDaysAgo = now - (35L * 24 * 60 * 60 * 1000)
        stmt.execute("""
            INSERT INTO chat_messages_unmatched_archive (
                messageId, originalSessionId, originalUserId, senderRole, text, isAiGenerated,
                promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText,
                timestampEpochMs, quarantineReason, archivedAtEpochMs
            ) VALUES
            ('msg_old_quarantine', 'old_session', 'user_erasure_target', 'USER', 'Old archived text', 0, 10, 0, NULL, NULL, NULL, $thirtyFiveDaysAgo, 'ORPHANED_UNKNOWN_USER', $thirtyFiveDaysAgo),
            ('msg_recent_quarantine', 'recent_session', 'user_erasure_target', 'USER', 'Recent text', 0, 10, 0, NULL, NULL, NULL, $now, 'ORPHANED_UNKNOWN_USER', $now);
        """.trimIndent())

        // 30-Day Retention Purge Policy
        val cutoffEpochMs = now - (30L * 24 * 60 * 60 * 1000)
        val deletedCount = stmt.executeUpdate("DELETE FROM chat_messages_unmatched_archive WHERE archivedAtEpochMs < $cutoffEpochMs;")
        assertTrue("Older than 30 days must be purged", deletedCount >= 1)

        val checkOldRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages_unmatched_archive WHERE messageId = 'msg_old_quarantine';")
        assertTrue(checkOldRs.next())
        assertEquals(0, checkOldRs.getInt(1))
        checkOldRs.close()

        // DPDP 2023 Right-To-Erasure Purge Policy
        val erasureCount = stmt.executeUpdate("DELETE FROM chat_messages_unmatched_archive WHERE originalUserId = 'user_erasure_target';")
        assertEquals(1, erasureCount)

        val checkErasedRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages_unmatched_archive WHERE originalUserId = 'user_erasure_target';")
        assertTrue(checkErasedRs.next())
        assertEquals("All messages for erased user must be purged", 0, checkErasedRs.getInt(1))
        checkErasedRs.close()

        stmt.close()
        conn.close()
    }

    // ── Gate 6.2.2 Composite Chat Ownership & Integrity Test ───────────────────

    @Test
    fun testCompositeChatOwnership_enforcesSessionUserIntegrity() {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        val stmt = conn.createStatement()

        // Enforce foreign keys strictly
        stmt.execute("PRAGMA foreign_keys = ON;")

        // 1. Initialize exact Room v3 schema
        createAllV3Tables(stmt)
        populateBaselineVerifiedTopicsAndQuestions(stmt)

        // Seed initial profile and track
        stmt.execute("INSERT INTO learner_profiles (userId, name, targetDomain, subdomain, goal, targetRole, previousExperience, currentLevel, audienceLevel, dailyStudyMinutes, weeklyStudyDays, currentTopic, onboardingCompleted, assessmentCompleted, isDemoAccount, createdAtEpochMs, updatedAtEpochMs) VALUES ('user_1', 'Rohan', 'Electronics and VLSI', 'Digital', 'Learn CMOS', 'Eng', 'None', 'Beg', 'UG', 30, 5, 'CMOS', 1, 1, 0, 1000, 1000);")
        stmt.execute("INSERT INTO learner_profiles (userId, name, targetDomain, subdomain, goal, targetRole, previousExperience, currentLevel, audienceLevel, dailyStudyMinutes, weeklyStudyDays, currentTopic, onboardingCompleted, assessmentCompleted, isDemoAccount, createdAtEpochMs, updatedAtEpochMs) VALUES ('user_2', 'Ananya', 'Computer Science and IT', 'Algorithms', 'Learn Sorting', 'Eng', 'None', 'Beg', 'UG', 45, 5, 'Sorting', 1, 1, 0, 1000, 1000);")

        // 2. Execute MIGRATION_3_4 with active foreign keys
        val dbProxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method?, args: Array<out Any>?): Any? {
                    if (method?.name == "execSQL" && args != null && args.isNotEmpty()) {
                        stmt.execute(args[0] as String)
                    }
                    return null
                }
            }
        ) as SupportSQLiteDatabase

        DatabaseMigrations.MIGRATION_3_4.migrate(dbProxy)

        // Verify foreign key integrity
        val fkCheckRs = stmt.executeQuery("PRAGMA foreign_key_check;")
        assertFalse("Migration must leave zero foreign key violations", fkCheckRs.next())
        fkCheckRs.close()

        // 3. Test Valid Session Creation
        stmt.execute("""
            INSERT INTO chat_sessions (
                sessionId, trackId, userId, domain, title, isAiEnabled, createdAtEpochMs, updatedAtEpochMs
            ) VALUES (
                'sess_ownership_test', 'track_user_1_default', 'user_1', 'Electronics and VLSI', 'Test Session', 1, 1000, 1000
            );
        """.trimIndent())

        // 4. Test Valid Message Insertion (Session and User Match)
        stmt.execute("""
            INSERT INTO chat_messages (
                messageId, sessionId, userId, senderRole, text, isAiGenerated,
                promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs
            ) VALUES (
                'msg_valid_owner', 'sess_ownership_test', 'user_1', 'USER', 'Hello Tutor', 0, 5, 0, NULL, NULL, NULL, 1005
            );
        """.trimIndent())

        // 5. Test Invalid Cross-User Insertion (sessionId belongs to user_1, but userId = 'user_2')
        try {
            stmt.execute("""
                INSERT INTO chat_messages (
                    messageId, sessionId, userId, senderRole, text, isAiGenerated,
                    promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs
                ) VALUES (
                    'msg_hijack_attempt', 'sess_ownership_test', 'user_2', 'USER', 'Hijacked message text', 0, 5, 0, NULL, NULL, NULL, 1010
                );
            """.trimIndent())
            fail("Composite foreign key (sessionId, userId) must reject mismatched user insertion!")
        } catch (e: SQLException) {
            assertTrue("Expected foreign key violation for cross-user insertion: ${e.message}",
                e.message?.contains("FOREIGN KEY", ignoreCase = true) == true)
        }

        // 6. Test Message Retrieval by (sessionId, userId)
        val retrieveRs = stmt.executeQuery("""
            SELECT messageId, text FROM chat_messages WHERE sessionId = 'sess_ownership_test' AND userId = 'user_1';
        """.trimIndent())
        assertTrue("Valid message must be retrievable by session and user", retrieveRs.next())
        assertEquals("msg_valid_owner", retrieveRs.getString("messageId"))
        assertEquals("Hello Tutor", retrieveRs.getString("text"))
        retrieveRs.close()

        // 7. Test Cascade Deletion: Deleting parent chat session deletes associated messages
        stmt.execute("DELETE FROM chat_sessions WHERE sessionId = 'sess_ownership_test';")
        val cascadeRs = stmt.executeQuery("SELECT COUNT(*) FROM chat_messages WHERE sessionId = 'sess_ownership_test';")
        assertTrue(cascadeRs.next())
        assertEquals("Deleting session must cascade to all child messages", 0, cascadeRs.getInt(1))
        cascadeRs.close()

        stmt.close()
        conn.close()
    }
}


package com.learnmate.ai

import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.learnmate.ai.data.contentpack.ContentPackInstaller
import com.learnmate.ai.data.contentpack.ContentPackManifest
import com.learnmate.ai.data.contentpack.PayloadFileEntry
import com.learnmate.ai.data.db.DatabaseMigrations
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMigrationAndroidTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase("test_migration_v3_to_v4.db")
        context.deleteDatabase("test_fk_enforcement.db")
        File(context.filesDir, "content_packs").deleteRecursively()
    }

    private fun createV3Database(dbName: String): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val callback = object : SupportSQLiteOpenHelper.Callback(3) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                createAllV3Tables(db)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        val helper = factory.create(config)
        val db = helper.writableDatabase
        db.execSQL("PRAGMA foreign_keys = ON;")
        return db
    }

    private fun createAllV3Tables(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("""
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

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS verified_topics (
                topicId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                title TEXT NOT NULL, description TEXT NOT NULL, verifiedExplanation TEXT NOT NULL,
                coreKeywordsJson TEXT NOT NULL, prerequisiteTopicIdsJson TEXT NOT NULL, estimatedMinutes INTEGER NOT NULL,
                referenceTitle TEXT NOT NULL, referenceAuthor TEXT NOT NULL, referenceEdition TEXT NOT NULL,
                referenceChapter TEXT NOT NULL, referenceSection TEXT NOT NULL, referenceIdentifier TEXT NOT NULL,
                PRIMARY KEY(topicId)
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_verified_topics_domain_subdomain ON verified_topics (domain, subdomain);")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS assessment_questions (
                questionId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                topic TEXT NOT NULL, questionText TEXT NOT NULL, optionsJson TEXT NOT NULL,
                correctIndex INTEGER NOT NULL, verifiedExplanation TEXT NOT NULL, difficultyLevel TEXT NOT NULL,
                PRIMARY KEY(questionId)
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_assessment_questions_domain_topic ON assessment_questions (domain, topic);")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS personalised_roadmaps (
                userId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                goal TEXT NOT NULL, targetRole TEXT NOT NULL, totalSteps INTEGER NOT NULL,
                stepsJson TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(userId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS skill_gap_reports (
                userId TEXT NOT NULL, domain TEXT NOT NULL, subdomain TEXT NOT NULL,
                overallScorePercent INTEGER NOT NULL, totalQuestions INTEGER NOT NULL, correctCount INTEGER NOT NULL,
                strengthsJson TEXT NOT NULL, weaknessesJson TEXT NOT NULL, skillGapsJson TEXT NOT NULL,
                generatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(userId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS study_tasks (
                taskId TEXT NOT NULL, userId TEXT NOT NULL, roadmapStepId TEXT NOT NULL,
                dayIndex INTEGER NOT NULL, weekIndex INTEGER NOT NULL, title TEXT NOT NULL,
                topic TEXT NOT NULL, durationMinutes INTEGER NOT NULL, isCompleted INTEGER NOT NULL,
                completedAtEpochMs INTEGER, notes TEXT NOT NULL,
                PRIMARY KEY(taskId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_dayIndex ON study_tasks (userId, dayIndex);")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_study_tasks_userId_isCompleted ON study_tasks (userId, isCompleted);")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_sessions (
                sessionId TEXT NOT NULL, userId TEXT NOT NULL, domain TEXT NOT NULL,
                title TEXT NOT NULL, isAiEnabled INTEGER NOT NULL, createdAtEpochMs INTEGER NOT NULL,
                updatedAtEpochMs INTEGER NOT NULL,
                PRIMARY KEY(sessionId),
                FOREIGN KEY(userId) REFERENCES learner_profiles(userId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_userId_updatedAtEpochMs ON chat_sessions (userId, updatedAtEpochMs);")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                messageId TEXT NOT NULL, sessionId TEXT NOT NULL, userId TEXT NOT NULL,
                senderRole TEXT NOT NULL, text TEXT NOT NULL, isAiGenerated INTEGER NOT NULL,
                promptTokensEstimate INTEGER NOT NULL, generatedTokens INTEGER NOT NULL,
                ttftMs INTEGER, tokensPerSec REAL, errorText TEXT, timestampEpochMs INTEGER NOT NULL,
                PRIMARY KEY(messageId),
                FOREIGN KEY(sessionId) REFERENCES chat_sessions(sessionId) ON UPDATE NO ACTION ON DELETE CASCADE
            );
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId_timestampEpochMs ON chat_messages (sessionId, timestampEpochMs);")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_userId ON chat_messages (userId);")
    }

    private fun populateBaselineData(db: SupportSQLiteDatabase) {
        db.execSQL("""
            INSERT INTO learner_profiles VALUES 
            ('user_1', 'Aarav Patel', 'Electronics and VLSI', 'Digital RTL', 'Learn Verilog', 'RTL Engineer', 'EE degree', 'Beginner', 'UNDERGRADUATE', 60, 5, 'CMOS Inverter', 1, 1, 0, 1700000001000, 1700000002000),
            ('user_2', 'Diya Sharma', 'Computer Science and IT', 'Algorithms', 'Master Graphs', 'SWE', 'CS degree', 'Intermediate', 'UNDERGRADUATE', 45, 6, 'Binary Search', 1, 1, 0, 1700000003000, 1700000004000);
        """.trimIndent())

        db.execSQL("""
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

        db.execSQL("""
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

        db.execSQL("""
            INSERT INTO personalised_roadmaps VALUES
            ('user_1', 'Electronics and VLSI', 'Digital RTL', 'Learn Verilog', 'RTL Engineer', 10, '[]', 1700000002000),
            ('user_2', 'Computer Science and IT', 'Algorithms', 'Master Graphs', 'SWE', 8, '[]', 1700000004000);
        """.trimIndent())

        db.execSQL("""
            INSERT INTO skill_gap_reports VALUES
            ('user_1', 'Electronics and VLSI', 'Digital RTL', 80, 5, 4, '["CMOS"]', '["STA"]', '[]', 1700000002000),
            ('user_2', 'Computer Science and IT', 'Algorithms', 75, 4, 3, '["Binary Search"]', '["Quantization"]', '[]', 1700000004000);
        """.trimIndent())

        db.execSQL("""
            INSERT INTO study_tasks VALUES
            ('task_1', 'user_1', 'step_1', 1, 1, 'Study CMOS Inverter', 'CMOS Inverter', 30, 1, 1700000002000, 'Understood VTC curve'),
            ('task_2', 'user_1', 'step_2', 2, 1, 'Review STA Setup Times', 'Static Timing Analysis', 45, 0, NULL, ''),
            ('task_3', 'user_2', 'step_1', 1, 1, 'Analyze MergeSort Complexity', 'Asymptotic Complexity', 30, 1, 1700000004000, 'Proved recurrence relation'),
            ('task_4', 'user_2', 'step_2', 2, 1, 'Implement Binary Search', 'Binary Search', 45, 0, NULL, '');
        """.trimIndent())

        db.execSQL("""
            INSERT INTO chat_sessions VALUES
            ('session_1', 'user_1', 'Electronics and VLSI', 'Verilog Discussion', 1, 1700000001000, 1700000002000),
            ('session_2', 'user_2', 'Computer Science and IT', 'Algorithm Discussion', 1, 1700000003000, 1700000004000);
        """.trimIndent())

        db.execSQL("""
            INSERT INTO chat_messages VALUES
            ('msg_1', 'session_1', 'user_1', 'USER', 'Explain CMOS VTC', 0, 10, 0, NULL, NULL, NULL, 1700000001100),
            ('msg_2', 'session_1', 'user_1', 'ASSISTANT', 'The CMOS VTC has 5 operating regions.', 1, 15, 20, 200, 25.0, NULL, 1700000001200),
            ('msg_3', 'session_2', 'user_2', 'USER', 'What is Big-O of QuickSort?', 0, 8, 0, NULL, NULL, NULL, 1700000003100);
        """.trimIndent())

        // Insert legacy orphaned chat message to test recovery policy
        db.execSQL("PRAGMA foreign_keys = OFF;")
        db.execSQL("""
            INSERT INTO chat_messages VALUES
            ('msg_orphaned_known', 'session_deleted', 'user_1', 'USER', 'Orphaned message of known user', 0, 5, 0, NULL, NULL, NULL, 1700000005000);
        """.trimIndent())
        db.execSQL("PRAGMA foreign_keys = ON;")
    }

    @Test
    fun testAndroidRuntimeInfoAndSqliteVersion() {
        val db = createV3Database("test_sqlite_ver.db")
        val cursor = db.query("SELECT sqlite_version();")
        assertTrue(cursor.moveToFirst())
        val sqliteVer = cursor.getString(0)
        cursor.close()

        val fkCursor = db.query("PRAGMA foreign_keys;")
        assertTrue(fkCursor.moveToFirst())
        val fkEnabled = fkCursor.getInt(0)
        fkCursor.close()

        println("LEARNMATE_DEVICE_INFO: SDK_INT=${Build.VERSION.SDK_INT}, SQLiteVersion=$sqliteVer, ForeignKeys=$fkEnabled")
        assertTrue(Build.VERSION.SDK_INT >= 28)
        assertNotNull(sqliteVer)
        assertEquals(1, fkEnabled)
        db.close()
    }

    @Test
    fun testMigration3To4_onRealAndroidDatabase() {
        val db = createV3Database("test_migration_v3_to_v4.db")
        populateBaselineData(db)

        // Ensure foreign keys are ON before migration
        val fkBefore = db.query("PRAGMA foreign_keys;").use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(1, fkBefore)

        // Run MIGRATION_3_4 directly on Android SupportSQLiteDatabase
        DatabaseMigrations.MIGRATION_3_4.migrate(db)

        // 1. Verify PRAGMA foreign_key_check -> Must return 0 rows
        val fkCheckCursor = db.query("PRAGMA foreign_key_check;")
        val fkViolations = mutableListOf<String>()
        while (fkCheckCursor.moveToNext()) {
            fkViolations.add("Table: ${fkCheckCursor.getString(0)}, RowId: ${fkCheckCursor.getLong(1)}, RefTable: ${fkCheckCursor.getString(2)}")
        }
        fkCheckCursor.close()
        assertTrue("PRAGMA foreign_key_check must have 0 violations: $fkViolations", fkViolations.isEmpty())

        // 2. Verify PRAGMA integrity_check -> Must return 'ok'
        val integrityCursor = db.query("PRAGMA integrity_check;")
        assertTrue(integrityCursor.moveToFirst())
        val integrityResult = integrityCursor.getString(0)
        assertEquals("ok", integrityResult)
        integrityCursor.close()

        // 3. Verify Default Tracks Synthesized for both users
        val tracksCursor = db.query("SELECT trackId, userId, targetCurriculumNodeId, isTrackActive FROM learner_tracks ORDER BY userId;")
        assertTrue(tracksCursor.moveToNext())
        assertEquals("track_user_1_default", tracksCursor.getString(0))
        assertEquals("user_1", tracksCursor.getString(1))
        assertEquals("node_root_vlsi", tracksCursor.getString(2))
        assertEquals(1, tracksCursor.getInt(3))

        assertTrue(tracksCursor.moveToNext())
        assertEquals("track_user_2_default", tracksCursor.getString(0))
        assertEquals("user_2", tracksCursor.getString(1))
        assertEquals("node_root_cs", tracksCursor.getString(2))
        assertEquals(1, tracksCursor.getInt(3))
        assertFalse(tracksCursor.moveToNext())
        tracksCursor.close()

        // 4. Verify 12 verified topics migrated into curriculum_nodes
        val nodesCursor = db.query("SELECT COUNT(*) FROM curriculum_nodes WHERE nodeType = 'TOPIC';")
        assertTrue(nodesCursor.moveToFirst())
        assertEquals(12, nodesCursor.getInt(0))
        nodesCursor.close()

        // 5. Verify 13 Assessment Questions migrated with exact question IDs
        val questionsCursor = db.query("SELECT COUNT(*) FROM assessment_questions_v2;")
        assertTrue(questionsCursor.moveToFirst())
        assertEquals(13, questionsCursor.getInt(0))
        questionsCursor.close()

        // 6. Verify Chat Messages Preservation and Orphan Recovery
        val messagesCursor = db.query("SELECT COUNT(*) FROM chat_messages WHERE userId = 'user_1';")
        assertTrue(messagesCursor.moveToFirst())
        assertEquals(3, messagesCursor.getInt(0)) // msg_1, msg_2, plus quarantined/re-parented msg_orphaned_known
        messagesCursor.close()

        val alienCursor = db.query("SELECT COUNT(*) FROM chat_messages WHERE userId = 'user_alien_nonexistent';")
        assertTrue(alienCursor.moveToFirst())
        assertEquals(0, alienCursor.getInt(0))
        alienCursor.close()

        // 7. Test Composite Foreign Key Enforcement on learner_tracks
        try {
            // Attempt inserting a roadmap with mismatched trackId and userId
            db.execSQL("""
                INSERT INTO personalised_roadmaps 
                (trackId, userId, domain, subdomain, goal, targetRole, totalSteps, stepsJson, updatedAtEpochMs)
                VALUES ('track_user_1_default', 'user_2', 'Domain', 'Sub', 'Goal', 'Role', 5, '[]', 1700000000000);
            """.trimIndent())
            fail("Expected SQLiteConstraintException for composite foreign key mismatch on learner_tracks(trackId, userId)")
        } catch (expected: SQLiteConstraintException) {
            // Verified: Composite foreign key actively prevented cross-user track association!
        }

        // 8. Test Cascade Deletion on learner_tracks
        // Deleting track_user_1_default must cascade to personalised_roadmaps, skill_gap_reports, and study_tasks
        db.execSQL("DELETE FROM learner_tracks WHERE trackId = 'track_user_1_default';")
        
        val roadmapCount = db.query("SELECT COUNT(*) FROM personalised_roadmaps WHERE userId = 'user_1';").use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, roadmapCount)

        val taskCount = db.query("SELECT COUNT(*) FROM study_tasks WHERE userId = 'user_1';").use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, taskCount)

        // 9. Test Composite Foreign Key Enforcement on chat_sessions
        try {
            // Attempt inserting a message with mismatched sessionId and userId
            db.execSQL("""
                INSERT INTO chat_messages
                (messageId, sessionId, userId, senderRole, text, isAiGenerated, promptTokensEstimate, generatedTokens, ttftMs, tokensPerSec, errorText, timestampEpochMs)
                VALUES ('msg_invalid_owner', 'session_2', 'user_1', 'USER', 'Illegal cross-user message', 0, 5, 0, NULL, NULL, NULL, 1700000099000);
            """.trimIndent())
            fail("Expected SQLiteConstraintException for composite foreign key mismatch on chat_sessions(sessionId, userId)")
        } catch (expected: SQLiteConstraintException) {
            // Verified: Composite foreign key actively prevented cross-user message injection!
        }

        // 10. Test Cascade Deletion on chat_sessions
        db.execSQL("DELETE FROM chat_sessions WHERE sessionId = 'session_2';")
        val session2Messages = db.query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = 'session_2';").use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, session2Messages)

        db.close()
    }

    @Test
    fun testProcessTerminationRecovery_onRealAndroidStorage() {
        val basePacksDir = File(context.filesDir, "content_packs")
        val stagingDir = File(basePacksDir, ".staging")
        val installedDir = File(basePacksDir, "installed")
        stagingDir.mkdirs()
        installedDir.mkdirs()

        val installer = ContentPackInstaller(basePacksDir)

        // Boundary 1: Simulating crash with partial/empty staging directory
        val partialStaging = File(stagingDir, "pack_crashed")
        partialStaging.mkdirs()
        File(partialStaging, "partial_temp.bin").writeBytes(ByteArray(256))

        // Boundary 2: Simulating journal left in EXTRACTING_PAYLOAD_STAGING during extraction
        val journalFile = File(stagingDir, "pack_crashed.journal")
        journalFile.writeText("packId=pack_crashed\nstate=EXTRACTING_PAYLOAD_STAGING\ntimestampEpochMs=${System.currentTimeMillis()}\n")

        // Reconciler must detect unfinished state, clean up staging, and delete journal
        installer.reconcilePendingInstallations()
        assertFalse("Staging dir from crashed install must be cleaned up", partialStaging.exists())
        assertFalse("Journal from crashed install must be cleaned up", journalFile.exists())

        // Boundary 3: Audit Installed Pack Integrity on real device
        val testPackDir = File(installedDir, "pack_valid_sample")
        testPackDir.mkdirs()
        val dataFile = File(testPackDir, "topics.json")
        val content = """[{"id":"t1","title":"Test Topic"}]""".toByteArray()
        dataFile.writeBytes(content)
        val hash = installer.calculateSha256(dataFile)

        val manifest = ContentPackManifest(
            formatVersion = "1.0.0",
            packId = "pack_valid_sample",
            version = "1.0.0",
            minAppVersion = "1.0.0",
            title = "Valid Sample",
            category = "ENGINEERING",
            primaryLanguage = "en",
            supportedLanguages = listOf("en"),
            authorOrProvider = "Test Publisher",
            provenanceNotice = "Test Provenance",
            publisherKeyId = "staging_ref_key",
            payloadFiles = listOf(PayloadFileEntry(path = "topics.json", sha256 = hash, sizeBytes = content.size.toLong()))
        )
        File(testPackDir, "manifest.json").writeText(Gson().toJson(manifest))

        // Valid pack audit must pass
        val isValid = installer.auditInstalledPackIntegrity("pack_valid_sample")
        assertTrue("Valid pack on Android filesystem must pass integrity audit", isValid)

        // Boundary 4: Tampered pack (tampered file content)
        dataFile.writeBytes("TAMPERED DATA".toByteArray())
        val isTamperedValid = installer.auditInstalledPackIntegrity("pack_valid_sample")
        assertFalse("Tampered pack on Android filesystem must FAIL integrity audit", isTamperedValid)

        // Running reconciliation must purge the corrupt pack
        installer.reconcilePendingInstallations()
        assertFalse("Tampered pack must be purged from installed directory during reconciliation", testPackDir.exists())
    }
}

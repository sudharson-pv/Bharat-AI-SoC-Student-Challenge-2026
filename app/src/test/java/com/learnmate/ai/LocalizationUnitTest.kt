package com.learnmate.ai

import com.learnmate.ai.domain.AudienceLevel
import com.learnmate.ai.domain.LearnerProfile
import com.learnmate.ai.localization.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalizationUnitTest {

    @Test
    fun testAppLanguageCodes() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.TAMIL, AppLanguage.fromCode("ta"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.KANNADA, AppLanguage.fromCode("kn"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("unknown_code"))
    }

    @Test
    fun testAudienceLevels() {
        assertEquals("Foundational", AudienceLevel.HIGH_SCHOOL.displayName)
        assertEquals("Academic", AudienceLevel.UNDERGRADUATE.displayName)
        assertEquals("Industry Specialist", AudienceLevel.PROFESSIONAL.displayName)
    }

    @Test
    fun testLearnerProfileCreation() {
        val profile = LearnerProfile(
            userId = "test_user_001",
            name = "Test Learner",
            targetDomain = "VLSI Design",
            previousBackground = "Electronics Engineering",
            audienceLevel = AudienceLevel.UNDERGRADUATE,
            dailyStudyMinutes = 60
        )
        assertNotNull(profile)
        assertEquals("test_user_001", profile.userId)
        assertEquals("VLSI Design", profile.targetDomain)
    }
}


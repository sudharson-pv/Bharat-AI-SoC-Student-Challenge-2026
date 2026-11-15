package com.learnmate.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlayAiPackManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEvaluateAgpCompatibility() {
        val assetManager = ModelAssetManager(tempFolder.newFolder("files"))
        val packManager = PlayAiPackManager(context = null, assetManager = assetManager)

        val analysis = packManager.evaluateAgpCompatibility()

        assertEquals("8.7.2", analysis.currentAgpVersion)
        assertFalse("AGP 8.7.2 does not support native Play AI Packs beta without AGP 8.8+", analysis.playAiPacksSupported)
        assertTrue("AGP 8.7.2 supports standard Play Asset Delivery (PAD) on-demand packaging", analysis.standardPadSupported)
        assertEquals(PlayAiPackManager.DeliveryMechanism.PLAY_ASSET_DELIVERY_ON_DEMAND, analysis.activeDeliveryMechanism)
        assertTrue(analysis.technicalRationale.contains("AGP 8.7.2 is pinned for production stability"))
    }
}


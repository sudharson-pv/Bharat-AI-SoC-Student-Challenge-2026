package com.learnmate.ai.auth

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthManagerTest {

    private fun createDummyContext(): Context {
        return ContextWrapper(null)
    }

    @Test
    fun testUnconfiguredClientIdIsBlocked() {
        val context = createDummyContext()
        val authManager = GoogleAuthManager(
            context = context,
            serverClientId = "",
            mainDispatcher = Dispatchers.Unconfined
        )

        assertFalse(authManager.isGoogleSignInConfigured())

        runBlocking {
            val result = authManager.signInWithGoogle(context)
            assertTrue(result.isFailure)
            val currentState = authManager.authState.value
            assertTrue(currentState is GoogleAuthManager.AuthState.Blocked)
            assertEquals(
                "Google OAuth Client ID is unconfigured (BLOCKED). Use offline guest mode.",
                (currentState as GoogleAuthManager.AuthState.Blocked).reason
            )
        }
    }

    @Test
    fun testSecureNonceGeneration() {
        val authManager = GoogleAuthManager()

        val nonce1 = authManager.generateSecureNonce()
        val nonce2 = authManager.generateSecureNonce()

        assertEquals("SHA-256 hex nonce must be 64 hex characters", 64, nonce1.length)
        assertEquals("SHA-256 hex nonce must be 64 hex characters", 64, nonce2.length)
        assertNotEquals("Each nonce must be cryptographically unique", nonce1, nonce2)
    }

    @Test
    fun testContinueOfflinePath() {
        val authManager = GoogleAuthManager()

        val userId = authManager.continueOffline("local_guest_42")
        assertEquals("local_guest_42", userId)

        val state = authManager.authState.value
        assertTrue(state is GoogleAuthManager.AuthState.OfflineMode)
        assertEquals("local_guest_42", (state as GoogleAuthManager.AuthState.OfflineMode).localUserId)
    }
}

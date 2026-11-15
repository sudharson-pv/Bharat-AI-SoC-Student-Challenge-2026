package com.learnmate.ai.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Gate 6.16 P1.2: Real Google Sign-In with Android Credential Manager.
 *
 * Implements genuine androidx.credentials.CredentialManager and GoogleIdOption integration.
 * Invariants:
 * 1. Prominent "Continue Offline" action allowing instant, zero-network local learning access.
 * 2. Unconfigured Client ID Safeguard: If SERVER_CLIENT_ID is blank/placeholder, live Google Auth
 *    is explicitly marked as BLOCKED, preventing silent failure or unexpected network traffic.
 * 3. Secure Nonce Generation: Uses cryptographically secure random nonces hashed via SHA-256.
 * 4. Cancellation & Exception Isolation: User cancellation does not break session state or corrupt local data.
 */
class GoogleAuthManager(
    private val context: Context? = null,
    private val serverClientId: String = SERVER_CLIENT_ID_CONFIG,
    private val credentialManagerProvider: (() -> CredentialManager)? = null,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    companion object {
        private const val TAG = "GoogleAuthManager"

        /**
         * Server Client ID (Web Application Client ID) from Google Cloud Console.
         * Default is empty/placeholder until provisioned by deployment pipeline.
         */
        const val SERVER_CLIENT_ID_CONFIG: String = "" // Placeholder when unprovisioned
    }

    data class GoogleAccount(
        val idToken: String,
        val email: String,
        val displayName: String?,
        val profilePictureUri: String?,
        val familyName: String?,
        val givenName: String?
    )

    sealed class AuthState {
        object Idle : AuthState()
        data class Blocked(val reason: String) : AuthState()
        object InProgress : AuthState()
        data class Authenticated(val account: GoogleAccount) : AuthState()
        data class OfflineMode(val localUserId: String) : AuthState()
        data class Cancelled(val reason: String = "User dismissed bottom sheet") : AuthState()
        data class Failure(val errorCode: String, val message: String) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val credentialManager: CredentialManager by lazy {
        credentialManagerProvider?.invoke()
            ?: (context?.let { CredentialManager.create(it) }
                ?: throw IllegalStateException("CredentialManager requires non-null Context"))
    }

    /**
     * Checks if Live Google Sign-In is configured and authorized.
     */
    fun isGoogleSignInConfigured(): Boolean {
        return serverClientId.isNotBlank() && !serverClientId.startsWith("UNCONFIGURED")
    }

    /**
     * Generates a 32-byte cryptographically secure SHA-256 hashed nonce.
     */
    fun generateSecureNonce(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(randomBytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Initiates Google Sign-In via Credential Manager bottom sheet.
     * Must be called with an Activity context for bottom sheet UI anchoring.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<GoogleAccount> = withContext(mainDispatcher) {
        if (!isGoogleSignInConfigured()) {
            val blockedReason = "Google OAuth Client ID is unconfigured (BLOCKED). Use offline guest mode."
            Log.w(TAG, blockedReason)
            _authState.value = AuthState.Blocked(blockedReason)
            return@withContext Result.failure(IllegalStateException(blockedReason))
        }

        _authState.value = AuthState.InProgress
        try {
            val hashedNonce = generateSecureNonce()

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = activityContext,
                request = request
            )

            val credential = response.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val account = GoogleAccount(
                    idToken = googleIdTokenCredential.idToken,
                    email = googleIdTokenCredential.id,
                    displayName = googleIdTokenCredential.displayName,
                    profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString(),
                    familyName = googleIdTokenCredential.familyName,
                    givenName = googleIdTokenCredential.givenName
                )
                _authState.value = AuthState.Authenticated(account)
                Log.i(TAG, "Successfully authenticated Google account: ${account.email}")
                Result.success(account)
            } else {
                val err = "Unsupported credential format: ${credential.type}"
                Log.e(TAG, err)
                _authState.value = AuthState.Failure("INVALID_CREDENTIAL_TYPE", err)
                Result.failure(IllegalArgumentException(err))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-In sheet")
            _authState.value = AuthState.Cancelled()
            Result.failure(e)
        } catch (e: NoCredentialException) {
            val msg = "No Google accounts available on device"
            Log.w(TAG, msg)
            _authState.value = AuthState.Failure("NO_CREDENTIAL", msg)
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager error: ${e.message}", e)
            _authState.value = AuthState.Failure("CREDENTIAL_MANAGER_ERROR", e.message ?: "Unknown error")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected authentication failure: ${e.message}", e)
            _authState.value = AuthState.Failure("UNEXPECTED_ERROR", e.message ?: "Authentication failed")
            Result.failure(e)
        }
    }

    /**
     * Prominent "Continue Offline" pathway enabling immediate full local app access
     * without network, Google accounts, or telemetry.
     */
    fun continueOffline(preferredUserId: String = "offline_learner"): String {
        Log.i(TAG, "User selected 'Continue Offline'. Local learner ID: $preferredUserId")
        _authState.value = AuthState.OfflineMode(preferredUserId)
        return preferredUserId
    }

    /**
     * Clears credential state on sign-out.
     */
    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            _authState.value = AuthState.Idle
            Log.i(TAG, "Credential state successfully cleared.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear credential state: ${e.message}")
            _authState.value = AuthState.Idle
        }
    }
}

# LearnMate AI - Optional Future Cloud Authentication Architecture

## 1. Overview
In accordance with product security and offline-first data sovereignty principles, online cloud authentication (such as Google Sign-In via Android Credential Manager) has been intentionally decoupled and removed from the active v1.0.0 product build.

All active user journeys—including onboarding, profile creation, diagnostic assessments, personalized roadmap generation, and local Llama 3.2 1B ExecuTorch inference—function 100% offline using local encrypted Room storage and on-device machine learning models. No Google account, cloud sign-in, or telemetry is required to operate the application.

## 2. Decoupled Components
The following architectural artifacts were maintained as optional modular components for future enterprise / optional cloud sync features:
- `com.learnmate.ai.auth.GoogleAuthManager`: Implemented `androidx.credentials.CredentialManager` and `GetGoogleIdOption`. It enforces a strict unconfigured Client ID safeguard that blocks network requests and falls back to offline guest mode when server credentials are not provisioned.
- `com.learnmate.ai.auth.CredentialAuthManager`: Interface for local biometric and future credential tokens.

## 3. Re-Enabling Cloud Authentication in Future Releases
To re-enable Google Sign-In in a future enterprise or cloud-sync edition:
1. Provision a Web Client ID in the Google Cloud Console for the application's production SHA-256 signing fingerprint.
2. In `app/build.gradle.kts`, restore the Credential Manager dependencies:
   ```kotlin
   implementation(libs.androidx.credentials)
   implementation(libs.androidx.credentials.play.services.auth)
   implementation(libs.googleid)
   ```
3. Populate `SERVER_CLIENT_ID` in `GoogleAuthManager.kt` via BuildConfig or secure runtime configuration.
4. Add the optional Google Sign-In action button in `OnboardingScreen.kt` or `SettingsScreen.kt` under an explicit opt-in "Cloud Sync" settings toggle.
5. Ensure local offline data remains primary and cloud sync is strictly secondary and user-consented.


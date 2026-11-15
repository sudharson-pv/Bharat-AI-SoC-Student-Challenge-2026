package com.learnmate.ai.ai

/**
 * Gate 6.16.5: Precise Lifecycle States for Mobile Model Distribution & Verification.
 *
 * Implements strict separation of states required by Section 3:
 * - NOT_INSTALLED
 * - DOWNLOADING
 * - DOWNLOAD_INTERRUPTED
 * - DOWNLOADED
 * - VERIFYING
 * - VERIFIED
 * - INSTALLING
 * - RUNTIME_INITIALIZING
 * - AI_READY
 * - FAILED
 * - INCOMPATIBLE
 */
typealias ModelInstallationState = ModelDownloadEngine.ModelDownloadState

/**
 * Tracks the provenance of the model assets on disk.
 * Preserves the architectural distinction between:
 * - Model present locally (e.g. adb push or filesystem copy)
 * - Model downloaded from the configured server
 * - Model cryptographically verified
 * - Model successfully loaded
 * - Real inference successfully completed
 */
enum class ModelProvenance {
    NONE,
    LOCAL_UNVERIFIED,
    REMOTE_DOWNLOADED,
    CRYPTOGRAPHICALLY_VERIFIED,
    RUNTIME_INITIALIZED,
    SMOKE_TEST_PASSED
}

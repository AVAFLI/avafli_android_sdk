package com.avafli.winrsdk

/**
 * Errors that can occur within the WINR SDK.
 */
sealed class WINRError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** SDK has not been initialized. Call WINR.initialize() first. */
    class NotInitialized : WINRError("WINR SDK has not been initialized. Call WINR.initialize() first.")

    /** The provided publisher key is invalid or missing. */
    class InvalidPublisherKey : WINRError("Invalid or missing publisher key.")

    /** No active campaign is available. */
    class NoCampaign : WINRError("No active campaign found.")

    /** Network request failed. */
    class NetworkError(message: String, cause: Throwable? = null) : WINRError(message, cause)

    /** Token refresh failed. */
    class TokenRefreshFailed : WINRError("Failed to refresh authentication token.")

    /** Device registration failed. */
    class RegistrationFailed(message: String) : WINRError("Device registration failed: $message")

    /** Daily entries have already been claimed today. */
    class AlreadyClaimed : WINRError("Daily entries have already been claimed today.")

    /** Server returned an error. */
    class ServerError(val code: Int, message: String) : WINRError("Server error ($code): $message")

    /** An unknown error occurred. */
    class Unknown(message: String = "An unknown error occurred", cause: Throwable? = null) :
        WINRError(message, cause)
}

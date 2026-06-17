package com.avafli.winrsdk

/**
 * Errors that can occur within the WINR SDK.
 */
sealed class WINRError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** SDK has not been configured. Call WINR.configure() first. */
    class NotInitialized : WINRError("WINR SDK has not been configured. Call WINR.configure() first.")

    /** The provided publisher key is invalid or missing. */
    class InvalidPublisherKey : WINRError("Invalid or missing publisher key.")

    /** No active giveaway is available. */
    class NoGiveaway : WINRError("No active giveaway found.")

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

    /**
     * The WINR experience is no longer available for this publisher — typically because
     * the publisher's account or API key has been suspended or revoked (e.g. billing lapse).
     * The SDK silently declines to present the default UI in this case; publishers using a
     * custom launch UI can surface their own "no longer available" messaging.
     */
    class ServiceUnavailable : WINRError("The WINR experience is no longer available.")

    /** An unknown error occurred. */
    class Unknown(message: String = "An unknown error occurred", cause: Throwable? = null) :
        WINRError(message, cause)
}

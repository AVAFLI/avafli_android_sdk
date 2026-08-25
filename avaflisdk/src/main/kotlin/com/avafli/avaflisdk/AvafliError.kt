package com.avafli.avaflisdk

/**
 * Errors that can occur within the Avafli SDK.
 */
sealed class AvafliError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** SDK has not been configured. Call Avafli.configure() first. */
    class NotInitialized : AvafliError("Avafli SDK has not been configured. Call Avafli.configure() first.")

    /** The provided publisher key is invalid or missing. */
    class InvalidPublisherKey : AvafliError("Invalid or missing publisher key.")

    /** No active giveaway is available. */
    class NoGiveaway : AvafliError("No active giveaway found.")

    /** Network request failed. */
    class NetworkError(message: String, cause: Throwable? = null) : AvafliError(message, cause)

    /** Token refresh failed. */
    class TokenRefreshFailed : AvafliError("Failed to refresh authentication token.")

    /** Device registration failed. */
    class RegistrationFailed(message: String) : AvafliError("Device registration failed: $message")

    /** Daily entries have already been claimed today. */
    class AlreadyClaimed : AvafliError("Daily entries have already been claimed today.")

    /** Server returned an error. */
    class ServerError(val code: Int, message: String) : AvafliError("Server error ($code): $message")

    /**
     * The backend geo-fence rejected the request: the promotion is US-only and
     * this request's IP either resolved outside the United States or could not
     * be verified (the fence fails closed). Mapped from the callable
     * permission-denied error bodies emitted by functions/src/gatekeeper.ts.
     */
    class GeoBlocked : AvafliError("This promotion is not available in your location.")

    /**
     * The Avafli experience is no longer available for this publisher — typically because
     * the publisher's account or API key has been suspended or revoked (e.g. billing lapse).
     * The SDK silently stops auto-opening the experience in this case.
     */
    class ServiceUnavailable : AvafliError("The Avafli experience is no longer available.")

    /**
     * The user opted out (RTD — Right To Delete). The experience is permanently
     * silenced on this device — it is never presented again.
     */
    class OptedOut : AvafliError("The user has opted out of the Avafli experience.")

    /** An unknown error occurred. */
    class Unknown(message: String = "An unknown error occurred", cause: Throwable? = null) :
        AvafliError(message, cause)
}

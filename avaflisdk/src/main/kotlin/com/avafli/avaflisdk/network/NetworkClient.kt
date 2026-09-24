package com.avafli.avaflisdk.network

import com.avafli.avaflisdk.AvafliConfiguration
import com.avafli.avaflisdk.AvafliError
import com.avafli.avaflisdk.AvafliEnvironment
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp-based network client with automatic token refresh on 401.
 *
 * Token-refresh hardening (3.1.4, the Sept 24 cold-open failure): the JWT
 * `exp` pre-check refreshes BEFORE a guaranteed-401 request, and refreshes
 * are single-flight — concurrent callers (the post-configure status fetch,
 * the drawer's own load, a push-token registration) share ONE in-flight
 * refresh instead of racing the same refresh token to the endpoint.
 *
 * @param baseUrlOverride Test seam only: points requests at a MockWebServer
 *   instead of [AvafliConfiguration.baseUrl].
 */
internal class NetworkClient(
    private val config: AvafliConfiguration,
    private val secureStorage: SecureStorage,
    private val logger: Logger,
    private val baseUrlOverride: String? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Single-flight refresh state: the one in-flight refresh (if any), guarded
     * by [refreshMutex]. The refresh itself runs in [refreshScope] so a caller
     * being cancelled mid-await never cancels the refresh other callers share.
     */
    private val refreshMutex = Mutex()
    private var inFlightRefresh: Deferred<String>? = null
    private val refreshScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)

        // Certificate pinning for production.
        // We CA-pin Google Trust Services rather than the leaf cert: Cloud Functions
        // hosts (*.cloudfunctions.net) rotate their leaf certificates frequently, but the
        // issuing GTS CA chain is stable. Pinning the GTS Root R1 (valid to 2036) plus the
        // GTS WR2 intermediate as a backup avoids hard outages on leaf rotation while still
        // defeating MITM via a rogue CA. These are SPKI SHA-256 pins in OkHttp format.
        if (config.environment == AvafliEnvironment.Production && config.options.enableCertificatePinning) {
            val certificatePinner = CertificatePinner.Builder()
                // GTS Root R1 (stable to 2036)
                .add("*.cloudfunctions.net", "sha256/hxqRlPTu1bMS/0DITB1SSu0vd4u/8l8TjPgfaAp63Gc=")
                // GTS WR2 intermediate (backup)
                .add("*.cloudfunctions.net", "sha256/YPtHaftLw6/0vnc2BnNKGF54xiCA28WFcccjkA4ypCM=")
                .build()
            builder.certificatePinner(certificatePinner)
        }

        builder.build()
    }

    /**
     * Execute an authenticated POST request.
     * Automatically handles 401 by refreshing the token and retrying once.
     */
    suspend fun authenticatedPost(
        endpoint: String,
        body: Map<String, JsonElement> = emptyMap()
    ): JsonObject {
        var token = secureStorage.getToken()
            ?: throw AvafliError.TokenRefreshFailed()

        // Proactive JWT expiry check: avoid a guaranteed-401 round trip when we can
        // already see the token is expired (or malformed). The server still validates
        // the signature; this is a cheap client-side pre-check only.
        if (isJwtExpired(token)) {
            logger.debug("Session token expired (exp pre-check), refreshing before request")
            token = refreshToken()
        }

        return try {
            executePost(endpoint, body, token)
        } catch (e: AvafliError.ServerError) {
            if (e.code == 401) {
                logger.debug("Received 401, attempting token refresh")
                val newToken = refreshToken(rejectedToken = token)
                executePost(endpoint, body, newToken)
            } else {
                throw e
            }
        }
    }

    /**
     * Execute an unauthenticated POST request.
     */
    suspend fun post(
        endpoint: String,
        body: Map<String, JsonElement> = emptyMap()
    ): JsonObject {
        return executePost(endpoint, body, authToken = null)
    }

    /**
     * Switch the persisted session to a different user. Used for cross-device
     * streak unification: when submitEmail adopts an existing canonical user,
     * the SDK swaps to those credentials so subsequent requests act as that user.
     * Tokens are read from [secureStorage] on every request, so saving here is
     * enough — no in-flight client state to update.
     */
    fun saveSession(token: String, refreshToken: String?, uuid: String) {
        secureStorage.saveToken(token)
        refreshToken?.let { secureStorage.saveRefreshToken(it) }
        secureStorage.saveUuid(uuid)
    }

    private suspend fun executePost(
        endpoint: String,
        body: Map<String, JsonElement>,
        authToken: String?
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "${baseUrlOverride ?: config.baseUrl}/$endpoint"
        val wrappedBody = buildJsonObject {
            put("data", JsonObject(body))
        }

        val requestBody = json.encodeToString(JsonObject.serializer(), wrappedBody)
            .toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")

        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
        }

        val request = requestBuilder.build()
        logger.debug("POST $url")

        try {
            val response = client.newCall(request).await()
            val responseBody = response.body?.string()
                ?: throw AvafliError.NetworkError("Empty response body")

            // Do NOT log the response body: it carries session tokens and PII.
            logger.debug("Response ${response.code} (${responseBody.length} bytes)")

            if (!response.isSuccessful) {
                // Geo-fence rejections get their own typed error so the UI can
                // show the dedicated "Not available in your location" state
                // instead of a generic failure.
                if (isGeoFenceRejection(response.code, responseBody)) {
                    throw AvafliError.GeoBlocked()
                }
                throw AvafliError.ServerError(response.code, responseBody)
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            // Firebase callable returns {result: {...}} wrapper
            jsonResponse["result"]?.jsonObject ?: jsonResponse
        } catch (e: AvafliError) {
            throw e
        } catch (e: IOException) {
            throw AvafliError.NetworkError("Network request failed: ${e.message}", e)
        } catch (e: Exception) {
            throw AvafliError.Unknown("Unexpected error: ${e.message}", e)
        }
    }

    /**
     * Lightweight JWT structure + `exp` pre-check.
     *
     * Verifies the token has three dot-separated segments and decodes the payload to
     * read `exp` (seconds since epoch). Returns true if the token is malformed or the
     * expiry is in the past or within [leewaySeconds] (60 s — covers clock skew plus
     * the request's own latency, so a token that dies mid-flight is refreshed first).
     * This is NOT a signature check — the server remains the source of truth — it
     * only avoids a doomed request.
     */
    internal fun isJwtExpired(token: String, leewaySeconds: Long = EXPIRY_LEEWAY_SECONDS): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size != 3) return true
            val payloadJson = String(
                Base64.getUrlDecoder().decode(parts[1]),
                Charsets.UTF_8
            )
            val exp = json.parseToJsonElement(payloadJson)
                .jsonObject["exp"]?.jsonPrimitive?.longOrNull
                ?: return false // no exp claim → let the server decide
            val nowSeconds = System.currentTimeMillis() / 1000
            nowSeconds >= (exp - leewaySeconds)
        } catch (e: Exception) {
            // Malformed payload → treat as expired so we refresh rather than send garbage.
            true
        }
    }

    /**
     * Refresh the authentication token — single-flight.
     *
     * Every concurrent caller awaits the same [Deferred]; a new refresh starts
     * only when none is in flight. [rejectedToken] is the token the caller just
     * used: if the stored token already differs (another caller's refresh landed
     * between our request and our 401) and still looks live, it is returned
     * without another round trip.
     */
    private suspend fun refreshToken(rejectedToken: String? = null): String {
        val deferred = refreshMutex.withLock {
            // A completed Deferred is simply replaced — no explicit clearing
            // step, so a cancelled awaiter can never leave stale state behind.
            inFlightRefresh?.takeIf { it.isActive }?.let { return@withLock it }
            if (rejectedToken != null) {
                val current = secureStorage.getToken()
                if (current != null && current != rejectedToken && !isJwtExpired(current)) {
                    logger.debug("Token already refreshed by another caller; reusing it")
                    return current
                }
            }
            refreshScope.async { performRefresh() }.also { inFlightRefresh = it }
        }
        return deferred.await()
    }

    /** The actual refresh round trip; runs at most once at a time (see [refreshToken]). */
    private suspend fun performRefresh(): String {
        try {
            val refreshToken = secureStorage.getRefreshToken()
                ?: throw AvafliError.TokenRefreshFailed()

            val body = mapOf(
                "refreshToken" to JsonPrimitive(refreshToken)
            )

            val response = executePost("refreshToken", body, authToken = null)

            val newToken = response["token"]?.jsonPrimitive?.contentOrNull
                ?: throw AvafliError.TokenRefreshFailed()
            val newRefreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull
                ?: throw AvafliError.TokenRefreshFailed()

            secureStorage.saveToken(newToken)
            secureStorage.saveRefreshToken(newRefreshToken)

            logger.debug("Token refreshed successfully")
            return newToken
        } catch (e: Exception) {
            logger.error("Token refresh failed: ${e.message}")
            secureStorage.clearTokens()
            throw AvafliError.TokenRefreshFailed()
        }
    }

    companion object {
        private val geoJson = Json { ignoreUnknownKeys = true; isLenient = true }

        /** Refresh when the token expires within this window (see [isJwtExpired]). */
        internal const val EXPIRY_LEEWAY_SECONDS = 60L

        /**
         * Detect a backend geo-fence rejection from a callable error response.
         *
         * The backend (functions/src/gatekeeper.ts, enforceGeoFence) throws
         * HttpsError("permission-denied", …) with one of two fixed messages —
         * "We couldn't verify your location. This promotion is only available
         * in the United States." (inconclusive lookup, fence fails closed) or
         * "This promotion is only available to users located in one of the 50
         * United States or Washington, D.C." (confirmed non-US). Over the
         * callable HTTP protocol that arrives as a 403 with body
         * {"error": {"message": …, "status": "PERMISSION_DENIED"}}.
         *
         * Matching requires BOTH the PERMISSION_DENIED status and a location
         * phrase from those messages, so other permission-denied rejections
         * (e.g. ban enforcement) never masquerade as geo blocks.
         */
        internal fun isGeoFenceRejection(httpCode: Int, body: String): Boolean {
            if (httpCode != 403) return false
            return try {
                val error = geoJson.parseToJsonElement(body)
                    .jsonObject["error"]?.jsonObject ?: return false
                val status = error["status"]?.jsonPrimitive?.contentOrNull
                    ?: error["code"]?.jsonPrimitive?.contentOrNull
                val message = error["message"]?.jsonPrimitive?.contentOrNull ?: ""
                status?.uppercase() == "PERMISSION_DENIED" && (
                    message.contains("promotion is only available", ignoreCase = true) ||
                        message.contains("verify your location", ignoreCase = true)
                    )
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Extension to make OkHttp calls suspendable.
     */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isCancelled) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }
}

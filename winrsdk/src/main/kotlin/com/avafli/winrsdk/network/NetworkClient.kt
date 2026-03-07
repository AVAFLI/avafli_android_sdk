package com.avafli.winrsdk.network

import com.avafli.winrsdk.WINRConfiguration
import com.avafli.winrsdk.WINRError
import com.avafli.winrsdk.WINREnvironment
import com.avafli.winrsdk.services.Logger
import com.avafli.winrsdk.storage.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OkHttp-based network client with automatic token refresh on 401.
 */
internal class NetworkClient(
    private val config: WINRConfiguration,
    private val secureStorage: SecureStorage,
    private val logger: Logger
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val isRefreshing = AtomicBoolean(false)

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.options.networkTimeoutSeconds, TimeUnit.SECONDS)

        // Certificate pinning for production
        if (config.environment == WINREnvironment.Production && config.options.enableCertificatePinning) {
            val certificatePinner = CertificatePinner.Builder()
                .add("us-central1-winr-9c11f.cloudfunctions.net", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
                .build()
            // Note: In production, replace the pin above with actual certificate hash
            // builder.certificatePinner(certificatePinner)
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
        val token = secureStorage.getToken()
            ?: throw WINRError.TokenRefreshFailed()

        return try {
            executePost(endpoint, body, token)
        } catch (e: WINRError.ServerError) {
            if (e.code == 401) {
                logger.debug("Received 401, attempting token refresh")
                val newToken = refreshToken()
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

    private suspend fun executePost(
        endpoint: String,
        body: Map<String, JsonElement>,
        authToken: String?
    ): JsonObject = withContext(Dispatchers.IO) {
        val url = "${config.baseUrl}/$endpoint"
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
                ?: throw WINRError.NetworkError("Empty response body")

            logger.debug("Response ${response.code}: $responseBody")

            if (!response.isSuccessful) {
                throw WINRError.ServerError(response.code, responseBody)
            }

            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject

            // Firebase callable returns {result: {...}} wrapper
            jsonResponse["result"]?.jsonObject ?: jsonResponse
        } catch (e: WINRError) {
            throw e
        } catch (e: IOException) {
            throw WINRError.NetworkError("Network request failed: ${e.message}", e)
        } catch (e: Exception) {
            throw WINRError.Unknown("Unexpected error: ${e.message}", e)
        }
    }

    /**
     * Refresh the authentication token.
     */
    private suspend fun refreshToken(): String {
        if (!isRefreshing.compareAndSet(false, true)) {
            // Another coroutine is already refreshing, wait briefly
            kotlinx.coroutines.delay(1000)
            return secureStorage.getToken()
                ?: throw WINRError.TokenRefreshFailed()
        }

        try {
            val refreshToken = secureStorage.getRefreshToken()
                ?: throw WINRError.TokenRefreshFailed()

            val body = mapOf(
                "refreshToken" to JsonPrimitive(refreshToken)
            )

            val response = executePost("refreshToken", body, authToken = null)

            val newToken = response["token"]?.jsonPrimitive?.contentOrNull
                ?: throw WINRError.TokenRefreshFailed()
            val newRefreshToken = response["refreshToken"]?.jsonPrimitive?.contentOrNull
                ?: throw WINRError.TokenRefreshFailed()

            secureStorage.saveToken(newToken)
            secureStorage.saveRefreshToken(newRefreshToken)

            logger.debug("Token refreshed successfully")
            return newToken
        } catch (e: Exception) {
            logger.error("Token refresh failed: ${e.message}")
            secureStorage.clearTokens()
            throw WINRError.TokenRefreshFailed()
        } finally {
            isRefreshing.set(false)
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

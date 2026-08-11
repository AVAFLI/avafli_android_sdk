package com.avafli.winrsdk.services

import android.content.Context
import com.avafli.winrsdk.network.WinrApi
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages FCM push notification token registration.
 */
internal class PushNotificationManager(
    private val api: WinrApi,
    private val logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    /**
     * Register the FCM token with the WINR backend.
     */
    fun registerToken(token: String) {
        scope.launch {
            try {
                val success = api.registerPushToken(token)
                if (success) {
                    logger.debug("Push token registered successfully")
                } else {
                    logger.warn("Push token registration returned false")
                }
            } catch (e: Exception) {
                logger.error("Failed to register push token: ${e.message}", e)
            }
        }
    }

    /**
     * Resolve the current FCM token and hand it to [callback] (null on any
     * failure). Firebase Messaging is a `compileOnly` dependency: the SDK
     * compiles against the API, but the host app supplies it at runtime. If the
     * host app has NOT bundled firebase-messaging, the class-load fails with a
     * [NoClassDefFoundError] (a Throwable, not an Exception) — caught here so
     * the SDK degrades gracefully to "no token" instead of crashing.
     */
    fun getCurrentToken(context: Context, callback: (String?) -> Unit) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    logger.debug("FCM token retrieved")
                    callback(token)
                }
                .addOnFailureListener { e ->
                    logger.error("Failed to retrieve FCM token: ${e.message}", e)
                    callback(null)
                }
        } catch (t: Throwable) {
            // NoClassDefFoundError when the host app hasn't included
            // firebase-messaging, or any other init failure.
            logger.debug("Firebase Messaging not available — host app must provide FCM token (${t.message})")
            callback(null)
        }
    }
}

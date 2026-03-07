package com.avafli.winrsdk.services

import android.content.Context
import com.avafli.winrsdk.network.WinrApi
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
     * Get the current FCM token.
     * Requires firebase-messaging dependency.
     */
    fun getCurrentToken(context: Context, callback: (String?) -> Unit) {
        try {
            val firebaseMessaging = Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            val getInstance = firebaseMessaging.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val getToken = firebaseMessaging.getMethod("getToken")
            val task = getToken.invoke(instance)

            // Use reflection to avoid hard dependency on Firebase
            val taskClass = Class.forName("com.google.android.gms.tasks.Task")
            val addOnSuccessListener = taskClass.getMethod(
                "addOnSuccessListener",
                Class.forName("com.google.android.gms.tasks.OnSuccessListener")
            )

            // This is a simplified approach — in practice, the host app handles FCM setup
            logger.debug("FCM token retrieval initiated via reflection")
            callback(null) // Host app should provide token via WINR.onNewToken()
        } catch (e: ClassNotFoundException) {
            logger.debug("Firebase Messaging not available — host app must provide FCM token")
            callback(null)
        } catch (e: Exception) {
            logger.error("Error getting FCM token: ${e.message}", e)
            callback(null)
        }
    }
}

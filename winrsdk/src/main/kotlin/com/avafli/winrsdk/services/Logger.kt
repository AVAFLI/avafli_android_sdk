package com.avafli.winrsdk.services

import android.util.Log

/**
 * SDK logger with configurable verbosity.
 */
internal class Logger(private val isDebug: Boolean = false) {

    fun debug(message: String) {
        if (isDebug) {
            Log.d(TAG, message)
        }
    }

    fun info(message: String) {
        // Gated like the other SDKs (iOS/Web/Flutter default to error/warn):
        // publishers' release builds shouldn't emit WINR chatter.
        if (isDebug) {
            Log.i(TAG, message)
        }
    }

    fun warn(message: String) {
        Log.w(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    companion object {
        private const val TAG = "WINR-SDK"
    }
}

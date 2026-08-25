package com.avafli.avaflisdk.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage using AndroidX EncryptedSharedPreferences.
 * Backed by Android Keystore for key management.
 */
internal class SecureStorage(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveRefreshToken(refreshToken: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply()
    }

    fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    fun saveUuid(uuid: String) {
        prefs.edit().putString(KEY_UUID, uuid).apply()
    }

    fun getUuid(): String? {
        return prefs.getString(KEY_UUID, null)
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return prefs.getBoolean(key, default)
    }

    /**
     * Stable per-install guest identity, minted on first use. Persisted in
     * EncryptedSharedPreferences so a guest's attribution doesn't churn per
     * session.
     *
     * Stored identity is NEVER migrated: an id persisted under the pre-3.0
     * `winr_guest_` prefix keeps being returned verbatim — only ids minted
     * from 3.0.0 on carry the `avafli_guest_` prefix.
     */
    fun loadOrCreateGuestId(): String {
        getString(KEY_GUEST_ID)?.let { return it }
        return mintGuestId().also { saveString(KEY_GUEST_ID, it) }
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        // Prefs file and keys deliberately keep their pre-rebrand winr_ names —
        // renaming them would orphan existing installs' auth, identity, and
        // opt-out state on upgrade.
        private const val PREFS_NAME = "winr_secure_prefs"
        private const val KEY_TOKEN = "winr_auth_token"
        private const val KEY_REFRESH_TOKEN = "winr_refresh_token"
        private const val KEY_UUID = "winr_device_uuid"
        private const val KEY_GUEST_ID = "winr_guest_id"

        /** A fresh guest id — `avafli_guest_` + lowercase UUID (3.0.0+). */
        internal fun mintGuestId(): String =
            "avafli_guest_" + java.util.UUID.randomUUID().toString().lowercase()
    }
}

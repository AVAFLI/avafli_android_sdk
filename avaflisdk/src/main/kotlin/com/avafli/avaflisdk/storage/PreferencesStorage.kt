package com.avafli.avaflisdk.storage

import android.content.Context
import android.content.SharedPreferences
import com.avafli.avaflisdk.offline.OfflineStateStore

/**
 * Non-sensitive preferences storage using standard SharedPreferences.
 * Used for streak state, UI preferences, and other non-secret data.
 * Implements [OfflineStateStore] so the offline retry queue and analytics
 * buffer persist alongside the SDK's other state (non-secret material, so
 * SharedPreferences rather than SecureStorage).
 */
internal class PreferencesStorage(context: Context) : OfflineStateStore {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * Auto-present keys are suffixed with the package name (mirrors the iOS
     * per-bundle keys) so state never cross-contaminates between publisher apps.
     */
    private val packageName: String = context.packageName

    fun saveStreakDay(day: Int) {
        prefs.edit().putInt(KEY_STREAK_DAY, day).apply()
    }

    fun getStreakDay(): Int {
        return prefs.getInt(KEY_STREAK_DAY, 0)
    }

    fun saveLastClaimDate(date: String) {
        prefs.edit().putString(KEY_LAST_CLAIM_DATE, date).apply()
    }

    fun getLastClaimDate(): String? {
        return prefs.getString(KEY_LAST_CLAIM_DATE, null)
    }

    fun saveTotalEntries(entries: Int) {
        prefs.edit().putInt(KEY_TOTAL_ENTRIES, entries).apply()
    }

    fun getTotalEntries(): Int {
        return prefs.getInt(KEY_TOTAL_ENTRIES, 0)
    }

    fun saveEmailSubmitted(submitted: Boolean) {
        prefs.edit().putBoolean(KEY_EMAIL_SUBMITTED, submitted).apply()
    }

    fun isEmailSubmitted(): Boolean {
        return prefs.getBoolean(KEY_EMAIL_SUBMITTED, false)
    }

    fun saveCompletedDays(days: List<Boolean>) {
        val encoded = days.joinToString(",") { if (it) "1" else "0" }
        prefs.edit().putString(KEY_COMPLETED_DAYS, encoded).apply()
    }

    fun getCompletedDays(): List<Boolean> {
        val encoded = prefs.getString(KEY_COMPLETED_DAYS, null) ?: return List(7) { false }
        return encoded.split(",").map { it == "1" }
    }

    // ── Auto-present (V2 experience: open once per calendar day) ──

    fun saveLastAutoPresentDay(day: String) {
        prefs.edit().putString("${KEY_LAST_AUTO_PRESENT}_$packageName", day).apply()
    }

    fun getLastAutoPresentDay(): String? {
        return prefs.getString("${KEY_LAST_AUTO_PRESENT}_$packageName", null)
    }

    fun saveUnregisteredImpressions(count: Int) {
        prefs.edit().putInt("${KEY_UNREGISTERED_IMPRESSIONS}_$packageName", count).apply()
    }

    fun getUnregisteredImpressions(): Int {
        return prefs.getInt("${KEY_UNREGISTERED_IMPRESSIONS}_$packageName", 0)
    }

    /** RTD opt-out — once set, the experience is permanently silenced on this device. */
    fun saveOptedOut(optedOut: Boolean) {
        prefs.edit().putBoolean("${KEY_OPTED_OUT}_$packageName", optedOut).apply()
    }

    fun isOptedOut(): Boolean {
        return prefs.getBoolean("${KEY_OPTED_OUT}_$packageName", false)
    }

    // ── OfflineStateStore (offline retry queue + analytics buffer) ──
    // Callers pass fully-namespaced keys (winr_… + `_$packageName` suffix).

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "winr_preferences"
        private const val KEY_STREAK_DAY = "streak_day"
        private const val KEY_LAST_CLAIM_DATE = "last_claim_date"
        private const val KEY_TOTAL_ENTRIES = "total_entries"
        private const val KEY_EMAIL_SUBMITTED = "email_submitted"
        private const val KEY_COMPLETED_DAYS = "completed_days"
        private const val KEY_LAST_AUTO_PRESENT = "winr_last_auto_present"
        private const val KEY_UNREGISTERED_IMPRESSIONS = "winr_unregistered_impressions"
        private const val KEY_OPTED_OUT = "winr_opted_out"
    }
}

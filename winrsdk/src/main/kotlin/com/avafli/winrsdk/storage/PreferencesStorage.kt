package com.avafli.winrsdk.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Non-sensitive preferences storage using standard SharedPreferences.
 * Used for streak state, UI preferences, and other non-secret data.
 */
internal class PreferencesStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

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

    fun saveWeeklyDaysCompleted(days: Int) {
        prefs.edit().putInt(KEY_WEEKLY_DAYS, days).apply()
    }

    fun getWeeklyDaysCompleted(): Int {
        return prefs.getInt(KEY_WEEKLY_DAYS, 0)
    }

    fun saveMonthlyDaysCompleted(days: Int) {
        prefs.edit().putInt(KEY_MONTHLY_DAYS, days).apply()
    }

    fun getMonthlyDaysCompleted(): Int {
        return prefs.getInt(KEY_MONTHLY_DAYS, 0)
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

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "winr_preferences"
        private const val KEY_STREAK_DAY = "streak_day"
        private const val KEY_LAST_CLAIM_DATE = "last_claim_date"
        private const val KEY_TOTAL_ENTRIES = "total_entries"
        private const val KEY_WEEKLY_DAYS = "weekly_days_completed"
        private const val KEY_MONTHLY_DAYS = "monthly_days_completed"
        private const val KEY_EMAIL_SUBMITTED = "email_submitted"
        private const val KEY_COMPLETED_DAYS = "completed_days"
    }
}

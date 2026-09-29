package com.avafli.avaflisdk

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.storage.SecureStorage
import com.avafli.avaflisdk.ui.AvafliExperienceViewModel
import com.avafli.avaflisdk.ui.OptOutPhase
import com.avafli.avaflisdk.ui.v2.AvafliV2Strings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * 24-hour rejoin after "Delete my data" (3.2.0). For 24 hours the device
 * stays silent exactly as before; after that the local opt-out AND every
 * piece of the deleted profile's session are cleared, and the device
 * registers again as a brand-new participant.
 *
 * Runs the real [PreferencesStorage] over an in-memory SharedPreferences so
 * each persisted key is asserted by name, not by mock interaction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OptOutRejoinTest {

    private companion object {
        const val PKG = "com.example.host"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val KEY_OPTED_OUT = "winr_opted_out_$PKG"
        const val KEY_OPTED_OUT_UNTIL = "winr_opted_out_until_$PKG"
        const val KEY_LAST_AUTO_PRESENT = "winr_last_auto_present_$PKG"
        const val KEY_LAST_CLAIM_AUTO_PRESENT = "winr_last_claim_auto_present_$PKG"
        const val KEY_IMPRESSIONS = "winr_unregistered_impressions_$PKG"
        const val KEY_ADOPTION_STAMP = "winr_adoption_code_sent_at"
    }

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: AvafliApi
    private lateinit var secureStorage: SecureStorage
    private lateinit var sharedPrefs: InMemorySharedPreferences
    private lateinit var prefs: PreferencesStorage
    private lateinit var context: Context
    private lateinit var activity: Activity

    private var storedToken: String? = null
    private var storedRefreshToken: String? = null
    private var storedUuid: String? = null

    private var now = Instant.parse("2026-09-29T18:00:00Z").toEpochMilli()

    private val giveaway = Giveaway(id = "gw", title = "Win", prizeDescription = "Cash")

    private fun registerResponse(
        optedOut: Boolean? = null,
        optedOutUntil: String? = null,
        isNewUser: Boolean? = true,
    ) = AvafliApi.RegisterDeviceResponse(
        token = "new-token",
        refreshToken = "new-refresh",
        uuid = "new-uuid",
        giveaway = if (optedOut == true) null else giveaway,
        optedOut = optedOut,
        optedOutUntil = optedOutUntil,
        isNewUser = isNewUser,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Avafli.resetForTests()
        Avafli.nowMs = { now }

        sharedPrefs = InMemorySharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageName } returns PKG
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        prefs = PreferencesStorage(context)

        storedToken = null
        storedRefreshToken = null
        storedUuid = null
        secureStorage = mockk(relaxed = true)
        every { secureStorage.getToken() } answers { storedToken }
        every { secureStorage.saveToken(any()) } answers { storedToken = firstArg() }
        every { secureStorage.getRefreshToken() } answers { storedRefreshToken }
        every { secureStorage.saveRefreshToken(any()) } answers { storedRefreshToken = firstArg() }
        every { secureStorage.getUuid() } answers { storedUuid }
        every { secureStorage.saveUuid(any()) } answers { storedUuid = firstArg() }
        every { secureStorage.clearSession() } answers {
            storedToken = null
            storedRefreshToken = null
            storedUuid = null
        }

        api = mockk()
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse()
        coEvery { api.submitUserProfile(any(), any(), any(), any(), any(), any()) } returns true

        activity = mockk(relaxed = true)
        every { activity.isFinishing } returns false
        every { activity.isDestroyed } returns false

        Avafli.testDependencies = Avafli.TestDependencies(secureStorage, prefs, api)
    }

    @After
    fun tearDown() {
        Avafli.resetForTests()
        Dispatchers.resetMain()
    }

    private fun configure() {
        // resetForTests() clears the seam; every (re)configure restores it.
        Avafli.testDependencies = Avafli.TestDependencies(secureStorage, prefs, api)
        Avafli.nowMs = { now }
        Avafli.configure(
            AvafliConfiguration(
                context = context,
                apiKey = "avafli_test_key",
                user = AvafliUser(id = "user-1"),
                options = AvafliOptions(enableCertificatePinning = false, enablePushReminders = false),
            )
        )
    }

    /** The device as "Delete my data" left it, [agoMs] ago: old session still on disk. */
    private fun seedDeletedProfile(agoMs: Long, withUntil: Boolean = true) {
        storedToken = "old-token"
        storedRefreshToken = "old-refresh"
        storedUuid = "old-uuid"
        prefs.saveOptedOut(true)
        if (withUntil) prefs.saveOptedOutUntil(now - agoMs + DAY_MS)
        prefs.saveEmailSubmitted(true)
        prefs.saveStreakDay(5)
        prefs.saveLastClaimDate("2026-09-28")
        prefs.saveTotalEntries(150)
        prefs.saveCompletedDays(listOf(true, true, true, true, true, false, false))
        prefs.saveLastAutoPresentDay("2026-09-28")
        prefs.saveLastClaimAutoPresentAt(now - agoMs)
        prefs.saveUnregisteredImpressions(2)
        prefs.putString(KEY_ADOPTION_STAMP, "1759168800000")
        // An opted-out device with a live token refreshes status, as today.
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = null,
            optedOut = true,
        )
    }

    // ── Before the 24 hours are up: nothing changes ──

    @Test
    fun `opted out with time left - same calls as before, nothing cleared, nothing shown`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = 60 * 60 * 1000L)
        val until = sharedPrefs.all[KEY_OPTED_OUT_UNTIL]

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        // Registration side: exactly today's status refresh, no new registration.
        coVerify(exactly = 1) { api.getActiveGiveaway() }
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
        verify(exactly = 0) { secureStorage.clearSession() }
        assertEquals("old-token", storedToken)
        assertEquals(true, sharedPrefs.all[KEY_OPTED_OUT])
        assertEquals(until, sharedPrefs.all[KEY_OPTED_OUT_UNTIL])
        assertEquals(true, sharedPrefs.all["email_submitted"])
        assertEquals(5, sharedPrefs.all["streak_day"])

        // No presentation, and present() declines.
        verify(exactly = 0) { activity.startActivity(any()) }
        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        assertTrue(result?.exceptionOrNull() is AvafliError.OptedOut)
        verify(exactly = 0) { activity.startActivity(any()) }

        // More foregrounds inside the window stay just as quiet.
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
    }

    // ── After the 24 hours ──

    @Test
    fun `opted out with the time passed - old session cleared key by key, then a new registration`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = DAY_MS + 1_000L)

        configure()

        // Cleared synchronously inside configure(), before any network call.
        assertNull(sharedPrefs.all[KEY_OPTED_OUT])
        assertNull(sharedPrefs.all[KEY_OPTED_OUT_UNTIL])
        assertNull(sharedPrefs.all["email_submitted"])
        assertNull(sharedPrefs.all["streak_day"])
        assertNull(sharedPrefs.all["last_claim_date"])
        assertNull(sharedPrefs.all["total_entries"])
        assertNull(sharedPrefs.all["completed_days"])
        assertNull(sharedPrefs.all[KEY_LAST_AUTO_PRESENT])
        assertNull(sharedPrefs.all[KEY_LAST_CLAIM_AUTO_PRESENT])
        assertNull(sharedPrefs.all[KEY_IMPRESSIONS])
        assertNull(sharedPrefs.all[KEY_ADOPTION_STAMP])
        assertNull(sharedPrefs.all["winr_offline_pending_intents_$PKG"])
        assertFalse(prefs.isOptedOut())
        assertFalse(prefs.isEmailSubmitted())
        assertEquals(0, prefs.getStreakDay())
        assertEquals(0, prefs.getTotalEntries())
        assertEquals(0, prefs.getUnregisteredImpressions())
        // Auth token, refresh token and user id.
        verify(exactly = 1) { secureStorage.clearSession() }
        assertNull(storedToken)
        assertNull(storedRefreshToken)
        assertNull(storedUuid)
        // The device id is not the SDK's to clear; the guest id is kept too.
        verify(exactly = 0) { secureStorage.clearAll() }

        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        // Registers normally — a full registerDevice, not a status refresh.
        coVerify(exactly = 1) { api.registerDevice("avafli_test_key", any(), any(), any()) }
        coVerify(exactly = 0) { api.getActiveGiveaway() }
        assertEquals("new-token", storedToken)
        assertEquals("new-uuid", storedUuid)

        // The normal flow follows: an unregistered user under the normal rules
        // (auto-open, first impression counted, email capture inside).
        verify(exactly = 1) { activity.startActivity(any()) }
        assertEquals(1, prefs.getUnregisteredImpressions())
        assertEquals(LocalDate.now().toString(), prefs.getLastAutoPresentDay())
        assertFalse(prefs.isEmailSubmitted())
        assertTrue(Avafli.isServiceAvailable())
    }

    @Test
    fun `the block lifts on foreground when the app stayed in memory`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = 23 * 60 * 60 * 1000L)
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }

        // An hour and a second later, the app comes back to the foreground.
        now += 60 * 60 * 1000L + 1_000L
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        assertFalse(prefs.isOptedOut())
        assertNull(sharedPrefs.all["streak_day"])
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    // ── Opt-outs cached before 3.2.0 ──

    @Test
    fun `a legacy opt-out with no time gets one stamped and lifts 24 hours later`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = 30L * DAY_MS, withUntil = false)
        assertNull(sharedPrefs.all[KEY_OPTED_OUT_UNTIL])

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        // First seen now: until = now + 24 h, stored. Still opted out.
        assertEquals(now + DAY_MS, sharedPrefs.all[KEY_OPTED_OUT_UNTIL])
        assertTrue(prefs.isOptedOut())
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
        verify(exactly = 0) { activity.startActivity(any()) }

        // One second short of 24 hours: still blocked.
        now += DAY_MS - 1_000L
        configure()
        advanceUntilIdle()
        assertTrue(prefs.isOptedOut())
        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }

        // 24 hours after the stamp: lifted.
        now += 1_000L
        configure()
        advanceUntilIdle()
        assertFalse(prefs.isOptedOut())
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
    }

    // ── The server has the last word ──

    @Test
    fun `server still opted out - adopts the server's time and never loops`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = DAY_MS + 1_000L)
        val serverUntil = now + 2 * 60 * 60 * 1000L
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(
            optedOut = true,
            optedOutUntil = Instant.ofEpochMilli(serverUntil).toString(),
            isNewUser = null,
        )
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = null,
            optedOut = true,
            optedOutUntil = Instant.ofEpochMilli(serverUntil).toString(),
        )

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        assertTrue(prefs.isOptedOut())
        assertEquals(serverUntil, sharedPrefs.all[KEY_OPTED_OUT_UNTIL])
        verify(exactly = 0) { activity.startActivity(any()) }

        // Foreground after foreground before that time: no further attempts.
        repeat(5) {
            now += 10 * 60 * 1000L
            Avafli.onHostActivityResumed(activity)
            advanceUntilIdle()
        }
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }

        // After the server's time: one more attempt, and this one is let in.
        now = serverUntil + 1_000L
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        coVerify(exactly = 2) { api.registerDevice(any(), any(), any(), any()) }
        assertFalse(prefs.isOptedOut())
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `a server time already behind this device's clock cannot cause a retry storm`() = runTest(dispatcher) {
        seedDeletedProfile(agoMs = DAY_MS + 1_000L)
        // Sweep lag / clock skew: still opted out, with a time in OUR past.
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(
            optedOut = true,
            optedOutUntil = Instant.ofEpochMilli(now - 5 * 60 * 1000L).toString(),
            isNewUser = null,
        )
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = null,
            optedOut = true,
            optedOutUntil = Instant.ofEpochMilli(now - 5 * 60 * 1000L).toString(),
        )

        configure()
        advanceUntilIdle()
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        assertEquals(now + Avafli.OPT_OUT_RECHECK_FLOOR_MS, sharedPrefs.all[KEY_OPTED_OUT_UNTIL])

        repeat(10) {
            now += 60 * 1000L
            Avafli.onHostActivityResumed(activity)
            advanceUntilIdle()
        }
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
    }

    @Test
    fun `a first-seen server opt-out stores the server's time`() = runTest(dispatcher) {
        // Fresh install of a deleted profile: nothing local, the server knows.
        val serverUntil = now + 20 * 60 * 60 * 1000L
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(
            optedOut = true,
            optedOutUntil = Instant.ofEpochMilli(serverUntil).toString(),
            isNewUser = null,
        )

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        assertTrue(prefs.isOptedOut())
        assertEquals(serverUntil, prefs.getOptedOutUntil())
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    @Test
    fun `a server opt-out with no time (older backend) is first seen now`() = runTest(dispatcher) {
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns
            registerResponse(optedOut = true, isNewUser = null)

        configure()
        advanceUntilIdle()

        assertTrue(prefs.isOptedOut())
        assertEquals(now + DAY_MS, prefs.getOptedOutUntil())
    }

    // ── Where the SDK itself performs the delete ──

    @Test
    fun `optOut stores the moment the block lifts`() = runTest(dispatcher) {
        configure()
        advanceUntilIdle()
        coEvery { api.optOut() } returns true

        assertTrue(Avafli.optOut().isSuccess)

        assertTrue(prefs.isOptedOut())
        assertEquals(now + DAY_MS, prefs.getOptedOutUntil())
    }

    @Test
    fun `the in-experience delete stores the moment the block lifts`() = runTest(dispatcher) {
        coEvery { api.optOut() } returns true
        val viewModel = AvafliExperienceViewModel(api, prefs, Logger())
        viewModel.nowMs = { now }

        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        advanceUntilIdle()

        assertEquals(OptOutPhase.Done, viewModel.uiState.value.optOutPhase)
        assertTrue(prefs.isOptedOut())
        assertEquals(now + DAY_MS, prefs.getOptedOutUntil())
    }

    @Test
    fun `a failed delete stores nothing`() = runTest(dispatcher) {
        coEvery { api.optOut() } throws AvafliError.NetworkError("offline")
        val viewModel = AvafliExperienceViewModel(api, prefs, Logger())
        viewModel.showOptOutConfirmation()
        viewModel.confirmOptOut()
        advanceUntilIdle()

        assertFalse(prefs.isOptedOut())
        assertNull(prefs.getOptedOutUntil())
    }

    // ── Copy ──

    @Test
    fun `the delete confirmation promises the 24-hour rejoin, not a permanent block`() {
        assertEquals(
            "This permanently erases your information and ends your participation. " +
                "Entries and streaks are forfeited and cannot be restored. " +
                "You can join again as a new participant after 24 hours.",
            AvafliV2Strings.OPT_OUT_BODY,
        )
        assertFalse(AvafliV2Strings.OPT_OUT_BODY.contains("cannot be undone"))
    }
}

/** A plain in-memory [SharedPreferences] — enough for [PreferencesStorage]. */
private class InMemorySharedPreferences : SharedPreferences {

    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableListOf<(MutableMap<String, Any?>) -> Unit>()

        private fun put(key: String, value: Any?) = apply { pending += { it[key] = value } }

        override fun putString(key: String, value: String?) = put(key, value)
        override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values)
        override fun putInt(key: String, value: Int) = put(key, value)
        override fun putLong(key: String, value: Long) = put(key, value)
        override fun putFloat(key: String, value: Float) = put(key, value)
        override fun putBoolean(key: String, value: Boolean) = put(key, value)
        override fun remove(key: String) = apply { pending += { it.remove(key) } }
        override fun clear() = apply { pending += { it.clear() } }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            pending.forEach { it(values) }
            pending.clear()
        }
    }
}

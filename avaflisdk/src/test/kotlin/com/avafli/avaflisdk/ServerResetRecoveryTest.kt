package com.avafli.avaflisdk

// 3.2.2: identity-scoped local state is reconciled with the server.
//
// Scenario (Skape, Oct 2026): the account behind a device was deleted on the
// server. The device still held the "email submitted" flag, a Day-5 streak and
// a claim stamp. registerDevice minted a FRESH user (isNewUser: true, no email
// on file) and the SDK — trusting the stale flags — skipped email capture and
// auto-claimed; the backend refused every claim with "email required".

import android.app.Activity
import android.content.Context
import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.storage.SecureStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IdentityReconcilePlanTest {

    @Test
    fun `a fresh user drops account state, even when an email is reported`() {
        assertEquals(IdentityReconcile.Plan.DROP_ACCOUNT_STATE, IdentityReconcile.plan(true, false, null, null))
        assertEquals(IdentityReconcile.Plan.DROP_ACCOUNT_STATE, IdentityReconcile.plan(true, true, null, null))
    }

    @Test
    fun `an explicit no-email drops only the email flag`() {
        assertEquals(IdentityReconcile.Plan.DROP_EMAIL_FLAG, IdentityReconcile.plan(false, false, null, null))
        assertEquals(IdentityReconcile.Plan.DROP_EMAIL_FLAG, IdentityReconcile.plan(null, false, false, false))
    }

    @Test
    fun `returning, older backend, parked adoption and opt-out are left alone`() {
        assertEquals(IdentityReconcile.Plan.LEAVE_ALONE, IdentityReconcile.plan(false, true, null, null))
        assertEquals(IdentityReconcile.Plan.LEAVE_ALONE, IdentityReconcile.plan(null, null, null, null))
        assertEquals(IdentityReconcile.Plan.LEAVE_ALONE, IdentityReconcile.plan(null, false, true, null))
        assertEquals(IdentityReconcile.Plan.LEAVE_ALONE, IdentityReconcile.plan(true, false, null, true))
    }
}

class RegisterDeviceConsentParseTest {

    @Test
    fun `registerDevice parses emailConsentStatus and treats absence as null`() = runTest {
        val networkClient = mockk<NetworkClient>()
        val api = AvafliApi(networkClient, Logger(isDebug = false))
        fun body(extra: String) = Json.parseToJsonElement(
            """{"token":"t","refreshToken":"r","uuid":"u"$extra}"""
        ).jsonObject

        coEvery { networkClient.post("registerDevice", any()) } returns body(""","emailConsentStatus":false""")
        assertEquals(false, api.registerDevice("k", "fp", "b", "UTC").emailConsentStatus)

        coEvery { networkClient.post("registerDevice", any()) } returns body("")
        assertNull(api.registerDevice("k", "fp", "b", "UTC").emailConsentStatus)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServerResetRecoveryTest {

    private companion object {
        const val PKG = "com.example.host"
        const val KEY_LAST_AUTO_PRESENT = "winr_last_auto_present_$PKG"
        const val KEY_IMPRESSIONS = "winr_unregistered_impressions_$PKG"
        const val KEY_ADOPTION_STAMP = "winr_adoption_code_sent_at"
        const val KEY_PENDING_INTENTS = "winr_offline_pending_intents_$PKG"
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

    private val giveaway = Giveaway(id = "gw", title = "Win", prizeDescription = "Cash")

    private fun registerResponse(isNewUser: Boolean?, emailConsentStatus: Boolean?) =
        AvafliApi.RegisterDeviceResponse(
            token = "new-token",
            refreshToken = "new-refresh",
            uuid = "new-uuid",
            giveaway = giveaway,
            isNewUser = isNewUser,
            emailConsentStatus = emailConsentStatus,
        )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Avafli.resetForTests()

        sharedPrefs = InMemorySharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageName } returns PKG
        every { context.getSharedPreferences(any(), any()) } returns sharedPrefs
        prefs = PreferencesStorage(context)

        secureStorage = mockk(relaxed = true)
        every { secureStorage.getToken() } answers { storedToken }
        every { secureStorage.saveToken(any()) } answers { storedToken = firstArg() }
        every { secureStorage.getRefreshToken() } answers { storedRefreshToken }
        every { secureStorage.saveRefreshToken(any()) } answers { storedRefreshToken = firstArg() }
        every { secureStorage.getUuid() } answers { storedUuid }
        every { secureStorage.saveUuid(any()) } answers { storedUuid = firstArg() }
        every { secureStorage.clearSession() } answers {
            storedToken = null; storedRefreshToken = null; storedUuid = null
        }

        api = mockk()
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
        Avafli.testDependencies = Avafli.TestDependencies(secureStorage, prefs, api)
        Avafli.configure(
            AvafliConfiguration(
                context = context,
                apiKey = "avafli_test_key",
                user = AvafliUser(id = "user-1"),
                options = AvafliOptions(enableCertificatePinning = false, enablePushReminders = false),
            )
        )
    }

    /** The device as the dead account left it. */
    private fun seedStaleIdentity() {
        prefs.saveEmailSubmitted(true)
        prefs.saveStreakDay(5)
        prefs.saveLastClaimDate("2026-10-18")
        prefs.saveTotalEntries(150)
        prefs.saveCompletedDays(listOf(true, true, true, true, true, false, false))
        prefs.saveLastAutoPresentDay("2026-10-18")
        prefs.saveUnregisteredImpressions(2)
        prefs.putString(KEY_ADOPTION_STAMP, "1759168800000")
        prefs.putString(KEY_PENDING_INTENTS, """[{"kind":"CLAIM","dayKey":"2026-10-19","createdAtMs":1}]""")
    }

    @Test
    fun `fresh user from registerDevice - identity state dropped, auto-open marks kept`() = runTest(dispatcher) {
        seedStaleIdentity()
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(isNewUser = true, emailConsentStatus = false)

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        assertEquals("new-token", storedToken)
        assertNull(sharedPrefs.all["email_submitted"])
        assertNull(sharedPrefs.all["streak_day"])
        assertNull(sharedPrefs.all["last_claim_date"])
        assertNull(sharedPrefs.all["total_entries"])
        assertNull(sharedPrefs.all["completed_days"])
        assertNull(sharedPrefs.all[KEY_ADOPTION_STAMP])
        assertFalse(prefs.isEmailSubmitted())
        assertEquals(0, prefs.getStreakDay())
        // Not identity state: never removed (the SDK may legitimately re-stamp
        // the once-a-day mark when it auto-opens after this registration).
        assertTrue(sharedPrefs.all[KEY_LAST_AUTO_PRESENT] != null)
        assertTrue((sharedPrefs.all[KEY_IMPRESSIONS] as Int) >= 2)
    }

    @Test
    fun `returning device with an email on file - nothing dropped`() = runTest(dispatcher) {
        seedStaleIdentity()
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(isNewUser = false, emailConsentStatus = true)

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        assertTrue(prefs.isEmailSubmitted())
        assertEquals(5, prefs.getStreakDay())
        assertEquals(150, prefs.getTotalEntries())
        assertEquals("1759168800000", sharedPrefs.all[KEY_ADOPTION_STAMP])
    }

    @Test
    fun `older backend omits the fields - nothing dropped`() = runTest(dispatcher) {
        seedStaleIdentity()
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(isNewUser = null, emailConsentStatus = null)

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        assertTrue(prefs.isEmailSubmitted())
        assertEquals(5, prefs.getStreakDay())
    }

    @Test
    fun `cached session, status says no email on file - email flag dropped, streak kept`() = runTest(dispatcher) {
        seedStaleIdentity()
        storedToken = "old-token"; storedRefreshToken = "old-refresh"; storedUuid = "old-uuid"
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(
            giveaway = giveaway,
            emailConsentStatus = false,
        )

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
        assertFalse(prefs.isEmailSubmitted())
        assertEquals(5, prefs.getStreakDay())
        assertEquals(150, prefs.getTotalEntries())
    }

    @Test
    fun `cached session, status omits the field - nothing dropped`() = runTest(dispatcher) {
        seedStaleIdentity()
        storedToken = "old-token"; storedRefreshToken = "old-refresh"; storedUuid = "old-uuid"
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(giveaway = giveaway)

        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        assertTrue(prefs.isEmailSubmitted())
        assertEquals(5, prefs.getStreakDay())
    }
}

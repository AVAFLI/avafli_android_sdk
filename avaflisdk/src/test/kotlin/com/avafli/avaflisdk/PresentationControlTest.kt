package com.avafli.avaflisdk

import android.app.Activity
import android.content.Context
import com.avafli.avaflisdk.domain.ExperienceConfig
import com.avafli.avaflisdk.domain.Giveaway
import com.avafli.avaflisdk.domain.SdkConfig
import com.avafli.avaflisdk.network.AvafliApi
import com.avafli.avaflisdk.network.NetworkClient
import com.avafli.avaflisdk.services.Logger
import com.avafli.avaflisdk.storage.PreferencesStorage
import com.avafli.avaflisdk.storage.SecureStorage
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Publisher presentation control (3.1.4): `AvafliConfiguration.autoOpen`,
 * the server's `experience.autoOpenMode`, `Avafli.present()` and
 * `holdAutoOpen()/releaseAutoOpen()`.
 *
 * Non-negotiables under test: the default (ALWAYS) is today's behavior, and
 * `registerDevice` runs on `configure()` in EVERY mode — presentation never
 * defers tracking.
 */

// ── Effective-mode policy (pure) ──

class AutoOpenPolicyTest {

    @Test
    fun `effective mode is the most restrictive of server and client`() {
        val a = AvafliAutoOpen.ALWAYS
        val r = AvafliAutoOpen.RETURNING_USERS_ONLY
        val n = AvafliAutoOpen.NEVER
        assertEquals(a, AvafliAutoOpen.effective(null, null, a))
        assertEquals(a, AvafliAutoOpen.effective(true, a, a))
        assertEquals(r, AvafliAutoOpen.effective(null, null, r))
        assertEquals(r, AvafliAutoOpen.effective(null, r, a))
        assertEquals(n, AvafliAutoOpen.effective(null, n, a))
        assertEquals(n, AvafliAutoOpen.effective(null, a, n))
        assertEquals(n, AvafliAutoOpen.effective(null, r, n))
        assertEquals(r, AvafliAutoOpen.effective(null, r, r))
    }

    @Test
    fun `server kill switch wins over everything`() {
        assertEquals(AvafliAutoOpen.NEVER, AvafliAutoOpen.effective(false, null, AvafliAutoOpen.ALWAYS))
        assertEquals(AvafliAutoOpen.NEVER, AvafliAutoOpen.effective(false, AvafliAutoOpen.ALWAYS, AvafliAutoOpen.ALWAYS))
    }

    @Test
    fun `wire values parse and unknown values are ignored`() {
        assertEquals(AvafliAutoOpen.ALWAYS, AvafliAutoOpen.fromWire("always"))
        assertEquals(AvafliAutoOpen.RETURNING_USERS_ONLY, AvafliAutoOpen.fromWire("returningUsersOnly"))
        assertEquals(AvafliAutoOpen.NEVER, AvafliAutoOpen.fromWire("never"))
        assertNull(AvafliAutoOpen.fromWire(null))
        assertNull(AvafliAutoOpen.fromWire("sometimes"))
        assertNull(AvafliAutoOpen.fromWire("NEVER"))
    }

    @Test
    fun `configuration default is ALWAYS and stays source-compatible`() {
        val config = AvafliConfiguration(
            context = mockk(relaxed = true),
            apiKey = "k",
            user = AvafliUser(id = "u"),
        )
        assertEquals(AvafliAutoOpen.ALWAYS, config.autoOpen)
    }
}

// ── Wire parsing (registerDevice.isNewUser, sdkConfig.experience.autoOpenMode) ──

class PresentationControlApiTest {

    private lateinit var networkClient: NetworkClient
    private lateinit var api: AvafliApi

    @Before
    fun setup() {
        networkClient = mockk()
        api = AvafliApi(networkClient, Logger(isDebug = false))
    }

    private fun jsonObj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun registerBody(extra: String) = jsonObj(
        """{"token":"t","refreshToken":"r","uuid":"u"$extra}"""
    )

    @Test
    fun `registerDevice parses isNewUser and treats absence as null`() = runTest {
        coEvery { networkClient.post("registerDevice", any()) } returns registerBody(""","isNewUser":true""")
        assertEquals(true, api.registerDevice("k", "fp", "b", "UTC").isNewUser)

        coEvery { networkClient.post("registerDevice", any()) } returns registerBody("")
        assertNull(api.registerDevice("k", "fp", "b", "UTC").isNewUser)
    }

    @Test
    fun `sdkConfig autoOpenMode parses known values and drops unknown ones`() = runTest {
        suspend fun modeFor(json: String): AvafliAutoOpen? {
            coEvery { networkClient.authenticatedPost("getActiveGiveaway", any()) } returns jsonObj(
                """{"giveaway":null,"sdkConfig":{"experience":$json}}"""
            )
            return api.getActiveGiveaway().sdkConfig?.experience?.autoOpenMode
        }
        assertEquals(AvafliAutoOpen.NEVER, modeFor("""{"autoOpenMode":"never"}"""))
        assertEquals(AvafliAutoOpen.RETURNING_USERS_ONLY, modeFor("""{"autoOpenMode":"returningUsersOnly"}"""))
        assertEquals(AvafliAutoOpen.ALWAYS, modeFor("""{"autoOpenMode":"always"}"""))
        assertNull(modeFor("""{"autoOpenMode":"someFutureMode"}"""))
        assertNull(modeFor("""{"autoOpenEnabled":true}"""))
    }
}

// ── Avafli singleton: configure → register → auto-open / present / hold ──

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationControlTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var api: AvafliApi
    private lateinit var secureStorage: SecureStorage
    private lateinit var prefs: PreferencesStorage
    private lateinit var activity: Activity

    private var storedToken: String? = null
    private var lastAutoPresentDay: String? = null
    private var unregisteredImpressions = 0

    private val giveaway = Giveaway(id = "gw", title = "Win", prizeDescription = "Cash")

    private fun registerResponse(isNewUser: Boolean? = null, sdkConfig: SdkConfig? = null) =
        AvafliApi.RegisterDeviceResponse(
            token = "tok",
            refreshToken = "ref",
            uuid = "uuid",
            giveaway = giveaway,
            sdkConfig = sdkConfig,
            isNewUser = isNewUser,
        )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        Avafli.resetForTests()
        storedToken = null
        lastAutoPresentDay = null
        unregisteredImpressions = 0

        api = mockk()
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse()
        coEvery { api.submitUserProfile(any(), any(), any(), any(), any(), any()) } returns true

        secureStorage = mockk(relaxed = true)
        every { secureStorage.getToken() } answers { storedToken }
        every { secureStorage.saveToken(any()) } answers { storedToken = firstArg() }

        prefs = mockk(relaxed = true)
        every { prefs.isOptedOut() } returns false
        every { prefs.getString(any()) } returns null
        every { prefs.getLastAutoPresentDay() } answers { lastAutoPresentDay }
        every { prefs.saveLastAutoPresentDay(any()) } answers { lastAutoPresentDay = firstArg() }
        every { prefs.getUnregisteredImpressions() } answers { unregisteredImpressions }
        every { prefs.saveUnregisteredImpressions(any()) } answers { unregisteredImpressions = firstArg() }

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

    private fun configure(autoOpen: AvafliAutoOpen = AvafliAutoOpen.ALWAYS) {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.packageName } returns "com.example.host"
        Avafli.configure(
            AvafliConfiguration(
                context = context,
                apiKey = "avafli_test_key",
                user = AvafliUser(id = "user-1"),
                options = AvafliOptions(enableCertificatePinning = false, enablePushReminders = false),
                autoOpen = autoOpen,
            )
        )
    }

    private fun today() = LocalDate.now().toString()

    // ── Non-negotiable: tracking is unaffected by the mode ──

    @Test
    fun `autoOpen NEVER still registers the device on configure and never auto-opens`() = runTest(dispatcher) {
        configure(AvafliAutoOpen.NEVER)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice("avafli_test_key", any(), any(), any()) }
        coVerify(exactly = 1) { api.submitUserProfile(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
        assertNull(lastAutoPresentDay)
        assertEquals(0, unregisteredImpressions)
    }

    // ── Default behavior unchanged ──

    @Test
    fun `default ALWAYS auto-opens once per day after registration`() = runTest(dispatcher) {
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        verify(exactly = 1) { activity.startActivity(any()) }
        assertEquals(today(), lastAutoPresentDay)
        // Unregistered (no email consent) → the impression is counted.
        assertEquals(1, unregisteredImpressions)

        // Same day, another resume: no second pop.
        Avafli.noteExperienceClosed()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `ALWAYS auto-opens even in the first-registration session`() = runTest(dispatcher) {
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(isNewUser = true)
        configure(AvafliAutoOpen.ALWAYS)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    // ── returningUsersOnly ──

    @Test
    fun `RETURNING_USERS_ONLY skips the auto-open when registerDevice reports isNewUser`() = runTest(dispatcher) {
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(isNewUser = true)
        configure(AvafliAutoOpen.RETURNING_USERS_ONLY)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
        assertNull(lastAutoPresentDay)
        assertEquals(0, unregisteredImpressions)
    }

    @Test
    fun `RETURNING_USERS_ONLY auto-opens when isNewUser is absent (older backend)`() = runTest(dispatcher) {
        configure(AvafliAutoOpen.RETURNING_USERS_ONLY)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `RETURNING_USERS_ONLY auto-opens on a later launch (already registered)`() = runTest(dispatcher) {
        storedToken = "existing-token"
        coEvery { api.getActiveGiveaway() } returns AvafliApi.GetActiveGiveawayResponse(giveaway = giveaway)
        configure(AvafliAutoOpen.RETURNING_USERS_ONLY)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        coVerify(exactly = 0) { api.registerDevice(any(), any(), any(), any()) }
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    // ── Server-side mode ──

    @Test
    fun `server autoOpenMode never overrides a client ALWAYS`() = runTest(dispatcher) {
        val sdkConfig = SdkConfig(experience = ExperienceConfig(autoOpenMode = AvafliAutoOpen.NEVER))
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(sdkConfig = sdkConfig)
        configure(AvafliAutoOpen.ALWAYS)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 0) { activity.startActivity(any()) }
        assertNull(lastAutoPresentDay)
    }

    @Test
    fun `server autoOpenMode returningUsersOnly restricts a client ALWAYS for new users`() = runTest(dispatcher) {
        val sdkConfig = SdkConfig(experience = ExperienceConfig(autoOpenMode = AvafliAutoOpen.RETURNING_USERS_ONLY))
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns
            registerResponse(isNewUser = true, sdkConfig = sdkConfig)
        configure(AvafliAutoOpen.ALWAYS)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    @Test
    fun `server autoOpenEnabled false remains the hard kill switch`() = runTest(dispatcher) {
        val sdkConfig = SdkConfig(experience = ExperienceConfig(autoOpenEnabled = false, autoOpenMode = AvafliAutoOpen.ALWAYS))
        coEvery { api.registerDevice(any(), any(), any(), any()) } returns registerResponse(sdkConfig = sdkConfig)
        configure(AvafliAutoOpen.ALWAYS)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    // ── present() ──

    @Test
    fun `present bypasses the once-per-day mark and the impression cap without counting`() = runTest(dispatcher) {
        lastAutoPresentDay = today()
        unregisteredImpressions = 99
        configure(AvafliAutoOpen.NEVER)
        advanceUntilIdle()

        Avafli.present(activity)
        verify(exactly = 1) { activity.startActivity(any()) }
        assertEquals(99, unregisteredImpressions)
    }

    @Test
    fun `present waits for an in-flight registration and then opens`() = runTest(dispatcher) {
        configure(AvafliAutoOpen.NEVER)
        // Registration hasn't run yet (test dispatcher is idle).
        Avafli.present(activity)
        verify(exactly = 0) { activity.startActivity(any()) }

        advanceUntilIdle()
        coVerify(exactly = 1) { api.registerDevice(any(), any(), any(), any()) }
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `present declines with NoGiveaway when registration failed`() = runTest(dispatcher) {
        coEvery { api.registerDevice(any(), any(), any(), any()) } throws AvafliError.NetworkError("offline")
        configure(AvafliAutoOpen.NEVER)
        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        advanceUntilIdle()

        verify(exactly = 0) { activity.startActivity(any()) }
        assertTrue(result?.exceptionOrNull() is AvafliError.NoGiveaway)
        assertNull(lastAutoPresentDay)
    }

    @Test
    fun `present is a no-op while the experience is on screen and works again after close`() = runTest(dispatcher) {
        configure(AvafliAutoOpen.NEVER)
        advanceUntilIdle()

        Avafli.present(activity)
        Avafli.present(activity)
        verify(exactly = 1) { activity.startActivity(any()) }

        Avafli.noteExperienceClosed()
        // Close writes the once-per-day mark so auto-open won't double-pop today.
        assertEquals(today(), lastAutoPresentDay)
        Avafli.present(activity)
        verify(exactly = 2) { activity.startActivity(any()) }
    }

    @Test
    fun `present before configure reports NotInitialized`() {
        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        assertTrue(result?.exceptionOrNull() is AvafliError.NotInitialized)
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    @Test
    fun `present declines for an opted-out user`() = runTest(dispatcher) {
        every { prefs.isOptedOut() } returns true
        configure(AvafliAutoOpen.NEVER)
        advanceUntilIdle()
        var result: Result<*>? = null
        Avafli.present(activity) { result = it }
        assertTrue(result?.exceptionOrNull() is AvafliError.OptedOut)
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    // ── holdAutoOpen / releaseAutoOpen ──

    @Test
    fun `hold defers the auto-open without burning anything and release re-runs the check`() = runTest(dispatcher) {
        Avafli.holdAutoOpen() // before configure
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        verify(exactly = 0) { activity.startActivity(any()) }
        assertNull(lastAutoPresentDay)
        assertEquals(0, unregisteredImpressions)

        Avafli.releaseAutoOpen()
        verify(exactly = 1) { activity.startActivity(any()) }
        assertEquals(today(), lastAutoPresentDay)
        assertEquals(1, unregisteredImpressions)

        // Idempotent.
        Avafli.releaseAutoOpen()
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `present still works while auto-open is held`() = runTest(dispatcher) {
        Avafli.holdAutoOpen()
        configure()
        advanceUntilIdle()
        Avafli.present(activity)
        verify(exactly = 1) { activity.startActivity(any()) }
    }

    @Test
    fun `release under NEVER still never auto-opens`() = runTest(dispatcher) {
        Avafli.holdAutoOpen()
        configure(AvafliAutoOpen.NEVER)
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        Avafli.releaseAutoOpen()
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    // ── Boot resilience ──

    @Test
    fun `a network-failed boot status fetch is retried on the next foreground and then auto-opens`() = runTest(dispatcher) {
        storedToken = "existing-token"
        var calls = 0
        coEvery { api.getActiveGiveaway() } coAnswers {
            calls += 1
            if (calls == 1) throw AvafliError.NetworkError("timeout")
            AvafliApi.GetActiveGiveawayResponse(giveaway = giveaway)
        }
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()

        // Failed boot: nothing marked, nothing counted, nothing shown.
        verify(exactly = 0) { activity.startActivity(any()) }
        assertNull(lastAutoPresentDay)
        assertEquals(0, unregisteredImpressions)

        // Next foreground re-runs the fetch and the auto-open check.
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        assertEquals(2, calls)
        verify(exactly = 1) { activity.startActivity(any()) }
        assertEquals(today(), lastAutoPresentDay)
    }

    @Test
    fun `a backend-rejected boot fetch is not retried on foreground`() = runTest(dispatcher) {
        storedToken = "existing-token"
        var calls = 0
        coEvery { api.getActiveGiveaway() } coAnswers {
            calls += 1
            throw AvafliError.ServerError(400, "bad request")
        }
        configure()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        Avafli.onHostActivityResumed(activity)
        advanceUntilIdle()
        assertEquals(1, calls)
        verify(exactly = 0) { activity.startActivity(any()) }
    }
}

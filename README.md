# Avafli Android SDK

**Drop-in daily-entry sweepstakes for your Android app — one `configure()` call.**

[![JitPack](https://jitpack.io/v/AVAFLI/avafli_android_sdk.svg)](https://jitpack.io/#AVAFLI/avafli_android_sdk)
[![API](https://img.shields.io/badge/minSdk-26-brightgreen.svg)](https://developer.android.com/tools/releases/platforms#8.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)

Version **3.3.1** · [Changelog](CHANGELOG.md) · [Dashboard](https://sdk.avafli.com/dashboard) · [Docs](https://sdk.avafli.com/android) · [info@avafli.com](mailto:info@avafli.com)

Avafli adds a daily-entry sweepstakes to your app. After `Avafli.configure(...)`
the SDK opens a bottom drawer once a day, captures the user's email (Day 1),
claims that day's entries automatically, shows the streak, and — if the user is
drawn — walks them through the prize claim. Prizes, branding and timing rules
are managed from the [Avafli Dashboard](https://sdk.avafli.com/dashboard); the
app ships once.

**Contents:** [Requirements](#requirements) · [Install](#install) · [Configure](#configure) ·
[What the user sees](#what-the-user-sees) · [Controlling presentation](#controlling-presentation) ·
[Push reminders](#push-reminders-fcm) · [Account deletion](#account-deletion) ·
[Analytics](#analytics) · [Testing checklist](#testing-checklist) · [API reference](#api-reference)

---

## Requirements

| | |
| --- | --- |
| Android | `minSdk 26` (Android 8.0) or higher in your app |
| `compileSdk` | 35 or higher (the SDK is compiled against API 35) |
| Kotlin / JVM | Kotlin 2.0+ (the SDK is built with Kotlin 2.1.0), Java 17 toolchain |
| Android Gradle Plugin | 8.x (built with 8.7.3), AndroidX enabled |
| UI toolkit | None required on your side. The drawer is Jetpack Compose + Material 3 internally and is pulled in transitively (Compose BOM 2024.12.01); your app can be Views, Compose or anything else. |
| Firebase Cloud Messaging | Optional — only for push reminders (see [Push reminders](#push-reminders-fcm)) |
| API key | From your dashboard: `avafli_live_…` for release builds and `avafli_test_…` for debug builds. Keys issued before the Avafli rename keep their original prefix and stay valid. No key yet? [Pricing](https://sdk.avafli.com/pricing) · [info@avafli.com](mailto:info@avafli.com) |

Nothing to add to your manifest: the SDK's manifest merges in `INTERNET`,
`ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` (requested at runtime only when
push reminders are enabled) and its own translucent, non-exported
`AvafliExperienceActivity`. R8/ProGuard rules ship as consumer rules — no
`-keep` lines needed.

## Install

**1. JitPack repository** — project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

**2. Dependency** — app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.AVAFLI:avafli_android_sdk:v3.3.1")
}
```

That coordinate is the JitPack build of this repository (Maven group
`com.avafli`, artifact `avafli-sdk`, version `3.3.1` in `gradle.properties`).

## Configure

Call `Avafli.configure(...)` once, as early as possible — `Application.onCreate()`
is the right place. `configure()` registers the device with the backend (this is
also what powers your dashboard's DAU/MAU), then auto-opens the drawer when it is
eligible (see [Controlling presentation](#controlling-presentation)). The SDK
keeps only the application context and watches Activity lifecycle itself, so you
never pass an Activity to `configure()`; the drawer opens over whichever of your
Activities is in the foreground.

```kotlin
// ExampleApplication.kt
import android.app.Application
import com.avafli.avaflisdk.Avafli
import com.avafli.avaflisdk.AvafliAutoOpen
import com.avafli.avaflisdk.AvafliConfiguration
import com.avafli.avaflisdk.AvafliOptions
import com.avafli.avaflisdk.AvafliUser

class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Avafli.configure(
            AvafliConfiguration(
                context = this,
                // Sandbox key in debug builds, live key in release builds.
                apiKey = if (BuildConfig.DEBUG) "avafli_test_XXXXXXXX" else "avafli_live_XXXXXXXX",
                // Only `id` is required. Pass what you have; the SDK captures the rest.
                user = AvafliUser(
                    id = "user_123",
                    firstName = "Jane",             // optional — asked at prize claim if missing
                    lastName = "Doe",               // optional
                    email = "jane@example.com",     // optional — pre-fills AND locks the capture field
                ),
                autoOpen = AvafliAutoOpen.ALWAYS,   // default; or RETURNING_USERS_ONLY / NEVER
                options = AvafliOptions(
                    debugLogging = BuildConfig.DEBUG,  // Logcat tag "Avafli-SDK"
                    enablePushReminders = true,        // default; needs FCM in your app, see below
                ),
            )
        )
    }
}
```

Register the class in `AndroidManifest.xml` (`<application android:name=".ExampleApplication" …>`).
`BuildConfig` needs `buildFeatures { buildConfig = true }` in your module when
you use it for the key switch.

### Identity: pass what you have

| Situation | What to pass | What the SDK does |
| --- | --- | --- |
| Signed-in user, no email on file | `AvafliUser(id = "user_123")` (+ name if known) | Shows its own email-capture screen on Day 1 — you end up with an address you did not have |
| Signed-in user with a verified email | `AvafliUser(id = "user_123", email = "jane@example.com")` | Pre-fills the capture field and **locks** it (consent is still an explicit tick). A malformed value is ignored and the field stays editable |
| Nobody signed in | `AvafliUser.GUEST` | Mints a stable per-install guest id (`avafli_guest_…`). Never fabricate placeholder ids yourself |

When a guest signs in later, call `Avafli.configure(...)` again with the real
user: attribution upgrades in place and the streak (device-anchored) carries
over. Calling `configure()` more than once is safe.

### Sandbox vs live keys

Your dashboard shows two keys. Both hit the same production backend with
identical behaviour; the sandbox key puts every user and entry in an isolated
sandbox tenant with its own always-active test giveaway.

| Key | Use in | Effect |
| --- | --- | --- |
| `avafli_live_…` | Release builds | Your real giveaway, counts toward MAU |
| `avafli_test_…` | Debug builds, CI | Testers can never enter or win the real giveaway; never counts toward MAU or billing |

Your registered application IDs work with both keys. The SDK reads the
application ID from the `Context` (`packageName`) — register it in the dashboard
or registration fails with an `ERROR`-level Logcat line and the drawer never
appears.

## What the user sees

Everything below is built into the drawer; there is nothing to wire up.

- **Day 1 — email capture.** A capture screen with an age gate (minimum age is
  configured per publisher in the dashboard; default 18) and a marketing-consent
  checkbox that is **unchecked by default** and never gates entry. Official
  Rules and the Privacy Policy open inside the drawer (in-app WebView). Submit
  claims Day 1 entries immediately.
  - If the typed email already belongs to an Avafli account (another device or
    app), the SDK asks for a **6-digit code** emailed to that address before
    merging the streak across devices. Someone who leaves mid-way picks up at
    the code screen next time.
  - If the platform's email-verification flag is on, a brand-new address shows a
    dismissible "Verify your email" chip on the dashboard. It never blocks play
    or the draw; winners prove their inbox at claim time instead.
- **Every day — the dashboard.** The drawer opens once per calendar day, claims
  that day's entries automatically and celebrates: the day tile checks off, the
  total counts up, a "YOU'RE ON A ROLL!" toast ("YOU'RE IN!" on Day 1). The only
  button is GOT IT. Entries follow the giveaway's **streak ladder**, configured
  in the dashboard (the default ladder is +10 entries per consecutive day for 30
  days: 10, 20 … 300). Miss a day and the streak resets — unless the giveaway
  uses **visit mode**, a never-resetting variant for low-frequency apps.
- **"WE HAVE A WINNER!" banner** on the dashboard with the latest winner —
  shown only when enabled for your app in the dashboard (off by default).
- **Winner flow.** When one of your users is drawn, the next open lands on a
  winner splash instead of the dashboard (a pending claim opens even if the
  giveaway has already ended and nothing else is running). Before the claim
  form, the winner enters a **6-digit code** sent to the email on their account
  (skipped if they proved that inbox earlier). The form collects name, optional
  phone, a US shipping address (50 states + DC; address autocomplete when you
  add a Google Places key in the dashboard), an optional photo and an optional
  story/share step, and ends with a claim number. Progress lives on the server,
  so closing the drawer, reinstalling or switching device resumes where they
  were. Winners have a **claim window** to submit — 3 days (72 hours) by
  default, configurable per giveaway in the dashboard (1–60 days); an expired
  claim is not redrawn automatically.
- **Shared giveaway sponsor line (3.3.0).** If you opt your app into the shared
  giveaway (one prize pool across many Avafli apps, Avafli LLC as Sponsor of
  Record), a small "Sponsored by Avafli LLC" line appears under the prize on the
  capture screen, the dashboard and the winner splash. It is legally required
  and cannot be turned off. Exclusive giveaways show nothing extra.
- **US only.** The backend geo-fences every request; outside the US (or when
  the location cannot be verified) the drawer shows a "not available in your
  location" state instead of claiming.
- **Branding** comes from the dashboard: logo, prize image and primary colour
  update without an app release. The rest of the design is fixed.
- **Delete my data.** Users delete their Avafli data themselves from the
  Privacy Policy screen inside the experience ("Delete my data & stop
  participating"): the erasure is identity-wide, the drawer stays silent on
  that device for 24 hours, and after that they may join again as a brand-new
  participant. There is nothing for the host app to do, and no host-app call
  for it — only the person can erase their own data.
- **Privacy.** The SDK collects no advertising identifier.

## Controlling presentation

By default: register on `configure()`, auto-open once per calendar day (after
registration finishes, and on any Activity resume — a new day re-opens it even
if the app stayed in memory). Three controls change *when* it appears. Device
registration and analytics run inside `configure()` in every mode.

### `AvafliConfiguration.autoOpen`

| Value | Behaviour |
| --- | --- |
| `AvafliAutoOpen.ALWAYS` (default) | Auto-open once per calendar day when eligible |
| `AvafliAutoOpen.RETURNING_USERS_ONLY` | Skip the auto-open for the session in which this device registered for the first time (never interrupt a first-run onboarding); every later launch auto-opens |
| `AvafliAutoOpen.NEVER` | Never auto-open; you call `Avafli.present(activity)` |

The dashboard can set a server-side mode too; the effective mode is the most
restrictive of yours and the server's (`NEVER` > `RETURNING_USERS_ONLY` >
`ALWAYS`), and the dashboard's auto-open kill switch always wins.

### `Avafli.present(activity, callback)`

Opens the drawer now — from a button, a rewards screen, the end of onboarding.

```kotlin
Avafli.present(this) { result ->          // `this` = the current Activity
    result.onSuccess { grant -> /* today's entries were claimed in this open: grant.entries, grant.streakDay, grant.totalEntries */ }
          .onFailure { e -> /* AvafliError — see the table in the API reference */ }
}
```

- Same guards as the auto-open: configured, user not opted out, publisher not
  suspended, an active giveaway (or a pending prize claim), not already on
  screen. Failures go to the optional callback — never thrown.
- If `configure()`'s registration is still in flight, `present()` waits for it,
  then opens.
- Bypasses the once-per-day mark and the unregistered-impression cap and does
  not count an impression. When the drawer closes, the once-per-day mark is
  written so the auto-open does not pop again that day.
- The callback fires with `Result.success(grant)` only if today's entries were
  claimed during that open (not when they were already claimed earlier). A
  second `present()` while the drawer is open is a no-op and does not invoke
  the callback.

### `Avafli.holdAutoOpen()` / `Avafli.releaseAutoOpen()`

For boot flows (splash, onboarding, a sign-in wall). Both are safe before
`configure()` and idempotent.

```kotlin
Avafli.holdAutoOpen()          // e.g. first thing in Application.onCreate
Avafli.configure(config)       // registers; the drawer stays closed
// … onboarding …
Avafli.releaseAutoOpen()       // re-runs the eligibility check immediately
```

While held nothing is burned — no once-per-day mark, no impression — and
`present()` still works.

### Eligibility rules the auto-open applies

- Once per calendar day (device local date), after registration succeeded.
- Users who have not submitted an email see at most **3** auto-opens in total
  (dashboard-configurable), then silence until they register.
- Opted-out users and suspended publishers never see it.
- **Pending prize claim:** while a claim is pending the once-per-day mark, the
  impression cap and `RETURNING_USERS_ONLY` no longer hold the drawer back — it
  opens once per app launch and, on return to the foreground, at most every 30
  minutes until the claim is submitted. `holdAutoOpen()`, `NEVER`, the opt-out
  and the kill switch still apply (on `NEVER`, open it with `present()`; it lands
  on the winner splash).
- If `configure()`'s registration failed with a network error, the next Activity
  resume retries it and then runs the eligibility check.

## Push reminders (FCM)

Streak reminders are sent from **your** Firebase project through Avafli's
backend. The SDK's only job is to hand your FCM token to Avafli; schedules and
copy are configured in the dashboard.

1. Set up [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging/android/client)
   in your app (the SDK compiles against `firebase-messaging` but does not bundle
   it — your app provides it at runtime).
2. Upload your FCM service-account key in the [Avafli Dashboard](https://sdk.avafli.com/dashboard).
3. Keep `AvafliOptions.enablePushReminders = true` (the default) and forward tokens:

```kotlin
// After configure(), from an Activity: fetches the current token and, on
// Android 13+, requests POST_NOTIFICATIONS if not yet granted.
Avafli.registerForPushNotifications(this)

// Keep the backend current when FCM rotates the token:
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Avafli.onNewToken(token)
    }
}
```

Both calls are no-ops when `enablePushReminders` is `false`. If
`firebase-messaging` is not on the runtime classpath,
`registerForPushNotifications()` logs at debug level and does nothing.
`registerForPushNotifications()` needs an Activity context to show the
permission dialog; with any other context it still registers the token but
leaves the permission to you.

## Analytics

Forward the SDK's events to your own analytics by passing an
`AnalyticsAdapter` in `AvafliOptions`. The interface has three methods; the SDK
currently calls only `trackEvent` (implement the other two as no-ops).
`ConsoleAnalyticsAdapter` logs everything to Logcat (tag `Avafli-Analytics`)
for development.

```kotlin
import com.avafli.avaflisdk.services.analytics.AnalyticsAdapter

class MyAnalyticsAdapter : AnalyticsAdapter {
    override fun trackEvent(name: String, params: Map<String, Any?>) { analytics.track(name, params) }
    override fun trackScreenView(screenName: String) {}
    override fun setUserProperty(key: String, value: String) {}
}

AvafliOptions(analyticsAdapter = MyAnalyticsAdapter())
```

| Event | When | Params |
| --- | --- | --- |
| `avafli_daily_entry_claimed` | Today's entries were claimed (auto-claim on open, or an offline retry that landed later) | `day`, `entries`, optional `weekly_bonus`, `monthly_bonus`, `milestone_day`; `recovered_offline = true` on a retry |
| `avafli_email_verified` | The user confirmed the email-verification code | — |
| `avafli_adoption_restaged` | A parked cross-device link re-opened on the code screen | — |
| `avafli_winner_claim_shown` | The winner splash was shown | `giveaway_id` |
| `avafli_prize_claim_submitted` | The claim form was submitted | `giveaway_id`, `claim_number` |
| `avafli_opted_out` | The user deleted their data from inside the drawer | — |

Events emitted while offline are buffered (last 100, persisted) and flushed on
reconnect with `original_timestamp` / `original_timestamp_ms` added.

## Testing checklist

Use your `avafli_test_…` key and `debugLogging = true`, then filter Logcat by
`Avafli-SDK`.

1. **Registration.** First launch logs `Device registered successfully`. An
   `ERROR` line mentioning the API key or bundle ID means the key is wrong or the
   application ID is not registered in the dashboard.
2. **Day 1.** The drawer opens on the capture screen; submit an email → the
   dashboard shows Day 1 claimed with the "YOU'RE IN!" toast.
3. **Day 2.** Change the device date to tomorrow (or wait), relaunch → the
   drawer opens by itself and Day 2 is claimed. Same day again → it does not
   re-open (once per day).
4. **Reset.** To replay Day 1: clear the app's data (Settings → Apps → your app →
   Storage → Clear data). The sandbox tenant lets you register as many test
   users as you like.
5. **Presentation control.** With `autoOpen = NEVER`, nothing opens by itself
   and `Avafli.present(activity)` opens the drawer; the callback receives the
   grant on the first open of the day.
6. **Unregistered cap.** Close the capture screen without submitting on three
   different days → the fourth day stays quiet until an email is submitted.
7. **Push.** Call `registerForPushNotifications(activity)` → the Android 13+
   permission dialog appears and `Push token registered successfully` is logged.
8. **Delete my data.** Dashboard → Privacy Policy → "Delete my data" → the
   drawer closes and does not reopen. A rejoin is possible 24 hours later.
9. **Winner flow.** Ask Avafli ([info@avafli.com](mailto:info@avafli.com)) to
   draw a winner in your sandbox tenant → the next open shows the splash, the
   code step and the claim form end to end.
10. **Release build.** Switch to the live key and confirm nothing else changed.
    Release builds log only `WARN`/`ERROR`.

## API reference

Package `com.avafli.avaflisdk`. Everything the host app uses is below; the
remaining public types (`domain.Giveaway`, `domain.SdkConfig`,
`domain.PrizeClaimBlock`, `domain.StreakEngine`, …) are wire models used by the
drawer and are not needed for integration.

### `object Avafli`

| Member | Description |
| --- | --- |
| `configure(configuration: AvafliConfiguration)` | Single entry point. Registers the device, then auto-opens per `autoOpen`. Safe to call again (e.g. after sign-in). |
| `present(activity: Activity, callback: ((Result<DailyEntryGrant>) -> Unit)? = null)` | Open the drawer now. See [Controlling presentation](#controlling-presentation). |
| `holdAutoOpen()` / `releaseAutoOpen()` | Defer / resume the once-a-day auto-open. Safe before `configure()`. |
| `registerForPushNotifications(context: Context)` | Fetch the FCM token and register it; requests `POST_NOTIFICATIONS` on Android 13+ when given an Activity. No-op if `enablePushReminders` is `false`. |
| `onNewToken(token: String)` | Forward a (new) FCM token. No-op if `enablePushReminders` is `false`. |

### `data class AvafliConfiguration`

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `context` | `Context` | required | Any context; the SDK keeps `applicationContext` |
| `apiKey` | `String` | required | `avafli_live_…` or `avafli_test_…` |
| `user` | `AvafliUser` | required | Your user, or `AvafliUser.GUEST` |
| `options` | `AvafliOptions` | `AvafliOptions()` | See below |
| `autoOpen` | `AvafliAutoOpen` | `ALWAYS` | `ALWAYS`, `RETURNING_USERS_ONLY`, `NEVER` |
| `environment` | `AvafliEnvironment` | `Production` | The only value; there is no separate staging backend — use the sandbox key instead |

### `data class AvafliUser`

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `id` | `String` | required | Your stable user id (the only required field). `AvafliUser.GUEST` for no signed-in user |
| `firstName`, `lastName` | `String` | `""` | Optional; collected at prize claim if missing |
| `phone` | `String?` | `null` | Optional, E.164 |
| `email` | `String?` | `null` | Optional; pre-fills and locks the capture field. Never records consent |

### `data class AvafliOptions`

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `debugLogging` | `Boolean` | `false` | `DEBUG`/`INFO` lines on Logcat tag `Avafli-SDK` (`WARN`/`ERROR` always log) |
| `analyticsAdapter` | `AnalyticsAdapter?` | `null` | Receives the events in [Analytics](#analytics) |
| `enablePushReminders` | `Boolean` | `true` | Gates `registerForPushNotifications()` / `onNewToken()` |
| `enableCertificatePinning` | `Boolean` | `true` | Pins the backend's CA chain (Google Trust Services root + intermediate) |
| `networkTimeoutSeconds` | `Long` | `30` | Connect/read/write timeout |

### `enum class AvafliAutoOpen` — `ALWAYS`, `RETURNING_USERS_ONLY`, `NEVER`

### `data class DailyEntryGrant` (delivered to the `present()` callback)

`entries: Int`, `streakDay: Int`, `totalEntries: Int`, `weeklyBonusEntries: Int?`,
`monthlyBonusEntries: Int?`, `milestone: Milestone?` (`day`, `bonusEntries`, `badge`), `doubled: Boolean`.

### `sealed class AvafliError : Exception`

| Error | When |
| --- | --- |
| `NotInitialized` | `present()` before `configure()` |
| `InvalidPublisherKey` | Registration attempted without a key (declared; not reachable through the public API) |
| `NoGiveaway` | `present()` with no active giveaway and no pending claim |
| `OptedOut` | `present()` for a user who deleted their data (within the 24 hours) |
| `ServiceUnavailable` | Publisher account or key suspended/revoked — the SDK goes quiet |
| `GeoBlocked` | Request from outside the US (or location unverifiable) |
| `AlreadyClaimed` | Declared for the "already claimed today" case; 3.3.0 handles that server answer internally and never raises it |
| `NetworkError` | Transport failure (offline, timeout) — retried same-day automatically |
| `ServerError(code, message)` | Non-2xx from the backend after the SDK's own retries |
| `TokenRefreshFailed` | Session could not be refreshed; the drawer shows a retry state |
| `RegistrationFailed(message)` | Malformed registration response |
| `Unknown(message, cause)` | Anything else (e.g. `present()` on a finishing Activity) |

### `interface services.analytics.AnalyticsAdapter`

`trackEvent(name: String, params: Map<String, Any?> = emptyMap())`,
`trackScreenView(screenName: String)`, `setUserProperty(key: String, value: String)`.
`ConsoleAnalyticsAdapter` is a ready-made Logcat implementation.

---

## Example app

[`example/`](example) is the quick start above as a runnable project:
`ExampleApplication` configures the SDK, `MainActivity` has an "Open Avafli"
button that calls `Avafli.present(this)`. Put your sandbox and live keys in
`ExampleApplication.kt` and run `./gradlew :example:assembleDebug`.

© 2026 Avafli. All Rights Reserved.

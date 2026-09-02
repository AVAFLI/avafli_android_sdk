# Avafli Android SDK
**Drop-in sweepstakes, prizing, and gamification for your Android app**

[![JitPack](https://jitpack.io/v/AVAFLI/avafli_android_sdk.svg)](https://jitpack.io/#AVAFLI/avafli_android_sdk)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1%2B-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

---

## Overview

Avafli lets you add daily-entry sweepstakes and prize experiences to your app in under 20 lines of code. The V2 experience is a bottom drawer that opens itself on the first app-open of each day, claims the user's daily entries automatically, and celebrates the result. You integrate once; prize configuration and branding are managed server-side from the Avafli dashboard.

**Key capabilities:**
- **Daily entry sweepstakes** — Users earn entries every day they engage
- **V2 auto-open experience** — The bottom-drawer experience opens itself on the first app-open of each day and grants entries automatically
- **Daily streak + auto-claim** — Entries climb a +10/day ladder; the drawer auto-opens once per day and claims that day's entries — there is no manual present API
- **Email capture** — The SDK captures an email through its own opt-in screen, with an UNCHECKED-by-default marketing-consent tick and a publisher-configurable age gate
- **Cross-device verified adoption** — When a typed email matches an existing account, the SDK confirms a 6-digit code before merging the streak across devices
- **Soft email verification** — A brand-new typed email shows a persistent, dismissible "Verify your email" chip; it never blocks play, only prize-draw eligibility
- **Winner claim flow** — "WE HAVE A WINNER!" banner and an in-drawer prize-claim flow (name, shipping address incl. DC, optional photo, claim number)
- **Visit mode** — A never-resetting streak variant for low-frequency apps
- **Push reminders** — Drive re-engagement with daily nudges (FCM); requests POST_NOTIFICATIONS on Android 13+
- **Server-driven branding** — Logo, prize image, and primary color update without app releases
- **GDPR/CCPA compliant** — Built-in consent flows, RTD opt-out via `optOut()`, and a self-serve "Delete my data & stop participating" section inside the in-app Privacy Policy
- **No ad tracking** — The SDK collects no advertising identifiers (no Google advertising ID)
- **Analytics forwarding** — Route SDK events to your existing analytics stack

## Quick Start

```kotlin
import com.avafli.avaflisdk.Avafli
import com.avafli.avaflisdk.AvafliConfiguration
import com.avafli.avaflisdk.AvafliOptions
import com.avafli.avaflisdk.AvafliUser

val config = AvafliConfiguration(
    context = applicationContext,
    apiKey = "YOUR_API_KEY",  // debug builds: use your avafli_test_ sandbox key
    user = AvafliUser(
        id = "user_123",              // only id is required — pass whatever identity you have
        firstName = "Jane",
        lastName = "Doe",
        email = "jane@example.com"    // include it when you have it — pre-fills & locks the capture form (consent stays explicit)
    ),
    // Nobody signed in? use user = AvafliUser.GUEST
    options = AvafliOptions(
        debugLogging = false,         // true while integrating
        enablePushReminders = true    // streak reminders via YOUR Firebase project (upload the key in your dashboard)
    )
)
Avafli.configure(config)

// Done — the experience auto-opens once per day. No further calls needed.
// Push reminders: forward your FCM token from your FirebaseMessagingService:
//   Avafli.onNewToken(token)
```

> **Auto-open:** After `configure()`, the SDK presents the experience automatically once per calendar day (after registration and on activity resumes — a new day re-opens it even if the app stayed in memory). It can be disabled remotely via the dashboard's `experience.autoOpenEnabled` kill switch; unregistered users see at most 3 auto-opens until they submit an email, and RTD opted-out users never see it.

### Identity — pass what you have, the SDK captures the rest

Only `id` is required. Construct an `AvafliUser` from whatever identity data you
already hold — even just an id — and the SDK fills in the gaps: it captures the
email through its own screen, and the name at prize-claim time if the user wins.
There are three cases:

**1. Signed-in user without an email (the common case, and Avafli's main value).**
Pass the id plus whatever you have and OMIT `email`. The SDK shows its capture
screen and the user types their email — so you capture an address you didn't
have before:

```kotlin
user = AvafliUser(id = "user_123", firstName = "Jane", lastName = "Doe")   // no email
```

Even just `AvafliUser(id = "user_123")` is valid — name is collected later at
prize-claim, only if they win.

**2. Signed-in user with an email.** Pass `email` too and it pre-fills and
**locks** the capture field (consent is still an explicit tick inside the flow).
`email` is a plain `String`:

```kotlin
user = AvafliUser(id = "user_123", firstName = "Jane", lastName = "Doe", email = "jane@example.com")
```

**3. No signed-in user at all.** Pass `AvafliUser.GUEST`:

```kotlin
Avafli.configure(AvafliConfiguration(
    context = applicationContext,
    apiKey = "YOUR_API_KEY",
    user = AvafliUser.GUEST,
))
```

The SDK mints a stable per-install guest id (`avafli_guest_…`) for attribution —
never fabricate placeholder ids yourself. The experience is fully functional
for guests. When the user signs in, call `configure` again with the real user:
attribution upgrades in place and the streak carries over automatically.

## Installation

### 1. Add JitPack repository

In your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add dependency

In your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.AVAFLI:avafli_android_sdk:v3.1.1")
}
```

> **Note:** Contact [AVAFLI](https://sdk.avafli.com/pricing) to obtain an API key.

## Configuration

Initialize the SDK with your user and environment settings:

```kotlin
val config = AvafliConfiguration(
    context = applicationContext,
    apiKey = "avafli_live_xxxxxxxxxx",
    environment = AvafliEnvironment.Production,
    user = AvafliUser(
        id = "user_abc123",
        firstName = "Jane",
        lastName = "Doe",
        phone = "+15551234567"  // optional
    ),
    options = AvafliOptions(
        debugLogging = true,
        analyticsAdapter = myAdapter,
        enablePushReminders = true
    )
)

Avafli.configure(config)
```

### AvafliConfiguration

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `context` | `Context` | ✅ | Application or Activity context |
| `apiKey` | `String` | ✅ | Your Avafli API key from the dashboard |
| `environment` | `AvafliEnvironment` | — | `.Production` (default) |
| `user` | `AvafliUser` | ✅ | The authenticated user |
| `options` | `AvafliOptions?` | — | Optional behavior toggles |

> **App ID:** The SDK auto-detects your Android application ID from the host `Context` (`context.packageName`). You do not pass it manually.

### AvafliOptions

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `debugLogging` | `Boolean` | `false` | Enable verbose logging |
| `analyticsAdapter` | `AnalyticsAdapter?` | `null` | Routes SDK events to your analytics stack |
| `enablePushReminders` | `Boolean` | `true` | Enables streak reminder push notifications (requires FCM in the host app) |
| `enableCertificatePinning` | `Boolean` | `true` | Certificate pinning for backend calls (recommended for production) |
| `networkTimeoutSeconds` | `Long` | `30` | Custom timeout for network requests |

### AvafliUser

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `id` | `String` | ✅ | Unique, stable user identifier (the only required field) |
| `firstName` | `String` | — | User's first name; captured at prize-claim if omitted |
| `lastName` | `String` | — | User's last name; captured at prize-claim if omitted |
| `phone` | `String?` | — | Phone number in E.164 format |
| `email` | `String?` | — | If passed, pre-fills and locks the capture field; if omitted, the SDK captures it |

> **Email:** Omit it and the SDK captures an address through its own opt-in
> screen (the common case). Pass it and that address pre-fills and locks —
> consent is still an explicit tick inside the flow. See the three identity
> cases above.

## Test in Development: Your Sandbox Key

Your publisher dashboard shows two API keys:

| Key | Use it in |
| --- | --------- |
| `avafli_live_…` | Release builds — your real giveaway |
| `avafli_test_…` | Debug/dev builds and CI — an isolated sandbox |

The sandbox key hits the **same production backend** with identical behavior —
registration, streaks, entries, the full experience — but every user and entry
lands in a separate sandbox tenant with its own always-active test giveaway.
That means:

- Your developers and testers **can never enter (or win) your real giveaway.**
- Sandbox usage **never counts toward your MAU** or your bill.
- Your registered bundle IDs work with both keys automatically.

Swap keys per build configuration and nothing else about your integration
changes.

## The Experience

The V2 experience presents itself automatically once per calendar day (first app-open of the day). Entries are claimed automatically when it opens, and the celebration is the first thing the user sees: the dashboard opens with today's grant already showing — the day tile checks off with a confetti burst, the total counts up and pops, and the bar leads with a "YOU'RE ON A ROLL!" toast before settling into the come-back message. There is no button to tap to collect entries (the pill just reads GOT IT and closes) and no launch API — after `configure()`, the SDK handles everything: presentation timing, entry claiming, and celebration. Brand-new users first submit their email, then land straight on the same celebrating dashboard — the toast just reads "YOU'RE IN!" on Day 1.

## Email Capture & Verification

Email is captured inside the SDK's own opt-in screen (see the identity section above). The screen shows a publisher-configurable **age gate** — an affirmative tick that gates the CTA — and a **marketing-consent** checkbox that is **unchecked by default** and never gates entry (declining it costs neither the entry nor, if drawn, winner contact).

Two verification paths run from that screen:

- **Cross-device verified adoption.** When the typed email matches an existing Avafli account (from another device or install), the SDK asks for a **6-digit code** emailed to that address before the two identities are merged — so a streak follows the person across devices without letting anyone attach to someone else's record.
- **Soft email verification (2.7.0+).** A brand-new, never-before-seen typed email surfaces a persistent, dismissible **"Verify your email"** chip on the dashboard. It **never blocks play** — the user keeps earning entries — it only affects prize-draw eligibility until the address is confirmed.

## Winner Experience

When one of your users is drawn as a giveaway winner, the drawer automatically opens on a winner splash instead of the dashboard, then walks them through a prize-claim form (name, shipping address, optional photo) and a confirmation with their claim number. This requires no integration work — the flow appears only for the drawn winner and disappears once their claim is submitted.

## Push Notifications

Drive re-engagement with daily reminders. Publishers forward their FCM token to Avafli:

### 1. Setup Firebase Cloud Messaging

Follow the [Firebase Android setup guide](https://firebase.google.com/docs/cloud-messaging/android/client) if you haven't already.

### 2. Register for Notifications

```kotlin
// After Avafli.configure() — no-op if AvafliOptions.enablePushReminders is false
Avafli.registerForPushNotifications(context)
```

### 3. Forward FCM Token

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Avafli.onNewToken(token)
    }
}
```

### 4. Upload FCM Service Account Key

Upload your FCM service account key via the [Avafli Dashboard](https://sdk.avafli.com/dashboard) to enable push notifications. Reminder schedules and messaging are configured server-side from the dashboard.

## Customization

The V2 experience is hardcoded to the Avafli design; publishers customize exactly three things through the [Avafli Dashboard](https://sdk.avafli.com/dashboard):

- **Logo** — Shown in the drawer header
- **Prize image** — Art for the dashboard prize card
- **Primary color** — Accent for CTAs, streak tiles, and highlights

Plus prize configuration (active giveaways, the entry ladder) and push reminder schedules.

Changes apply instantly across all app installations without requiring an app update.

## Analytics

Forward Avafli events to your existing analytics stack:

```kotlin
class MyAnalyticsAdapter : AnalyticsAdapter {
    override fun trackEvent(name: String, params: Map<String, Any?>) {
        // Forward to Amplitude, Mixpanel, Segment, etc.
        analytics.track(name, params)
    }

    override fun trackScreenView(screenName: String) {
        // Track screen views
    }

    override fun setUserProperty(key: String, value: String) {
        // Set user properties
    }
}

// Pass during configuration
val options = AvafliOptions(
    analyticsAdapter = MyAnalyticsAdapter()
)
```

**Events emitted by the SDK:**
- `avafli_daily_entry_claimed` — Daily entries awarded (auto-claimed on open). Params: `day`, `entries`.
- `avafli_email_verified` / `avafli_adoption_restaged` — Email verification confirmed / an abandoned cross-device adoption re-staged
- `avafli_winner_claim_shown` / `avafli_prize_claim_submitted` — Winner claim flow shown / submitted
- `avafli_opted_out` — Right-to-delete opt-out completed

## Account deletion in your app

If your app has its own delete-account flow, call `optOut()` from it so the
user's Avafli data is erased along with their account. Users can also delete
their data themselves at any time from the Privacy Policy screen inside the
experience — no integration required.

```kotlin
// From your delete-account flow (optOut is a suspend function)
lifecycleScope.launch {
    Avafli.optOut()
        .onSuccess { /* Avafli data erased, experience silenced */ }
        .onFailure { error -> /* Handle error */ }
}
```

The erasure is identity-wide (one call covers all of the person's devices),
includes prize-claim records, and permanently silences the experience on the
device — it survives a reinstall. De-identified entry records are retained as
the legally required evidence that drawings were fair (GDPR Art. 17(3)): the
person is erased, the proof is kept.

## API Reference

### Core Methods

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `Avafli.configure(config)` | `Unit` | Initialize the SDK; the experience auto-opens once per day |
| `Avafli.optOut()` | `suspend Result<Unit>` | RTD opt-out — permanently silence the experience |

### Push Notifications

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `Avafli.registerForPushNotifications(context)` | `Unit` | Register for push notifications |
| `Avafli.onNewToken(token)` | `Unit` | Forward an FCM token to Avafli |

For detailed API documentation, see the [Avafli Docs](https://sdk.avafli.com/android).

## Links

- **Dashboard:** [https://sdk.avafli.com/dashboard](https://sdk.avafli.com/dashboard)
- **Documentation:** [https://sdk.avafli.com/android](https://sdk.avafli.com/android)
- **Support:** [info@avafli.com](mailto:info@avafli.com)

---

© 2026 Avafli. All Rights Reserved.

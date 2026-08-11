# WINR Android SDK
**Drop-in sweepstakes, prizing, and gamification for your Android app**

[![JitPack](https://jitpack.io/v/AVAFLI/winr_android_sdk.svg)](https://jitpack.io/#AVAFLI/winr_android_sdk)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1%2B-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

---

## Overview

WINR lets you add daily-entry sweepstakes and prize experiences to your app in under 20 lines of code. The V2 experience is a bottom drawer that opens itself on the first app-open of each day, claims the user's daily entries automatically, and celebrates the result. You integrate once; prize configuration and branding are managed server-side from the WINR dashboard.

**Key capabilities:**
- **Daily entry sweepstakes** — Users earn entries every day they engage
- **V2 auto-open experience** — The bottom-drawer experience opens itself on the first app-open of each day and grants entries automatically
- **Streak ladder + milestone accelerators** — Escalating daily entry rewards, with server-configurable milestone bonuses
- **Winner announcements** — "WE HAVE A WINNER!" banner and winner dialog, driven by the giveaway's `latestWinner`
- **Visit mode** — A never-resetting streak variant for low-frequency apps
- **Push reminders** — Drive re-engagement with daily nudges (FCM)
- **Server-driven branding** — Logo, prize image, and primary color update without app releases
- **GDPR/CCPA compliant** — Built-in consent flows, RTD opt-out, and user data deletion
- **Analytics forwarding** — Route SDK events to your existing analytics stack

## Quick Start

```kotlin
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRConfiguration
import com.avafli.winrsdk.WINREnvironment
import com.avafli.winrsdk.WINRUser

// 1. Configure the SDK — call once at app launch
val config = WINRConfiguration(
    context = applicationContext,
    apiKey = "YOUR_API_KEY",
    environment = WINREnvironment.Production,
    user = WINRUser(
        id = "user_123",
        firstName = "Jane",
        lastName = "Doe"
    )
)
WINR.configure(config)

// 2. That's it — one call, and the experience opens itself
//    on the first app-open of each day.
```

> **Auto-open:** After `configure()`, the SDK presents the experience automatically once per calendar day (after registration and on activity resumes — a new day re-opens it even if the app stayed in memory). It can be disabled remotely via the dashboard's `experience.autoOpenEnabled` kill switch; unregistered users see at most 3 auto-opens until they submit an email, and RTD opted-out users never see it.

### Guest / logged-out users

No account system, or the user isn't signed in? Pass `WINRUser.GUEST`:

```kotlin
WINR.configure(context, WINRConfiguration(
    apiKey = "winr_live_…",
    bundleId = packageName,
    user = WINRUser.GUEST,
))
```

The SDK mints a stable per-install guest id (`winr_guest_…`) for attribution —
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
    implementation("com.github.AVAFLI:winr_android_sdk:v2.6.2")
}
```

> **Note:** Contact [AVAFLI](https://avafli-website.web.app/sdk/pricing) to obtain an API key.

## Configuration

Initialize the SDK with your user and environment settings:

```kotlin
val config = WINRConfiguration(
    context = applicationContext,
    apiKey = "winr_live_xxxxxxxxxx",
    environment = WINREnvironment.Production,
    user = WINRUser(
        id = "user_abc123",
        firstName = "Jane",
        lastName = "Doe",
        phone = "+15551234567"  // optional
    ),
    options = WINROptions(
        debugLogging = true,
        analyticsAdapter = myAdapter,
        enablePushReminders = true
    )
)

WINR.configure(config)
```

### WINRConfiguration

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `context` | `Context` | ✅ | Application or Activity context |
| `apiKey` | `String` | ✅ | Your WINR API key from the dashboard |
| `environment` | `WINREnvironment` | — | `.Production` (default) |
| `user` | `WINRUser` | ✅ | The authenticated user |
| `options` | `WINROptions?` | — | Optional behavior toggles |

> **App ID:** The SDK auto-detects your Android application ID from the host `Context` (`context.packageName`). You do not pass it manually.

### WINROptions

| Parameter | Type | Default | Description |
| --------- | ---- | ------- | ----------- |
| `debugLogging` | `Boolean` | `false` | Enable verbose logging |
| `analyticsAdapter` | `AnalyticsAdapter?` | `null` | Routes SDK events to your analytics stack |
| `enablePushReminders` | `Boolean` | `true` | Enables streak reminder push notifications (requires FCM in the host app) |
| `enableCertificatePinning` | `Boolean` | `true` | Certificate pinning for backend calls (recommended for production) |
| `networkTimeoutSeconds` | `Long` | `30` | Custom timeout for network requests |

### WINRUser

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `id` | `String` | ✅ | Unique, stable user identifier |
| `firstName` | `String` | ✅ | User's first name |
| `lastName` | `String` | ✅ | User's last name |
| `phone` | `String?` | — | Phone number in E.164 format |

> **Email:** The SDK captures email through its own opt-in UI. Do not pass email via `WINRUser`.

## The Experience

The V2 experience presents itself automatically once per calendar day (first app-open of the day). Entries are claimed automatically when it opens, and the celebration is the first thing the user sees: the dashboard opens with today's grant already showing — the day tile checks off with a confetti burst, the total counts up and pops, and the bar leads with a "YOU'RE ON A ROLL!" toast before settling into the come-back message. There is no button to tap to collect entries (the pill just reads GOT IT and closes) and no launch API — after `configure()`, the SDK handles everything: presentation timing, entry claiming, and celebration. Brand-new users first submit their email, then land straight on the same celebrating dashboard — the toast just reads "YOU'RE IN!" on Day 1.

## Winner Experience

When one of your users is drawn as a giveaway winner, the drawer automatically opens on a winner splash instead of the dashboard, then walks them through a prize-claim form (name, shipping address, optional photo) and a confirmation with their claim number. This requires no integration work — the flow appears only for the drawn winner and disappears once their claim is submitted.

## Push Notifications

Drive re-engagement with daily reminders. Publishers forward their FCM token to WINR:

### 1. Setup Firebase Cloud Messaging

Follow the [Firebase Android setup guide](https://firebase.google.com/docs/cloud-messaging/android/client) if you haven't already.

### 2. Register for Notifications

```kotlin
// After WINR.configure() — no-op if WINROptions.enablePushReminders is false
WINR.registerForPushNotifications(context)
```

### 3. Forward FCM Token

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        WINR.onNewToken(token)
    }
}
```

### 4. Upload FCM Service Account Key

Upload your FCM service account key via the [WINR Dashboard](https://avafli-website.web.app/sdk/dashboard) to enable push notifications. Reminder schedules and messaging are configured server-side from the dashboard.

## Customization

The V2 experience is hardcoded to the WINR design; publishers customize exactly three things through the [WINR Dashboard](https://avafli-website.web.app/sdk/dashboard):

- **Logo** — Shown in the drawer header
- **Prize image** — Art for the dashboard prize card
- **Primary color** — Accent for CTAs, streak tiles, and highlights

Plus prize configuration (active giveaways, ladder, milestones) and push reminder schedules.

Changes apply instantly across all app installations without requiring an app update.

## Analytics

Forward WINR events to your existing analytics stack:

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
val options = WINROptions(
    analyticsAdapter = MyAnalyticsAdapter()
)
```

**Events emitted by the SDK:**
- `winr_daily_entry_claimed` — Daily entries awarded (auto-claimed on open). Params: `day`, `entries`, plus `weekly_bonus`, `monthly_bonus`, and `milestone_day` when awarded.

## GDPR / CCPA

Handle erasure requests with `optOut()`:

```kotlin
lifecycleScope.launch {
    WINR.optOut()
        .onSuccess { /* Data erased, experience silenced */ }
        .onFailure { error -> /* Handle error */ }
}
```

This is the complete Right-to-be-Forgotten path. It removes the person's personal
information everywhere it is held — including prize-claim records, which carry name,
address and phone — links their devices together so one call covers all of them, and
permanently silences the experience on the device so it survives a reinstall.

De-identified entry records are deliberately retained. They are the evidence that a
drawing was fair and that a prize went to a real eligible person, which a sweepstakes
operator must be able to show; GDPR Art. 17(3) exempts data needed for legal claims.
The person is erased, the proof is kept.

> A previous `deleteAccount()` method was removed in 2.5.0. It hard-deleted entry
> records, which both destroyed that evidence and — because it left no tombstone —
> allowed delete-and-re-register to farm unlimited entries. Use `optOut()`.


## API Reference

### Core Methods

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `WINR.configure(config)` | `Unit` | Initialize the SDK; the experience auto-opens once per day |
| `WINR.optOut()` | `suspend Result<Unit>` | RTD opt-out — permanently silence the experience |

### Push Notifications

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `WINR.registerForPushNotifications(context)` | `Unit` | Register for push notifications |
| `WINR.onNewToken(token)` | `Unit` | Forward an FCM token to WINR |

For detailed API documentation, see the [WINR Docs](https://avafli-website.web.app/sdk/android).

## Links

- **Dashboard:** [https://avafli-website.web.app/sdk/dashboard](https://avafli-website.web.app/sdk/dashboard)
- **Documentation:** [https://avafli-website.web.app/sdk/android](https://avafli-website.web.app/sdk/android)
- **Support:** [info@avafli.com](mailto:info@avafli.com)

---

© 2026 Avafli. All Rights Reserved.

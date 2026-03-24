# WINR Android SDK
**Drop-in sweepstakes, prizing, and gamification for your Android app**

[![JitPack](https://jitpack.io/v/avafli/winr-android-sdk.svg)](https://jitpack.io/#avafli/winr-android-sdk)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1%2B-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

---

## Overview

WINR lets you add daily-entry sweepstakes and prize experiences to your app in under 20 lines of code. The entire UI — branding, theming, copy, and prize configuration — is managed server-side from the WINR dashboard. You integrate once; your marketing team controls the rest.

**Key capabilities:**
- **Daily entry sweepstakes** — Users earn entries every day they engage
- **Bonus entries via rewarded video** — Monetize attention with opt-in ads
- **Push reminders** — Drive re-engagement with daily nudges (FCM)
- **Server-driven UI** — Branding, prizes, and copy update without app releases
- **GDPR/CCPA compliant** — Built-in consent flows and user data deletion
- **Analytics forwarding** — Route SDK events to your existing analytics stack

## Quick Start

```kotlin
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRConfiguration
import com.avafli.winrsdk.WINREnvironment
import com.avafli.winrsdk.WINRUser

// 1. Configure the SDK
val config = WINRConfiguration(
    context = applicationContext,
    apiKey = "YOUR_API_KEY",
    bundleId = "com.example.myapp",
    environment = WINREnvironment.Production,
    user = WINRUser(
        id = "user_123",
        firstName = "Jane",
        lastName = "Doe"
    )
)
WINR.configure(config)

// 2. Present the experience
WINR.present(activity)
```

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
    implementation("com.github.avafli:winr-android-sdk:1.0.0")
}
```

> **Note:** Contact [team@avafli.com](mailto:team@avafli.com) to obtain an API key.

## Configuration

Initialize the SDK with your user and environment settings:

```kotlin
val config = WINRConfiguration(
    context = applicationContext,
    apiKey = "winr_live_xxxxxxxxxx",
    bundleId = "com.example.myapp",
    environment = WINREnvironment.Production,
    user = WINRUser(
        id = "user_abc123",
        firstName = "Jane",
        lastName = "Doe",
        phone = "+15551234567"  // optional
    ),
    options = WINROptions(
        logging = LoggingLevel.Info,
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
| `bundleId` | `String` | ✅ | App bundle ID (e.g., com.example.myapp) |
| `environment` | `WINREnvironment` | ✅ | `.Production`, `.Staging`, or `.QA` |
| `user` | `WINRUser` | ✅ | The authenticated user |
| `options` | `WINROptions?` | — | Optional behavior toggles |

### WINRUser

| Parameter | Type | Required | Description |
| --------- | ---- | -------- | ----------- |
| `id` | `String` | ✅ | Unique, stable user identifier |
| `firstName` | `String` | ✅ | User's first name |
| `lastName` | `String` | ✅ | User's last name |
| `phone` | `String?` | — | Phone number in E.164 format |

> **Email:** The SDK captures email through its own opt-in UI. Do not pass email via `WINRUser`.

## Present the Experience

Launch the full-screen WINR experience:

```kotlin
// Simple presentation
WINR.present(activity)

// With completion callback
WINR.present(activity) { result ->
    result.onSuccess { grant ->
        Log.d("WINR", "Entries earned: ${grant.entries}, streak day: ${grant.streakDay}")
    }
    result.onFailure { error ->
        Log.e("WINR", "Error: ${error.message}")
    }
}
```

The callback receives a `DailyEntryGrant` with the entries earned during the session.

## Push Notifications

Drive re-engagement with daily reminders. Publishers forward their FCM token to WINR:

### 1. Setup Firebase Cloud Messaging

Follow the [Firebase Android setup guide](https://firebase.google.com/docs/cloud-messaging/android/client) if you haven't already.

### 2. Register for Notifications

```kotlin
// After WINR.configure()
WINRPushNotificationManager.instance.registerForPushNotifications(context)
```

### 3. Forward FCM Token

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        WINRPushNotificationManager.instance.didReceiveRegistrationToken(token)
    }
}
```

### 4. Upload FCM Service Account Key

Upload your FCM service account key via the [WINR Dashboard](https://avafli-website.web.app/sdk/dashboard) to enable push notifications.

### 5. Enable Push Reminders

Set `enablePushReminders = true` in `WINROptions` during configuration.

## Customization

All branding, themes, and copy are managed server-side through the [WINR Dashboard](https://avafli-website.web.app/sdk/dashboard):

- **Colors & Branding** — Primary colors, logos, backgrounds
- **Copy & Messaging** — Headlines, CTAs, legal text
- **Prize Configuration** — Active giveaways, entry mechanics
- **Push Notifications** — Reminder schedules and messaging

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
    analyticsAdapter = MyAnalyticsAdapter(),
    enablePushReminders = true
)
```

**Events emitted by the SDK:**
- `winr.session_started` — User opened the WINR experience
- `winr.entry_granted` — Daily entries awarded
- `winr.bonus_entry_granted` — Bonus entries earned via rewarded video
- `winr.session_completed` — User closed the WINR experience
- `winr.push_registered` — Device registered for push reminders

## GDPR / Delete User Data

Support GDPR/CCPA deletion requests:

```kotlin
lifecycleScope.launch {
    WINR.deleteUserData()
        .onSuccess { /* All data deleted */ }
        .onFailure { error -> /* Handle error */ }
}
```

This permanently removes all user data, entries, preferences, and consent records from WINR servers.

## API Reference

### Core Methods

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `WINR.configure(config)` | `Unit` | Initialize the SDK with user and settings |
| `WINR.present(activity, callback?)` | `Unit` | Launch the full-screen WINR experience |
| `WINR.deleteUserData()` | `Result<Unit>` | Permanently delete all user data |

### Push Notifications

| Method | Returns | Description |
| ------ | ------- | ----------- |
| `WINRPushNotificationManager.instance.didReceiveRegistrationToken(token)` | `Unit` | Forward FCM token to WINR |
| `WINRPushNotificationManager.instance.registerForPushNotifications(context)` | `Unit` | Register for push notifications |

For detailed API documentation, see the [WINR Docs](https://docs.avafli.com).

## Links

- **Dashboard:** [https://avafli-website.web.app/sdk/dashboard](https://avafli-website.web.app/sdk/dashboard)
- **Documentation:** [https://docs.avafli.com](https://docs.avafli.com)
- **Support:** [team@avafli.com](mailto:team@avafli.com)

---

© 2026 Avafli. All Rights Reserved.
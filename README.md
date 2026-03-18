<p align="center">
  <img src="https://avafli.com/winr-logo.png" alt="WINR" width="120" />
</p>

<h1 align="center">WINR Android SDK</h1>

<p align="center">
  <a href="https://jitpack.io/#avafli/winr-android-sdk"><img src="https://jitpack.io/v/avafli/winr-android-sdk.svg" alt="JitPack" /></a>
  <img src="https://img.shields.io/badge/API-26%2B-brightgreen.svg" alt="API 26+" />
  <img src="https://img.shields.io/badge/Kotlin-2.1%2B-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin 2.1+" />
  <img src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" />
</p>

<p align="center">
  Sweepstakes, gamification, and prizing infrastructure for Android app publishers.<br />
  Engage your users with daily entry systems, streak rewards, and rewarded video — in two lines of code.
</p>

---

## Overview

WINR is a drop-in SDK that adds sweepstakes and prizing experiences to your Android app. Publishers integrate the SDK, configure giveaways through the [WINR Dashboard](https://dashboard.avafli.com), and the SDK handles everything else: entry mechanics, streak tracking, email capture, UI rendering, push notifications, and compliance.

**Key characteristics:**

- **2-line integration** — configure and present
- **100% server-driven branding** — colors, logos, copy, and layout configured in your dashboard
- **Built-in email capture** — the SDK collects and manages user emails; publishers never handle PII
- **Jetpack Compose UI** — modern Material 3 components, no XML
- **Secure by default** — certificate pinning, encrypted local storage, GDPR delete

## Requirements

| Requirement | Minimum |
|---|---|
| Android API | 26 (Android 8.0) |
| Kotlin | 2.1+ |
| Jetpack Compose | BOM 2024.01+ |
| Gradle | 8.0+ |

## Installation

### 1. Add the JitPack repository

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

### 2. Add the dependency

In your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.avafli:winr-android-sdk:1.0.0")
}
```

### 3. Sync and build

The SDK bundles its own ProGuard consumer rules. No additional configuration required.

---

## Quick Start

```kotlin
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRConfiguration
import com.avafli.winrsdk.WINREnvironment

// Configure (once, in Application.onCreate or your launcher Activity)
val config = WINRConfiguration(
    context = applicationContext,
    apiKey = "winr_live_xxxxxxxxxxxxxxxx",
    environment = WINREnvironment.Production
)
WINR.configure(config)

// Present the WINR experience
WINR.present(activity)
```

That's it. The SDK registers the device, fetches active giveaways, renders the experience using your dashboard-configured branding, and manages the full entry lifecycle.

---

## API Reference

### `WINR.configure`

Configures the SDK. Call once before any other WINR method.

```kotlin
WINR.configure(
    WINRConfiguration(
        context = applicationContext,
        apiKey = "winr_live_xxxxxxxxxxxxxxxx",
        environment = WINREnvironment.Production,  // or .Sandbox
        options = WINROptions()
    )
)
```

| `WINRConfiguration` Field | Type | Required | Description |
|---|---|---|---|
| `context` | `Context` | ✅ | Application or Activity context |
| `apiKey` | `String` | ✅ | Your publisher API key (`winr_live_xxx`) |
| `environment` | `WINREnvironment` | — | `.Production` (default) or `.Sandbox` |
| `options` | `WINROptions` | — | Additional configuration (see below) |

**Environments:**

| Environment | Purpose |
|---|---|
| `WINREnvironment.Production` | Live giveaways, real entries |
| `WINREnvironment.Sandbox` | Testing — no real prizes, entries reset daily |

> **Get your API key** from the [WINR Dashboard](https://dashboard.avafli.com) → Settings → API Keys.

---

### `WINR.setUser`

Associates a known user with the SDK session. Call after `configure` and before `present` when user info is available.

```kotlin
WINR.setUser(
    WINRUser(
        id = "user_abc123",
        firstName = "Jane",      // optional
        lastName = "Smith",      // optional
        phone = "+15551234567"   // optional
    )
)
```

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | `String` | ✅ | Your internal user identifier |
| `firstName` | `String?` | — | User's first name |
| `lastName` | `String?` | — | User's last name |
| `phone` | `String?` | — | Phone number (E.164 format) |

> **Note:** Email is captured directly by the SDK within the sweepstakes experience. Publishers do not pass or manage user email addresses.

---

### `WINR.present`

Launches the WINR experience as a full-screen activity. All UI, branding, and copy are driven by your dashboard configuration.

```kotlin
// Fire-and-forget
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

The callback receives a `Result<DailyEntryGrant>` with the outcome of the session.

---

### `WINR.registerForPushNotifications`

Registers the device for WINR push notifications via Firebase Cloud Messaging. Call after `configure`.

```kotlin
WINR.registerForPushNotifications(context)
```

---

### `WINR.onNewToken`

Forwards a new FCM token to the WINR backend. Call from your `FirebaseMessagingService`.

```kotlin
WINR.onNewToken(token)
```

---

### `WINR.deleteAccount`

Permanently deletes all user data from WINR servers and local storage. Supports GDPR right-to-erasure and CCPA delete requests.

```kotlin
lifecycleScope.launch {
    WINR.deleteAccount()
        .onSuccess { /* All data deleted */ }
        .onFailure { error -> /* Handle error */ }
}
```

This is a `suspend` function — call from a coroutine scope.

**Data removed:**
- Entries, streaks, and engagement history
- User profile and email
- Device registration and push tokens
- Local encrypted storage

---

## Push Notifications (FCM)

WINR sends push notifications for giveaway reminders, winner announcements, and streak nudges. Setup takes three steps.

### 1. Add Firebase to your project

Follow the [Firebase Android setup guide](https://firebase.google.com/docs/android/setup) if you haven't already.

### 2. Register for notifications

```kotlin
// After WINR.configure()
WINR.registerForPushNotifications(context)
```

### 3. Forward token refreshes

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        WINR.onNewToken(token)
    }
}
```

The SDK automatically handles notification display, deep linking back into the experience, and token lifecycle management.

---

## Analytics Adapter

Route WINR engagement events to your existing analytics stack by implementing `AnalyticsAdapter`:

```kotlin
class MyAnalyticsAdapter : AnalyticsAdapter {
    override fun trackEvent(name: String, params: Map<String, Any?>) {
        // Forward to Amplitude, Mixpanel, Segment, etc.
    }

    override fun trackScreenView(screenName: String) {
        // Track screen views
    }

    override fun setUserProperty(key: String, value: String) {
        // Set user properties
    }
}
```

Pass it during configuration:

```kotlin
WINR.configure(
    WINRConfiguration(
        context = applicationContext,
        apiKey = "winr_live_xxxxxxxxxxxxxxxx",
        options = WINROptions(analyticsAdapter = MyAnalyticsAdapter())
    )
)
```

**Events emitted include:** `winr_experience_opened`, `winr_entry_earned`, `winr_streak_continued`, `winr_video_watched`, `winr_email_captured`, and more. See the [Event Catalog](https://docs.avafli.com/events) for the full list.

---

## Rewarded Video

WINR supports entry multipliers via rewarded video ads. Publishers configure ad unit IDs and providers in the [WINR Dashboard](https://dashboard.avafli.com) — the SDK handles provider initialization, ad loading, and entry crediting automatically.

### Supported Ad Providers

| Provider | Dependency |
|---|---|
| Google AdMob | `com.google.android.gms:play-services-ads` |
| AppLovin MAX | `com.applovin:applovin-sdk` |
| ironSource | `com.ironsource.sdk:mediationsdk` |
| Unity Ads | `com.unity3d.ads:unity-ads` |

Add the dependency for your ad provider:

```kotlin
// Example: AdMob
implementation("com.google.android.gms:play-services-ads:23.6.0")
```

The SDK uses reflection to load ad providers at runtime — only include the provider you use. No compile-time coupling.

---

## Branding & Theming

All branding is configured server-side through the [WINR Dashboard](https://dashboard.avafli.com):

- **Colors** — primary, secondary, background, surface, text
- **Logo** — upload your brand logo and icon
- **Copy** — headlines, CTAs, legal text
- **Layout** — card styles, corner radius, spacing

Changes publish instantly to all SDK instances without an app update.

> No client-side branding code is required. The SDK fetches and applies your configuration at runtime.

---

## ProGuard / R8

The SDK ships with embedded consumer ProGuard rules that are applied automatically. If you encounter issues with a custom R8 configuration, add the following to your `proguard-rules.pro`:

```
# WINR SDK
-keep class com.avafli.winrsdk.** { *; }
-dontwarn com.avafli.winrsdk.**

# OkHttp (transitive)
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.avafli.winrsdk.**$$serializer { *; }
```

---

## Architecture

```
com.avafli.winrsdk/
├── WINR.kt                        # Public API — singleton entry point
├── domain/
│   ├── Giveaway.kt                # Giveaway model
│   ├── StreakEngine.kt             # Streak calculation engine
│   └── StreakState.kt              # Streak state model
├── network/
│   ├── NetworkClient.kt           # OkHttp client, certificate pinning, token refresh
│   └── WinrApi.kt                 # API endpoints
├── storage/
│   ├── SecureStorage.kt           # EncryptedSharedPreferences (Android Keystore)
│   └── PreferencesStorage.kt      # Non-sensitive preferences
├── rewards/
│   ├── RewardedVideoProvider.kt   # Ad provider interface
│   └── AdProviderFactory.kt       # Runtime provider resolution
└── ui/                            # Jetpack Compose (Material 3)
    ├── WINRExperienceScreen.kt    # Main experience screen
    ├── WINRExperienceActivity.kt  # Host activity
    └── components/                # Reusable UI components
```

**Dependencies:**

| Library | Purpose |
|---|---|
| OkHttp | Networking with certificate pinning |
| kotlinx.serialization | JSON parsing |
| EncryptedSharedPreferences | Secure local storage |
| Lottie Compose | Animated illustrations |

---

## Example App

A complete integration example is available in the [`example/`](example/) directory.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WINR.configure(
            WINRConfiguration(
                context = this,
                apiKey = "winr_live_xxxxxxxxxxxxxxxx"
            )
        )

        setContent {
            MaterialTheme {
                Button(onClick = { WINR.present(this@MainActivity) }) {
                    Text("Launch WINR")
                }
            }
        }
    }
}
```

---

## Sandbox & Testing

Use the sandbox environment during development:

```kotlin
WINR.configure(
    WINRConfiguration(
        context = applicationContext,
        apiKey = "winr_test_xxxxxxxxxxxxxxxx",
        environment = WINREnvironment.Sandbox
    )
)
```

Sandbox mode:
- Uses test giveaway data — no real prizes
- Entries reset daily for rapid iteration
- Logs verbose debug output to Logcat

---

## Migration Guide

### Upgrading to 1.x

WINR Android SDK 1.0.0 is the initial release. See the [changelog](https://github.com/AVAFLI/winr_android_sdk/releases) for release notes.

---

## Support

| Channel | Link |
|---|---|
| Documentation | [docs.avafli.com](https://docs.avafli.com) |
| Dashboard | [dashboard.avafli.com](https://dashboard.avafli.com) |
| Email | [support@avafli.com](mailto:support@avafli.com) |
| Status | [status.avafli.com](https://status.avafli.com) |

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

<p align="center">Built by <a href="https://avafli.com">Avafli</a></p>

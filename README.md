# WINR Android SDK

[![](https://jitpack.io/v/avafli/winr-android-sdk.svg)](https://jitpack.io/#avafli/winr-android-sdk)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)

Sweepstakes & prizing SDK for Android app publishers. Engage users with daily streak-based entry systems, rewarded video doubling, and beautiful Jetpack Compose UI — all with a 2-line integration.

## Features

- 🎟️ **Daily Streak System** — 7-day entry ladder with configurable multipliers
- 🎬 **Rewarded Video** — Double entries by watching ads (AdMob, AppLovin, IronSource, Unity)
- 🏆 **Weekly & Monthly Bonuses** — Extra entries for consistent engagement
- 🎨 **White-Label Theming** — Full branding customization via Material 3
- 🔒 **Secure Storage** — EncryptedSharedPreferences backed by Android Keystore
- 📱 **Push Notifications** — FCM integration for campaign updates
- 🗑️ **GDPR Compliant** — Right-to-be-forgotten with one API call
- 🧩 **100% Jetpack Compose** — Modern, declarative UI with no XML

## Requirements

- Android 8.0 (API 26) or later
- Kotlin 2.1+
- Jetpack Compose

## Installation

### JitPack

Add JitPack to your project-level `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.avafli:winr-android-sdk:1.0.0")
}
```

## Quick Start

**2 lines to integrate:**

```kotlin
// 1. Initialize (in Application.onCreate or Activity.onCreate)
WINR.initialize(context, publisherKey = "YOUR_PUBLISHER_KEY")

// 2. Present the experience
WINR.present(activity)
```

That's it! The SDK handles device registration, campaign fetching, streak tracking, and UI presentation.

## Configuration

### Full Initialization

```kotlin
WINR.initialize(
    context = applicationContext,
    publisherKey = "YOUR_PUBLISHER_KEY",
    environment = WINREnvironment.Production,
    options = WINROptions(
        debugLogging = BuildConfig.DEBUG,
        branding = WINRBranding(
            primaryColor = Color(0xFF6C63FF),
            backgroundColor = Color(0xFF1A1A2E),
            cornerRadius = 16f
        ),
        enableCertificatePinning = true,
        networkTimeoutSeconds = 30
    )
)
```

### Set User Info (Optional)

```kotlin
WINR.setUser(
    WINRUser(
        id = "user-123",
        firstName = "John",
        lastName = "Doe",
        email = "john@example.com",
        phone = "+1234567890",
        smsConsent = true
    )
)
```

### Present with Callback

```kotlin
WINR.present(activity) { result ->
    result.onSuccess { grant ->
        Log.d("WINR", "Earned ${grant.entries} entries! Day ${grant.streakDay}")
        Log.d("WINR", "Total entries: ${grant.totalEntries}")
        
        grant.weeklyBonusEntries?.let { bonus ->
            Log.d("WINR", "Weekly bonus: +$bonus entries!")
        }
        
        grant.milestone?.let { milestone ->
            Log.d("WINR", "Milestone reached: ${milestone.badge}")
        }
    }
    result.onFailure { error ->
        Log.e("WINR", "Error: ${error.message}")
    }
}
```

## Customization

### Branding

Customize the look and feel to match your app:

```kotlin
WINRBranding(
    primaryColor = Color(0xFF00BCD4),      // Buttons, accents
    secondaryColor = Color(0xFF0097A7),    // Highlights
    backgroundColor = Color(0xFF121212),   // Sheet background
    surfaceColor = Color(0xFF1E1E1E),      // Cards
    onPrimaryColor = Color.White,          // Text on primary
    onBackgroundColor = Color.White,       // Text on background
    errorColor = Color(0xFFCF6679),        // Errors
    logoResId = R.drawable.your_logo,      // Logo drawable
    logoUrl = "https://example.com/logo.png", // Or logo URL
    cornerRadius = 20f                     // Corner radius (dp)
)
```

### Analytics

Implement `AnalyticsAdapter` to track SDK events:

```kotlin
class MyAnalytics : AnalyticsAdapter {
    override fun trackEvent(name: String, params: Map<String, Any?>) {
        // Send to your analytics provider
    }
    override fun trackScreenView(screenName: String) { /* ... */ }
    override fun setUserProperty(key: String, value: String) { /* ... */ }
}

// Pass in options
WINROptions(analyticsAdapter = MyAnalytics())
```

## Rewarded Video Setup

The SDK supports multiple ad providers. Configure via your campaign dashboard — the SDK auto-detects and initializes the correct provider.

### Supported Providers

| Provider | Dependency |
|----------|-----------|
| AdMob | `com.google.android.gms:play-services-ads` |
| AppLovin | `com.applovin:applovin-sdk` |
| IronSource | `com.ironsource.sdk:mediationsdk` |
| Unity Ads | `com.unity3d.ads:unity-ads` |

Add the appropriate dependency to your app's `build.gradle.kts`:

```kotlin
// Example: AdMob
implementation("com.google.android.gms:play-services-ads:23.6.0")
```

The SDK uses reflection to avoid hard dependencies — only include the provider you need.

## Push Notifications

### Setup FCM

1. Add Firebase to your project ([Firebase docs](https://firebase.google.com/docs/android/setup))
2. Add the FCM dependency:

```kotlin
implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
```

3. Register for push notifications:

```kotlin
// After WINR.initialize()
WINR.registerForPushNotifications(context)
```

4. Forward new tokens from your `FirebaseMessagingService`:

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        WINR.onNewToken(token)
    }
}
```

## GDPR / Right-to-be-Forgotten

Delete all user data with a single call:

```kotlin
// Suspend function — call from a coroutine
lifecycleScope.launch {
    val result = WINR.deleteAccount()
    result.onSuccess {
        Log.d("WINR", "All user data deleted")
    }
    result.onFailure { error ->
        Log.e("WINR", "Failed to delete: ${error.message}")
    }
}
```

This deletes:
- All entries and streak data
- User profile information
- Device registration
- Push notification tokens
- Local encrypted storage

## ProGuard Rules

The SDK includes consumer ProGuard rules that are automatically applied. If you encounter issues, add these to your `proguard-rules.pro`:

```
# WINR SDK
-keep class com.avafli.winrsdk.** { *; }
-dontwarn com.avafli.winrsdk.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.avafli.winrsdk.**$$serializer { *; }
```

## Architecture

```
com.avafli.winrsdk/
├── WINR.kt                    # Public API singleton
├── domain/                    # Business logic
│   ├── Campaign.kt           # Campaign model
│   ├── StreakEngine.kt        # Streak calculation engine
│   └── StreakState.kt         # Streak state model
├── network/                   # Networking
│   ├── NetworkClient.kt      # OkHttp client with auto token refresh
│   └── WinrApi.kt            # API endpoint implementations
├── storage/                   # Persistence
│   ├── SecureStorage.kt      # EncryptedSharedPreferences
│   └── PreferencesStorage.kt # Standard SharedPreferences
├── rewards/                   # Ad providers
│   ├── RewardedVideoProvider.kt  # Provider interface
│   └── AdProviderFactory.kt     # Provider factory
└── ui/                        # Jetpack Compose UI
    ├── WINRExperienceScreen.kt   # Main screen
    ├── WINRExperienceActivity.kt # Host activity
    └── components/               # UI components
```

## Example App

See the [`example/`](example/) directory for a complete integration example.

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WINR.initialize(this, publisherKey = "YOUR_KEY")
        
        setContent {
            Button(onClick = { WINR.present(this@MainActivity) }) {
                Text("Open WINR")
            }
        }
    }
}
```

## License

MIT License — see [LICENSE](LICENSE) for details.

---

Built with ❤️ by [Avafli](https://avafli.com)

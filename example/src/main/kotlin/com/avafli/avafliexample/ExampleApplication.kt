package com.avafli.avafliexample

import android.app.Application
import com.avafli.avaflisdk.Avafli
import com.avafli.avaflisdk.AvafliAutoOpen
import com.avafli.avaflisdk.AvafliConfiguration
import com.avafli.avaflisdk.AvafliOptions
import com.avafli.avaflisdk.AvafliUser

/**
 * The entire Avafli integration: one `Avafli.configure(...)` at app launch.
 *
 * The SDK registers the device, then auto-opens the drawer once per calendar
 * day over whichever Activity is in the foreground. To replay Day 1, clear the
 * app's data (Settings > Apps > Avafli Example > Storage > Clear data).
 */
class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Avafli.configure(
            AvafliConfiguration(
                context = this,
                // Paste your keys from the Avafli Dashboard (https://sdk.avafli.com/dashboard):
                // the avafli_test_ sandbox key for debug builds, the avafli_live_ key for release.
                apiKey = if (BuildConfig.DEBUG) SANDBOX_API_KEY else LIVE_API_KEY,
                // Only `id` is required; the SDK captures the email itself.
                // Nobody signed in? Use AvafliUser.GUEST.
                user = AvafliUser(
                    id = "android-example-user",
                    firstName = "Example",
                    lastName = "User",
                ),
                autoOpen = AvafliAutoOpen.ALWAYS, // or RETURNING_USERS_ONLY / NEVER
                options = AvafliOptions(
                    debugLogging = BuildConfig.DEBUG, // Logcat tag "Avafli-SDK"
                ),
            )
        )
    }

    private companion object {
        // Never commit real keys to source control.
        const val SANDBOX_API_KEY = "avafli_test_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        const val LIVE_API_KEY = "avafli_live_XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
    }
}

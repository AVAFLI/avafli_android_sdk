package com.avafli.winrexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRConfiguration
import com.avafli.winrsdk.WINREnvironment
import com.avafli.winrsdk.WINROptions
import com.avafli.winrsdk.WINRUser

/**
 * Example activity demonstrating WINR SDK integration.
 *
 * Integration is configure-only: the V2 experience AUTO-OPENS on the first
 * app-open of each calendar day — the `WINR.configure(...)` call below is the
 * entire integration. There is no manual launch API.
 *
 * To demo the auto-open again, clear the app's data (Settings > Apps > WINR
 * Example > Storage > Clear data) and relaunch.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure WINR SDK — this is the entire integration.
        WINR.configure(
            WINRConfiguration(
                context = this,
                // Replace with your publisher API key from the WINR Dashboard.
                // Never commit a real key to source control.
                apiKey = "winr_live_50b1b3b801a843d5e1f99593fcad4d14",   // demo key for this example app
                environment = WINREnvironment.Production,
                user = WINRUser(
                    id = "android-example-user",
                    firstName = "Example",
                    lastName = "User"
                ),
                options = WINROptions(
                    debugLogging = true,
                    enableCertificatePinning = false // Disabled until pin rotation is automated
                )
            )
        )

        setContent {
            ExampleApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ExampleApp() {
        MaterialTheme(
            colorScheme = darkColorScheme()
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("WINR SDK Example") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.winrmedia_logo_dark),
                        contentDescription = "WINR MEDIA",
                        modifier = Modifier.height(40.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "🏆 WINR SDK Demo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "The WINR experience opens itself automatically on the " +
                            "first app-open of each day — no launch code required. " +
                            "To see the auto-open again, clear the app's data and relaunch.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "© 2026 WINR MEDIA • All rights reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

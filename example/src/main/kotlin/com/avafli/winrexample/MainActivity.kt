package com.avafli.winrexample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.WINREnvironment
import com.avafli.winrsdk.WINROptions
import com.avafli.winrsdk.WINRUser

/**
 * Example activity demonstrating WINR SDK integration.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WINR SDK
        WINR.initialize(
            context = this,
            publisherKey = "winr_live_50b1b3b801a843d5e1f99593fcad4d14",
            environment = WINREnvironment.Production,
            options = WINROptions(
                debugLogging = true,
                enableCertificatePinning = false, // Disabled until pin rotation is automated
                branding = WINRBranding() // Customize colors here
            )
        )

        // Set user info
        WINR.setUser(WINRUser(id = "android-example-user"))

        setContent {
            ExampleApp()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ExampleApp() {
        var lastResult by remember { mutableStateOf<String?>(null) }

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
                    Text(
                        text = "🏆 WINR SDK Demo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Tap below to open the sweepstakes experience",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            WINR.present(this@MainActivity) { result ->
                                result.onSuccess { grant ->
                                    lastResult = "Earned ${grant.entries} entries! (Day ${grant.streakDay})"
                                }
                                result.onFailure { error ->
                                    lastResult = "Error: ${error.message}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "🎟️ Open WINR Experience",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    lastResult?.let { result ->
                        Spacer(modifier = Modifier.height(24.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = result,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

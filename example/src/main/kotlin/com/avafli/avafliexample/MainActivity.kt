package com.avafli.avafliexample

import android.os.Bundle
import android.util.Log
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
import com.avafli.avaflisdk.Avafli

/**
 * The SDK is configured in [ExampleApplication]; this Activity needs no Avafli
 * code for the default behaviour (the drawer auto-opens once a day).
 *
 * The button shows the one optional call a publisher may want:
 * `Avafli.present(activity)` opens the drawer on demand (a rewards button, the
 * end of onboarding). Pair it with `AvafliAutoOpen.NEVER` to own the timing.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        title = { Text("Avafli SDK Example") },
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
                        painter = painterResource(id = R.drawable.avafli_logo_dark),
                        contentDescription = "AVAFLI",
                        modifier = Modifier.height(40.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Avafli SDK Demo",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "The Avafli drawer opens itself on the first app-open of each " +
                            "day. To see it again today, tap the button, or clear the " +
                            "app's data and relaunch to replay Day 1.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(onClick = {
                        // Publisher-initiated open. The callback receives today's
                        // grant when entries are claimed during this open, or an
                        // AvafliError (never thrown) when the drawer cannot open.
                        Avafli.present(this@MainActivity) { result ->
                            result
                                .onSuccess { grant ->
                                    Log.i(TAG, "Day ${grant.streakDay}: +${grant.entries} entries (total ${grant.totalEntries})")
                                }
                                .onFailure { e -> Log.w(TAG, "Avafli did not open: ${e.message}") }
                        }
                    }) {
                        Text("Open Avafli")
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "© 2026 AVAFLI • All rights reserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "AvafliExample"
    }
}

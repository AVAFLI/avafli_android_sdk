package com.avafli.winrsdk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.ui.v2.WINRV2ExperienceRoot

/**
 * Full-screen transparent activity hosting the V2 experience drawer: the host
 * app stays visible (dimmed) behind the dark sheet, which sits flush to the
 * screen bottom + sides with rounded TOP corners and slides up with a spring.
 */
internal class WINRExperienceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars so the drawer is truly flush to the
        // screen's bottom + sides.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val viewModel = WINR.createExperienceViewModel()
        if (viewModel == null) {
            finish()
            return
        }

        viewModel.setSdkConfig(WINR.getSdkConfig())
        viewModel.setResultCallback(WINR.consumePendingCallback())
        viewModel.setPublisherUserId(WINR.getPublisherUserId())
        viewModel.setPrefillUser(WINR.getConfiguredUser())
        // Always fetches fresh status from the backend; the cached giveaway is
        // the offline fallback only.
        viewModel.load(WINR.getCachedGiveaway())

        setContent {
            WINRV2ExperienceRoot(
                viewModel = viewModel,
                onDismiss = {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
            )
        }
    }
}

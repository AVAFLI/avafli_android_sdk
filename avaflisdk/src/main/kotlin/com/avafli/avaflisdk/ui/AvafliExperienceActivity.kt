package com.avafli.avaflisdk.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.avafli.avaflisdk.Avafli
import com.avafli.avaflisdk.ui.v2.AvafliV2ExperienceRoot

/**
 * Full-screen transparent activity hosting the V2 experience drawer: the host
 * app stays visible (dimmed) behind the dark sheet, which sits flush to the
 * screen bottom + sides with rounded TOP corners and slides up with a spring.
 */
internal class AvafliExperienceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the system bars so the drawer is truly flush to the
        // screen's bottom + sides.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // IME never blocks fields (2.9): pin the window to adjustResize-
        // compatible behavior so Compose receives the IME inset — every
        // text-input screen applies imePadding() + scroll + bring-into-view
        // to keep fields and CTAs reachable with the keyboard open. Set in
        // code because the translucent theme's manifest default is
        // adjustUnspecified.
        @Suppress("DEPRECATION")
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val viewModel = Avafli.createExperienceViewModel()
        if (viewModel == null) {
            finish()
            return
        }

        viewModel.setSdkConfig(Avafli.getSdkConfig())
        viewModel.setResultCallback(Avafli.consumePendingCallback())
        viewModel.setPublisherUserId(Avafli.getPublisherUserId())
        viewModel.setPrefillUser(Avafli.getConfiguredUser())
        // Adoption re-entry (2.9): a parked verification-gated adoption makes
        // the load resume at the code screen (restageAdoption) instead of
        // email capture.
        viewModel.setAdoptionPending(Avafli.isAdoptionPending())
        // Always fetches fresh status from the backend; the cached giveaway is
        // the offline fallback only.
        viewModel.load(Avafli.getCachedGiveaway())

        setContent {
            AvafliV2ExperienceRoot(
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

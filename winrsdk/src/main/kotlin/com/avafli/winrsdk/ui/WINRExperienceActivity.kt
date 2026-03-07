package com.avafli.winrsdk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.avafli.winrsdk.WINR
import com.avafli.winrsdk.WINRBranding
import com.avafli.winrsdk.domain.Campaign
import com.avafli.winrsdk.domain.DailyEntryGrant

/**
 * Transparent activity that hosts the WINR experience bottom sheet.
 */
internal class WINRExperienceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = WINR.createExperienceViewModel()
        if (viewModel == null) {
            finish()
            return
        }

        val branding = WINR.getBranding()
        val campaign = WINR.getCachedCampaign()
        val callback = WINR.consumePendingCallback()

        viewModel.setResultCallback(callback)
        viewModel.loadCampaign(campaign)

        setContent {
            WINRExperienceScreen(
                viewModel = viewModel,
                branding = branding,
                onDismiss = { finish() }
            )
        }
    }
}

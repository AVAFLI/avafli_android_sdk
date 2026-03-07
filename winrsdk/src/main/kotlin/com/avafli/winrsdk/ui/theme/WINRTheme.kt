package com.avafli.winrsdk.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.avafli.winrsdk.WINRBranding

/**
 * Material 3 theme derived from WINRBranding configuration.
 */
@Composable
internal fun WINRTheme(
    branding: WINRBranding = WINRBranding(),
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = branding.primaryColor,
        secondary = branding.secondaryColor,
        background = branding.backgroundColor,
        surface = branding.surfaceColor,
        onPrimary = branding.onPrimaryColor,
        onBackground = branding.onBackgroundColor,
        onSurface = branding.onBackgroundColor,
        error = branding.errorColor,
        surfaceVariant = branding.surfaceColor.copy(alpha = 0.7f),
        onSurfaceVariant = branding.onBackgroundColor.copy(alpha = 0.7f),
        outline = branding.onBackgroundColor.copy(alpha = 0.3f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

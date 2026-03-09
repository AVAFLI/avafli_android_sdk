package com.avafli.winrsdk.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.avafli.winrsdk.WINRBranding

/**
 * CompositionLocal providing WINRBranding throughout the tree.
 */
internal val LocalWINRBranding = staticCompositionLocalOf { WINRBranding() }

/**
 * Material 3 theme derived from WINRBranding configuration.
 * Provides both Material theming and direct access to [WINRBranding] via [LocalWINRBranding].
 */
@Composable
internal fun WINRTheme(
    branding: WINRBranding = WINRBranding(),
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = branding.primaryButtonColor,
        secondary = branding.secondaryColor,
        background = branding.backgroundColor,
        surface = branding.cardBackgroundColor,
        onPrimary = branding.primaryButtonTextColor,
        onBackground = branding.primaryTextColor,
        onSurface = branding.primaryTextColor,
        error = branding.errorColor,
        surfaceVariant = branding.cardBackgroundColor.copy(alpha = 0.7f),
        onSurfaceVariant = branding.primaryTextColor.copy(alpha = 0.7f),
        outline = branding.cardBorderColor
    )

    CompositionLocalProvider(LocalWINRBranding provides branding) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}

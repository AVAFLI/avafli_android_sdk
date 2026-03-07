package com.avafli.winrsdk

import androidx.compose.ui.graphics.Color

/**
 * Branding configuration for white-labeling the WINR experience.
 */
data class WINRBranding(
    /** Primary brand color (buttons, accents). */
    val primaryColor: Color = Color(0xFF6C63FF),
    /** Secondary brand color (backgrounds, highlights). */
    val secondaryColor: Color = Color(0xFF3D3580),
    /** Background color for the experience. */
    val backgroundColor: Color = Color(0xFF1A1A2E),
    /** Surface color for cards and containers. */
    val surfaceColor: Color = Color(0xFF16213E),
    /** Text color on primary surfaces. */
    val onPrimaryColor: Color = Color.White,
    /** Text color on backgrounds. */
    val onBackgroundColor: Color = Color.White,
    /** Error/destructive action color. */
    val errorColor: Color = Color(0xFFCF6679),
    /** Logo drawable resource ID (optional). */
    val logoResId: Int? = null,
    /** Logo URL (optional, overrides logoResId if set). */
    val logoUrl: String? = null,
    /** Corner radius for cards and buttons (dp). */
    val cornerRadius: Float = 16f
)

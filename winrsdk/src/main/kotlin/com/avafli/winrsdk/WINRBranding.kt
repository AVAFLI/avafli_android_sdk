package com.avafli.winrsdk

import androidx.compose.ui.graphics.Color
import com.avafli.winrsdk.domain.SdkBranding

/**
 * Branding configuration for white-labeling the WINR experience.
 * Color system matches the iOS SDK for pixel-perfect cross-platform parity.
 */
data class WINRBranding(

    // ── Background & Surfaces ──

    /** Main background color for the experience. */
    val backgroundColor: Color = Color(0xFF1A1A2E),

    /** Card / container background. */
    val cardBackgroundColor: Color = Color(0xFF16213E),

    /** Card border / separator stroke. */
    val cardBorderColor: Color = Color(0x26FFFFFF),

    // ── Text Hierarchy ──

    /** Primary text color on dark backgrounds (headings, body). */
    val primaryTextColor: Color = Color.White,

    /** Secondary text color (sub-headings, labels). */
    val secondaryTextColor: Color = Color(0xFFD1D1D6),

    /** Muted / tertiary text color. */
    val mutedTextColor: Color = Color(0xFF8E8E93),

    // ── Buttons ──

    /** Primary button fill. */
    val primaryButtonColor: Color = Color(0xFF6C63FF),

    /** Primary button text. */
    val primaryButtonTextColor: Color = Color.White,

    // ── Accent & Glow ──

    /** Accent glow (radial highlight, streak fire ring, progress tint). */
    val accentGlowColor: Color = Color(0xFFFFA726),

    // ── Input Fields ──

    /** Input field background. */
    val inputFieldBackgroundColor: Color = Color(0xFF0D1B2A),

    /** Input field border / outline. */
    val inputFieldBorderColor: Color = Color(0x26FFFFFF),

    /** Input field placeholder text. */
    val inputFieldPlaceholderColor: Color = Color(0x59FFFFFF),

    // ── Corner Radius ──

    /** Default corner radius for cards and buttons (dp). */
    val cornerRadius: Float = 16f,

    // ── Logos ──

    /** Primary logo drawable resource ID (optional). */
    val logoResId: Int? = null,

    /** Primary logo URL (optional, overrides logoResId). */
    val logoUrl: String? = null,

    /** Secondary / hero logo drawable resource ID (optional — used on streak dashboard). */
    val secondaryLogoResId: Int? = null,

    /** Secondary / hero logo URL (optional). */
    val secondaryLogoUrl: String? = null,

    // ── Legacy Compat ──

    /** @deprecated Use [primaryButtonColor] instead. Kept for backward compatibility. */
    val primaryColor: Color = Color(0xFF6C63FF),

    /** @deprecated Use [cardBackgroundColor] instead. Kept for backward compatibility. */
    val surfaceColor: Color = Color(0xFF16213E),

    /** Secondary brand color. */
    val secondaryColor: Color = Color(0xFF3D3580),

    /** @deprecated Use [primaryButtonTextColor] instead. */
    val onPrimaryColor: Color = Color.White,

    /** @deprecated Use [primaryTextColor] instead. */
    val onBackgroundColor: Color = Color.White,

    /** Error / destructive action color. */
    val errorColor: Color = Color(0xFFCF6679)
) {

    /**
     * Merge server-driven branding overrides from [SdkBranding].
     * Server values override code-level defaults; nil/missing fields fall back to this instance.
     */
    fun applyingServerBranding(serverBranding: SdkBranding?): WINRBranding {
        if (serverBranding == null) return this

        val serverPrimary = parseColor(serverBranding.primaryColor)
        val serverSecondary = parseColor(serverBranding.secondaryColor)
        val serverBg = parseColor(serverBranding.backgroundColor)
        val serverLogoUrl = serverBranding.logoUrl

        return this.copy(
            primaryColor = serverPrimary ?: this.primaryColor,
            primaryButtonColor = serverPrimary ?: this.primaryButtonColor,
            secondaryColor = serverSecondary ?: this.secondaryColor,
            secondaryButtonColor = serverSecondary ?: this.secondaryButtonColor,
            accentGlowColor = serverSecondary ?: this.accentGlowColor,
            backgroundColor = serverBg ?: this.backgroundColor,
            logoUrl = serverLogoUrl ?: this.logoUrl
        )
    }

    companion object {
        /**
         * Parse a hex color string (e.g. "#FF6C63FF", "#6C63FF") into a Compose [Color].
         * Returns null if the string is blank or unparseable.
         */
        fun parseColor(hex: String?): Color? {
            if (hex.isNullOrBlank()) return null
            return try {
                Color(android.graphics.Color.parseColor(hex))
            } catch (_: Exception) {
                null
            }
        }
    }
}

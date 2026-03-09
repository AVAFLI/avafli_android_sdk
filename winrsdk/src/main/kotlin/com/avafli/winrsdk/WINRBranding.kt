package com.avafli.winrsdk

import androidx.compose.ui.graphics.Color

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
)

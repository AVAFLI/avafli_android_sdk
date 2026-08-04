package com.avafli.winrsdk.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

//
// Motion for the V2 experience, ported from iOS WINRV2Effects.swift (Joe's Figma
// prototype GIFs): confetti fields, the draw-on white checkmark, and the pulsing
// glow on the active streak tile. Everything is real Canvas/Compose animation
// (no GIFs) so it stays crisp at any scale and respects the publisher accent.
//

// MARK: - Confetti

internal enum class WINRConfettiStyle { Celebration, Gold }

private val celebrationPalette = listOf(
    Color(red = 0.96f, green = 0.31f, blue = 0.28f), // red
    Color(red = 0.35f, green = 0.78f, blue = 0.42f), // green
    Color(red = 0.30f, green = 0.62f, blue = 0.99f), // blue
    Color(red = 0.99f, green = 0.80f, blue = 0.28f), // yellow
    Color(red = 0.72f, green = 0.52f, blue = 0.96f), // purple
)

private val goldPalette = listOf(
    Color(red = 1.00f, green = 0.84f, blue = 0.35f),
    Color(red = 0.94f, green = 0.70f, blue = 0.18f),
    Color(red = 1.00f, green = 0.93f, blue = 0.66f),
)

private fun fract(v: Double): Double = v - kotlin.math.floor(v)

/**
 * A looping confetti field. [style] picks the palette: Celebration is the
 * multicolor sprinkle from the claim/celebration modals and streak tiles;
 * Gold is the winner-modal gold sparkle. Deterministic per-particle parameters
 * (no random) — identical math to iOS.
 */
@Composable
internal fun WINRV2ConfettiField(
    modifier: Modifier = Modifier,
    style: WINRConfettiStyle = WINRConfettiStyle.Celebration,
    count: Int = 42,
    speed: Double = 1.0,
) {
    val timeMillis by produceState(0L) {
        while (true) {
            withInfiniteAnimationFrameMillis { value = it }
        }
    }
    Canvas(modifier) {
        val t = (timeMillis / 1000.0) * speed
        val palette = if (style == WINRConfettiStyle.Celebration) celebrationPalette else goldPalette
        val density = 1.dp.toPx()
        for (i in 0 until count) {
            val seed = i.toDouble()
            val fx = fract(seed * 0.61803398875)                  // 0..1 x anchor
            val fall = 8.0 + fract(seed * 0.7548776662) * 7.0     // fall period s
            val phase = fract(seed * 0.2928932188)
            val progress = fract(t / fall + phase)                // 0..1 down screen
            val sway = sin((t * 1.7) + seed * 1.3) * 9.0 * density
            val x = (fx * size.width + sway).toFloat()
            val y = (progress * (size.height + 24 * density) - 12 * density).toFloat()
            val rotationDeg = Math.toDegrees(t * 2.1 + seed).toFloat()
            val w = ((4.0 + fract(seed * 0.833) * 4.0) * density).toFloat()
            val h = (w * (0.55 + fract(seed * 0.377) * 0.5)).toFloat()
            val alpha = if (style == WINRConfettiStyle.Gold) {
                (0.55 + fract(seed * 0.51) * 0.45).toFloat()
            } else {
                0.9f
            }
            // Flutter: squash on one axis as the piece "tumbles".
            val squash = (0.35 + abs(sin(t * 2.6 + seed * 2.0)) * 0.65).toFloat()

            withTransform({
                translate(left = x, top = y)
                rotate(degrees = rotationDeg, pivot = Offset.Zero)
                scale(scaleX = 1f, scaleY = squash, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = palette[i % palette.size],
                    topLeft = Offset(-w / 2f, -h / 2f),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(density, density),
                    alpha = alpha,
                )
            }
        }
    }
}

// MARK: - Animated checkmark (draw-on)

/**
 * The white circle-check from Joe's modals: the circle sweeps in, then the
 * check strokes on. Replaces the static GIF frame.
 */
@Composable
internal fun WINRV2AnimatedCheckmark(
    modifier: Modifier = Modifier,
    lineWidth: Dp = 7.dp,
) {
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { circleProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch {
            delay(400)
            checkProgress.animateTo(1f, tween(350, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier) {
        val strokePx = lineWidth.toPx()
        val stroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // Circle sweep, starting at 12 o'clock (iOS trims from -90°).
        val inset = strokePx / 2f
        drawArc(
            color = Color.White,
            startAngle = -90f,
            sweepAngle = 360f * circleProgress.value,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            style = stroke,
        )
        // Check glyph matching the design: starts left-center, dips to the low
        // point, kicks up past the circle's top-right edge.
        if (checkProgress.value > 0f) {
            val path = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.54f)
                lineTo(size.width * 0.45f, size.height * 0.72f)
                lineTo(size.width * 0.94f, size.height * 0.22f)
            }
            val measure = PathMeasure()
            measure.setPath(path, false)
            val segment = Path()
            measure.getSegment(0f, measure.length * checkProgress.value, segment, true)
            drawPath(segment, Color.White, style = stroke)
        }
    }
}

// MARK: - Pulsing glow (active streak tile)

/**
 * Joe's active-tile treatment: the accent glow breathes. Implemented as a soft
 * radial gradient drawn behind the tile (works on every supported API level).
 */
internal fun Modifier.winrPulseGlow(accent: Color): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "winrPulseGlow")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "winrPulseGlowValue",
    )
    drawBehind {
        // radius 7→14, opacity 0.55→0.95 (mirrors the iOS shadow modifier).
        val blur = (7f + 7f * pulse).dp.toPx()
        val alpha = 0.55f + 0.40f * pulse
        val glowRadius = max(size.width, size.height) / 2f + blur * 2f
        val brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = alpha), accent.copy(alpha = 0f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = glowRadius,
        )
        drawRect(
            brush = brush,
            topLeft = Offset(-blur * 2f, -blur * 2f),
            size = Size(size.width + blur * 4f, size.height + blur * 4f),
        )
    }
}

/**
 * The ready (pre-reveal) tile treatment: the same accent glow as
 * [winrPulseGlow] but fully STATIC — no breathing. Every moving element
 * (pulse, confetti, check draw) waits for the single reveal beat. Mirrors
 * iOS `.shadow(color: accent.opacity(0.75), radius: 10)`.
 */
internal fun Modifier.winrStaticGlow(accent: Color): Modifier = drawBehind {
    val blur = 10.dp.toPx()
    val glowRadius = max(size.width, size.height) / 2f + blur * 2f
    val brush = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.75f), accent.copy(alpha = 0f)),
        center = Offset(size.width / 2f, size.height / 2f),
        radius = glowRadius,
    )
    drawRect(
        brush = brush,
        topLeft = Offset(-blur * 2f, -blur * 2f),
        size = Size(size.width + blur * 4f, size.height + blur * 4f),
    )
}

/**
 * Recedes the dashboard rendered behind the celebration modal.
 *
 * API 31+: a real 3dp render-effect blur ([Modifier.blur]).
 *
 * API 26–30: [Modifier.blur] is a silent no-op (RenderEffect needs S), which
 * used to leave the dashboard fully crisp behind the modal. Instead of an
 * absent blur we make the recession deliberate: the content is drawn through
 * a saveLayer whose color filter drops saturation to 55%, and an extra dim is
 * painted on top (the modal's own 55% scrim then stacks above that). The
 * result reads as an intentionally muted, de-emphasised backdrop. This is a
 * single hardware-accelerated offscreen pass — no software blur, no extra
 * dependencies.
 */
internal fun Modifier.winrCelebrationBackdrop(): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(3.dp)
    } else {
        drawWithCache {
            val desaturate = Paint().apply {
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(0.55f) },
                )
            }
            val bounds = Rect(Offset.Zero, size)
            onDrawWithContent {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(bounds, desaturate)
                    drawContent()
                    canvas.restore()
                }
                // Compensating dim for the missing blur.
                drawRect(Color.Black.copy(alpha = 0.28f))
            }
        }
    }

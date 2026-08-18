package com.avafli.winrsdk.ui.v2

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.avafli.winrsdk.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//
// Joe's ACTUAL Figma confetti-burst animation (bundled GIF) for the Day 2+
// reveal beat, ported from iOS WINRV2GifView/WINRV2GifAsset. Playback is Coil-free:
// android.graphics.drawable.AnimatedImageDrawable decoded via ImageDecoder
// from res/raw (API 28+), hosted in a plain ImageView. The drawable honors
// the file's own per-frame delay table and, with repeatCount = 0, plays
// exactly once. Below API 28 there is no animation: onFinished fires
// immediately so callers fall straight to their resting state.
//

/**
 * Decoded-GIF cache. [prewarm] decodes the reveal GIF off-main at drawer open
 * so the reveal-beat mount plays the cached drawable instantly.
 */
internal object WINRV2GifAssets {
    /** Longest decoded edge; the burst renders at 200dp so 600px covers 3x density. */
    private const val MAX_PIXEL_SIZE = 600

    private val cache = ConcurrentHashMap<Int, AnimatedImageDrawable>()

    @RequiresApi(Build.VERSION_CODES.P)
    fun cached(@RawRes resId: Int): AnimatedImageDrawable? = cache[resId]

    /** Decode the reveal GIF into the cache off-main so a later mount is instant. */
    suspend fun prewarm(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            load(appContext, R.raw.winr_confetti_burst)
        }
    }

    /** Cached load; decodes synchronously on a miss (call off-main). */
    @RequiresApi(Build.VERSION_CODES.P)
    @WorkerThread
    fun load(context: Context, @RawRes resId: Int): AnimatedImageDrawable? {
        cache[resId]?.let { return it }
        val decoded = try {
            val source = ImageDecoder.createSource(context.resources, resId)
            ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_PIXEL_SIZE) {
                    decoder.setTargetSize(
                        info.size.width * MAX_PIXEL_SIZE / longest,
                        info.size.height * MAX_PIXEL_SIZE / longest,
                    )
                }
            }
        } catch (_: Exception) {
            null
        } as? AnimatedImageDrawable ?: return null
        decoded.repeatCount = 0 // play exactly once
        // AOSP race guard (observed as a FATAL NPE on API 35 at the reveal
        // beat): AnimatedImageDrawable.postOnAnimationEnd() null-checks
        // mAnimationCallbacks at POST time, but the posted handler lambda
        // iterates the field again at RUN time with no check. Unregistering
        // the last real callback (our DisposableEffect teardown racing a
        // just-posted end dispatch — e.g. the overlay is removed on the same
        // frame the GIF ends) nulls the list via clearAnimationCallbacks()
        // and the pending lambda crashes the app. A permanent no-op callback
        // keeps the list non-empty for the cached drawable's lifetime, so the
        // posted dispatch always has a list to iterate. Registration must run
        // on a looper thread (the native listener asserts one) — post to main;
        // it lands long before any burst's animation can end.
        Handler(Looper.getMainLooper()).post {
            decoded.registerAnimationCallback(object : Animatable2.AnimationCallback() {})
        }
        cache.putIfAbsent(resId, decoded)
        return cache[resId] ?: decoded
    }
}

/**
 * Plays a bundled GIF ONCE, starting exactly when the composable mounts, from
 * frame 0, honoring the file's own per-frame delays. When the last frame ends
 * [onFinished] fires on the main thread and the drawable stays stopped —
 * callers remove the overlay there. Non-interactive: the hosting ImageView
 * never consumes touch.
 *
 * Below API 28 (no AnimatedImageDrawable) the burst is skipped entirely:
 * [onFinished] fires immediately so the caller shows its resting state.
 */
@Composable
internal fun WINRV2GifBurst(
    @RawRes resId: Int,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        GifBurstApi28(resId, modifier, onFinished)
    } else {
        LaunchedEffect(resId) { onFinished() }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun GifBurstApi28(
    @RawRes resId: Int,
    modifier: Modifier,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)
    var drawable by remember(resId) { mutableStateOf(WINRV2GifAssets.cached(resId)) }

    // Mount IS the beat. Prewarming at drawer open makes the cache hit the
    // normal path; on a miss decode off-main and start playback on arrival.
    LaunchedEffect(resId) {
        if (drawable == null) {
            val loaded = withContext(Dispatchers.IO) { WINRV2GifAssets.load(context, resId) }
            if (loaded != null) drawable = loaded else currentOnFinished()
        }
    }

    val gif = drawable ?: return
    DisposableEffect(gif) {
        val callback = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(ended: Drawable?) {
                currentOnFinished()
            }
        }
        gif.registerAnimationCallback(callback)
        gif.repeatCount = 0
        gif.stop() // rewind in case a previous mount left it on the last frame
        gif.start() // frame 0, now
        onDispose {
            gif.unregisterAnimationCallback(callback)
            gif.stop()
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        },
        update = { view -> if (view.drawable !== gif) view.setImageDrawable(gif) },
    )
}

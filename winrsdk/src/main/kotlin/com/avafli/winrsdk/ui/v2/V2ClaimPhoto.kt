package com.avafli.winrsdk.ui.v2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photo handling for the winner claim form (mirrors iOS WINRClaimPhoto):
 * downscale so the long edge is ≤1200px, JPEG-encode stepping the quality down
 * until the payload fits the backend's 5MB decoded cap, then base64.
 */
internal object WINRClaimPhoto {
    const val MAX_BYTES = 5 * 1024 * 1024
    const val LONG_EDGE = 1200

    /**
     * Decode [uri] into a bitmap, memory-safely: bounds first, then a sampled
     * decode so huge camera images never fully inflate. Null on failure.
     */
    fun loadBitmap(context: Context, uri: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= LONG_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Downscales so the long edge is ≤[LONG_EDGE]px, then JPEG-encodes, stepping
     * the quality down until the payload fits the 5MB cap. Null if it can't fit.
     */
    fun base64Jpeg(bitmap: Bitmap): String? {
        val scaled = downscaled(bitmap, LONG_EDGE)
        var quality = 85
        var data = jpeg(scaled, quality)
        while (data.size > MAX_BYTES && quality > 25) {
            quality -= 15
            data = jpeg(scaled, quality)
        }
        if (data.size > MAX_BYTES) return null
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }

    fun downscaled(bitmap: Bitmap, longEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= longEdge || longest <= 0) return bitmap
        val scale = longEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}

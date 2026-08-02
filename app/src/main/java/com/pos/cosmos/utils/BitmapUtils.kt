package com.pos.cosmos.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import java.io.InputStream

/**
 * Memory-safe bitmap decoding.
 *
 * Phone cameras produce 12–108 MP images. Decoding one at full resolution costs
 * `width * height * 4` bytes — a 48 MP shot is ~192 MB, which exceeds the whole
 * heap on a low-RAM device and throws OutOfMemoryError before anything is drawn.
 * Every decode here reads the bounds first and picks an `inSampleSize` so the
 * result fits inside [DEFAULT_MAX_DIMENSION], which is ample for on-screen
 * preview, upload, and ML Kit text recognition.
 *
 * Never call BitmapFactory.decode* directly on camera or gallery input.
 */
object BitmapUtils {

    private const val TAG = "BitmapUtils"

    /** Long-edge cap. 1600px keeps OCR accurate while costing ~10 MB instead of ~190 MB. */
    const val DEFAULT_MAX_DIMENSION = 1600

    fun decodeSampled(bytes: ByteArray, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    } catch (e: OutOfMemoryError) {
        Log.e(TAG, "OOM decoding ${bytes.size} byte image", e)
        null
    }

    /** Consumes and closes [stream]. */
    fun decodeSampled(stream: InputStream?, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        val bytes = try {
            stream?.use { it.readBytes() } ?: return null
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM reading image stream", e)
            return null
        }
        return decodeSampled(bytes, maxDimension)
    }

    /**
     * Rotates [source] and recycles it, so only one full-size bitmap is ever live.
     * Returns [source] untouched when there is nothing to rotate.
     */
    fun rotateAndRecycle(source: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return source
        return try {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM rotating ${source.width}x${source.height}", e)
            source
        }
    }

    /**
     * Crops a vertically-centred strip of [source] whose height is
     * `width * heightOverWidthRatio`, recycling [source] afterwards.
     */
    fun cropCentreStripAndRecycle(source: Bitmap, heightOverWidthRatio: Float): Bitmap {
        return try {
            val cropHeight = (source.width * heightOverWidthRatio).toInt().coerceIn(1, source.height)
            val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(source, 0, top, source.width, cropHeight)
            if (cropped !== source) source.recycle()
            cropped
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM cropping ${source.width}x${source.height}", e)
            source
        }
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / sample > maxDimension) sample *= 2
        return sample
    }
}

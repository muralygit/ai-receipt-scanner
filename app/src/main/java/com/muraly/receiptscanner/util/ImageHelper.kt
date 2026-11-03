package com.muraly.receiptscanner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * Shrinks receipt photos for long-term storage: longest side capped at [MAX_DIMENSION] px,
 * re-encoded as JPEG at [JPEG_QUALITY] (typically 100-300 KB instead of several MB), with the
 * camera's EXIF rotation baked into the pixels (re-encoding drops the EXIF tag, so without this
 * some photos would turn sideways).
 *
 * Call from a background thread.
 */
object ImageHelper {
    const val MAX_DIMENSION = 1600
    const val JPEG_QUALITY = 80
    const val IMAGE_DIR_NAME = "receipt_images"

    /** Permanent, app-private folder that holds saved receipt photos (never cleared by Android). */
    fun imageDir(context: Context): File =
        File(context.filesDir, IMAGE_DIR_NAME).apply { mkdirs() }

    /** True if [path] points inside the permanent photo folder. */
    fun isPermanent(context: Context, path: String): Boolean =
        path.startsWith(imageDir(context).absolutePath + File.separator)

    /**
     * Reads [source], shrinks it, and writes the result to [dest]. Safe when source == dest
     * (writes a temp file first, then swaps it in). Returns false and leaves [dest] untouched
     * if the source is missing or unreadable.
     */
    fun compressToFile(source: File, dest: File): Boolean {
        if (!source.exists()) return false
        var bitmap: Bitmap? = null
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return false

            // Cheap power-of-two downsample while decoding, keeping longest side >= MAX_DIMENSION.
            val longest = maxOf(w, h)
            var sample = 1
            while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2

            bitmap = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return false

            // Exact resize to MAX_DIMENSION on the longest side.
            val decodedLongest = maxOf(bitmap.width, bitmap.height)
            if (decodedLongest > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / decodedLongest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) { bitmap.recycle(); bitmap = scaled }
            }

            // Bake in EXIF rotation.
            val degrees = try {
                when (ExifInterface(source.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }
            if (degrees != 0f) {
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(degrees) }, true
                )
                if (rotated !== bitmap) { bitmap.recycle(); bitmap = rotated }
            }

            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".tmp")
            val finalBitmap: Bitmap = bitmap
            val ok = tmp.outputStream().use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (!ok) { tmp.delete(); return false }
            if (dest.exists() && !dest.delete()) { tmp.delete(); return false }
            if (!tmp.renameTo(dest)) { tmp.delete(); return false }
            return true
        } catch (e: Exception) {
            return false
        } catch (e: OutOfMemoryError) {
            return false
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * One-time clean-up of photos saved by older versions: compresses photos already in the
     * permanent folder in place, and moves still-existing cache photos into it.
     * Returns a map of receiptId -> new path for receipts whose path changed.
     */
    fun migrateExisting(context: Context, photos: Map<Long, String>): Map<Long, String> {
        val changed = mutableMapOf<Long, String>()
        val dir = imageDir(context)
        for ((id, path) in photos) {
            if (path.isBlank()) continue
            val src = File(path)
            if (!src.exists()) continue
            if (isPermanent(context, path)) {
                compressToFile(src, src) // in place; path unchanged
            } else {
                val dest = File(dir, "receipt_${id}_${System.currentTimeMillis()}.jpg")
                if (compressToFile(src, dest)) changed[id] = dest.absolutePath
            }
        }
        return changed
    }
}

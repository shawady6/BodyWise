package com.shawady.bodywise.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/** Smallest power-of-two sample size that brings the longest side to at most [maxSide]. */
fun sampleSizeAtMost(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (maxOf(width, height) / sample > maxSide) sample *= 2
    return sample
}

/** Largest power-of-two sample size that keeps the longest side at or above [maxSide]. */
fun sampleSizeAtLeast(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** Decodes a small bitmap for display so large photos do not exhaust memory. */
fun decodeThumbnail(path: String, maxSide: Int = 800): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeAtLeast(bounds.outWidth, bounds.outHeight, maxSide)
    }
    return BitmapFactory.decodeFile(path, options)
}

/**
 * Saves a progress photo: applies the EXIF rotation, limits the size (about 2000px) and stores a JPEG.
 * [open] is called several times and must return a fresh stream each time.
 */
fun importPhoto(open: () -> InputStream?, dest: File, maxSide: Int = 2048): Boolean {
    val orientation = open()?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeAtMost(bounds.outWidth, bounds.outHeight, maxSide)
    }
    val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) } ?: return false
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
    }
    val result = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    dest.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    return true
}

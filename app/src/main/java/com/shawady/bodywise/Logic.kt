package com.shawady.bodywise

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Saturday that starts the Saturday–Friday week containing [date]. */
internal fun weekStartFor(date: LocalDate): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))

internal fun versionIsNewer(latest: String, current: String): Boolean {
    fun parts(value: String) = value.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** Largest power-of-two sample size that keeps the longest side at or above [maxSide]. */
internal fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** Decodes a downscaled bitmap so large camera photos do not exhaust memory. */
internal fun decodeSampledBitmap(path: String, maxSide: Int = 800): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
    }
    return BitmapFactory.decodeFile(path, options)
}

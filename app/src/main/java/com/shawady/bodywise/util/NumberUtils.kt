package com.shawady.bodywise.util

import java.math.BigDecimal

/** Parses user input including Arabic-Indic digits and comma / Arabic decimal separators. */
fun parseDecimal(text: String): Double? {
    if (text.isBlank()) return null
    val sb = StringBuilder()
    for (ch in text.trim()) {
        when (ch) {
            in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
            in '۰'..'۹' -> sb.append('0' + (ch - '۰'))
            ',', '٫', '،' -> sb.append('.')
            else -> sb.append(ch)
        }
    }
    val value = sb.toString().toDoubleOrNull() ?: return null
    return if (value.isNaN() || value.isInfinite()) null else value
}

/** 70.0 -> "70", 70.5 -> "70.5". */
fun formatNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

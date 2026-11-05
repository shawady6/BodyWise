package com.shawady.bodywise.data

import com.shawady.bodywise.util.formatNumber

internal fun csvEscape(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value

/** One row per measurement, oldest first. Write it with a UTF-8 BOM so Excel shows Arabic correctly. */
fun measurementsToCsv(list: List<Measurement>): String {
    val header = listOf("التاريخ") + Metric.entries.map { "${it.label} (${it.unit})" }
    val rows = list.sortedBy { it.date }.map { m ->
        listOf(m.date.toString()) + Metric.entries.map { metric -> m.values[metric]?.let { formatNumber(it) } ?: "" }
    }
    return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csvEscape(it) } } + "\r\n"
}

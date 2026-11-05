package com.shawady.bodywise.update

fun versionIsNewer(latest: String, current: String): Boolean {
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

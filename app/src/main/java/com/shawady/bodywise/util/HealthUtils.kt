package com.shawady.bodywise.util

/** Body-mass index estimate (kg / m²). Returns null for implausible input. */
fun bmi(weightKg: Double, heightCm: Double): Double? {
    if (weightKg <= 0.0 || heightCm < 50.0 || heightCm > 260.0) return null
    val meters = heightCm / 100.0
    return weightKg / (meters * meters)
}

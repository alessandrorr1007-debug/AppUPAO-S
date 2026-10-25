package com.example.upaos.data.canvas

import java.math.BigDecimal

const val UPAO_PASSING_RATIO = 10.5 / 20.0
fun canvasPoints(points: Double?): String = points?.takeIf { it.isFinite() }
    ?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() } ?: "No disponible"
fun canvasAttempts(attempts: Int?): String = when (attempts) {
    null -> "No especificado"
    -1 -> "Ilimitados"
    else -> attempts.toString()
}
fun canvasPassing(score: Double?, possible: Double?): Boolean? =
    if (score != null && possible != null && possible > 0) score / possible >= UPAO_PASSING_RATIO else null

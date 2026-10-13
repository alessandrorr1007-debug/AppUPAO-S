package com.example.upaos.ui.components

import kotlin.math.roundToInt

/** Presentation only: retain the original percentage for risk thresholds and calculations. */
fun attendancePercentText(percent: Double): String = percent.roundToInt().toString()

package com.example.upaos.data.canvas

import java.time.LocalDate
const val CANVAS_ANNOUNCEMENT_DAYS = 180L
fun canvasContextBatches(ids: List<Long>): List<List<String>> = ids.distinct().map { "course_$it" }.chunked(10)
fun canvasAnnouncementWindow(today: LocalDate = LocalDate.now()): Pair<String, String> =
    today.minusDays(CANVAS_ANNOUNCEMENT_DAYS).toString() to today.plusDays(30).toString()
fun canvasCalendarWindow(today: LocalDate = LocalDate.now()): Pair<String, String> =
    today.minusDays(30).toString() to today.plusDays(120).toString()

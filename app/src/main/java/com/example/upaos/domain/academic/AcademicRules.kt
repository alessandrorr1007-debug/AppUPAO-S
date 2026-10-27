package com.example.upaos.domain.academic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure rules shared by UI, reminders and synchronization. No semester is "current" by fiat. */
object AcademicRules {
    val LIMA: ZoneId = ZoneId.of("America/Lima")
    const val TOTAL_WEEKS = 17
    const val PASS_GRADE = 10.5
    private val regular = Regex("[0-9]{4}(10|20)")

    fun isRegular(term: String?) = term != null && regular.matches(term.trim())

    fun latestAvailable(terms: List<String>, fallback: String? = null): String =
        terms.map { it.trim() }.filter(::isRegular).maxOrNull()
            ?: fallback?.trim()?.takeIf(::isRegular).orEmpty()

    data class StartDate(val date: LocalDate, val verified: Boolean, val source: String? = null)

    // Legacy local dates have no documentary source in this repository: explicitly estimates.
    private val historicalEstimates = mapOf(
        "202510" to LocalDate.of(2025, 3, 24),
        "202520" to LocalDate.of(2025, 9, 1)
    )

    fun startDate(term: String, verifiedDates: Map<String, StartDate> = emptyMap()): StartDate? {
        val code = term.trim()
        if (!isRegular(code)) return null
        verifiedDates[code]?.takeIf { it.verified && !it.source.isNullOrBlank() }?.let { return it }
        val year = code.take(4).toInt()
        if (year !in 1..9999) return null
        val date = historicalEstimates[code] ?: when (code.takeLast(2)) {
            "10" -> LocalDate.of(year, 3, 23)
            else -> LocalDate.of(year, 8, 31)
        }
        return StartDate(date, verified = false)
    }

    /** Estimated scheduled meetings, never attendance actually recorded by Banner. */
    fun scheduledMeetings(term: String, weekdays: Set<Int>, today: LocalDate): Int? {
        val start = startDate(term)?.date ?: return null
        if (weekdays.isEmpty() || weekdays.any { it !in 0..6 }) return null
        if (today.isBefore(start)) return 0
        val end = minOf(today, start.plusWeeks(TOTAL_WEEKS.toLong()).minusDays(1))
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val weeks = days / 7
        val remainder = (days % 7).toInt()
        return (weeks * weekdays.size).toInt() + (0 until remainder).count {
            (start.plusDays(it.toLong()).dayOfWeek.value - DayOfWeek.MONDAY.value) in weekdays
        }
    }

    fun credits(raw: Map<String, Any?>?): Int? {
        val value = raw?.get("hoursAttempted") ?: return null
        val number = (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: return null
        return number.takeIf { it.isFinite() && it > 0 && it == it.toInt().toDouble() }?.toInt()
    }
}

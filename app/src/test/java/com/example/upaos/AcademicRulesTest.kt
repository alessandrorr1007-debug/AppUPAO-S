package com.example.upaos

import com.example.upaos.domain.academic.AcademicRules as Rules
import com.example.upaos.data.upao.AcademicCalendar
import com.example.upaos.service.SyncIntervalPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

class AcademicRulesTest {
    @Test fun atLeastThreePeriodsHaveIdenticalBoundaryAndAttendanceRules() {
        for (term in listOf("202510", "202620", "202710", "202820")) {
            val start = Rules.startDate(term)!!
            fun info(day: LocalDate) = AcademicCalendar.calcularSemana(term, Date.from(day.atStartOfDay(Rules.LIMA).toInstant()))
            assertNull(info(start.date.minusDays(1)).semana)
            assertEquals(1, info(start.date).semana)
            assertEquals(17, info(start.date.plusWeeks(16)).semana)
            assertTrue(info(start.date.plusWeeks(17)).fueraDeCiclo == true)
            assertEquals(0, Rules.scheduledMeetings(term, setOf(0,2), start.date.minusDays(1)))
            assertEquals(34, Rules.scheduledMeetings(term, setOf(0,2), start.date.plusYears(1)))
            assertTrue(info(start.date).fechaEstimada)
        }
    }
    @Test fun unknownAndLanguagePeriodsNeverGetInventedRegularDates() {
        for (term in listOf("", "invalid", "202690", "202700", "10", "000010")) {
            assertNull(Rules.startDate(term))
            assertFalse(AcademicCalendar.calcularSemana(term).configurada)
        }
        assertEquals("", Rules.latestAvailable(listOf("202790"), "202690"))
        assertEquals("202720", Rules.latestAvailable(listOf("202710","202720","202790")))
    }
    @Test fun verifiedDatesRequireProvenanceAndDoNotBecomeGlobalMutableState() {
        val verified = Rules.StartDate(LocalDate.of(2027,3,29),true,"fixture://verified-calendar")
        assertEquals(verified,Rules.startDate("202710", mapOf("202710" to verified)))
        assertFalse(Rules.startDate("202710")!!.verified)
        assertFalse(Rules.startDate("202710",mapOf("202710" to verified.copy(source=null)))!!.verified)
    }
    @Test fun calendarUsesLimaRegardlessOfPhoneTimezone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val instant = ZonedDateTime.parse("2026-03-23T02:00:00Z").toInstant()
            assertNull(AcademicCalendar.calcularSemana("202610",Date.from(instant)).semana)
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun creditsOnlyComeFromHoursAttemptedAndMissingPeriodIsConservative() {
        assertEquals(Rules.startDate("202710"), Rules.startDate(" 202710 "))
        assertEquals(4, Rules.credits(mapOf("hoursAttempted" to "4.0", "creditHours" to 9)))
        assertNull(Rules.credits(mapOf("creditHours" to 9)))
        assertNull(Rules.credits(mapOf("hoursAttempted" to "NaN")))
        val day = ZonedDateTime.of(2027,4,5,10,0,0,0,Rules.LIMA)
        assertEquals(60,SyncIntervalPolicy.proximoIntervalo(day).toMinutes())
        assertEquals(10,SyncIntervalPolicy.proximoIntervalo(day,SyncIntervalPolicy.SyncContext(periodoAcademico="202710")).toMinutes())
    }
    @Test fun failureCannotMakeNightOrUnknownPeriodPollMoreFrequently() {
        val night = ZonedDateTime.of(2027,4,5,23,0,0,0,Rules.LIMA)
        val day = night.withHour(10)
        for (failures in 1..3) {
            assertEquals(60,SyncIntervalPolicy.proximoIntervalo(night,
                SyncIntervalPolicy.SyncContext(fallosConsecutivos=failures,periodoAcademico="202710")).toMinutes())
            assertEquals(60,SyncIntervalPolicy.proximoIntervalo(day,
                SyncIntervalPolicy.SyncContext(fallosConsecutivos=failures)).toMinutes())
        }
    }
}

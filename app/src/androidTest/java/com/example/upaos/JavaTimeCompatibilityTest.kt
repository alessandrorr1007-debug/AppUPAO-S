package com.example.upaos

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Run on API 24 and 25 to validate the packaged java.time backport, not the desktop JDK. */
@RunWith(AndroidJUnit4::class)
class JavaTimeCompatibilityTest {
    @Test fun limaDatesAndDurationsWorkOnTheDeviceRuntime() {
        val zone = ZoneId.of("America/Lima")
        val time = ZonedDateTime.of(2026, 10, 19, 7, 0, 0, 0, zone)
        assertEquals(LocalDate.of(2026, 10, 19), time.toLocalDate())
        assertEquals(60L, java.time.Duration.between(time, time.plusHours(1)).toMinutes())
        assertEquals(7, time.plusWeeks(1).dayOfYear - time.dayOfYear)
    }
}

package com.example.upaos

import com.example.upaos.service.SyncPolicy
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test fun weeksAndBoundaries() {
        for (week in listOf(7, 10, 15, 18)) {
            for ((time, inWindow) in listOf("06:59" to false, "07:00" to true, "22:59" to true, "23:00" to false)) {
                val expected = if (week in setOf(7, 15) && inWindow) SyncPolicy.Mode.INTENSIVO else SyncPolicy.Mode.NORMAL
                assertEquals("week=$week time=$time", expected, SyncPolicy.modo(week, LocalTime.parse(time)))
            }
        }
    }
    @Test fun unknownWeekAndConfiguration() {
        assertEquals(SyncPolicy.Mode.NORMAL, SyncPolicy.modo(null, LocalTime.NOON))
        assertEquals(SyncPolicy.Mode.INTENSIVO, SyncPolicy.modo(10, LocalTime.NOON, setOf(10)))
    }
}

package com.example.upaos

import com.example.upaos.ui.components.attendancePercentText
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendanceFormatTest {
    @Test fun roundsAttendanceForPresentationWithoutChangingTheValue() {
        val original = 92.85
        assertEquals("93", attendancePercentText(original))
        assertEquals(92.85, original, 0.0)
        assertEquals("93", attendancePercentText(92.5))
        assertEquals("92", attendancePercentText(92.499))
        assertEquals("0", attendancePercentText(0.0))
        assertEquals("100", attendancePercentText(100.0))
        println("92.85 -> ${attendancePercentText(original)}% (original: $original)")
    }
}

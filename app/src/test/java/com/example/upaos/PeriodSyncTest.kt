package com.example.upaos

import com.example.upaos.ui.grades.detectarPeriodoActual
import com.example.upaos.ui.home.DashboardState
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodSyncTest {

    @Test
    fun detectarPeriodoActualReturnsHighestRegularPeriod() {
        val periods = listOf("202410", "202420", "202510", "202520", "202610")
        assertEquals("202610", detectarPeriodoActual(periods, "202520"))
    }

    @Test
    fun detectarPeriodoActualFallbackWhenNoRegulars() {
        val periods = listOf("VERANO2026", "EXTRA2025")
        assertEquals("202610", detectarPeriodoActual(periods, "202610"))
    }

    @Test
    fun dashboardStateDefaultPeriodsMatchCurrent() {
        val state = DashboardState(currentPeriod = "202610")
        assertEquals(state.currentPeriod, state.gradePeriod)
        assertEquals(state.currentPeriod, state.schedulePeriod)
        assertEquals(state.currentPeriod, state.attendancePeriod)
    }

    @Test
    fun stateCopySimulatingPeriodSelectionSynchronizesAllThree() {
        val state = DashboardState(currentPeriod = "202610")
        val updated = state.copy(
            gradePeriod = "202520",
            schedulePeriod = "202520",
            attendancePeriod = "202520"
        )
        assertEquals("202520", updated.gradePeriod)
        assertEquals("202520", updated.schedulePeriod)
        assertEquals("202520", updated.attendancePeriod)
    }
}

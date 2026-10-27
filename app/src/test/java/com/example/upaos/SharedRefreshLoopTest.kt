package com.example.upaos

import com.example.upaos.data.refresh.SharedRefreshLoop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SharedRefreshLoopTest {
    @Test fun oneRoundForAllTabsAndOneTimerAfterRepeatedStarts() = runTest {
        var calls = 0
        val loop = SharedRefreshLoop(backgroundScope, { calls++ }, clock = { testScheduler.currentTime })
        repeat(5) { loop.setForeground(true) }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(SharedRefreshLoop.FIVE_MINUTES)
        runCurrent()
        assertEquals(2, calls)
        loop.setForeground(false)
    }

    @Test fun pausesInBackgroundAndRotationDoesNotReload() = runTest {
        var calls = 0
        val loop = SharedRefreshLoop(backgroundScope, { calls++ }, clock = { testScheduler.currentTime })
        loop.setForeground(true); runCurrent()
        loop.setForeground(false)
        loop.setForeground(true); runCurrent()
        assertEquals(1, calls)
        loop.setForeground(false)
        advanceTimeBy(SharedRefreshLoop.FIVE_MINUTES * 3); runCurrent()
        assertEquals(1, calls)
        loop.setForeground(true); runCurrent(); advanceTimeBy(1); runCurrent()
        assertEquals(2, calls)
        loop.setForeground(false)
    }

    @Test fun manualRefreshResetsFiveMinuteDeadline() = runTest {
        var calls = 0
        val loop = SharedRefreshLoop(backgroundScope, { calls++ }, clock = { testScheduler.currentTime })
        loop.setForeground(true); runCurrent()
        advanceTimeBy(200_000)
        loop.requestRefresh(); runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(100_000); runCurrent()
        assertEquals(2, calls) // The old automatic deadline must be gone.
        advanceTimeBy(200_000); runCurrent()
        assertEquals(3, calls)
        loop.setForeground(false)
    }

    @Test fun simultaneousRefreshesShareInflightRoundAcrossRotation() = runTest {
        var calls = 0
        val complete = CompletableDeferred<Unit>()
        val loop = SharedRefreshLoop(backgroundScope, { calls++; complete.await() }, clock = { testScheduler.currentTime })
        loop.setForeground(true); runCurrent()
        val first = loop.requestRefresh()
        repeat(4) { assertSame(first, loop.requestRefresh()) }
        loop.setForeground(false); loop.setForeground(true); runCurrent()
        assertEquals(1, calls)
        complete.complete(Unit); runCurrent()
        advanceTimeBy(SharedRefreshLoop.FIVE_MINUTES - 1); runCurrent()
        assertEquals(1, calls)
        loop.setForeground(false)
    }
    @Test fun adaptiveDeadlineUsesBackoffAndManualRefreshStillCoalesces() = runTest {
        var calls = 0
        var interval = 60 * 60_000L
        val loop = SharedRefreshLoop(backgroundScope, { calls++ },
            clock = { testScheduler.currentTime }, nextIntervalMillis = { interval })
        loop.setForeground(true); runCurrent()
        advanceTimeBy(15 * 60_000L); runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(45 * 60_000L); runCurrent()
        assertEquals(2, calls)
        interval = 20 * 60_000L
        loop.requestRefresh(); runCurrent()
        advanceTimeBy(interval); runCurrent()
        assertEquals(4, calls)
        loop.setForeground(false)
    }
}

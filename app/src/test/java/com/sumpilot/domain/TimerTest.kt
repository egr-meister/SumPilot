package com.sumpilot.domain

import com.sumpilot.domain.timer.ActiveTimer
import com.sumpilot.domain.timer.formatClock
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimerTest {
    private val limit = 300_000L

    @Test fun countsOnlyWhileRunning() {
        var t = ActiveTimer(limit).resume(1_000)
        assertEquals(290_000, t.remainingMillis(11_000))
        t = t.pause(11_000)
        // Time passing while paused (feedback, hints, background) is not counted.
        assertEquals(290_000, t.remainingMillis(500_000))
        t = t.resume(500_000)
        assertEquals(280_000, t.remainingMillis(510_000))
    }

    @Test fun expiresAndClampsAtZero() {
        val t = ActiveTimer(limit).resume(0)
        assertFalse(t.isExpired(299_999))
        assertTrue(t.isExpired(300_000))
        assertEquals(0, t.remainingMillis(400_000))
        assertEquals(limit, t.activeMillis(400_000))
    }

    @Test fun cannotResumeAfterExpiry() {
        val t = ActiveTimer(limit).resume(0).pause(300_000)
        assertFalse(t.resume(301_000).isRunning)
    }

    @Test fun restoreFromPersistedCheckpointStartsPaused() {
        val t = ActiveTimer.restore(limit, remainingMillis = 123_000, activeMillis = 177_000)
        assertFalse(t.isRunning)
        assertEquals(123_000, t.remainingMillis(999_999_999))
    }

    @Test fun monotonicClockIndependentOfWallClock() {
        // The timer never reads wall-clock time; only elapsed-realtime deltas matter.
        val t = ActiveTimer(limit).resume(50_000)
        assertEquals(240_000, t.remainingMillis(110_000))
    }

    @Test fun untimedTimerAccumulatesActiveTime() {
        val t = ActiveTimer(null).resume(0).pause(5_000).resume(10_000)
        assertEquals(8_000, t.activeMillis(13_000))
        assertEquals(null, t.remainingMillis(13_000))
        assertFalse(t.isExpired(Long.MAX_VALUE / 2))
    }

    @Test fun clockFormatting() {
        assertEquals("5:00", formatClock(300_000))
        assertEquals("0:01", formatClock(1))
        assertEquals("0:00", formatClock(0))
        assertEquals("1:05", formatClock(64_100))
    }
}

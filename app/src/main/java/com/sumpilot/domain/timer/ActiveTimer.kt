package com.sumpilot.domain.timer

/**
 * Pausable timer for active answering time, computed from checkpoints on a monotonic clock
 * (SystemClock.elapsedRealtime) rather than from a decrementing counter. Wall-clock changes
 * have no effect.
 *
 * [limitMillis] is null for untimed sessions, where only active time is accumulated.
 * The class is immutable; every transition returns a new value that can be persisted.
 */
data class ActiveTimer(
    val limitMillis: Long?,
    /** Active time accumulated up to the last pause. */
    val accumulatedMillis: Long = 0L,
    /** Monotonic timestamp of the last resume, or null when paused. */
    val runningSince: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    fun activeMillis(now: Long): Long {
        val running = runningSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L
        val total = accumulatedMillis + running
        return if (limitMillis != null) total.coerceAtMost(limitMillis) else total
    }

    fun remainingMillis(now: Long): Long? = limitMillis?.let { (it - activeMillis(now)).coerceAtLeast(0L) }

    fun isExpired(now: Long): Boolean = limitMillis != null && activeMillis(now) >= limitMillis

    fun resume(now: Long): ActiveTimer =
        if (isRunning || isExpired(now)) this else copy(runningSince = now)

    fun pause(now: Long): ActiveTimer =
        if (!isRunning) this else copy(accumulatedMillis = activeMillis(now), runningSince = null)

    companion object {
        /** Restores a timer from persisted values. Restored timers always start paused. */
        fun restore(limitMillis: Long?, remainingMillis: Long?, activeMillis: Long): ActiveTimer {
            val accumulated = if (limitMillis != null && remainingMillis != null) {
                (limitMillis - remainingMillis).coerceIn(0L, limitMillis)
            } else {
                activeMillis.coerceAtLeast(0L)
            }
            return ActiveTimer(limitMillis, accumulated, null)
        }
    }
}

fun formatClock(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000 // round up so "0:00" only appears at expiry
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "$m min $s s" else "$s s"
}

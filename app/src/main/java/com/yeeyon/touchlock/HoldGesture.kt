package com.yeeyon.touchlock

/** Monotonic times are supplied by the view; cancellation requires a fresh press. */
internal class HoldGesture(private val durationMs: Long = 3_000L) {
    private var startMs: Long? = null
    fun begin(nowMs: Long) { startMs = nowMs }
    fun cancel() { startMs = null }
    fun progress(nowMs: Long): Float = startMs?.let {
        ((nowMs - it).toFloat() / durationMs).coerceIn(0f, 1f)
    } ?: 0f
    fun isComplete(nowMs: Long): Boolean = startMs?.let { nowMs - it >= durationMs } ?: false
}

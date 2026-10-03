package com.yeeyon.touchlock

import org.junit.Assert.*
import org.junit.Test

class HoldGestureTest {
    @Test fun requiresFullThreeSeconds() {
        val hold = HoldGesture()
        hold.begin(1_000)
        assertFalse(hold.isComplete(3_999))
        assertTrue(hold.isComplete(4_000))
    }
    @Test fun earlyReleaseNeverCompletesLater() {
        val hold = HoldGesture()
        hold.begin(1_000)
        assertEquals(2_999f / 3_000f, hold.progress(3_999), 0.0001f)
        hold.cancel()
        assertFalse(hold.isComplete(10_000))
        assertEquals(0f, hold.progress(10_000), 0f)
    }
    @Test fun cancelledAttemptDoesNotCarryIntoNextPress() {
        val hold = HoldGesture()
        hold.begin(0)
        hold.cancel()
        hold.begin(2_000)
        assertFalse(hold.isComplete(3_000))
        assertTrue(hold.isComplete(5_000))
    }
    @Test fun progressIsBoundedAndIdleDoesNotComplete() {
        val hold = HoldGesture()
        assertFalse(hold.isComplete(30_000))
        hold.begin(5_000)
        assertEquals(0f, hold.progress(4_999), 0f)
        assertEquals(1f, hold.progress(50_000), 0f)
    }
}

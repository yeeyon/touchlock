package com.yeeyon.touchlock

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class UnlockStepsTest {
    @Test fun threeStepsWithExactlyOneHold() {
        val random = Random(3)
        repeat(200) {
            val steps = UnlockSteps.plan(random)
            assertEquals(3, steps.count)
            assertEquals(1, (0 until 3).count { steps.needsHold(it) })
        }
    }

    @Test fun spotsAreFractionsAndConsecutiveStepsMoveApart() {
        val random = Random(11)
        repeat(500) {
            val spots = UnlockSteps.plan(random).spots
            spots.forEach { assertTrue(it.first in 0f..1f && it.second in 0f..1f) }
            for (i in 1 until spots.size) {
                val dx = spots[i].first - spots[i - 1].first
                val dy = spots[i].second - spots[i - 1].second
                assertTrue(dx * dx + dy * dy >= 0.35f * 0.35f)
            }
        }
    }

    @Test fun eachLockGetsADifferentRoute() {
        val random = Random(5)
        val routes = (0 until 50).map { UnlockSteps.plan(random) }
        assertTrue(routes.map { it.spots[0] }.toSet().size > 40)
        assertEquals(setOf(0, 1, 2), routes.map { it.holdStep }.toSet())
    }
}

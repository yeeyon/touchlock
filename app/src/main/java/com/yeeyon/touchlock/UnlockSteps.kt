package com.yeeyon.touchlock

import android.content.Context
import java.util.Random

/** Feature flag: unlock through three buttons shown one at a time at random spots. */
internal fun stepUnlockEnabled(context: Context): Boolean =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("stepUnlock", false)

/**
 * One lock's unlock route. Spots are fractions of the safe area, so a step keeps its place
 * across rotation and inset changes. Exactly one step needs the three-second hold.
 */
internal class UnlockSteps(val spots: List<Pair<Float, Float>>, val holdStep: Int) {
    val count get() = spots.size
    fun needsHold(step: Int) = step == holdStep

    companion object {
        /** Consecutive spots differ by at least [minDistance] (fraction of the area) when possible. */
        fun plan(random: Random, count: Int = 3, minDistance: Float = 0.35f): UnlockSteps {
            val spots = ArrayList<Pair<Float, Float>>(count)
            repeat(count) {
                var spot = random.nextFloat() to random.nextFloat()
                val previous = spots.lastOrNull()
                if (previous != null) {
                    for (attempt in 0 until 50) {
                        val dx = spot.first - previous.first
                        val dy = spot.second - previous.second
                        if (dx * dx + dy * dy >= minDistance * minDistance) break
                        spot = random.nextFloat() to random.nextFloat()
                    }
                }
                spots.add(spot)
            }
            return UnlockSteps(spots, random.nextInt(count))
        }
    }
}

package com.spotter.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rep counting, which is where a naive implementation embarrasses itself.
 *
 * The first test below is the one that matters: the measured value jitters every frame, so
 * anything built on `value < threshold` counts thirty reps for a lifter pausing at the bottom.
 *
 * Values here are squat depth in shin-lengths of hip above knee — 1.0 standing, 0 at parallel —
 * not degrees. See `Squat.metric` for why a knee angle cannot do this job head-on.
 */
class RepCounterTest {

    private val counter = RepCounter(Squat)

    private fun frame(depth: Float, trustworthy: Boolean = true) = Verdict(
        depth = when {
            depth >= Squat.topValue -> Depth.TOP
            depth <= Squat.bottomValue -> Depth.BOTTOM
            else -> Depth.MOVING
        },
        metric = depth,
        fault = null,
        isTrustworthy = trustworthy,
    )

    private fun feed(vararg depths: Float) = depths.map { counter.accept(frame(it)) }

    @Test
    fun `pausing at the bottom does not rack up reps`() {
        // Thirty frames of a lifter holding depth, jittering around the threshold.
        feed(1.0f, 0.4f, 0.1f)
        repeat(30) { counter.accept(frame(if (it % 2 == 0) 0.14f else 0.16f)) }
        assertEquals("a held bottom position is not thirty reps", 0, counter.reps)
    }

    @Test
    fun `a full squat counts once`() {
        val completions = feed(1.0f, 0.8f, 0.5f, 0.1f, 0.05f, 0.5f, 0.8f, 0.95f)
        assertEquals(1, counter.reps)
        assertEquals("the rep completes exactly once", 1, completions.count { it })
    }

    @Test
    fun `standing still counts nothing`() {
        feed(1.0f, 0.99f, 1.0f, 0.98f)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `three squats count three`() {
        repeat(3) { feed(1.0f, 0.5f, 0.1f, 0.5f, 1.0f) }
        assertEquals(3, counter.reps)
    }

    @Test
    fun `a shallow rep still counts, and is told the truth`() {
        // It felt like a rep to the lifter, so refusing to count it reads as a broken app. Counting
        // it silently would be worse — that is the rep they most need to hear about.
        feed(1.0f, 0.8f, 0.5f, 0.8f, 1.0f)
        assertEquals(1, counter.reps)
        assertEquals(Fault.TOO_SHALLOW, counter.lastRepFault)
    }

    @Test
    fun `a full-depth rep carries no fault`() {
        feed(1.0f, 0.5f, 0.1f, 0.5f, 1.0f)
        assertNull(counter.lastRepFault)
    }

    @Test
    fun `frames the camera could not read are ignored, not counted`() {
        // Someone stepping out of shot mid-set must come back to the same count, not to reps the
        // app invented from landmarks it never saw.
        counter.accept(frame(1.0f))
        repeat(10) { counter.accept(frame(0.1f, trustworthy = false)) }
        counter.accept(frame(1.0f))
        assertEquals(0, counter.reps)
    }

    @Test
    fun `an untrustworthy frame never reports a completion`() {
        assertFalse(counter.accept(frame(1.0f, trustworthy = false)))
    }

    @Test
    fun `reset clears the set`() {
        feed(1.0f, 0.5f, 0.1f, 0.5f, 1.0f)
        assertTrue(counter.reps > 0)
        counter.reset()
        assertEquals(0, counter.reps)
        assertNull(counter.lastRepFault)
        assertEquals(RepCounter.Stage.TOP, counter.stage)
    }
}

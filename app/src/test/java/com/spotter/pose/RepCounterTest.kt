package com.spotter.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rep counting, which is where a naive implementation embarrasses itself.
 *
 * The first test below is the one that matters: knee angle jitters by a few degrees every frame,
 * so anything built on `angle < threshold` counts thirty reps for a lifter pausing at the bottom.
 */
class RepCounterTest {

    private val counter = RepCounter(Squat)

    private fun frame(angle: Float, trustworthy: Boolean = true) = Verdict(
        depth = when {
            angle >= Squat.topAngle -> Depth.TOP
            angle <= Squat.bottomAngle -> Depth.BOTTOM
            else -> Depth.MOVING
        },
        angle = angle,
        fault = null,
        isTrustworthy = trustworthy,
    )

    private fun feed(vararg angles: Float) = angles.map { counter.accept(frame(it)) }

    @Test
    fun `pausing at the bottom does not rack up reps`() {
        // Thirty frames of a lifter holding depth, jittering around the threshold.
        feed(180f, 140f, 95f)
        repeat(30) { counter.accept(frame(if (it % 2 == 0) 99f else 101f)) }
        assertEquals("a held bottom position is not thirty reps", 0, counter.reps)
    }

    @Test
    fun `a full squat counts once`() {
        val completions = feed(180f, 150f, 120f, 95f, 90f, 120f, 150f, 175f)
        assertEquals(1, counter.reps)
        assertEquals("the rep completes exactly once", 1, completions.count { it })
    }

    @Test
    fun `standing still counts nothing`() {
        feed(180f, 179f, 180f, 178f)
        assertEquals(0, counter.reps)
    }

    @Test
    fun `three squats count three`() {
        repeat(3) { feed(180f, 130f, 95f, 130f, 180f) }
        assertEquals(3, counter.reps)
    }

    @Test
    fun `a shallow rep still counts, and is told the truth`() {
        // It felt like a rep to the lifter, so refusing to count it reads as a broken app. Counting
        // it silently would be worse — that is the rep they most need to hear about.
        feed(180f, 150f, 130f, 150f, 180f)
        assertEquals(1, counter.reps)
        assertEquals(Fault.TOO_SHALLOW, counter.lastRepFault)
    }

    @Test
    fun `a full-depth rep carries no fault`() {
        feed(180f, 130f, 95f, 130f, 180f)
        assertNull(counter.lastRepFault)
    }

    @Test
    fun `frames the camera could not read are ignored, not counted`() {
        // Someone stepping out of shot mid-set must come back to the same count, not to reps the
        // app invented from landmarks it never saw.
        counter.accept(frame(180f))
        repeat(10) { counter.accept(frame(95f, trustworthy = false)) }
        counter.accept(frame(180f))
        assertEquals(0, counter.reps)
    }

    @Test
    fun `an untrustworthy frame never reports a completion`() {
        assertFalse(counter.accept(frame(180f, trustworthy = false)))
    }

    @Test
    fun `reset clears the set`() {
        feed(180f, 130f, 95f, 130f, 180f)
        assertTrue(counter.reps > 0)
        counter.reset()
        assertEquals(0, counter.reps)
        assertNull(counter.lastRepFault)
        assertEquals(RepCounter.Stage.TOP, counter.stage)
    }
}

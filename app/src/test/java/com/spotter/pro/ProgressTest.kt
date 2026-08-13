package com.spotter.pro

import com.spotter.pose.Fault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the training history is allowed to claim.
 *
 * This is the paid feature, so being wrong here is worse than being wrong elsewhere — someone is
 * paying for the insight. The rule these tests defend is that the app says nothing rather than
 * something shaky: telling a lifter they are getting worse on the evidence of two sets is both
 * likely wrong and actively discouraging.
 */
class ProgressTest {

    private var clock = 1_000L

    private fun set(exercise: String = "Squat", reps: Int = 10, faulty: Int = 0): LoggedSet {
        clock += 60_000
        return LoggedSet(exercise, reps, faulty, clock)
    }

    @Test
    fun `fault rate is faulty reps over all reps`() {
        assertEquals(0.3f, LoggedSet("Squat", 10, 3, 0L).faultRate, 0.001f)
    }

    @Test
    fun `a set with no reps does not divide by zero`() {
        assertEquals(0f, LoggedSet("Squat", 0, 0, 0L).faultRate, 0.001f)
    }

    @Test
    fun `no trend is claimed from too little history`() {
        val sets = listOf(set(faulty = 5), set(faulty = 1))
        assertNull("two sets is not evidence of anything", Progress.trend(sets, "Squat"))
    }

    @Test
    fun `improving form reads as a negative trend`() {
        val sets = listOf(
            set(faulty = 8), set(faulty = 7),   // older half
            set(faulty = 2), set(faulty = 1),   // newer half
        )
        val trend = Progress.trend(sets, "Squat")!!
        assertTrue("expected improvement, got $trend", trend < 0f)
    }

    @Test
    fun `worsening form reads as a positive trend`() {
        val sets = listOf(
            set(faulty = 1), set(faulty = 0),
            set(faulty = 6), set(faulty = 7),
        )
        assertTrue(Progress.trend(sets, "Squat")!! > 0f)
    }

    @Test
    fun `exercises are tracked separately`() {
        // Squats improving while push-ups fall apart must not average into "no change" — that
        // would hide the exact thing someone paid to see.
        val sets = listOf(
            set("Squat", faulty = 8), set("Squat", faulty = 8),
            set("Push-up", faulty = 0), set("Push-up", faulty = 0),
            set("Squat", faulty = 1), set("Squat", faulty = 1),
            set("Push-up", faulty = 9), set("Push-up", faulty = 9),
        )
        assertTrue("squats improved", Progress.trend(sets, "Squat")!! < 0f)
        assertTrue("push-ups got worse", Progress.trend(sets, "Push-up")!! > 0f)
    }

    @Test
    fun `an exercise never performed has no trend`() {
        assertNull(Progress.trend(listOf(set("Squat")), "Push-up"))
    }

    @Test
    fun `order in the list does not matter, only time does`() {
        // Sets arrive newest-first from storage; the trend must sort by timestamp rather than
        // trusting the order, or improving and worsening would be reported backwards.
        val ordered = listOf(
            set(faulty = 8), set(faulty = 8), set(faulty = 1), set(faulty = 1),
        )
        assertEquals(
            Progress.trend(ordered, "Squat")!!,
            Progress.trend(ordered.reversed(), "Squat")!!,
            0.001f,
        )
    }

    @Test
    fun `the fault worth working on is the one that keeps happening`() {
        val faults = listOf(
            Fault.KNEES_CAVING, Fault.TOO_SHALLOW,
            Fault.KNEES_CAVING, Fault.BACK_ROUNDING, Fault.KNEES_CAVING,
        )
        assertEquals(Fault.KNEES_CAVING, Progress.mostCommonFault(faults))
    }

    @Test
    fun `no faults means nothing to work on`() {
        assertNull(Progress.mostCommonFault(emptyList()))
    }
}

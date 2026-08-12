package com.spotter.coach

import com.spotter.pose.Fault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When to speak, which is the only hard part of talking.
 *
 * Pose runs at about 30fps. The naive version of this says "knees out" thirty times a second, and
 * the app is muted before the end of the first set. Every test below is a rule that stops that,
 * and they are testable here only because the decision is separated from the speaking — the other
 * way to check "does it nag" is to do fifty squats.
 */
class SpokenCoachTest {

    private val coach = SpokenCoach()

    @Test
    fun `a fault is spoken once, not once per frame`() {
        // Same fault, thirty consecutive frames, one second of real time.
        val cues = (0..29).mapNotNull { frame ->
            coach.liveFault(Fault.KNEES_CAVING, nowMillis = frame * 33L)
        }
        assertEquals("thirty frames must produce one utterance", 1, cues.size)
    }

    @Test
    fun `the correction is the fault that was seen`() {
        val cue = coach.liveFault(Fault.BACK_ROUNDING, nowMillis = 5_000)
        assertEquals(Cue.Correction(Fault.BACK_ROUNDING), cue)
    }

    @Test
    fun `a new rep allows a new correction`() {
        assertNotNull(coach.liveFault(Fault.KNEES_CAVING, 5_000))
        coach.repCompleted(rep = 1, fault = Fault.KNEES_CAVING, nowMillis = 7_000)
        assertNotNull("the next rep gets its own correction",
            coach.liveFault(Fault.KNEES_CAVING, 9_000))
    }

    @Test
    fun `nothing is said twice inside the minimum gap`() {
        assertNotNull(coach.liveFault(Fault.KNEES_CAVING, 5_000))
        coach.repCompleted(rep = 1, fault = Fault.KNEES_CAVING, nowMillis = 5_100)
        // A rep completing 100ms later is not physically a rep, but even if it were, speaking
        // again this fast means two utterances overlapping.
        assertNull(coach.liveFault(Fault.KNEES_CAVING, 5_200))
    }

    @Test
    fun `the rep count is spoken when there was nothing to correct`() {
        val cue = coach.repCompleted(rep = 3, fault = null, nowMillis = 5_000)
        assertEquals(Cue.Count(3), cue)
    }

    @Test
    fun `a correction suppresses the count for that rep`() {
        // The number is the least useful thing that could be said in that second, and stacking it
        // behind a correction means the correction is still playing into the next rep.
        coach.liveFault(Fault.KNEES_CAVING, 5_000)
        assertNull(coach.repCompleted(rep = 1, fault = Fault.KNEES_CAVING, nowMillis = 6_000))
    }

    @Test
    fun `the same fault three reps running escalates once, then goes quiet`() {
        var now = 0L
        fun rep(fault: Fault?): List<Cue> {
            val cues = mutableListOf<Cue>()
            if (fault != null) coach.liveFault(fault, now)?.let(cues::add)
            now += 2_000
            coach.repCompleted(rep = 1, fault = fault, nowMillis = now)?.let(cues::add)
            now += 2_000
            return cues
        }

        val spoken = (1..6).map { rep(Fault.KNEES_CAVING) }

        val corrections = spoken.flatten().filterIsInstance<Cue.Correction>()
        val escalations = corrections.filter { it.escalated }

        assertEquals("escalate exactly once", 1, escalations.size)
        // After the escalation the coach stops mentioning it. Repeating a correction someone has
        // heard four times is not coaching, and it drowns out anything else worth saying.
        val afterEscalation = corrections.dropWhile { !it.escalated }.drop(1)
        assertTrue("nothing more about this fault after escalating", afterEscalation.isEmpty())
    }

    @Test
    fun `a different fault is heard even after another has been nagged out`() {
        var now = 0L
        repeat(5) {
            coach.liveFault(Fault.KNEES_CAVING, now)
            now += 2_000
            coach.repCompleted(rep = 1, fault = Fault.KNEES_CAVING, nowMillis = now)
            now += 2_000
        }
        // Going quiet about knees must not make the coach mute in general — the next fault is new
        // information and could be the one that matters.
        assertNotNull(coach.liveFault(Fault.BACK_ROUNDING, now))
    }

    @Test
    fun `a clean rep resets the nag counter`() {
        var now = 0L
        repeat(2) {
            coach.liveFault(Fault.KNEES_CAVING, now); now += 2_000
            coach.repCompleted(1, Fault.KNEES_CAVING, now); now += 2_000
        }
        coach.repCompleted(3, fault = null, nowMillis = now); now += 2_000
        // They fixed it, then it came back. That deserves the normal correction, not the
        // "drop the weight" escalation.
        val cue = coach.liveFault(Fault.KNEES_CAVING, now) as? Cue.Correction
        assertEquals(false, cue?.escalated)
    }

    @Test
    fun `what gets said is short enough to finish inside a rep`() {
        // A squat is two to three seconds. "Your knees are collapsing inward" describes something
        // that already happened by the time the sentence ends.
        val correction = Cue.Correction(Fault.KNEES_CAVING).spoken()
        assertTrue("'$correction' is too long to be useful mid-rep", correction.length <= 12)
        assertEquals("Knees out", correction)
    }

    @Test
    fun `counts are spoken as bare numbers`() {
        assertEquals("7", Cue.Count(7).spoken())
    }

    @Test
    fun `reset clears the set`() {
        coach.liveFault(Fault.KNEES_CAVING, 1_000)
        coach.reset()
        assertNotNull("a new set starts fresh", coach.liveFault(Fault.KNEES_CAVING, 2_000))
    }
}

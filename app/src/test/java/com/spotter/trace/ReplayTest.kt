package com.spotter.trace

import com.spotter.pose.Body
import com.spotter.pose.Fault
import com.spotter.pose.Point
import com.spotter.pose.Squat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tuning harness, proved out on synthetic traces before a real one exists.
 *
 * That ordering is deliberate. The recording device is borrowed — one window, then it goes back —
 * and discovering afterwards that the harness mangles the data would waste the only chance to
 * capture it. So the round trip and the replay are proven against bodies built in code first, and
 * the real session only has to press record.
 */
class ReplayTest {

    /**
     * A squat seen head-on, which is where this app asks for the phone.
     *
     * Hip, knee and ankle sit in a vertical line at every depth, because that is what a head-on
     * camera actually shows — the thigh bends towards the lens, not across it. [depth] slides the
     * hip down: 1.0 is standing, 0 is hip level with the knee.
     *
     * An earlier version of this helper bent the leg *in the image plane* and passed happily. It
     * was modelling a side view, and it hid the fact that the app was measuring squat depth by a
     * 2D knee angle that reads 180° at every depth from the front. The test was confirming the
     * assumption rather than the reality, which is the failure mode worth remembering here.
     *
     * [kneeInset] shifts the knees toward the midline — what caving looks like head-on, and
     * genuinely independent of depth in this projection.
     */
    private fun squatAt(depth: Float, kneeInset: Float = 0f): Body {
        val kneeY = 300f
        val ankleY = 400f
        val hipY = kneeY - depth * (ankleY - kneeY)

        return Body(
            leftHip = Point(-50f, hipY), rightHip = Point(50f, hipY),
            leftKnee = Point(-50f + kneeInset, kneeY), rightKnee = Point(50f - kneeInset, kneeY),
            leftAnkle = Point(-50f, ankleY), rightAnkle = Point(50f, ankleY),
            // Torso upright above the hips, so back rounding never fires and each test measures
            // only the thing it names.
            leftShoulder = Point(-50f, hipY - 120f), rightShoulder = Point(50f, hipY - 120f),
        )
    }

    /** One descent and return, at 30fps. */
    private fun oneRep(startMillis: Long, kneeInset: Float = 0f): List<TracedFrame> {
        val depths = listOf(1.0f, 0.7f, 0.4f, 0.1f, 0.05f, 0.1f, 0.4f, 0.7f, 1.0f)
        return depths.mapIndexed { index, depth ->
            TracedFrame(startMillis + index * 33L, squatAt(depth, kneeInset))
        }
    }

    private fun trace(vararg frames: TracedFrame) =
        Trace(label = "test", exercise = "Squat", frames = frames.toList())

    @Test
    fun `a recorded rep replays as a rep`() {
        val outcome = Replay.run(trace(*oneRep(0).toTypedArray()), Squat)
        assertEquals(1, outcome.reps)
    }

    @Test
    fun `three recorded reps replay as three`() {
        val frames = (0..2).flatMap { oneRep(it * 3_000L) }
        assertEquals(3, Replay.run(trace(*frames.toTypedArray()), Squat).reps)
    }

    @Test
    fun `a caving knee in the recording is flagged on replay`() {
        val outcome = Replay.run(trace(*oneRep(0, kneeInset = 45f).toTypedArray()), Squat)
        assertTrue("expected knee cave, got ${outcome.faults}",
            outcome.faults.contains(Fault.KNEES_CAVING))
    }

    @Test
    fun `a clean recording produces no corrections`() {
        val outcome = Replay.run(trace(*oneRep(0).toTypedArray()), Squat)
        assertEquals("a good squat should be left alone", emptyList<Fault>(), outcome.faults)
    }

    @Test
    fun `replay reports what would have been said out loud`() {
        // The point of capturing this: you can hear what the app would have shouted at you,
        // without anyone having to be in a gym.
        val outcome = Replay.run(trace(*oneRep(0, kneeInset = 45f).toTypedArray()), Squat)
        assertTrue("expected a spoken cue, got ${outcome.spoken}",
            outcome.spoken.any { it.contains("Knees", ignoreCase = true) })
    }

    @Test
    fun `frames the detector could not read are counted, not silently dropped`() {
        // A trace that is mostly unreadable is a bad recording, and the harness has to say so —
        // otherwise a tuning session draws conclusions from six usable frames.
        val murky = oneRep(0).map { frame ->
            TracedFrame(frame.atMillis, squatAt(0.4f).let {
                it.copy(leftKnee = it.leftKnee.copy(confidence = 0.1f))
            })
        }
        val outcome = Replay.run(trace(*murky.toTypedArray()), Squat)
        assertEquals(murky.size, outcome.untrustworthyFrames)
        assertEquals("nothing should be counted from unreadable frames", 0, outcome.reps)
    }

    @Test
    fun `a trace survives being written and read back`() {
        // The whole asset depends on this. A recording that does not round-trip is a recording
        // that was never taken.
        val original = trace(*oneRep(0, kneeInset = 30f).toTypedArray())
        val restored = TraceReader.fromJson(original.toJson())

        assertNotNull(restored)
        assertEquals(original.label, restored!!.label)
        assertEquals(original.exercise, restored.exercise)
        assertEquals(original.frames.size, restored.frames.size)
        assertEquals(
            "replaying the restored trace must give the identical verdict",
            Replay.run(original, Squat).faults,
            Replay.run(restored, Squat).faults,
        )
    }

    @Test
    fun `optional arm joints survive the round trip as absent`() {
        // A squat trace never saw wrists. Writing nulls and reading them back as present would
        // make a push-up replay claim evidence that does not exist.
        val restored = TraceReader.fromJson(trace(*oneRep(0).toTypedArray()).toJson())!!
        assertEquals(null, restored.frames.first().body.leftWrist)
    }

    @Test
    fun `corrupt json is refused rather than half-read`() {
        assertEquals(null, TraceReader.fromJson("{\"label\":\"broken\""))
    }

    @Test
    fun `comparing two outcomes names what changed`() {
        // The question tuning actually asks. Loosening a threshold is supposed to flag fewer reps;
        // the risk is that it stops flagging the ones that mattered, and a diff makes that visible.
        val strict = Replay.run(trace(*oneRep(0, kneeInset = 45f).toTypedArray()), Squat)
        val clean = Replay.run(trace(*oneRep(0).toTypedArray()), Squat)

        val report = Replay.compare(strict, clean)
        assertTrue("expected the lost fault to be named, got:\n$report",
            report.contains("no longer flagged"))
        assertTrue(report.contains("KNEES_CAVING"))
    }
}

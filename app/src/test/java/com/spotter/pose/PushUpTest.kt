package com.spotter.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Push-up geometry, tested with synthetic bodies lying down.
 *
 * A push-up is seen from the side, which is the opposite of a squat and changes what can be
 * judged: hip sag is invisible front-on, and knee cave is invisible side-on. The two exercises
 * genuinely need the phone in different places, and these tests pin down the side-on case.
 *
 * Coordinates are image-space: y grows **downward**, so a hip at y=220 has sagged below a
 * shoulder-ankle line at y=200.
 */
class PushUpTest {

    /**
     * Someone in a plank, facing right, arms roughly straight.
     *
     * [hipDrop] pushes the hips down (positive) or up (negative) relative to the straight line
     * from shoulders to ankles. [elbowBend] lowers the shoulder towards the floor to bend the arm.
     */
    private fun body(
        hipDrop: Float = 0f,
        elbowBend: Float = 0f,
        armsVisible: Boolean = true,
        confidence: Float = 1f,
    ): Body {
        val shoulderX = 100f
        val shoulderY = 200f - elbowBend
        val wristX = 100f
        val wristY = 400f
        val elbowX = 100f + elbowBend
        val elbowY = (shoulderY + wristY) / 2f

        return Body(
            leftShoulder = Point(shoulderX, shoulderY, confidence),
            rightShoulder = Point(shoulderX, shoulderY, confidence),
            leftHip = Point(400f, 200f + hipDrop, confidence),
            rightHip = Point(400f, 200f + hipDrop, confidence),
            leftKnee = Point(550f, 200f, confidence),
            rightKnee = Point(550f, 200f, confidence),
            leftAnkle = Point(700f, 200f, confidence),
            rightAnkle = Point(700f, 200f, confidence),
            leftElbow = if (armsVisible) Point(elbowX, elbowY, confidence) else null,
            rightElbow = if (armsVisible) Point(elbowX, elbowY, confidence) else null,
            leftWrist = if (armsVisible) Point(wristX, wristY, confidence) else null,
            rightWrist = if (armsVisible) Point(wristX, wristY, confidence) else null,
        )
    }

    @Test
    fun `a straight body has no hip deviation`() {
        assertEquals(0f, PushUp.hipDeviation(body(hipDrop = 0f)), 0.01f)
    }

    @Test
    fun `dropped hips read as positive deviation`() {
        // Positive is downward in image coordinates, which is the direction that hurts.
        assertTrue(PushUp.hipDeviation(body(hipDrop = 60f)) > 0f)
    }

    @Test
    fun `raised hips read as negative deviation`() {
        assertTrue(PushUp.hipDeviation(body(hipDrop = -60f)) < 0f)
    }

    @Test
    fun `sagging hips are called out`() {
        val verdict = PushUp.read(body(hipDrop = 60f, elbowBend = 60f))
        assertEquals(Fault.HIPS_SAGGING, verdict.fault)
    }

    @Test
    fun `piked hips are called out`() {
        // -80 was tried first and came out at -0.083 against a -0.09 threshold: a body that is
        // arguably piked rather than obviously so. The threshold is the deliberate part, so the
        // test body moved rather than the constant — this is what an unmistakable pike looks like.
        val verdict = PushUp.read(body(hipDrop = -130f, elbowBend = 60f))
        assertEquals(Fault.HIPS_PIKED, verdict.fault)
    }

    @Test
    fun `a borderline pike is left alone`() {
        // The other side of the same line, kept explicit so a future tweak to HIP_PIKE_RATIO has
        // to make a decision about it rather than silently widening what gets nagged about.
        assertNull(PushUp.read(body(hipDrop = -80f, elbowBend = 60f)).fault)
    }

    @Test
    fun `a small amount of hip movement is tolerated`() {
        // Nobody holds a perfectly straight line. An app that complains about a good push-up gets
        // muted, exactly like the squat case.
        val verdict = PushUp.read(body(hipDrop = 4f, elbowBend = 60f))
        assertNull(verdict.fault)
    }

    @Test
    fun `sagging is caught sooner than piking`() {
        // Sagging puts load on the lower back; piking just makes the movement easier. The
        // thresholds encode that asymmetry deliberately, so this pins it against a tidy-up that
        // would "simplify" them into one number.
        assertTrue(
            "sag threshold should be tighter than pike",
            PushUp.HIP_SAG_RATIO < PushUp.HIP_PIKE_RATIO,
        )
    }

    @Test
    fun `no verdict without a visible arm`() {
        // The elbow angle is the whole measurement. Without an arm there is no rep to judge, and
        // guessing from the legs would invent one.
        val verdict = PushUp.read(body(armsVisible = false))
        assertFalse(verdict.isTrustworthy)
        assertNull(verdict.fault)
    }

    @Test
    fun `no verdict when the body was barely seen`() {
        val verdict = PushUp.read(body(hipDrop = 60f, confidence = 0.2f))
        assertFalse(verdict.isTrustworthy)
    }

    @Test
    fun `straight arms read as the top of the rep`() {
        assertEquals(Depth.TOP, PushUp.read(body(elbowBend = 0f)).depth)
    }

    @Test
    fun `a bent arm is not the top of the rep`() {
        val deep = PushUp.read(body(elbowBend = 120f))
        assertTrue("expected to be past the top, was ${deep.angle}°", deep.angle < PushUp.topAngle)
    }

    @Test
    fun `nothing is corrected at the top of a rep`() {
        // Someone resting in a plank between reps is not mid-push-up, and shouting at them there
        // is how the app becomes background noise.
        val resting = PushUp.read(body(hipDrop = 60f, elbowBend = 0f))
        assertEquals(Depth.TOP, resting.depth)
        assertNull(resting.fault)
    }

    @Test
    fun `the counter works on push-ups without knowing what they are`() {
        // The point of the Exercise abstraction: RepCounter sees only an angle that falls and
        // rises. If this passes, adding a third movement costs nothing here.
        val counter = RepCounter(PushUp)
        listOf(170f, 140f, 95f, 140f, 170f).forEach { angle ->
            counter.accept(Verdict(Depth.MOVING, angle, null, isTrustworthy = true))
        }
        assertEquals(1, counter.reps)
    }

    @Test
    fun `push-ups and squats measure different joints`() {
        // A regression guard on the abstraction: if PushUp.angle ever fell back to the knee, the
        // rep count would still look plausible and be measuring the wrong limb entirely.
        val plank = body(elbowBend = 100f)
        assertTrue(
            "push-up angle should follow the elbow, not the knee",
            PushUp.angle(plank) != Squat.angle(plank),
        )
    }
}

package com.spotter.pose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Squat geometry, tested at a desk with synthetic bodies.
 *
 * This is the whole reason the judgement is a pure function. Verifying "does it spot a knee caving
 * in" by running the app requires a person, a camera, a gym, and someone willing to do a bad squat
 * on purpose — which means in practice it would be checked once, badly, and never again. Here
 * every case is a set of coordinates.
 *
 * Coordinates are image-space: y grows **downward**, so a hip at y=100 is above a knee at y=200.
 */
class SquatTest {

    /**
     * A body seen from the front, standing upright.
     *
     * [kneeInset] shifts both knees toward the midline, which is what knee cave looks like from
     * the front. [hipY] lowers the hips to squat.
     */
    private fun body(
        hipY: Float = 200f,
        kneeInset: Float = 0f,
        shoulderX: Float = 0f,
        shoulderY: Float = 100f,
        confidence: Float = 1f,
    ) = Body(
        leftHip = Point(-50f, hipY, confidence),
        rightHip = Point(50f, hipY, confidence),
        leftKnee = Point(-50f + kneeInset, 300f, confidence),
        rightKnee = Point(50f - kneeInset, 300f, confidence),
        leftAnkle = Point(-50f, 400f, confidence),
        rightAnkle = Point(50f, 400f, confidence),
        leftShoulder = Point(-50f + shoulderX, shoulderY, confidence),
        rightShoulder = Point(50f + shoulderX, shoulderY, confidence),
    )

    @Test
    fun `a straight leg reads as standing`() {
        // Hip, knee and ankle in a vertical line: the knee angle is 180.
        val verdict = Squat.read(body(hipY = 200f))
        assertEquals(Depth.TOP, verdict.depth)
        assertNull("nothing to say to someone standing still", verdict.fault)
    }

    @Test
    fun `bending the knee lowers the angle and eventually reads as deep`() {
        // Hips dropped to knee height and forward: a deeply bent knee.
        val deep = Body(
            leftHip = Point(-50f, 290f), rightHip = Point(50f, 290f),
            leftKnee = Point(-90f, 300f), rightKnee = Point(90f, 300f),
            leftAnkle = Point(-50f, 400f), rightAnkle = Point(50f, 400f),
            leftShoulder = Point(-50f, 200f), rightShoulder = Point(50f, 200f),
        )
        assertEquals(Depth.BOTTOM, Squat.read(deep).depth)
    }

    @Test
    fun `knees tracking over the feet are not a fault`() {
        assertFalse(Squat.kneesAreCaving(body(kneeInset = 0f)))
    }

    @Test
    fun `a knee collapsing inward is caught`() {
        // Thigh is ~100px; 30px inward is well past the 12% threshold.
        assertTrue(Squat.kneesAreCaving(body(kneeInset = 30f)))
    }

    @Test
    fun `a small amount of inward travel is tolerated`() {
        // Some drift is normal. An app that complains about a good squat gets muted, and then it
        // is not there for the rep that actually needed the warning.
        assertFalse(Squat.kneesAreCaving(body(kneeInset = 5f)))
    }

    @Test
    fun `an upright torso is not rounding`() {
        assertFalse(Squat.backIsRounding(body(shoulderX = 0f, shoulderY = 100f)))
    }

    @Test
    fun `a torso pitched well forward is rounding`() {
        // Shoulders pushed far ahead of the hips and barely above them.
        assertTrue(Squat.backIsRounding(body(shoulderX = 200f, shoulderY = 190f)))
    }

    @Test
    fun `knees caving outranks a rounding back`() {
        // One instruction, not three: someone at the bottom of a loaded squat can act on exactly
        // one, and knee cave is the fault that injures people.
        val both = Body(
            leftHip = Point(-50f, 280f), rightHip = Point(50f, 280f),
            leftKnee = Point(-20f, 300f), rightKnee = Point(20f, 300f),
            leftAnkle = Point(-50f, 400f), rightAnkle = Point(50f, 400f),
            leftShoulder = Point(150f, 270f), rightShoulder = Point(250f, 270f),
        )
        assertEquals(Fault.KNEES_CAVING, Squat.read(both).fault)
    }

    @Test
    fun `a joint the camera did not see produces no verdict at all`() {
        // ML Kit reports plausible-looking coordinates for a joint that is out of frame. Coaching
        // someone on a knee the camera never saw is the fastest way to lose their trust.
        val verdict = Squat.read(body(kneeInset = 40f, confidence = 0.2f))
        assertFalse(verdict.isTrustworthy)
        assertNull("no guess when the body was not clearly seen", verdict.fault)
    }

    @Test
    fun `the same squat filmed near and far gives the same answer`() {
        // Scale invariance is why the cave test is a ratio and not a pixel count. Without it a
        // lifter who steps back from the camera would be told their form improved.
        val near = body(kneeInset = 30f)
        val far = Body(
            leftHip = Point(near.leftHip.x / 2, near.leftHip.y / 2),
            rightHip = Point(near.rightHip.x / 2, near.rightHip.y / 2),
            leftKnee = Point(near.leftKnee.x / 2, near.leftKnee.y / 2),
            rightKnee = Point(near.rightKnee.x / 2, near.rightKnee.y / 2),
            leftAnkle = Point(near.leftAnkle.x / 2, near.leftAnkle.y / 2),
            rightAnkle = Point(near.rightAnkle.x / 2, near.rightAnkle.y / 2),
            leftShoulder = Point(near.leftShoulder.x / 2, near.leftShoulder.y / 2),
            rightShoulder = Point(near.rightShoulder.x / 2, near.rightShoulder.y / 2),
        )
        assertEquals(Squat.kneesAreCaving(near), Squat.kneesAreCaving(far))
    }

    @Test
    fun `coincident points do not produce a bogus fully-bent angle`() {
        // Degenerate input should read as "straight", which is harmless, rather than as "fully
        // bent", which would announce a deep squat that never happened.
        val same = Point(10f, 10f)
        assertEquals(180f, Geometry.angleAt(same, same, same), 0.01f)
    }
}

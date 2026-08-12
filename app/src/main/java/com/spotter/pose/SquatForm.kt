package com.spotter.pose

import kotlin.math.min

/**
 * What is wrong with a squat, if anything.
 *
 * Ordered by how much it matters. Only the first one gets said out loud mid-rep: a person halfway
 * down a squat can act on exactly one instruction, and a list of three is the same as silence.
 */
enum class Fault {
    /** Knees collapsing inward. The one that actually injures people, so it outranks everything. */
    KNEES_CAVING,

    /** Back rounding forward under load. */
    BACK_ROUNDING,

    /** Not reaching depth — the common one, and the least urgent. */
    TOO_SHALLOW,
}

/** How far through a squat the body is right now. */
enum class Depth { STANDING, DESCENDING, DEEP }

data class Verdict(
    val depth: Depth,
    /** Knee angle in degrees: 180 standing, smaller as they descend. */
    val kneeAngle: Float,
    /** The one thing worth saying right now, or null when the rep looks good. */
    val fault: Fault?,
    /** False when a joint the verdict depends on was not clearly seen. */
    val isTrustworthy: Boolean,
)

/**
 * Reading a squat from a single frame.
 *
 * **Pure, and that is the point of the architecture.** The thing this app does cannot be tested by
 * running it — verifying "does it correctly spot a knee caving in" needs a person, a camera, a
 * gym, and a willingness to do a bad squat on purpose. So the judgement is separated from the
 * capture: this file decides what a set of coordinates means and is tested exhaustively at a desk,
 * and the camera layer only has to be trusted to deliver coordinates.
 *
 * The thresholds below are the part that genuinely needs a real body in front of a real camera.
 * They are first estimates from published coaching norms, and they are collected here, named, and
 * commented so that tuning them later is a five-minute job rather than an archaeology expedition.
 */
object SquatForm {

    /**
     * Knee angle below which the squat counts as deep.
     *
     * ~90° is thighs-parallel-to-floor, the usual definition of a full squat. Set at 100 rather
     * than 90 so someone who is genuinely deep is not told they are shallow because of a few
     * degrees of camera angle.
     */
    const val DEEP_KNEE_ANGLE = 100f

    /** Above this the lifter is effectively upright and no verdict is worth giving. */
    const val STANDING_KNEE_ANGLE = 160f

    /**
     * How far a knee may drift inward before it is called out, as a fraction of thigh length.
     *
     * Some inward travel is normal and harmless. This is set to catch the visible collapse rather
     * than to police every millimetre — an app that complains about a good squat gets muted.
     *
     * **Needs tuning against a real body.** It is the number most likely to be wrong.
     */
    const val KNEE_CAVE_RATIO = 0.12f

    /** Torso lean, in degrees from vertical, past which the back is rounding rather than hinging. */
    const val BACK_ROUNDING_DEGREES = 55f

    fun read(body: Body): Verdict {
        val kneeAngle = min(
            Geometry.angleAt(body.leftKnee, body.leftHip, body.leftAnkle),
            Geometry.angleAt(body.rightKnee, body.rightHip, body.rightAnkle),
        )

        val depth = when {
            kneeAngle >= STANDING_KNEE_ANGLE -> Depth.STANDING
            kneeAngle <= DEEP_KNEE_ANGLE -> Depth.DEEP
            else -> Depth.DESCENDING
        }

        // A joint that was not clearly seen produces confident nonsense, so no verdict is given at
        // all rather than a guess. Silence is a fine thing for a coach to do; being wrong is not.
        if (!body.isFullyVisible) {
            return Verdict(depth, kneeAngle, fault = null, isTrustworthy = false)
        }

        return Verdict(depth, kneeAngle, faultIn(body, depth, kneeAngle), isTrustworthy = true)
    }

    /**
     * The single most important thing wrong right now.
     *
     * One fault, never a list. Someone at the bottom of a squat with a loaded bar can act on one
     * instruction; three at once is noise they will ignore, and then they will ignore the one that
     * mattered too.
     */
    private fun faultIn(body: Body, depth: Depth, kneeAngle: Float): Fault? {
        // Nothing to say to someone standing still.
        if (depth == Depth.STANDING) return null

        if (kneesAreCaving(body)) return Fault.KNEES_CAVING
        if (backIsRounding(body)) return Fault.BACK_ROUNDING

        // Depth is only judged at the point they stop descending, which the frame alone cannot
        // know — so it is reported by the rep counter at the turnaround, not here.
        return null
    }

    /** True when either knee has travelled inward of the line from its hip to its ankle. */
    fun kneesAreCaving(body: Body): Boolean {
        val left = Geometry.lateralOffsetRatio(
            point = body.leftKnee,
            lineStart = body.leftHip,
            lineEnd = body.leftAnkle,
            reference = body.rightHip,
        )
        val right = Geometry.lateralOffsetRatio(
            point = body.rightKnee,
            lineStart = body.rightHip,
            lineEnd = body.rightAnkle,
            reference = body.leftHip,
        )
        return left > KNEE_CAVE_RATIO || right > KNEE_CAVE_RATIO
    }

    /** True when the torso has pitched further forward than a hip hinge accounts for. */
    fun backIsRounding(body: Body): Boolean {
        val shoulder = midpoint(body.leftShoulder, body.rightShoulder)
        val hip = midpoint(body.leftHip, body.rightHip)
        return Geometry.tiltFromVertical(shoulder, hip) > BACK_ROUNDING_DEGREES
    }

    private fun midpoint(a: Point, b: Point) = Point(
        x = (a.x + b.x) / 2f,
        y = (a.y + b.y) / 2f,
        confidence = min(a.confidence, b.confidence),
    )
}

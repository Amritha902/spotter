package com.spotter.pose

import kotlin.math.abs
import kotlin.math.min

/**
 * What is wrong with the current rep, if anything.
 *
 * Ordered by how much it matters, and only the first one is ever said. Someone mid-rep can act on
 * exactly one instruction; a list of three is the same as silence.
 */
enum class Fault {
    /** Knees collapsing inward under a squat. The one that injures people. */
    KNEES_CAVING,

    /** Hips dropped towards the floor in a push-up — the lower back takes the load. */
    HIPS_SAGGING,

    /** Back rounding forward under a squat. */
    BACK_ROUNDING,

    /** Hips in the air in a push-up, shortening the movement into something easier. */
    HIPS_PIKED,

    /** Not reaching depth. Common, and the least urgent. */
    TOO_SHALLOW,
}

/** How far through a rep the body is right now. */
enum class Depth { TOP, MOVING, BOTTOM }

data class Verdict(
    val depth: Depth,
    /**
     * The scalar this movement is measured by. Higher means nearer the top of the rep.
     *
     * Not always an angle, and that is the point — see [Squat.metric]. Each exercise picks
     * whatever its camera position can actually see, and the units differ between them.
     */
    val metric: Float,
    /** The one thing worth saying right now, or null when the rep looks fine. */
    val fault: Fault?,
    /** False when a joint the verdict depends on was not clearly seen. */
    val isTrustworthy: Boolean,
)

/**
 * A movement the app can watch.
 *
 * The abstraction earns its place at the second exercise rather than the first. Squats and
 * push-ups differ in every particular — which joint angle measures the rep, which joints have to
 * be visible, what counts as a fault, even which side of the body the camera sees — but they share
 * one shape: an angle that starts high, comes down, and goes back up. [RepCounter] only needs that
 * shape, so it works for both without knowing which it is watching.
 *
 * Every threshold in the implementations below is a first estimate from coaching norms, and every
 * one of them wants tuning against a real body. They are gathered as named constants for exactly
 * that reason.
 */
interface Exercise {

    val name: String

    /** At or above this the lifter is at the top of the rep and there is nothing to correct. */
    val topValue: Float

    /** At or below this the rep has reached depth. */
    val bottomValue: Float

    /** Which joints must be clearly seen before any verdict is given. */
    fun canSee(body: Body): Boolean

    /**
     * How far through the rep the body is, higher meaning nearer the top.
     *
     * **Deliberately not "the joint angle".** A squat is filmed head-on, and from there the 2D
     * knee angle reads 180° whether the lifter is standing or at parallel — the thigh bends
     * towards the camera, which an image cannot see. Measuring squat depth by knee angle is
     * therefore not merely imprecise, it is constant. Each exercise picks a quantity its own
     * camera position can actually observe, and the units differ between them.
     */
    fun metric(body: Body): Float

    /** The single most important thing wrong, or null. */
    fun fault(body: Body, metric: Float): Fault?

    /**
     * Reading one frame.
     *
     * Shared across exercises because the shape of the judgement never differs: work out where in
     * the rep they are, refuse to guess if the camera could not see them, then ask the movement
     * what is wrong.
     */
    fun read(body: Body): Verdict {
        val metric = metric(body)
        val depth = when {
            metric >= topValue -> Depth.TOP
            metric <= bottomValue -> Depth.BOTTOM
            else -> Depth.MOVING
        }

        // A joint that was not clearly seen produces confident nonsense, so no verdict is given at
        // all rather than a guess. Silence is a fine thing for a coach to do; being wrong is not.
        if (!canSee(body)) return Verdict(depth, metric, fault = null, isTrustworthy = false)

        val fault = if (depth == Depth.TOP) null else fault(body, metric)
        return Verdict(depth, metric, fault, isTrustworthy = true)
    }
}

/**
 * A squat, seen from the front.
 *
 * Front-on because the fault worth catching — knees travelling inward of the feet — is invisible
 * from the side. That is a real constraint on where the phone goes, and the app says so rather
 * than silently coaching from an angle that cannot see the problem.
 */
object Squat : Exercise {

    override val name = "Squat"

    /**
     * Depth in shin-lengths of hip above knee, not degrees.
     *
     * Standing puts the hip roughly one shin above the knee; at parallel the hip crease is level
     * with it, which is 0. The thresholds leave room either side rather than arguing over the
     * exact instant of parallel.
     *
     * Measured this way because it is the only thing a head-on camera can see. See [metric].
     */
    override val bottomValue = 0.15f
    override val topValue = 0.75f

    /**
     * How far a knee may drift inward before it is called out, as a fraction of thigh length.
     *
     * Some inward travel is normal and harmless. This catches the visible collapse rather than
     * policing every millimetre — an app that complains about a good squat gets muted, and then it
     * is not there for the rep that needed the warning.
     *
     * **The number most likely to be wrong.** Needs a real body.
     */
    const val KNEE_CAVE_RATIO = 0.12f

    /** Torso lean in degrees past which the back is rounding rather than hinging. */
    const val BACK_ROUNDING_DEGREES = 55f

    override fun canSee(body: Body) = body.seesLowerBody

    /**
     * How far the hip sits above the knee, measured in shin lengths.
     *
     * **Not the knee angle, and that correction matters more than it looks.** Filmed head-on — the
     * placement this app asks for, because knee cave is invisible from anywhere else — hip, knee
     * and ankle stay in a near-vertical line at every depth. The thigh bends *towards the camera*,
     * so the 2D knee angle reads about 180° whether someone is standing or at parallel. Depth
     * measured that way is not noisy, it is constant, and the rep counter would never see a rep.
     *
     * Hip height relative to the knee is the standard coaching definition of depth ("hip crease
     * below the knee") and is exactly what a head-on view does show. The shin is the ruler because
     * it is the one segment whose apparent length barely changes through the movement.
     */
    override fun metric(body: Body): Float {
        val hip = midpoint(body.leftHip, body.rightHip)
        val knee = midpoint(body.leftKnee, body.rightKnee)
        val ankle = midpoint(body.leftAnkle, body.rightAnkle)

        val shin = ankle.y - knee.y
        // A shin of no apparent length means the camera is looking straight down the leg, and
        // nothing about depth can be read from it. "At the top" is the harmless answer.
        if (abs(shin) < 1e-3f) return topValue

        return (knee.y - hip.y) / shin
    }

    override fun fault(body: Body, metric: Float): Fault? = when {
        kneesAreCaving(body) -> Fault.KNEES_CAVING
        backIsRounding(body) -> Fault.BACK_ROUNDING
        // Depth is judged at the turnaround, which a single frame cannot see, so RepCounter
        // reports it rather than this.
        else -> null
    }

    /** True when either knee has travelled inward of the line from its hip to its ankle. */
    fun kneesAreCaving(body: Body): Boolean {
        val left = Geometry.lateralOffsetRatio(
            point = body.leftKnee, lineStart = body.leftHip,
            lineEnd = body.leftAnkle, reference = body.rightHip,
        )
        val right = Geometry.lateralOffsetRatio(
            point = body.rightKnee, lineStart = body.rightHip,
            lineEnd = body.rightAnkle, reference = body.leftHip,
        )
        return left > KNEE_CAVE_RATIO || right > KNEE_CAVE_RATIO
    }

    /** True when the torso has pitched further forward than a hip hinge accounts for. */
    fun backIsRounding(body: Body): Boolean {
        val shoulder = midpoint(body.leftShoulder, body.rightShoulder)
        val hip = midpoint(body.leftHip, body.rightHip)
        return Geometry.tiltFromVertical(shoulder, hip) > BACK_ROUNDING_DEGREES
    }
}

/**
 * A push-up, seen from the side.
 *
 * Side-on because the fault that matters — hips sagging towards the floor — is exactly the fault a
 * front-on camera cannot see. The phone goes beside you rather than in front, and half-open on the
 * floor it points at your midline without a stand, which is the same physical trick as the squat
 * and the reason both work on this hardware.
 *
 * The measured angle is the elbow, and the line the hips are judged against runs shoulder to
 * ankle: a good push-up holds one straight line from head to heel, and both of the faults here are
 * departures from it in opposite directions.
 */
object PushUp : Exercise {

    override val name = "Push-up"

    /**
     * Elbow angle in degrees — genuinely readable here, unlike the squat's knee.
     *
     * A push-up is filmed from the side, and from there the arm bends *across* the image rather
     * than towards the camera, so the angle is real information. Different units from [Squat] on
     * purpose: each movement measures what its own viewpoint can see.
     */
    override val bottomValue = 100f
    override val topValue = 155f

    /**
     * How far the hips may leave the shoulder-to-ankle line, as a fraction of that line's length.
     *
     * Sagging is the dangerous one — the lower back takes load the abdominals should — so it is
     * caught earlier than piking, which merely makes the movement easier. Both wanted tuning
     * against a real body before anyone trusts them.
     */
    const val HIP_SAG_RATIO = 0.06f
    const val HIP_PIKE_RATIO = 0.09f

    override fun canSee(body: Body) = body.sees(
        body.leftShoulder, body.rightShoulder,
        body.leftHip, body.rightHip,
        body.leftAnkle, body.rightAnkle,
    ) && (
        body.sees(body.leftElbow, body.leftWrist) || body.sees(body.rightElbow, body.rightWrist)
        )

    /**
     * The elbow angle, from whichever arm the camera can see.
     *
     * Not the minimum of both, unlike the squat. Seen from the side one arm is behind the other and
     * its landmarks are inferred rather than observed — taking the minimum would let the guessed
     * arm decide the rep. The nearer arm is the one with real evidence behind it.
     */
    override fun metric(body: Body): Float {
        val left = if (body.sees(body.leftElbow, body.leftWrist)) {
            Geometry.angleAt(body.leftElbow!!, body.leftShoulder, body.leftWrist!!)
        } else {
            null
        }
        val right = if (body.sees(body.rightElbow, body.rightWrist)) {
            Geometry.angleAt(body.rightElbow!!, body.rightShoulder, body.rightWrist!!)
        } else {
            null
        }

        val leftConfidence = body.leftElbow?.confidence ?: 0f
        val rightConfidence = body.rightElbow?.confidence ?: 0f

        return when {
            left != null && right != null ->
                if (leftConfidence >= rightConfidence) left else right
            left != null -> left
            right != null -> right
            // Nothing visible to measure. Reads as "at the top", which is the harmless answer —
            // canSee has already refused to give a verdict by this point anyway.
            else -> 180f
        }
    }

    override fun fault(body: Body, metric: Float): Fault? {
        val deviation = hipDeviation(body)
        return when {
            deviation > HIP_SAG_RATIO -> Fault.HIPS_SAGGING
            deviation < -HIP_PIKE_RATIO -> Fault.HIPS_PIKED
            else -> null
        }
    }

    /** Positive when the hips have dropped below the shoulder-to-ankle line. */
    fun hipDeviation(body: Body): Float = Geometry.verticalDeviationRatio(
        point = midpoint(body.leftHip, body.rightHip),
        lineStart = midpoint(body.leftShoulder, body.rightShoulder),
        lineEnd = midpoint(body.leftAnkle, body.rightAnkle),
    )
}

internal fun midpoint(a: Point, b: Point) = Point(
    x = (a.x + b.x) / 2f,
    y = (a.y + b.y) / 2f,
    confidence = min(a.confidence, b.confidence),
)

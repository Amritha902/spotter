package com.spotter.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * One tracked point on the body, in image coordinates.
 *
 * x grows to the right, y grows **downward** — the screen convention ML Kit reports in, kept rather
 * than flipped because quietly converting to maths convention halfway through is how sign errors
 * get into angle code and stay there.
 *
 * [confidence] is the detector's own certainty, 0..1. It matters more than it looks: a limb that is
 * out of frame still gets coordinates, and they are plausible-looking nonsense. Telling someone
 * their knee is caving in based on a knee the camera cannot see is worse than saying nothing.
 */
data class Point(val x: Float, val y: Float, val confidence: Float = 1f)

/** The joints this app needs. A full pose has 33; carrying the rest would be noise. */
data class Body(
    val leftHip: Point,
    val rightHip: Point,
    val leftKnee: Point,
    val rightKnee: Point,
    val leftAnkle: Point,
    val rightAnkle: Point,
    val leftShoulder: Point,
    val rightShoulder: Point,
) {
    /**
     * Whether every joint needed for a verdict was actually seen.
     *
     * The threshold is deliberately not near-zero. ML Kit reports a landmark for a joint that is
     * off-frame or occluded, with a low score and a confident-looking position, and coaching
     * someone on a joint the camera never saw is the fastest way to make them stop trusting this.
     */
    val isFullyVisible: Boolean
        get() = listOf(
            leftHip, rightHip, leftKnee, rightKnee,
            leftAnkle, rightAnkle, leftShoulder, rightShoulder,
        ).all { it.confidence >= MIN_CONFIDENCE }

    companion object {
        const val MIN_CONFIDENCE = 0.5f
    }
}

/** Angle arithmetic, kept apart from any judgement about what a given angle means. */
object Geometry {

    /**
     * The angle at [vertex], in degrees, between the two limbs meeting there.
     *
     * Returns 180 for a straight limb and gets smaller as it bends, which is how knee and hip
     * angles are described in every coaching context — so the numbers in this codebase read the
     * same way a physio would say them out loud.
     */
    fun angleAt(vertex: Point, a: Point, b: Point): Float {
        val ax = a.x - vertex.x
        val ay = a.y - vertex.y
        val bx = b.x - vertex.x
        val by = b.y - vertex.y

        val magnitude = hypot(ax, ay) * hypot(bx, by)
        // Coincident points have no angle between them. Zero would read as "fully bent", which is
        // a confident wrong answer; 180 reads as "straight", which is the harmless one.
        if (magnitude < 1e-6f) return 180f

        val cosine = ((ax * bx + ay * by) / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }

    /**
     * How far a point sits sideways from the line joining two others, as a fraction of that line's
     * length.
     *
     * Positive means displaced toward [reference]. Expressed as a ratio rather than in pixels
     * because the same squat filmed from two metres and from four metres must give the same
     * answer — an absolute pixel threshold would call a distant lifter perfect and a close one
     * broken.
     */
    fun lateralOffsetRatio(point: Point, lineStart: Point, lineEnd: Point, reference: Point): Float {
        val lineX = lineEnd.x - lineStart.x
        val lineY = lineEnd.y - lineStart.y
        val length = hypot(lineX, lineY)
        if (length < 1e-6f) return 0f

        // Signed perpendicular distance via the 2D cross product.
        val crossPoint = (point.x - lineStart.x) * lineY - (point.y - lineStart.y) * lineX
        val crossReference = (reference.x - lineStart.x) * lineY - (reference.y - lineStart.y) * lineX

        val ratio = crossPoint / (length * length)
        // Sign relative to the reference side, so "inward" means the same thing for both legs
        // regardless of which way the lifter is facing.
        return if (crossReference < 0) -ratio else ratio
    }

    /** How far from vertical a line is, in degrees. 0 is upright. */
    fun tiltFromVertical(top: Point, bottom: Point): Float {
        val dx = top.x - bottom.x
        val dy = top.y - bottom.y
        if (abs(dx) < 1e-6f && abs(dy) < 1e-6f) return 0f
        val vertical = Point(bottom.x, bottom.y - 100f)
        return angleAt(bottom, top, vertical)
    }
}

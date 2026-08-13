package com.spotter.pose

import kotlin.math.max

/** The upright image the detector actually saw, in pixels. */
data class Frame(val width: Int, val height: Int)

/** The area on screen the preview is drawn into, in pixels. */
data class Viewport(val width: Float, val height: Float)

/** A point in view coordinates, ready to draw. */
data class Drawn(val x: Float, val y: Float)

/**
 * Putting a detected joint where the person actually is on screen.
 *
 * **This is pure because getting it wrong is both extremely easy and extremely obvious.** A
 * skeleton drawn twenty pixels off the body, or mirrored, or squashed, is the single most visible
 * possible bug in this app — and it comes from three transforms that all have to agree: the
 * detector works in upright image pixels, the preview is centre-cropped to fill a differently
 * shaped view, and the front camera is mirrored.
 *
 * Debugging that by squinting at a live preview is guesswork. Here it is arithmetic with tests.
 */
object Projection {

    /**
     * Maps a detected point into the viewport.
     *
     * Assumes the preview fills the viewport and centre-crops the overflow, which is what
     * CameraX's viewfinder does by default. Letterboxing instead would need the *smaller* scale
     * factor here — if the preview ever gains bars down the sides, this is the line to change.
     *
     * [mirror] is true for the front camera, where the preview shows you your reflection. The
     * skeleton has to be mirrored with it or it lands on the wrong side of your body — and worse,
     * it looks almost right, so it reads as a tracking problem rather than a maths one.
     */
    fun map(point: Point, image: Frame, view: Viewport, mirror: Boolean): Drawn {
        if (image.width <= 0 || image.height <= 0) return Drawn(0f, 0f)

        val scale = max(view.width / image.width, view.height / image.height)
        val offsetX = (view.width - image.width * scale) / 2f
        val offsetY = (view.height - image.height * scale) / 2f

        val x = point.x * scale + offsetX
        val y = point.y * scale + offsetY

        return Drawn(if (mirror) view.width - x else x, y)
    }

    /**
     * The image dimensions the detector saw, given the camera's own rotation.
     *
     * `InputImage.fromMediaImage` is handed the rotation, so ML Kit reports coordinates in the
     * *upright* frame — which for a 90° or 270° rotation has width and height swapped relative to
     * the raw buffer. Using the raw dimensions here is the subtle version of this bug: the
     * skeleton tracks correctly but is stretched along one axis, which looks like a bad model
     * rather than a bad constant.
     */
    fun uprightFrame(bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int): Frame =
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            Frame(bufferHeight, bufferWidth)
        } else {
            Frame(bufferWidth, bufferHeight)
        }
}

/**
 * The lines worth drawing between joints.
 *
 * Only the limbs this app reasons about. A full 33-point skeleton with fingers and facial landmarks
 * looks more impressive in a screenshot and tells the lifter nothing — and it implies the app is
 * judging things it is not.
 */
val BONES: List<Pair<(Body) -> Point, (Body) -> Point>> = listOf(
    { b: Body -> b.leftShoulder } to { b: Body -> b.rightShoulder },
    { b: Body -> b.leftShoulder } to { b: Body -> b.leftHip },
    { b: Body -> b.rightShoulder } to { b: Body -> b.rightHip },
    { b: Body -> b.leftHip } to { b: Body -> b.rightHip },
    { b: Body -> b.leftHip } to { b: Body -> b.leftKnee },
    { b: Body -> b.rightHip } to { b: Body -> b.rightKnee },
    { b: Body -> b.leftKnee } to { b: Body -> b.leftAnkle },
    { b: Body -> b.rightKnee } to { b: Body -> b.rightAnkle },
)

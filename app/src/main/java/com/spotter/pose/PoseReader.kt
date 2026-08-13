package com.spotter.pose

import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.Pose as MlPose

/**
 * Turning ML Kit's 33-landmark pose into the eight joints this app reasons about.
 *
 * A separate file with a single job, because it is the seam between a third-party model and the
 * geometry that everything else is built on. Keeping the conversion here means [Exercise] and
 * [RepCounter] never see an ML Kit type, and can therefore be tested with plain numbers — which is
 * the only reason this app's judgement is testable at all.
 */
object PoseReader {

    /**
     * Null when the frame does not contain all eight joints.
     *
     * ML Kit returns a `Pose` object for an empty room; it simply has no landmarks in it. Returning
     * null here rather than a body full of zeroes means the rest of the app never has to wonder
     * whether it is looking at a person.
     */
    fun read(pose: MlPose): Body? {
        fun joint(type: Int): Point? = pose.getPoseLandmark(type)?.let {
            Point(it.position.x, it.position.y, it.inFrameLikelihood)
        }

        return Body(
            leftHip = joint(PoseLandmark.LEFT_HIP) ?: return null,
            rightHip = joint(PoseLandmark.RIGHT_HIP) ?: return null,
            leftKnee = joint(PoseLandmark.LEFT_KNEE) ?: return null,
            rightKnee = joint(PoseLandmark.RIGHT_KNEE) ?: return null,
            leftAnkle = joint(PoseLandmark.LEFT_ANKLE) ?: return null,
            rightAnkle = joint(PoseLandmark.RIGHT_ANKLE) ?: return null,
            leftShoulder = joint(PoseLandmark.LEFT_SHOULDER) ?: return null,
            rightShoulder = joint(PoseLandmark.RIGHT_SHOULDER) ?: return null,
            // Arms are optional rather than required: a squat is judged without them, and
            // refusing a well-framed squat because a wrist was out of shot would be absurd. The
            // exercise that needs them says so in its own canSee.
            leftElbow = joint(PoseLandmark.LEFT_ELBOW),
            rightElbow = joint(PoseLandmark.RIGHT_ELBOW),
            leftWrist = joint(PoseLandmark.LEFT_WRIST),
            rightWrist = joint(PoseLandmark.RIGHT_WRIST),
        )
    }
}

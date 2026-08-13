package com.spotter.trace

import com.spotter.pose.Body
import com.spotter.pose.Point
import org.json.JSONArray
import org.json.JSONObject

/** One frame of a recording: when it happened, and what the detector saw. */
data class TracedFrame(val atMillis: Long, val body: Body)

/**
 * A recorded set, as landmark coordinates.
 *
 * **This exists to solve a scheduling problem, not a technical one.** Every threshold in this app
 * is a guess until a real person moves in front of it, and the device to do that on is borrowed —
 * one window, and then it goes back. Tuning `KNEE_CAVE_RATIO` by repeatedly borrowing a phone and
 * doing deliberately bad squats is not a process anyone will complete.
 *
 * Recording the landmarks converts that one-shot session into a permanent asset. Capture a few
 * good squats and a few bad ones once, and every threshold in the app can be tuned, re-tuned and
 * regression-tested at a desk afterwards, with no hardware and no gym.
 *
 * Only the writing half lives in the app. Nothing in Spotter ever loads a trace back — the phone
 * records, and the replaying happens at a desk — so the reader is test-side tooling. The
 * reachability check is what pointed that out: `fromJson` and the replay harness were production
 * code no screen could ever reach.
 *
 * Landmarks rather than video, deliberately. A trace is a few hundred kilobytes of numbers instead
 * of a film of someone exercising — it can live in the repository, be diffed, and be attached to a
 * bug report without anyone having to think about who is in the footage.
 */
data class Trace(
    val label: String,
    val exercise: String,
    val frames: List<TracedFrame>,
) {
    fun toJson(): String {
        val array = JSONArray()
        frames.forEach { frame ->
            array.put(
                JSONObject()
                    .put("at", frame.atMillis)
                    .put("joints", frame.body.toJson())
            )
        }
        return JSONObject()
            .put("label", label)
            .put("exercise", exercise)
            .put("frames", array)
            .toString()
    }

}

private fun Point.toJson() = JSONArray().put(x.toDouble()).put(y.toDouble()).put(confidence.toDouble())

private fun Body.toJson(): JSONObject = JSONObject()
    .put("lHip", leftHip.toJson()).put("rHip", rightHip.toJson())
    .put("lKnee", leftKnee.toJson()).put("rKnee", rightKnee.toJson())
    .put("lAnkle", leftAnkle.toJson()).put("rAnkle", rightAnkle.toJson())
    .put("lShoulder", leftShoulder.toJson()).put("rShoulder", rightShoulder.toJson())
    .apply {
        // Arms are optional on Body and stay optional here. Writing nulls would make a squat trace
        // claim it observed wrists it never saw.
        leftElbow?.let { put("lElbow", it.toJson()) }
        rightElbow?.let { put("rElbow", it.toJson()) }
        leftWrist?.let { put("lWrist", it.toJson()) }
        rightWrist?.let { put("rWrist", it.toJson()) }
    }


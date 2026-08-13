package com.spotter.trace

import com.spotter.pose.Body
import com.spotter.pose.Point
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loading a recorded trace back, for tuning at a desk.
 *
 * Test-side rather than in the app, because the app never does this: the phone records during the
 * one session the borrowed device is available, and every replay afterwards happens here. Keeping
 * the reader out of `main` also keeps it out of the shipped APK, which is where it belongs.
 */
object TraceReader {

    fun fromJson(raw: String): Trace? = runCatching {
        val root = JSONObject(raw)
        val array = root.getJSONArray("frames")
        Trace(
            label = root.getString("label"),
            exercise = root.getString("exercise"),
            frames = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                TracedFrame(item.getLong("at"), bodyFromJson(item.getJSONObject("joints")))
            },
        )
    }.getOrNull()

    private fun pointFromJson(array: JSONArray?) = array?.let {
        Point(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat())
    }

    private fun bodyFromJson(json: JSONObject) = Body(
        leftHip = pointFromJson(json.getJSONArray("lHip"))!!,
        rightHip = pointFromJson(json.getJSONArray("rHip"))!!,
        leftKnee = pointFromJson(json.getJSONArray("lKnee"))!!,
        rightKnee = pointFromJson(json.getJSONArray("rKnee"))!!,
        leftAnkle = pointFromJson(json.getJSONArray("lAnkle"))!!,
        rightAnkle = pointFromJson(json.getJSONArray("rAnkle"))!!,
        leftShoulder = pointFromJson(json.getJSONArray("lShoulder"))!!,
        rightShoulder = pointFromJson(json.getJSONArray("rShoulder"))!!,
        leftElbow = pointFromJson(json.optJSONArray("lElbow")),
        rightElbow = pointFromJson(json.optJSONArray("rElbow")),
        leftWrist = pointFromJson(json.optJSONArray("lWrist")),
        rightWrist = pointFromJson(json.optJSONArray("rWrist")),
    )
}

package com.spotter.trace

import android.content.Context
import android.util.Log
import com.spotter.pose.Body
import java.io.File

/**
 * Capturing a set as landmark coordinates, on the phone.
 *
 * The writing half of the arrangement described in [Trace]: the borrowed device is available once,
 * so the session that matters is recorded rather than merely watched. Everything afterwards —
 * tuning `KNEE_CAVE_RATIO`, checking whether a looser threshold stops flagging the reps that
 * mattered — happens at a desk against the file this produces.
 *
 * Off by default and started deliberately. An app that silently recorded every session would be
 * collecting body-position data nobody asked it to collect, which is a different product from this
 * one.
 */
class TraceRecorder(private val context: Context) {

    private val frames = mutableListOf<TracedFrame>()
    private var recording = false

    val isRecording: Boolean get() = recording
    val frameCount: Int get() = frames.size

    fun start() {
        frames.clear()
        recording = true
    }

    fun capture(body: Body, atMillis: Long = System.currentTimeMillis()) {
        if (recording) frames += TracedFrame(atMillis, body)
    }

    /**
     * Stops and writes the file, returning where it went.
     *
     * Null when nothing was captured — an empty trace is a recording that did not happen, and
     * writing it would leave a file that looks like data.
     */
    fun stopAndSave(label: String, exercise: String): File? {
        recording = false
        if (frames.isEmpty()) return null

        val trace = Trace(label, exercise, frames.toList())
        val file = File(context.getExternalFilesDir(null) ?: context.filesDir, "$label.json")

        return runCatching {
            file.writeText(trace.toJson())
            Log.i(TAG, "Wrote ${frames.size} frames to ${file.absolutePath}")
            file
        }.getOrElse {
            Log.e(TAG, "Could not write the trace", it)
            null
        }
    }

    private companion object {
        const val TAG = "SpotterTrace"
    }
}

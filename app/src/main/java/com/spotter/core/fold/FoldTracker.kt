package com.spotter.core.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The posture, plus where the hinge actually is.
 *
 * [creaseFraction] is how far down the window the fold sits, 0..1, or null when there is no
 * horizontal fold to speak of. **Splitting a layout at 0.5 instead of at this number is the
 * difference between a two-pane app and a foldable app.** A phone whose hinge is not dead centre —
 * or whose window is offset by a status bar — puts content directly on the crease, where it is
 * bent away from the reader and physically hard to look at.
 */
data class Fold(
    val posture: Posture,
    val creaseFraction: Float? = null,
)

/**
 * What the phone is doing, as a stream.
 *
 * Two sources, because neither alone is enough. [WindowInfoTracker] says whether there is a fold,
 * how open it is, and where it lies; gravity says whether the thing is standing on a surface or
 * being held. A Fold reports `HALF_OPENED` identically whether it is propped on the floor pointing
 * at your face or lying sideways in one hand, and only one of those is Flex Mode.
 */
class FoldTracker(private val activity: Activity) {

    fun folds(): Flow<Fold> =
        combine(foldingFeatures(), gravityZ()) { feature, gravity ->
            Fold(
                posture = Postures.of(
                    hingeDegrees = feature?.toApproximateDegrees(),
                    gravityZ = gravity,
                    hasFoldingFeature = feature != null,
                ),
                creaseFraction = feature?.let { creaseFractionOf(it) },
            )
        }.distinctUntilChanged()

    private fun foldingFeatures(): Flow<FoldingFeature?> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }

    /**
     * Where the crease sits down the window, 0..1.
     *
     * Null for a vertical fold: this app's split is horizontal — the top half faces the lifter and
     * the bottom lies flat — and a vertical crease cannot be honoured by a horizontal split. Saying
     * null and falling back to an even split is honest; pretending a sideways hinge is a horizontal
     * one would put the seam somewhere the hardware does not bend.
     *
     * The fold's bounds are in window coordinates already, so this needs the window's own height
     * rather than the display's — they differ by the system bars, and that difference is easily
     * enough to land text on the crease.
     */
    private fun creaseFractionOf(feature: FoldingFeature): Float? {
        if (feature.orientation != FoldingFeature.Orientation.HORIZONTAL) {
            // Logged rather than silently ignored: a vertical crease and a centred horizontal one
            // both end up splitting the screen at 0.5, and from a screenshot they are
            // indistinguishable. Without this line there is no way to tell whether the layout is
            // honouring the hardware or merely coinciding with it.
            Log.d(TAG, "Fold is ${feature.orientation}, not horizontal — even split, no crease claim")
            return null
        }

        val window = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
            .bounds
        val height = window.height()
        if (height <= 0) return null

        val centre = feature.bounds.exactCenterY() - window.top
        val fraction = (centre / height).coerceIn(0f, 1f)
        Log.d(TAG, "Crease at ${"%.3f".format(fraction)} of window (fold ${feature.bounds}, window $window)")
        return fraction
    }

    private companion object {
        const val TAG = "SpotterFold"
    }

    /**
     * `FoldingFeature` reports a state, not an angle, so this maps back to the middle of each band.
     *
     * That is lossy and deliberately so: [Postures] only needs to know which band it is in, and
     * inventing a precise angle from a coarse state would be pretending to a resolution the API
     * does not have. It matters more here than usual: the phone is on the floor being asked to
     * stand at an aimed angle, so "is it propped up" is the only question, not "at what angle".
     */
    private fun FoldingFeature.toApproximateDegrees(): Float = when (state) {
        FoldingFeature.State.HALF_OPENED -> 95f
        FoldingFeature.State.FLAT -> 180f
        else -> 95f
    }

    private fun gravityZ(): Flow<Float> = callbackFlow {
        val sensors = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)

        if (gravity == null) {
            // No gravity sensor: report "not flat" so the app never claims a posture it cannot
            // detect. Being wrong towards HELD costs a nicer layout; being wrong towards FLAT
            // would put the counterpart's half face-down on a table.
            trySend(0f)
            awaitClose { }
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[2])
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensors.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI)
        awaitClose { sensors.unregisterListener(listener) }
    }
}

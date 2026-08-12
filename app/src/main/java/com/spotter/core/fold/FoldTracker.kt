package com.spotter.core.fold

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * What the phone is doing, as a stream.
 *
 * Two sources, because neither alone is enough. [WindowInfoTracker] says whether there is a fold
 * and roughly how open it is, but a Fold reports `HALF_OPENED` whether it is standing on a desk or
 * being held sideways in one hand. Gravity distinguishes those, and the difference decides whether
 * the top half is pointing at someone's face or at the ceiling.
 */
class FoldTracker(private val activity: Activity) {

    fun postures(): Flow<Posture> =
        combine(foldingFeatures(), gravityZ()) { feature, gravity ->
            Postures.of(
                hingeDegrees = feature?.toApproximateDegrees(),
                gravityZ = gravity,
                hasFoldingFeature = feature != null,
            )
        }.distinctUntilChanged()

    private fun foldingFeatures(): Flow<FoldingFeature?> =
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .map { info -> info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() }

    /**
     * `FoldingFeature` reports a state, not an angle, so this maps back to the middle of each band.
     *
     * That is lossy and deliberately so: [Postures] only needs to know which band it is in, and
     * inventing a precise angle from a coarse state would be pretending to a resolution the API
     * does not have. It matters more here than usual: the phone is on the floor being asked to
     * stand at an aimed angle, so "is it propped up" is the only question, not "at what angle". The hinge sensor gives a real angle on some hardware and nothing on the rest,
     * which is a worse thing to depend on than a state every foldable reports.
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

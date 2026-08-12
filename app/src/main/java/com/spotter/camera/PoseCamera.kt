package com.spotter.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.spotter.pose.Body
import com.spotter.pose.PoseReader
import java.util.concurrent.Executors

/**
 * The camera, feeding poses out one frame at a time.
 *
 * `STREAM_MODE` rather than single-image mode: it tracks a person between frames, which both costs
 * less per frame and stops landmarks jumping around between consecutive reads of a body that has
 * barely moved. A squat judged from jittering landmarks produces a verdict that flickers between
 * "good" and "knees caving" several times a second, which is useless to coach from.
 */
class PoseCamera(private val context: Context) {

    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    /**
     * Starts the camera and calls [onBody] for every frame containing a whole person.
     *
     * [onBody] receives null when nobody is fully in shot, which is a real state the screen needs
     * to show — "stand back so I can see your feet" is the most common thing this app has to say.
     */
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun start(owner: LifecycleOwner, onBody: (Body?) -> Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrElse {
                Log.e(TAG, "Camera provider unavailable", it)
                return@addListener
            }

            val analysis = ImageAnalysis.Builder()
                // Dropping frames is correct here. The alternative is a queue that grows until the
                // overlay is drawing a squat the lifter finished two seconds ago, and late coaching
                // is worse than none.
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { frame -> analyse(frame, onBody) }

            val lens = availableLens(provider)
            if (lens == null) {
                Log.e(TAG, "This device reports no usable camera")
                return@addListener
            }

            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(owner, lens, analysis)
            }.onFailure { Log.e(TAG, "Could not bind the camera", it) }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Front camera first, then back, then anything at all.
     *
     * Front is the one the product wants: the phone stands on the floor looking up at the lifter,
     * who needs to see themselves while they move. But binding to a lens that is not there throws
     * `IllegalArgumentException: No available camera can be found` and the app simply shows an
     * empty screen forever — which is what a device with one rear camera, or an emulator, does.
     * Coaching from the back camera is worse than coaching from the front; both are far better
     * than a blank screen and no explanation.
     */
    private fun availableLens(provider: ProcessCameraProvider): CameraSelector? = listOf(
        CameraSelector.DEFAULT_FRONT_CAMERA,
        CameraSelector.DEFAULT_BACK_CAMERA,
    ).firstOrNull { runCatching { provider.hasCamera(it) }.getOrDefault(false) }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun analyse(frame: ImageProxy, onBody: (Body?) -> Unit) {
        val image = frame.image
        if (image == null) {
            frame.close()
            return
        }

        val input = InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { pose -> onBody(PoseReader.read(pose)) }
            .addOnFailureListener { error ->
                Log.w(TAG, "Pose detection failed on a frame", error)
                onBody(null)
            }
            // Closing the frame is what allows the next one to arrive. Miss this and the camera
            // delivers exactly one frame and then silently stops, which looks like a frozen app.
            .addOnCompleteListener { frame.close() }
    }

    fun release() {
        runCatching { detector.close() }
        analysisExecutor.shutdown()
    }

    private companion object {
        const val TAG = "SpotterCamera"
    }
}

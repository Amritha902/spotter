package com.spotter

import androidx.camera.core.SurfaceRequest
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spotter.core.design.LocalSpotterColors
import com.spotter.core.fold.Posture
import com.spotter.pose.Fault

/** Everything the screen needs to know, so the layout has no opinions about pose maths. */
data class Coaching(
    val reps: Int,
    val fault: Fault?,
    val personVisible: Boolean,
    val cameraReady: Boolean,
    /** Non-null once the camera has a surface to draw into. */
    val surface: SurfaceRequest? = null,
)

/**
 * The set, as seen from the floor.
 *
 * In [Posture.FLEX] the phone is standing half-open: the **top** half is angled up at the lifter
 * and gets the two things worth seeing mid-rep — the count and the one correction. The bottom half
 * lies flat and carries setup and controls, which are only ever touched between sets when someone
 * has crouched down to the phone anyway.
 *
 * In any other posture there is no second half to use, so it becomes one column with the same
 * hierarchy. Nothing here pretends to a split the hardware is not currently making.
 */
@Composable
fun SpotterScreen(
    coaching: Coaching,
    posture: Posture,
    onNewSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpotterColors.current

    Column(modifier.fillMaxSize().background(colors.floor)) {
        // In Flex Mode the halves are equal because the crease decides where they are. Otherwise
        // the glance content takes two thirds — it is what someone is actually looking at.
        val glanceWeight = if (posture == Posture.FLEX) 1f else 2f

        Box(Modifier.weight(glanceWeight).fillMaxWidth()) {
            coaching.surface?.let { request ->
                CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
                // Dimmed hard, and this is a deliberate trade rather than a style choice. The
                // preview has to be legible enough to answer "are my feet in shot" and faint
                // enough that a 148sp number stays readable on top of it from two metres away.
                // Readability of the count wins; framing only has to be roughly right.
                //
                // 0.82 was tried first and the count visibly fought a bright background. At 0.92
                // the preview is silhouettes — which is all "are my feet in shot" ever needed,
                // and the number is unambiguous against any scene.
                Box(Modifier.fillMaxSize().background(colors.floor.copy(alpha = 0.92f)))
            }
            GlanceHalf(coaching)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { SetupHalf(coaching, onNewSet) }
    }
}

/**
 * What you read without looking directly at it.
 *
 * Two things only. A third would mean choosing between them mid-rep, which is the same as reading
 * none of them.
 */
@Composable
private fun GlanceHalf(coaching: Coaching) {
    val colors = LocalSpotterColors.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = coaching.reps.toString(),
            style = MaterialTheme.typography.displayLarge,
            // Green only once a rep is banked. A zero in the "good" colour would be congratulating
            // someone for standing still.
            color = if (coaching.reps > 0) colors.good else colors.inkMuted,
        )

        Text(
            text = if (coaching.reps == 1) "REP" else "REPS",
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkMuted,
        )

        // The correction sits in reserved space rather than pushing the count around when it
        // appears — a number that jumps as you squat is unreadable at two metres.
        Box(
            Modifier.fillMaxWidth().height(80.dp).padding(top = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            coaching.fault?.let { fault ->
                Text(
                    text = fault.callout(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = fault.colour(colors.danger, colors.caution),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Setup and controls, on the half lying flat. Only touched between sets. */
@Composable
private fun SetupHalf(coaching: Coaching, onNewSet: () -> Unit) {
    val colors = LocalSpotterColors.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !coaching.cameraReady -> colors.inkMuted
                            coaching.personVisible -> colors.good
                            else -> colors.caution
                        }
                    )
            )
            Text(
                text = when {
                    !coaching.cameraReady -> "Camera off"
                    coaching.personVisible -> "I can see you"
                    // The single most common thing this app has to say, so it says it plainly
                    // rather than as an error.
                    else -> "Step back until your feet are in shot"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Text(
            text = "Stand the phone half-open on the floor, screen towards you.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        TextButton(onClick = onNewSet) {
            Text("NEW SET", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
    }
}

/**
 * Three words at most.
 *
 * A correction is read in the half-second between noticing the screen and having to move, by
 * someone with a loaded bar. "Your knees are collapsing inward" is a true sentence nobody reads in
 * that window; "KNEES OUT" is what a coach standing next to you would actually shout.
 */
private fun Fault.callout(): String = when (this) {
    Fault.KNEES_CAVING -> "KNEES OUT"
    Fault.BACK_ROUNDING -> "CHEST UP"
    Fault.TOO_SHALLOW -> "DEEPER"
}

/** Red is reserved for the fault that injures people. Everything else is amber. */
private fun Fault.colour(danger: Color, caution: Color): Color = when (this) {
    Fault.KNEES_CAVING -> danger
    Fault.BACK_ROUNDING, Fault.TOO_SHALLOW -> caution
}

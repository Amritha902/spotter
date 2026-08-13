package com.spotter

import androidx.camera.core.SurfaceRequest
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spotter.core.design.LocalSpotterColors
import com.spotter.camera.Seen
import com.spotter.core.fold.Fold
import com.spotter.core.fold.Posture
import com.spotter.pose.BONES
import com.spotter.pose.Exercise
import com.spotter.pose.Fault
import com.spotter.pose.Point
import com.spotter.pose.Projection
import com.spotter.pose.PushUp
import com.spotter.pose.Squat
import com.spotter.pose.Viewport

/** Everything the screen needs to know, so the layout has no opinions about pose maths. */
data class Coaching(
    val reps: Int,
    val fault: Fault?,
    val personVisible: Boolean,
    val cameraReady: Boolean,
    /** Non-null once the camera has a surface to draw into. */
    val surface: SurfaceRequest? = null,
    /** The last body seen, and the geometry its coordinates are expressed in. */
    val seen: Seen? = null,
    val exercise: Exercise = Squat,
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
    fold: Fold,
    onNewSet: () -> Unit,
    onPickExercise: (Exercise) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSpotterColors.current

    Column(modifier.fillMaxSize().background(colors.floor)) {
        // In Flex Mode the seam goes where the hardware actually bends, not at a tidy midpoint.
        // A hinge that is not dead centre — or a window offset by the status bar — would otherwise
        // put the rep count directly on the crease, bent away from the person reading it.
        //
        // Falling back to 0.5 when the fold reports no horizontal crease is the honest default:
        // it is an even split that claims nothing about the hardware.
        val glanceWeight = when {
            fold.posture != Posture.FLEX -> 2f
            else -> (fold.creaseFraction ?: 0.5f).coerceIn(0.2f, 0.8f)
        }
        val restWeight = when {
            fold.posture != Posture.FLEX -> 1f
            else -> 1f - glanceWeight
        }

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
                // Drawn over the scrim, not under it — the whole point of the skeleton is that it
                // is the one thing in the preview you can actually see.
                coaching.seen?.let { Skeleton(it) }
            }
            GlanceHalf(coaching)
        }
        Box(Modifier.weight(restWeight).fillMaxWidth()) {
            SetupHalf(coaching, onNewSet, onPickExercise, onOpenHistory)
        }
    }
}

/**
 * The detected body, drawn where the body actually is.
 *
 * This is the moment someone believes the app: a number counting up could be anything, but a
 * skeleton that tracks your legs as you move is unambiguous proof it is watching *you*. It doubles
 * as the fastest possible diagnosis when coaching looks wrong — if the skeleton is off the body,
 * the geometry was never the problem.
 */
@Composable
private fun Skeleton(seen: Seen) {
    val colors = LocalSpotterColors.current
    val body = seen.body ?: return

    Canvas(Modifier.fillMaxSize()) {
        val view = Viewport(size.width, size.height)
        fun place(point: Point) = Projection.map(point, seen.frame, view, seen.mirrored)
            .let { Offset(it.x, it.y) }

        BONES.forEach { (from, to) ->
            drawLine(
                color = colors.good,
                start = place(from(body)),
                end = place(to(body)),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
            )
        }

        listOf(
            body.leftShoulder, body.rightShoulder, body.leftHip, body.rightHip,
            body.leftKnee, body.rightKnee, body.leftAnkle, body.rightAnkle,
        ).forEach { joint ->
            drawCircle(color = colors.ink, radius = 9f, center = place(joint))
        }
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
private fun SetupHalf(
    coaching: Coaching,
    onNewSet: () -> Unit,
    onPickExercise: (Exercise) -> Unit,
    onOpenHistory: () -> Unit,
) {
    val colors = LocalSpotterColors.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
    ) {
        Row {
            listOf(Squat, PushUp).forEach { option ->
                TextButton(onClick = { onPickExercise(option) }) {
                    Text(
                        text = option.name.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (option == coaching.exercise) colors.good else colors.inkMuted,
                    )
                }
            }
        }

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
            // Where the phone goes is not the same for both, and getting it wrong means the app
            // physically cannot see the fault it is looking for: knee cave is invisible from the
            // side, hip sag is invisible from the front.
            text = when (coaching.exercise) {
                PushUp -> "Half-open on the floor beside you, facing your side."
                else -> "Half-open on the floor in front of you, facing you."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
        )

        Row {
            TextButton(onClick = onNewSet) {
                Text("NEW SET", style = MaterialTheme.typography.labelLarge, color = colors.ink)
            }
            TextButton(onClick = onOpenHistory) {
                Text("HISTORY", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
            }
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
    // Said as the direction to move, not the name of the error. Someone holding a plank position
    // can act on "hips up"; working out which way to go from "hips sagging" costs a second they
    // do not have.
    Fault.HIPS_SAGGING -> "HIPS UP"
    Fault.HIPS_PIKED -> "HIPS DOWN"
    Fault.TOO_SHALLOW -> "DEEPER"
}

/**
 * Red is reserved for the faults that injure people. Everything else is amber.
 *
 * Sagging hips join knee cave in red because both put load somewhere it does not belong — the
 * lower back in one case, the knee joint in the other. Piking and shallow depth only make the
 * movement easier, which is a waste of a rep rather than a risk.
 */
private fun Fault.colour(danger: Color, caution: Color): Color = when (this) {
    Fault.KNEES_CAVING, Fault.HIPS_SAGGING -> danger
    Fault.BACK_ROUNDING, Fault.HIPS_PIKED, Fault.TOO_SHALLOW -> caution
}

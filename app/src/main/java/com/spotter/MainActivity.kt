package com.spotter

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotter.camera.PoseCamera
import com.spotter.camera.Seen
import com.spotter.coach.SpokenCoach
import com.spotter.coach.Voice
import com.spotter.core.design.LocalSpotterColors
import com.spotter.core.design.SpotterTheme
import com.spotter.core.fold.Fold
import com.spotter.core.fold.FoldTracker
import com.spotter.core.fold.Posture
import com.spotter.pose.Fault
import com.spotter.pose.RepCounter
import com.spotter.pose.Exercise
import com.spotter.pose.PushUp
import com.spotter.pose.Squat
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folds = FoldTracker(this).folds()
        setContent { SpotterTheme { SpotterApp(folds) } }
    }
}

@Composable
private fun SpotterApp(folds: Flow<Fold>) {
    val fold by folds.collectAsStateWithLifecycle(initialValue = Fold(Posture.HELD))
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var personVisible by remember { mutableStateOf(false) }
    var reps by remember { mutableStateOf(0) }
    var fault by remember { mutableStateOf<Fault?>(null) }
    var surface by remember { mutableStateOf<SurfaceRequest?>(null) }
    var seen by remember { mutableStateOf<Seen?>(null) }

    var exercise by remember { mutableStateOf<Exercise>(Squat) }
    // Keyed on the exercise: switching movement mid-session must start a fresh count rather than
    // carry squat reps into push-ups.
    val counter = remember(exercise) { RepCounter(exercise) }
    val coach = remember { SpokenCoach() }
    val voice = remember { Voice(context) }
    val camera = remember { PoseCamera(context) }
    DisposableEffect(Unit) {
        onDispose {
            camera.release()
            voice.release()
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        cameraReady = true
        camera.onSurface = { request -> surface = request }
        camera.start(owner) { frame ->
            seen = frame
            val body = frame.body
            personVisible = body != null
            if (body == null) return@start

            val verdict = exercise.read(body)
            val now = System.currentTimeMillis()

            // Mid-rep corrections replace the previous one rather than accumulating; standing
            // still leaves the last callout alone rather than clearing it mid-set.
            if (verdict.fault != null && counter.stage != RepCounter.Stage.TOP) {
                fault = verdict.fault
                // The screen is unreadable from the bottom of a squat. Speech is the only channel
                // that reaches someone with their head down, so the voice — not the display — is
                // the primary output here.
                coach.liveFault(verdict.fault, now)?.let(voice::say)
            }

            if (counter.accept(verdict)) {
                reps = counter.reps
                fault = counter.lastRepFault
                coach.repCompleted(counter.reps, counter.lastRepFault, now)?.let(voice::say)
            }
        }
    }

    if (!granted) {
        CameraNeeded(onAsk = { permission.launch(Manifest.permission.CAMERA) })
        return
    }

    SpotterScreen(
        coaching = Coaching(
            reps = reps,
            fault = fault,
            personVisible = personVisible,
            cameraReady = cameraReady,
            surface = surface,
            seen = seen,
            exercise = exercise,
        ),
        fold = fold,
        onNewSet = {
            counter.reset()
            coach.reset()
            reps = 0
            fault = null
        },
        onPickExercise = { picked ->
            exercise = picked
            // remember(exercise) rebuilds the counter, but the coach and the displayed numbers are
            // ours to clear — carrying a squat's rep count into push-ups would be nonsense.
            coach.reset()
            reps = 0
            fault = null
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

@Composable
private fun CameraNeeded(onAsk: () -> Unit) {
    val colors = LocalSpotterColors.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.floor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Spotter watches your form.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            // Said here rather than in a privacy policy nobody opens: it is the question anyone
            // pointing a camera at themselves in a gym actually has.
            text = "Everything runs on the phone. No video is recorded or sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onAsk) {
            Text("ALLOW CAMERA", style = MaterialTheme.typography.labelLarge, color = colors.good)
        }
    }
}

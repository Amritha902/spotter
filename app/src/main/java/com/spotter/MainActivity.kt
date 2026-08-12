package com.spotter

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.spotter.camera.PoseCamera
import com.spotter.pose.Depth
import com.spotter.pose.Fault
import com.spotter.pose.RepCounter
import com.spotter.pose.SquatForm

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { SpotterApp() } }
    }
}

/**
 * The spike screen.
 *
 * Deliberately plain. The open question this project has to answer first is whether pose detection
 * runs on a foldable at a usable frame rate and whether the geometry survives contact with a real
 * body — and none of that is a design problem. Making it beautiful before knowing it works would
 * be building the shopfront before checking the roof.
 */
@Composable
private fun SpotterApp() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(false) }
    var seenPeople by remember { mutableStateOf(0) }
    var lastSeen by remember { mutableStateOf("waiting for the camera…") }
    val counter = remember { RepCounter() }
    var reps by remember { mutableStateOf(0) }
    var fault by remember { mutableStateOf<Fault?>(null) }

    val camera = remember { PoseCamera(context) }
    DisposableEffect(Unit) { onDispose { camera.release() } }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) { permission.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        camera.start(owner) { body ->
            if (body == null) {
                lastSeen = "nobody fully in shot"
                return@start
            }
            seenPeople++
            val verdict = SquatForm.read(body)
            lastSeen = "knee ${verdict.kneeAngle.toInt()}°  ${verdict.depth}" +
                if (verdict.isTrustworthy) "" else "  (unclear)"
            if (counter.accept(verdict)) {
                reps = counter.reps
                fault = counter.lastRepFault
            }
            if (verdict.depth != Depth.STANDING) fault = verdict.fault ?: fault
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Spotter", style = MaterialTheme.typography.headlineMedium)

        if (!granted) {
            Text("Spotter needs the camera to watch your form.", textAlign = TextAlign.Center)
            TextButton(onClick = { permission.launch(Manifest.permission.CAMERA) }) {
                Text("Allow camera")
            }
            return@Column
        }

        Text("$reps reps", style = MaterialTheme.typography.displaySmall)
        Text(lastSeen, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Text("$seenPeople frames with a whole person", style = MaterialTheme.typography.bodySmall)

        fault?.let {
            Text(
                text = when (it) {
                    Fault.KNEES_CAVING -> "Knees out"
                    Fault.BACK_ROUNDING -> "Chest up"
                    Fault.TOO_SHALLOW -> "Go deeper"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        // A set ends and another begins; without this the count runs on forever and the number
        // stops meaning anything. Flagged by the reachability check, which noticed RepCounter had
        // a reset() no screen ever called.
        TextButton(
            onClick = {
                counter.reset()
                reps = 0
                fault = null
            }
        ) { Text("New set") }
    }
}

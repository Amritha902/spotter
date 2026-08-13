package com.spotter.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.Package
import com.spotter.core.design.LocalSpotterColors
import com.spotter.pose.Fault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Your training history — the paid feature.
 *
 * The free/paid line is drawn here on purpose and it is the app's whole ethic: **nothing that keeps
 * someone safe is ever behind a paywall.** Every exercise, the live coaching, the spoken
 * corrections, the rep count — all free, permanently. A knee about to give way is not a
 * monetisation opportunity.
 *
 * What is paid for is the part that only exists over time: whether you are actually getting better.
 * A free user loses insight, not safety, and still sees the set they just finished.
 */
@Composable
fun HistoryScreen(
    sets: List<LoggedSet>,
    isPro: Boolean,
    offering: Package?,
    problem: String?,
    onBuy: (Package) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalSpotterColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.floor)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("History", style = MaterialTheme.typography.headlineLarge, color = colors.ink)

        // Today's set is always visible. Someone who just finished should not be shown a paywall
        // instead of the thing they were standing there doing.
        val latest = sets.firstOrNull()
        if (latest == null) {
            Text(
                "No sets yet. Finish one and it will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        } else {
            SetRow(latest, isLatest = true)
        }

        if (!isPro) {
            Locked(offering, problem, onBuy, onRestore)
        } else {
            Trends(sets)
            sets.drop(1).forEach { SetRow(it, isLatest = false) }
        }

        TextButton(onClick = onBack) {
            Text("BACK", style = MaterialTheme.typography.labelLarge, color = colors.ink)
        }
    }
}

@Composable
private fun Trends(sets: List<LoggedSet>) {
    val colors = LocalSpotterColors.current
    val exercises = sets.map { it.exercise }.distinct()

    exercises.forEach { exercise ->
        val trend = Progress.trend(sets, exercise)
        Text(
            text = when {
                // Saying nothing is a real answer. Four sets is the minimum before a claim about
                // someone's form is worth making, and a confident number from two would be worse
                // than silence.
                trend == null -> "$exercise — not enough sets yet to call a trend."
                abs(trend) < 0.02f -> "$exercise — holding steady."
                trend < 0f -> "$exercise — ${(abs(trend) * 100).roundToInt()}% fewer bad reps than before."
                else -> "$exercise — ${(trend * 100).roundToInt()}% more bad reps than before."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                trend == null -> colors.inkMuted
                trend < -0.02f -> colors.good
                trend > 0.02f -> colors.caution
                else -> colors.ink
            },
        )
    }
}

@Composable
private fun SetRow(set: LoggedSet, isLatest: Boolean) {
    val colors = LocalSpotterColors.current
    val when_ = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(set.atMillis))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = "${set.reps} × ${set.exercise}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLatest) colors.ink else colors.inkMuted,
        )
        Text(
            text = when {
                set.faultyReps == 0 -> "clean · $when_"
                // Naming the fault is the actionable half. "3 flagged" tells someone the set was
                // imperfect; "3 × knees" tells them what to do about it.
                set.commonFault != null -> "${set.faultyReps} × ${set.commonFault.shortName()} · $when_"
                else -> "${set.faultyReps} flagged · $when_"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (set.faultyReps == 0) colors.good else colors.caution,
        )
    }
}

/** The same vocabulary the voice uses, so the history reads like what you heard in the gym. */
private fun Fault.shortName(): String = when (this) {
    Fault.KNEES_CAVING -> "knees"
    Fault.BACK_ROUNDING -> "chest"
    Fault.HIPS_SAGGING -> "hips down"
    Fault.HIPS_PIKED -> "hips up"
    Fault.TOO_SHALLOW -> "shallow"
}

@Composable
private fun Locked(
    offering: Package?,
    problem: String?,
    onBuy: (Package) -> Unit,
    onRestore: () -> Unit,
) {
    val colors = LocalSpotterColors.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Spotter Pro",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.ink,
        )
        Text(
            // Says what stays free, not just what is paid for. A paywall that implies the coaching
            // might disappear would make people distrust the free version too.
            "Coaching, corrections and rep counting are free forever, on every exercise. Pro keeps " +
                "your history so you can see whether your form is actually improving.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
            textAlign = TextAlign.Start,
        )

        if (offering == null) {
            Text(
                "Subscriptions aren't available in this build.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkMuted,
            )
        } else {
            TextButton(onClick = { onBuy(offering) }) {
                Text(
                    text = "GET PRO — ${offering.product.price.formatted}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.good,
                )
            }
        }

        TextButton(onClick = onRestore) {
            Text("RESTORE PURCHASE", style = MaterialTheme.typography.labelLarge, color = colors.inkMuted)
        }

        problem?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.caution)
        }
    }
}

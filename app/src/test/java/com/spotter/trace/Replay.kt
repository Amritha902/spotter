package com.spotter.trace

import com.spotter.coach.Cue
import com.spotter.coach.SpokenCoach
import com.spotter.coach.spoken
import com.spotter.pose.Exercise
import com.spotter.pose.Fault
import com.spotter.pose.RepCounter
import com.spotter.pose.Verdict

/** What the app would have done with a recording. */
data class Outcome(
    val reps: Int,
    /** Every fault the coach would have raised, in order. */
    val faults: List<Fault>,
    /** Everything that would have been said out loud, in order. */
    val spoken: List<String>,
    /** Frames the detector saw but the app refused to judge. */
    val untrustworthyFrames: Int,
)

/**
 * Running a recorded set back through the whole judgement pipeline.
 *
 * **This is the tuning tool, and it is the reason recording exists.** Change `KNEE_CAVE_RATIO`,
 * replay the same trace, and see exactly how many reps flip from clean to flagged — in
 * milliseconds, at a desk, with no phone and no gym. Doing that by squatting is a process nobody
 * finishes.
 *
 * It is deliberately the *real* pipeline rather than a simplified stand-in: the same [Exercise],
 * the same [RepCounter], the same [SpokenCoach]. A tuning harness that approximated any of them
 * would tune the approximation.
 */
object Replay {

    fun run(trace: Trace, exercise: Exercise, coach: SpokenCoach = SpokenCoach()): Outcome {
        val counter = RepCounter(exercise)
        val faults = mutableListOf<Fault>()
        val spoken = mutableListOf<String>()
        var untrustworthy = 0

        trace.frames.forEach { frame ->
            val verdict: Verdict = exercise.read(frame.body)
            if (!verdict.isTrustworthy) untrustworthy++

            if (verdict.fault != null && counter.stage != RepCounter.Stage.TOP) {
                coach.liveFault(verdict.fault, frame.atMillis)?.let { cue ->
                    spoken += cue.spoken()
                    if (cue is Cue.Correction) faults += cue.fault
                }
            }

            if (counter.accept(verdict)) {
                coach.repCompleted(counter.reps, counter.lastRepFault, frame.atMillis)
                    ?.let { spoken += it.spoken() }
                counter.lastRepFault?.let(faults::add)
            }
        }

        return Outcome(counter.reps, faults, spoken, untrustworthy)
    }

    /**
     * How a change to the thresholds would land, without changing them.
     *
     * Answers the question tuning actually asks: *if I loosen this, which reps stop being flagged?*
     * Comparing two outcomes is more useful than looking at either alone, because the risk in
     * loosening a threshold is not that it flags fewer reps — that is the point — but that it stops
     * flagging the ones that mattered.
     */
    fun compare(before: Outcome, after: Outcome): String = buildString {
        appendLine("reps:   ${before.reps} → ${after.reps}")
        appendLine("faults: ${before.faults.size} → ${after.faults.size}")
        val gone = before.faults - after.faults.toSet()
        val added = after.faults - before.faults.toSet()
        if (gone.isNotEmpty()) appendLine("no longer flagged: ${gone.distinct().joinToString()}")
        if (added.isNotEmpty()) appendLine("newly flagged:     ${added.distinct().joinToString()}")
    }
}

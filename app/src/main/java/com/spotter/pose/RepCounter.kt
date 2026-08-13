package com.spotter.pose

/**
 * Counting reps from a stream of frames.
 *
 * **A state machine rather than a threshold test, because a threshold test double-counts.** Knee
 * angle does not fall smoothly and rise smoothly; it jitters by a few degrees every frame, so a
 * lifter pausing at 100.5° with a naive `angle < 100` check racks up thirty reps without moving.
 * Requiring a full standing → deep → standing cycle makes that impossible rather than unlikely.
 *
 * The counter also owns the depth verdict, because depth is the one fault a single frame genuinely
 * cannot judge: "you did not go deep enough" is only true once someone has started coming back up,
 * and that is a fact about the trajectory, not the pose.
 */
class RepCounter(private val exercise: Exercise) {

    /** Where we are in the current rep. */
    enum class Stage { TOP, DESCENDING, AT_DEPTH, RISING }

    var stage: Stage = Stage.TOP
        private set

    var reps: Int = 0
        private set

    /**
     * The lowest metric reached in the rep currently underway.
     *
     * MAX_VALUE rather than 180 as the reset: the metric is no longer always an angle, so a
     * degrees-shaped sentinel would be silently wrong for the squat.
     *
     * Tracked rather than sampled because the bottom of a squat is a single instant that any given
     * frame is likely to miss.
     */
    private var deepestThisRep: Float = Float.MAX_VALUE

    /** What the counter has to say at the end of a rep, if anything. */
    var lastRepFault: Fault? = null
        private set

    /**
     * Feeds one frame in. Returns true on the frame that completes a rep.
     *
     * Untrustworthy frames are ignored outright rather than treated as a neutral reading: a lifter
     * who walks out of shot mid-set should come back to the same rep count, not to a rep the app
     * invented from landmarks it could not see.
     */
    fun accept(verdict: Verdict): Boolean {
        if (!verdict.isTrustworthy) return false

        val metric = verdict.metric
        deepestThisRep = minOf(deepestThisRep, metric)

        when (stage) {
            Stage.TOP ->
                if (metric < exercise.topValue) stage = Stage.DESCENDING

            Stage.DESCENDING -> when {
                metric <= exercise.bottomValue -> stage = Stage.AT_DEPTH
                // Went back up without ever reaching depth. That is a rep in the lifter's head, so
                // it is counted — and told the truth about.
                metric >= exercise.topValue -> return completeRep(Fault.TOO_SHALLOW)
            }

            Stage.AT_DEPTH ->
                if (metric > exercise.bottomValue) stage = Stage.RISING

            Stage.RISING ->
                if (metric >= exercise.topValue) return completeRep(null)
        }
        return false
    }

    private fun completeRep(fault: Fault?): Boolean {
        reps++
        lastRepFault = fault
        stage = Stage.TOP
        deepestThisRep = Float.MAX_VALUE
        return true
    }

    /** Between sets. */
    fun reset() {
        stage = Stage.TOP
        reps = 0
        deepestThisRep = Float.MAX_VALUE
        lastRepFault = null
    }
}

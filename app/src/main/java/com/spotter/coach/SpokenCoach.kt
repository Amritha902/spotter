package com.spotter.coach

import com.spotter.pose.Fault

/** Something worth saying out loud. */
sealed interface Cue {
    /** A correction, mid-rep. */
    data class Correction(val fault: Fault, val escalated: Boolean = false) : Cue

    /** The rep just banked, counted the way a spotter counts. */
    data class Count(val rep: Int) : Cue
}

/**
 * Deciding when to speak, which is the whole difficulty.
 *
 * The screen is unreadable at the bottom of a squat — the lifter's head is down, their eyes are
 * anywhere but the floor, and they have about a second. Speech is the only channel that works
 * there. But pose runs at ~30fps, so the naive version says "knees out" thirty times a second and
 * the app gets turned off inside one set.
 *
 * This is pure and stateful in the same way [com.spotter.pose.RepCounter] is: it can be driven
 * frame by frame in a test, which matters because the alternative way to check "does it nag" is to
 * do fifty squats.
 */
class SpokenCoach {

    /**
     * Null means nothing has been said yet, and it has to be null rather than 0.
     *
     * With 0 as "never", the very first correction of a session is compared against a last-spoken
     * time of zero and suppressed as too soon — so a lifter who starts squatting immediately gets
     * silence for the exact reps they most needed a voice on. Caught by the first test that ran
     * against this file.
     */
    private var lastSpokenAt: Long? = null
    private var spokenThisRep = false
    private var consecutiveRepsWithFault = 0
    private var lastFault: Fault? = null
    private var escalatedFor: Fault? = null

    /**
     * A fault seen in the current rep. Returns what to say, or null for silence.
     *
     * At most one correction per rep, because a second one lands while they are still acting on
     * the first — and a coach who talks continuously is one you stop hearing.
     */
    fun liveFault(fault: Fault, nowMillis: Long): Cue? {
        if (spokenThisRep) return null
        lastSpokenAt?.let { if (nowMillis - it < MIN_GAP_MS) return null }

        // They have heard this three reps running and it has not changed. Saying it a fourth time
        // is not coaching, it is nagging, and it drowns out anything else worth hearing. Say the
        // stronger thing once — the honest advice at that point is to take weight off the bar —
        // and then leave it alone for the rest of the set.
        if (fault == lastFault && consecutiveRepsWithFault >= NAG_LIMIT) {
            if (escalatedFor == fault) return null
            escalatedFor = fault
            spokenThisRep = true
            lastSpokenAt = nowMillis
            return Cue.Correction(fault, escalated = true)
        }

        spokenThisRep = true
        lastSpokenAt = nowMillis
        return Cue.Correction(fault)
    }

    /**
     * A rep just completed. Returns the count to say, or null.
     *
     * Silent when a correction was already given this rep: the number is the least important thing
     * that could be said in that second, and stacking it behind a correction means the correction
     * is still playing while they start the next rep.
     */
    fun repCompleted(rep: Int, fault: Fault?, nowMillis: Long): Cue? {
        // Track nagging across reps before resetting the per-rep state.
        if (fault != null && fault == lastFault) {
            consecutiveRepsWithFault++
        } else if (fault != null) {
            lastFault = fault
            consecutiveRepsWithFault = 1
            escalatedFor = null
        } else {
            lastFault = null
            consecutiveRepsWithFault = 0
            escalatedFor = null
        }

        val alreadySpoke = spokenThisRep
        spokenThisRep = false

        if (alreadySpoke) return null

        lastSpokenAt = nowMillis
        return Cue.Count(rep)
    }

    fun reset() {
        lastSpokenAt = null
        spokenThisRep = false
        consecutiveRepsWithFault = 0
        lastFault = null
        escalatedFor = null
    }

    private companion object {
        /**
         * Nothing is said twice inside this window.
         *
         * A squat takes two to three seconds. Anything faster than this is two utterances inside
         * one rep, which is the thing that makes people mute an app.
         */
        const val MIN_GAP_MS = 1_800L

        /** Reps in a row with the same fault before repeating it stops being useful. */
        const val NAG_LIMIT = 3
    }
}

/**
 * What a cue actually sounds like.
 *
 * Short, because it has to finish before they are back at the bottom. A spotter says "knees" not
 * "your knees are collapsing inward" — by the time the sentence ends the rep is over and the
 * advice describes something that already happened.
 */
fun Cue.spoken(): String = when (this) {
    is Cue.Count -> rep.toString()
    is Cue.Correction -> when {
        escalated && fault == Fault.KNEES_CAVING -> "Knees still caving. Drop the weight."
        escalated -> "Still happening. Reset and go lighter."
        fault == Fault.KNEES_CAVING -> "Knees out"
        fault == Fault.BACK_ROUNDING -> "Chest up"
        else -> "Deeper"
    }
}

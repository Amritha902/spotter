package com.spotter.pro

import android.content.Context
import com.spotter.pose.Fault
import org.json.JSONArray
import org.json.JSONObject

/** One completed set. */
data class LoggedSet(
    val exercise: String,
    val reps: Int,
    /** How many of those reps carried a fault. */
    val faultyReps: Int,
    val atMillis: Long,
    /**
     * The fault that came up most in this set, if any.
     *
     * One per set rather than a list, for the same reason only one is ever said out loud: the
     * useful question is "what should I work on", and that has a single answer.
     */
    val commonFault: Fault? = null,
) {
    /** Share of reps that went wrong, 0f–1f. */
    val faultRate: Float get() = if (reps == 0) 0f else faultyReps.toFloat() / reps
}

/**
 * Every set you have finished.
 *
 * **This is what Pro unlocks, and the choice of what to gate is the whole ethic of the app.**
 * Nothing that keeps someone safe is ever behind a paywall: every exercise, live coaching, the
 * spoken corrections and the rep count all work for free, forever. A knee about to give way is not
 * a monetisation opportunity.
 *
 * What Pro adds is the thing that is only valuable over time — whether your form is actually
 * improving, or whether the same fault has shown up on a third of your reps every session for a
 * month. Withholding that from a free user costs them insight, not safety, and they can see today's
 * set regardless.
 *
 * Stored as plain JSON in app-private storage. Small, inspectable, and gone when the app is
 * uninstalled — a training log is not worth a database, and it is nobody else's business.
 */
class SetLog(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("spotter.sets", Context.MODE_PRIVATE)

    fun record(
        exercise: String,
        reps: Int,
        faults: List<Fault>,
        atMillis: Long = System.currentTimeMillis(),
    ) {
        // An empty set is someone opening the app and putting it down. Recording it would dilute
        // every average with sets that never happened.
        if (reps == 0) return

        val all = read().toMutableList()
        all += LoggedSet(
            exercise = exercise,
            reps = reps,
            faultyReps = faults.size,
            atMillis = atMillis,
            commonFault = Progress.mostCommonFault(faults),
        )
        write(all.takeLast(MAX_SETS))
    }

    fun all(): List<LoggedSet> = read().sortedByDescending { it.atMillis }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun read(): List<LoggedSet> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        // A corrupt log is not worth crashing over — it is a training history, not a bank ledger.
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                LoggedSet(
                    exercise = item.getString("exercise"),
                    reps = item.getInt("reps"),
                    faultyReps = item.getInt("faultyReps"),
                    atMillis = item.getLong("at"),
                    // Older entries predate this field, and a missing fault is not a corrupt log.
                    commonFault = item.optString("fault").takeIf { it.isNotEmpty() }
                        ?.let { name -> runCatching { Fault.valueOf(name) }.getOrNull() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun write(sets: List<LoggedSet>) {
        val array = JSONArray()
        sets.forEach { set ->
            array.put(
                JSONObject()
                    .put("exercise", set.exercise)
                    .put("reps", set.reps)
                    .put("faultyReps", set.faultyReps)
                    .put("at", set.atMillis)
                    .put("fault", set.commonFault?.name ?: "")
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "sets"

        /**
         * Enough history to see a trend, bounded so the log cannot grow without limit.
         *
         * At a few sets a day this is over a year, which is far longer than anyone will look back.
         */
        const val MAX_SETS = 500
    }
}

/**
 * What the history actually tells you, as a pure function over logged sets.
 *
 * Separate from storage so the summary can be tested without a device — the interesting part is
 * the arithmetic, and the interesting arithmetic is comparing two periods, which is fiddly enough
 * to get wrong silently.
 */
object Progress {

    /**
     * How the fault rate has moved between the older and newer halves of a run of sets.
     *
     * Negative means improving — fewer reps going wrong. Null when there is not enough history to
     * say anything honest, which is a real answer and better than a confident number derived from
     * two sets.
     */
    fun trend(sets: List<LoggedSet>, exercise: String): Float? {
        val relevant = sets.filter { it.exercise == exercise }.sortedBy { it.atMillis }
        if (relevant.size < MIN_SETS_FOR_TREND) return null

        val half = relevant.size / 2
        val older = relevant.take(half)
        val newer = relevant.drop(half)

        return newer.averageFaultRate() - older.averageFaultRate()
    }

    /** The fault a lifter should actually work on: the one that keeps happening. */
    fun mostCommonFault(faults: List<Fault>): Fault? =
        faults.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    private fun List<LoggedSet>.averageFaultRate(): Float =
        if (isEmpty()) 0f else sumOf { it.faultRate.toDouble() }.toFloat() / size

    /**
     * Four sets before a trend is claimed.
     *
     * Two sets can differ for a hundred reasons that are not form, and telling someone they are
     * getting worse on that evidence is both wrong and discouraging.
     */
    const val MIN_SETS_FOR_TREND = 4
}

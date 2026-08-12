package com.spotter.coach

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Saying a cue out loud.
 *
 * Thin on purpose — every decision about *whether* to speak lives in [SpokenCoach], where it can
 * be tested. This class only has to make a sound, and the one judgement it does make is what to do
 * when a cue arrives while another is still playing.
 */
class Voice(context: Context) {

    private var ready = false

    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (!ready) Log.w(TAG, "No speech engine; coaching will be silent")
    }

    fun say(cue: Cue) {
        if (!ready) return

        val line = cue.spoken()
        val language = tts.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.UK)
        }

        // A correction interrupts whatever is playing; a count waits its turn and is dropped if
        // something is already speaking. That ordering is the point: "three" cutting off "knees
        // out" is exactly backwards, and by the time a queued count is heard the lifter is two
        // reps further on and it is simply wrong.
        val mode = if (cue is Cue.Correction) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (cue is Cue.Count && tts.isSpeaking) return

        tts.speak(line, mode, null, line)
    }

    fun release() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    private companion object {
        const val TAG = "SpotterVoice"
    }
}

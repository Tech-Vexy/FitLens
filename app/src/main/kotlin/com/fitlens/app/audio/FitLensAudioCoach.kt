package com.fitlens.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * FitLensAudioCoach provides real-time voice coaching cues during workouts.
 * Essential for hands-free exercise guidance when the user cannot view the screen.
 */
class FitLensAudioCoach(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "FitLensAudioCoach"
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    private var isMuted = false
    private var lastSpokenCue: String = ""
    private var lastSpokenTimestamp: Long = 0

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS Language US is not supported or missing data.")
            } else {
                isInitialized = true
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
                Log.i(TAG, "FitLens Audio Coach initialized.")
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    /**
     * Toggles audio coaching on or off. When muted, all TTS output is suppressed
     * including rep counts and form corrections.
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            tts?.stop()
            Log.i(TAG, "Audio coach muted.")
        } else {
            Log.i(TAG, "Audio coach unmuted.")
        }
    }

    fun isMuted(): Boolean = isMuted

    /**
     * Speaks real-time form correction cues with throttling to avoid speech collisions.
     */
    fun speakCorrection(cue: String, isUrgent: Boolean = false) {
        if (!isInitialized || isMuted || cue.isBlank()) return

        val now = System.currentTimeMillis()
        // Throttle repeated non-urgent cues within 3.5 seconds
        if (!isUrgent && cue == lastSpokenCue && (now - lastSpokenTimestamp) < 3500) {
            return
        }

        lastSpokenCue = cue
        lastSpokenTimestamp = now

        val queueMode = if (isUrgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(cue, queueMode, null, "cue_${System.currentTimeMillis()}")
    }

    /**
     * Speaks rep count cleanly.
     */
    fun speakRepCount(rep: Int) {
        if (!isInitialized || isMuted) return
        tts?.speak("$rep", TextToSpeech.QUEUE_FLUSH, null, "rep_$rep")
    }

    /**
     * Announces phase transitions (e.g. Set Complete, Rest Interval).
     */
    fun announcePhase(announcement: String) {
        speakCorrection(announcement, isUrgent = true)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

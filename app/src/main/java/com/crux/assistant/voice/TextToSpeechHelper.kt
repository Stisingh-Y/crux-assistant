package com.crux.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

/**
 * TextToSpeechHelper.kt
 *
 * Thin wrapper around Android's built-in TextToSpeech engine. CRUX speaks every response
 * through this — greetings, answers, confirmation questions, and cancellations.
 *
 * Two user-facing preferences live here:
 *  - speechRate: applied from AppSettings.speechRate() (Normal/Slow toggle on the main screen).
 *  - preferMaleVoice: if true, picks a male-sounding voice from whatever the phone's TTS
 *    engine has installed. Android doesn't guarantee a "male"/"female" field on every voice,
 *    so this is a best-effort name match (most engines, including Google's, name their
 *    voices with "male"/"female" in them) — it silently falls back to the engine default
 *    if no match is found rather than failing.
 */
class TextToSpeechHelper(
    context: Context,
    private val initialSpeechRate: Float = 1.0f,
    private val preferMaleVoice: Boolean = false,
    private val onReady: () -> Unit = {}
) {

    private var isReady = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts.language = Locale.getDefault()
            tts.setSpeechRate(initialSpeechRate)
            if (preferMaleVoice) applyMaleVoiceIfAvailable()
            onReady()
        }
    }

    private fun applyMaleVoiceIfAvailable() {
        val voices: Set<Voice> = tts.voices ?: return
        val deviceLanguage = Locale.getDefault().language

        val candidate = voices.firstOrNull {
            it.locale.language == deviceLanguage &&
                it.name.contains("male", ignoreCase = true) &&
                !it.name.contains("female", ignoreCase = true)
        } ?: voices.firstOrNull {
            it.name.contains("male", ignoreCase = true) &&
                !it.name.contains("female", ignoreCase = true)
        }

        candidate?.let { tts.voice = it }
        // No match found -> silently keep the engine's default voice.
    }

    /** Lets the UI change speed live when the user flips the Normal/Slow switch. */
    fun setSpeechRate(rate: Float) {
        if (isReady) tts.setSpeechRate(rate)
    }

    /**
     * Speaks [text] aloud. [onDone] fires after speech finishes, which callers use to know
     * when it's safe to start listening again (e.g. right after asking a yes/no question).
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isReady) return

        val utteranceId = UUID.randomUUID().toString()
        if (onDone != null) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) = onDone()
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun destroy() {
        tts.stop()
        tts.shutdown()
    }
}

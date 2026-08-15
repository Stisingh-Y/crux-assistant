package com.crux.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * TextToSpeechHelper.kt
 *
 * Thin wrapper around Android's built-in TextToSpeech engine. CRUX speaks every response
 * through this — greetings, answers, confirmation questions, and cancellations — so the
 * user never has to read a screen.
 */
class TextToSpeechHelper(context: Context, private val onReady: () -> Unit = {}) {

    private var isReady = false
    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            onReady()
        }
    }

    init {
        tts.language = Locale.getDefault()
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

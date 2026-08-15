package com.crux.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * SpeechToTextHelper.kt
 *
 * Thin wrapper around Android's built-in SpeechRecognizer. Nothing here is CRUX-specific —
 * it just turns "start listening" into a callback with the best-guess recognized text
 * (or an error). Used for BOTH manual mic-tap commands and the follow-up command that's
 * captured right after the "Hey CRUX" wake word fires.
 *
 * Kept deliberately dumb: this class does NOT decide what a phrase means. That's
 * CommandProcessor's job. This class only turns audio into text.
 */
class SpeechToTextHelper(context: Context) {

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    /**
     * Starts listening for a single utterance.
     * @param onResult called with the top recognized phrase once the user stops talking.
     * @param onError called with a human-readable reason if recognition fails or times out.
     */
    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val topMatch = matches?.firstOrNull()
                if (topMatch.isNullOrBlank()) {
                    onError("I didn't catch that.")
                } else {
                    onResult(topMatch)
                }
            }

            override fun onError(error: Int) {
                onError("Speech recognition error (code $error).")
            }

            // Required overrides we don't need to act on.
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun stopListening() = recognizer.stopListening()

    /** Call from onDestroy to release the recognizer. */
    fun destroy() = recognizer.destroy()
}

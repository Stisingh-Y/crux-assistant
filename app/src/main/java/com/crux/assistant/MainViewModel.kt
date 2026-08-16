package com.crux.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crux.assistant.command.AssistantEngine
import com.crux.assistant.data.AppSettings
import com.crux.assistant.data.ContactStore
import com.crux.assistant.voice.SpeechToTextHelper
import kotlinx.coroutines.launch

/**
 * MainViewModel.kt
 *
 * Owns the manual mic-tap flow for the main screen: SpeechToTextHelper captures whatever
 * the user says after tapping the mic button, and AssistantEngine (the same class the
 * wake-word service uses) does the rest — parsing, confirmation, execution, speaking.
 *
 * Also tracks whether wake-word mode is on, so MainActivity knows whether to start/stop
 * WakeWordService.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val contactStore = ContactStore(application)
    private val speechToText = SpeechToTextHelper(application)

    val engine = AssistantEngine(
        context = application,
        contactStore = contactStore,
        speechRate = AppSettings.speechRate(application),
        preferMaleVoice = true,
        pitch = AppSettings.VOICE_PITCH,
        onNeedsFollowUpListening = { startListening() }
    )

    var onStatusUpdate: ((String) -> Unit)? = null

    /** Called from MainActivity when the user flips the Normal/Slow speech switch. */
    fun updateSpeechRate(slow: Boolean) {
        AppSettings.setSlowSpeech(getApplication(), slow)
        engine.setSpeechRate(AppSettings.speechRate(getApplication()))
    }

    fun startListening() {
        speechToText.startListening(
            onResult = { text ->
                onStatusUpdate?.invoke(text)
                viewModelScope.launch { engine.handleRecognizedSpeech(text) }
            },
            onError = { message -> onStatusUpdate?.invoke(message) }
        )
    }

    override fun onCleared() {
        speechToText.destroy()
        engine.destroy()
        super.onCleared()
    }
}

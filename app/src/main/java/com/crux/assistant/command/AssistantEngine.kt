package com.crux.assistant.command

import android.content.Context
import com.crux.assistant.R
import com.crux.assistant.data.ContactStore
import com.crux.assistant.voice.TextToSpeechHelper

/**
 * AssistantEngine.kt
 *
 * The single place that turns "here's some recognized text" into "here's what CRUX says
 * and does about it" — shared by BOTH the manual mic-tap flow (MainViewModel) and the
 * wake-word flow (WakeWordService), so the confirmation logic and command handling exist
 * in exactly one place instead of being duplicated per entry point.
 *
 * This class owns the ConfirmationManager, so it inherently knows whether the next thing
 * it's given should be parsed as a brand-new command or as a yes/no answer to a pending
 * sensitive action.
 */
class AssistantEngine(
    context: Context,
    private val contactStore: ContactStore,
    private val onNeedsFollowUpListening: () -> Unit // "start listening again" callback
) {
    private val appContext = context.applicationContext
    private val commandProcessor = CommandProcessor(contactStore)
    private val actionExecutor = ActionExecutor(appContext)
    private val confirmationManager = ConfirmationManager()
    private val tts = TextToSpeechHelper(appContext)

    /** Entry point: call with whatever SpeechToTextHelper just recognized. */
    suspend fun handleRecognizedSpeech(text: String) {
        if (confirmationManager.isAwaitingConfirmation) {
            handleConfirmationAnswer(text)
            return
        }

        when (val command = commandProcessor.process(text)) {
            is Command.Sensitive -> askForConfirmation(command)
            is Command.ContactNotFound ->
                speak(appContext.getString(R.string.tts_contact_not_found, command.name))
            Command.Unknown ->
                speak(appContext.getString(R.string.tts_unrecognized))
            else -> {
                val result = actionExecutor.execute(command)
                speak(result)
            }
        }
    }

    private fun askForConfirmation(command: Command.Sensitive) {
        confirmationManager.beginConfirmation(command)
        val question = when (command) {
            is Command.Sensitive.SendSms ->
                appContext.getString(R.string.tts_confirm_send_sms, command.message, command.contactName)
        }
        // After asking, CRUX needs to hear the answer -> trigger another listening pass.
        speak(question, thenListenAgain = true)
    }

    private fun handleConfirmationAnswer(text: String) {
        when (val resolution = confirmationManager.resolve(text)) {
            is ConfirmationManager.Resolution.Confirmed -> {
                val result = actionExecutor.execute(resolution.command)
                speak(result)
            }
            ConfirmationManager.Resolution.Cancelled ->
                speak(appContext.getString(R.string.tts_cancelled))
            ConfirmationManager.Resolution.Unclear ->
                speak(appContext.getString(R.string.tts_didnt_catch_confirmation))
        }
    }

    private fun speak(text: String, thenListenAgain: Boolean = false) {
        if (text.isBlank()) return
        tts.speak(text) {
            if (thenListenAgain) onNeedsFollowUpListening()
        }
    }

    fun destroy() = tts.destroy()
}

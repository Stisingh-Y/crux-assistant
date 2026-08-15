package com.crux.assistant.command

/**
 * ConfirmationManager.kt
 *
 * Generic "is CRUX currently waiting for a yes/no answer?" state machine, used for any
 * Command.Sensitive action (starting with SendSms, but written to be reused for whatever
 * sensitive commands get added later — no SMS-specific logic lives in this file).
 *
 * How it's used (see MainViewModel.kt for the full wiring):
 *   1. CommandProcessor recognizes a Command.Sensitive.
 *   2. MainViewModel calls beginConfirmation(command) instead of running it, and speaks
 *      the confirmation question via TextToSpeechHelper.
 *   3. The NEXT thing SpeechToTextHelper hears is passed into resolve(text) instead of
 *      being parsed as a new command.
 *   4. resolve() returns one of: Confirmed (run it), Cancelled (say "Cancelled"), or
 *      Unclear (treat as a cancel too — CRUX must never default to proceeding).
 *
 * There is deliberately no "assume yes" path anywhere in this class.
 */
class ConfirmationManager {

    private var pending: Command.Sensitive? = null

    val isAwaitingConfirmation: Boolean
        get() = pending != null

    fun beginConfirmation(command: Command.Sensitive) {
        pending = command
    }

    /** Call this with the next thing the user says once a confirmation is pending. */
    fun resolve(spokenText: String): Resolution {
        val command = pending ?: return Resolution.Unclear
        pending = null // clear state regardless of the answer

        val normalized = spokenText.trim().lowercase()
        return when {
            AFFIRMATIVE_PHRASES.any { normalized.contains(it) } -> Resolution.Confirmed(command)
            NEGATIVE_PHRASES.any { normalized.contains(it) } -> Resolution.Cancelled
            else -> Resolution.Unclear // mishear or off-topic reply -> treated as cancel, never as "yes"
        }
    }

    fun cancel() {
        pending = null
    }

    sealed class Resolution {
        data class Confirmed(val command: Command.Sensitive) : Resolution()
        data object Cancelled : Resolution()
        data object Unclear : Resolution()
    }

    private companion object {
        val AFFIRMATIVE_PHRASES = listOf("yes", "yeah", "yep", "confirm", "do it", "go ahead", "sure")
        val NEGATIVE_PHRASES = listOf("no", "nope", "cancel", "don't", "stop")
    }
}

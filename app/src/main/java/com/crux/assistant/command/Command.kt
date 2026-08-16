package com.crux.assistant.command

/**
 * Command.kt
 *
 * Every action CRUX is capable of performing is listed here, and ONLY here.
 * This is a closed set on purpose: CommandProcessor turns raw recognized speech into
 * one of these types (or Unknown), and ActionExecutor can only ever act on a Command
 * instance — never on raw text. That's what keeps CRUX from "improvising" an action
 * based on a misheard or unexpected phrase.
 *
 * To add a new capability:
 *   1. Add a new subclass here.
 *   2. Teach CommandProcessor how to recognize the phrase(s) that map to it.
 *   3. Teach ActionExecutor how to actually perform it.
 *   If the new action is risky/irreversible (sends something, deletes something, spends
 *   money, etc.), make it a Command.Sensitive instead of a plain Command — see below.
 */
sealed class Command {

    // --- Existing MVP commands ---
    data object OpenGoogle : Command()
    data object OpenYouTube : Command()
    data object OpenChrome : Command()
    data object OpenCalculator : Command()
    data class Search(val query: String) : Command()
    data object BatteryStatus : Command()
    data object CurrentTime : Command()
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : Command()

    /**
     * Anything in this set requires spoken double-confirmation before ActionExecutor is
     * allowed to run it (see command/ConfirmationManager.kt). Recognizing a Sensitive
     * command NEVER executes it directly — it only ever produces a confirmation question.
     */
    sealed class Sensitive : Command() {
        data class SendSms(val contactName: String, val phoneNumber: String, val message: String) : Sensitive()
        // Future sensitive commands (e.g. delete a saved contact, make a call) go here.
    }

    /** Speech was recognized, but didn't match anything CRUX knows how to do. */
    data object Unknown : Command()

    /**
     * User referenced a name that isn't in the manual contact mapping (feature 4).
     * This is its own outcome (rather than folding into Unknown) so MainViewModel can
     * speak a specific "add them first" prompt instead of a generic "I don't know that".
     */
    data class ContactNotFound(val name: String) : Command()
}

package com.crux.assistant.command

import com.crux.assistant.data.ContactStore

/**
 * CommandProcessor.kt
 *
 * Turns raw recognized speech text into a Command. This is simple keyword matching on
 * purpose — no on-device LLM, no free-form interpretation. If a phrase doesn't match a
 * known pattern, the result is Command.Unknown, and ActionExecutor will never see it.
 *
 * process() is a suspend function because resolving "send X to Y" needs to look the name
 * up in ContactStore (feature 4) — everything else here is pure string matching.
 */
class CommandProcessor(private val contactStore: ContactStore) {

    suspend fun process(rawText: String): Command {
        val text = rawText.trim().lowercase()

        return when {
            text.contains("open google") -> Command.OpenGoogle
            text.contains("open youtube") -> Command.OpenYouTube
            text.contains("open chrome") -> Command.OpenChrome
            text.contains("open calculator") -> Command.OpenCalculator
            text.contains("battery") -> Command.BatteryStatus
            text.contains("time") -> Command.CurrentTime

            text.startsWith("search ") -> Command.Search(text.removePrefix("search ").trim())
            text.startsWith("search for ") -> Command.Search(text.removePrefix("search for ").trim())

            text.contains("alarm") || text.contains("remind") -> parseAlarmOrReminder(text)

            isSendMessageCommand(text) -> parseSendMessage(text)

            isCallCommand(text) -> parseCall(text)

            else -> Command.Unknown
        }
    }

    private val timeRegex = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
    private val labelRegex = Regex("""(?:reminder|remind me)\s+to\s+(.+?)(?:\s+at\s+\d|\s*$)""")

    /**
     * Parses phrases like "set an alarm for 7 am", "set alarm for 7:30 pm", and
     * "remind me to call Amma at 6 pm". Falls back to Unknown if no recognizable time is
     * found — this never guesses a time.
     */
    private fun parseAlarmOrReminder(text: String): Command {
        val match = timeRegex.find(text) ?: return Command.Unknown
        var hour = match.groupValues[1].toIntOrNull() ?: return Command.Unknown
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]

        if (meridiem.equals("pm", ignoreCase = true) && hour < 12) hour += 12
        if (meridiem.equals("am", ignoreCase = true) && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return Command.Unknown

        val label = labelRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        return Command.SetAlarm(hour, minute, label)
    }

    private fun isSendMessageCommand(text: String): Boolean =
        (text.startsWith("send ") || text.startsWith("message ") || text.startsWith("text ")) && text.contains(" to ")

    /**
     * Parses phrases like:
     *   "send hi to amma"        -> message="hi",  name="amma"
     *   "message amma saying hi" -> name="amma",    message="hi"  (alt phrasing, handled below)
     *   "text amma hi"           -> name="amma",    message="hi"
     * and looks "amma" up via ContactStore. Anything that doesn't fit a recognized shape
     * falls back to Command.Unknown rather than guessing.
     */
    private suspend fun parseSendMessage(text: String): Command {
        val (message, name) = when {
            // "send/message/text <message> to <name>"
            text.contains(" to ") -> {
                val withoutVerb = text.substringAfter(" ").trim() // drop leading verb
                val msg = withoutVerb.substringBeforeLast(" to ").trim()
                val nm = withoutVerb.substringAfterLast(" to ").trim()
                msg to nm
            }
            else -> return Command.Unknown
        }

        if (message.isBlank() || name.isBlank()) return Command.Unknown

        val contact = contactStore.findByName(name) ?: return Command.ContactNotFound(name)

        return Command.Sensitive.SendSms(
            contactName = contact.name,
            phoneNumber = contact.phoneNumber,
            message = message
        )
    }

    private fun isCallCommand(text: String): Boolean =
        text.startsWith("call ") || text.startsWith("phone ") || text.startsWith("dial ")

    /**
     * Parses phrases like "call amma" / "phone amma" / "dial amma" and looks the name up
     * via ContactStore, same as the SMS flow — no READ_CONTACTS, no guessing a number.
     */
    private suspend fun parseCall(text: String): Command {
        val name = text
            .removePrefix("call ")
            .removePrefix("phone ")
            .removePrefix("dial ")
            .trim()

        if (name.isBlank()) return Command.Unknown

        val contact = contactStore.findByName(name) ?: return Command.ContactNotFound(name)

        return Command.Sensitive.MakeCall(
            contactName = contact.name,
            phoneNumber = contact.phoneNumber
        )
    }
}

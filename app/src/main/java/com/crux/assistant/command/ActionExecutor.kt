package com.crux.assistant.command

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import java.util.Date

/**
 * ActionExecutor.kt
 *
 * Actually performs a Command. Only ever called with a concrete Command instance — never
 * with raw text — and, for anything under Command.Sensitive, only ever called AFTER
 * ConfirmationManager has resolved a spoken "yes" (see MainViewModel.kt).
 *
 * Every action here uses standard, public Android Intents/APIs. No accessibility
 * service, no reflection, no device-admin, nothing undocumented.
 *
 * Returns a String: what CRUX should say back to the user as a result of the action.
 */
class ActionExecutor(private val context: Context) {

    /** Spoken before every command result, e.g. "Okay boss, opening Google for you." */
    private val ack = "Okay boss, "

    fun execute(command: Command): String = when (command) {
        Command.OpenGoogle -> openUrl("https://www.google.com", "Google")
        Command.OpenChrome -> openChrome()
        Command.OpenYouTube -> openYouTube()
        Command.OpenCalculator -> openCalculator()
        is Command.Search -> openUrl(
            "https://www.google.com/search?q=${Uri.encode(command.query)}",
            "a search for ${command.query}"
        )
        Command.BatteryStatus -> readBatteryStatus()
        Command.CurrentTime -> readCurrentTime()
        is Command.SetAlarm -> setAlarm(command)

        is Command.Sensitive.SendSms -> sendSms(command)
        is Command.Sensitive.MakeCall -> makeCall(command)

        is Command.ContactNotFound -> "" // MainViewModel speaks this directly; nothing to execute
        Command.Unknown -> ""            // MainViewModel speaks the "don't know how" line
    }

    // --- MVP actions (unchanged behavior, just centralized here) ---

    private fun openUrl(url: String, spokenLabel: String): String {
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "${ack}let's open $spokenLabel."
    }

    private fun openChrome(): String {
        val chromeIntent = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
        return if (chromeIntent != null) {
            launch(chromeIntent)
            "${ack}opening Chrome."
        } else {
            openUrl("https://www.google.com", "Google") // fallback to default browser
        }
    }

    private fun openYouTube(): String {
        val ytIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        return if (ytIntent != null) {
            launch(ytIntent)
            "${ack}opening YouTube."
        } else {
            openUrl("https://www.youtube.com", "YouTube") // fallback to website
        }
    }

    private fun openCalculator(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
        return try {
            launch(intent)
            "${ack}opening the calculator."
        } catch (e: ActivityNotFoundException) {
            "I couldn't find a calculator app on this phone."
        }
    }

    private fun readBatteryStatus(): String {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "${ack}your battery is at $level percent."
    }

    private fun readCurrentTime(): String {
        val formatted = DateFormat.getTimeFormat(context).format(Date())
        return "${ack}it's $formatted."
    }

    // --- New: SMS via ACTION_SENDTO (feature 5) ---

    /**
     * Opens the phone's default SMS app with the recipient and message pre-filled, using
     * an smsto: URI. This deliberately does NOT call SmsManager.sendTextMessage() and does
     * NOT require the SEND_SMS permission — the user still has to tap "send" themselves in
     * their SMS app. That manual tap is intentional: it's a safety layer on top of the
     * voice confirmation already given, in case speech recognition misheard the name,
     * number, or the "yes".
     */
    private fun sendSms(command: Command.Sensitive.SendSms): String {
        val uri = Uri.parse("smsto:${command.phoneNumber}")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", command.message)
        }
        return try {
            launch(intent)
            "${ack}message ready to send to ${command.contactName}. Just tap send."
        } catch (e: ActivityNotFoundException) {
            "I couldn't find a messaging app to send that with."
        }
    }

    /**
     * Opens the Phone app's dialer with the number pre-filled, using ACTION_DIAL — this
     * deliberately does NOT use ACTION_CALL (which requires the sensitive CALL_PHONE
     * permission and would place the call with no further action from the user). CRUX
     * fills in the number; the user still taps the call button themselves, same
     * manual-final-step safety pattern as SMS.
     */
    private fun makeCall(command: Command.Sensitive.MakeCall): String {
        val uri = Uri.parse("tel:${command.phoneNumber}")
        val intent = Intent(Intent.ACTION_DIAL, uri)
        return try {
            launch(intent)
            "${ack}dialing ${command.contactName} — just tap call."
        } catch (e: ActivityNotFoundException) {
            "I couldn't find a phone app on this device."
        } catch (e: SecurityException) {
            "I don't have permission to open the dialer on this phone."
        }
    }

    // --- New: Alarm / Reminder via standard AlarmClock intent ---

    /**
     * Opens the phone's default Clock app with the time (and, if given, a label) pre-filled,
     * using android.provider.AlarmClock.ACTION_SET_ALARM. EXTRA_SKIP_UI is deliberately left
     * false — same reasoning as the SMS flow: CRUX fills it in, but the user still taps
     * "Save" in the Clock app themselves, so a misheard time can't silently become a real
     * alarm.
     */
    private fun setAlarm(command: Command.SetAlarm): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, command.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
            command.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return try {
            launch(intent)
            "${ack}opening your clock app to set that alarm — just confirm it there."
        } catch (e: ActivityNotFoundException) {
            "I couldn't find a clock app on this phone."
        } catch (e: SecurityException) {
            "I don't have permission to set an alarm on this phone."
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

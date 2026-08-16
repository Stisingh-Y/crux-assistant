package com.crux.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.crux.assistant.MainActivity
import com.crux.assistant.R
import com.crux.assistant.command.AssistantEngine
import com.crux.assistant.data.AppSettings
import com.crux.assistant.data.ContactStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * WakeWordService.kt
 *
 * Foreground service that keeps Porcupine ("Hey CRUX") running in the background, per
 * Android's requirement that any ongoing microphone use while the app isn't in the
 * foreground must be a foreground service with a persistent, visible notification. That
 * notification is intentionally NOT hidden or minimized — it's how the user knows CRUX
 * might be listening.
 *
 * On detecting the wake word, this service:
 *   1. Speaks a time-of-day greeting ("Good morning. How can I help?") via AssistantEngine.
 *   2. Starts a normal SpeechToTextHelper capture for the actual command.
 *   3. Hands the recognized text to the same shared AssistantEngine the manual mic-tap
 *      flow uses, so confirmation logic isn't duplicated.
 *
 * Only runs at all if the user has turned the wake-word toggle ON in the main screen —
 * see MainViewModel for where this service is started/stopped.
 */
class WakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wakeWordHelper: WakeWordHelper
    private lateinit var speechToText: SpeechToTextHelper
    private lateinit var assistantEngine: AssistantEngine

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        speechToText = SpeechToTextHelper(this)
        assistantEngine = AssistantEngine(
            context = this,
            contactStore = ContactStore(this),
            speechRate = AppSettings.speechRate(this),
            preferMaleVoice = true,
            pitch = AppSettings.VOICE_PITCH,
            onNeedsFollowUpListening = { listenForFollowUp() }
        )

        wakeWordHelper = WakeWordHelper(
            context = this,
            onWakeWordDetected = { onWakeWordHeard() },
            onError = { /* Could surface via a notification update if desired. */ }
        )
        wakeWordHelper.start()
    }

    private fun onWakeWordHeard() {
        // Pause wake-word listening while greeting + capturing the actual command, to avoid
        // Porcupine and SpeechRecognizer/TTS fighting over the microphone at once.
        wakeWordHelper.stop()
        // Speaks "Good morning/afternoon/evening. How can I help?" then, once that finishes,
        // AssistantEngine's onNeedsFollowUpListening callback fires listenForFollowUp() below.
        assistantEngine.speakWakeGreeting()
    }

    private fun listenForFollowUp() {
        // Used when AssistantEngine just asked a yes/no confirmation question.
        speechToText.startListening(
            onResult = { text ->
                serviceScope.launch {
                    assistantEngine.handleRecognizedSpeech(text)
                    wakeWordHelper.start()
                }
            },
            onError = { wakeWordHelper.start() }
        )
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wakeword_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wakeword_notification_title))
            .setContentText(getString(R.string.wakeword_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        wakeWordHelper.stop()
        speechToText.destroy()
        assistantEngine.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "crux_wakeword_channel"
        private const val NOTIFICATION_ID = 1001
    }
}

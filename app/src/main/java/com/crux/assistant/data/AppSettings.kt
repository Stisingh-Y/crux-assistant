package com.crux.assistant.data

import android.content.Context

/**
 * AppSettings.kt
 *
 * Small helper around SharedPreferences for the two on/off settings CRUX exposes on the
 * main screen: wake-word mode (already handled inline in MainActivity) and speech speed.
 * Kept in one file so both the manual-tap flow (MainViewModel) and the wake-word service
 * (WakeWordService) read the same values when building their AssistantEngine.
 */
object AppSettings {
    private const val PREFS_NAME = "crux_prefs"
    private const val KEY_SLOW_SPEECH = "slow_speech_enabled"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"

    private const val NORMAL_RATE = 1.0f
    private const val SLOW_RATE = 0.75f

    /**
     * Lowered pitch for a deeper, harder-toned voice (requested style: less "chirpy"
     * assistant, more deep/robotic). 1.0 would be the engine's natural pitch.
     */
    const val VOICE_PITCH = 0.78f

    fun isWakeWordEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            // Defaults to true: "Hey CRUX" listening is on unless the user explicitly turns it off.
            .getBoolean(KEY_WAKE_WORD_ENABLED, true)

    fun setWakeWordEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WAKE_WORD_ENABLED, enabled)
            .apply()
    }

    fun isSlowSpeech(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SLOW_SPEECH, false)

    fun setSlowSpeech(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SLOW_SPEECH, enabled)
            .apply()
    }

    /** Speech rate to hand to TextToSpeech.setSpeechRate(). */
    fun speechRate(context: Context): Float =
        if (isSlowSpeech(context)) SLOW_RATE else NORMAL_RATE
}

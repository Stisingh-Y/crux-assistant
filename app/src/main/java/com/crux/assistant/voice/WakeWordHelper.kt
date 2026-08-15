package com.crux.assistant.voice

import android.content.Context
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineException

/**
 * WakeWordHelper.kt
 *
 * Wraps Picovoice Porcupine, a lightweight, fully ON-DEVICE wake-word engine. It listens
 * ONLY for the trigger phrase "Hey CRUX" — it does not do general speech recognition and
 * does not send any audio anywhere, which is what keeps it cheap enough to run constantly
 * in the background.
 *
 * SETUP REQUIRED BEFORE THIS WILL RUN (both free, one-time, done on Picovoice's console —
 * not something that can be generated from code):
 *   1. Create a free account at https://console.picovoice.ai and copy your AccessKey into
 *      ACCESS_KEY below (or better, load it from local.properties / BuildConfig so it
 *      isn't committed to source control).
 *   2. On the same console, train a custom wake word for the phrase "Hey CRUX" (Porcupine
 *      calls these ".ppn" keyword files, one per platform). Download the Android .ppn file
 *      and place it at app/src/main/assets/hey_crux_android.ppn.
 * Until both of those are done, WakeWordService will fail to start and the app should
 * fall back to manual mic-tap only (see MainViewModel's wake-word toggle handling).
 */
class WakeWordHelper(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var manager: PorcupineManager? = null

    // TODO: replace with your own Picovoice AccessKey (see class doc above).
    private val accessKey = "YOUR_PICOVOICE_ACCESS_KEY"
    private val keywordAssetPath = "hey_crux_android.ppn"

    fun start() {
        try {
            manager = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setKeywordPath(keywordAssetPath) // resolved relative to assets/
                .build(context, object : PorcupineManagerCallback {
                    override fun invoke(keywordIndex: Int) {
                        onWakeWordDetected()
                    }
                })
            manager?.start()
        } catch (e: PorcupineException) {
            onError("Wake word engine couldn't start: ${e.message}")
        }
    }

    fun stop() {
        try {
            manager?.stop()
            manager?.delete()
        } catch (e: PorcupineException) {
            // Safe to ignore on shutdown.
        }
        manager = null
    }
}

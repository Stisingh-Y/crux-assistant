package com.crux.assistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.crux.assistant.data.AppSettings

/**
 * BootReceiver.kt
 *
 * Android stops every app's running services when the phone restarts — so without this,
 * "Hey CRUX" would silently stop working after every reboot until the user reopened the
 * app. This receiver listens for the system's BOOT_COMPLETED broadcast and, if the user
 * had wake-word mode on (AppSettings.isWakeWordEnabled), restarts WakeWordService right
 * away — no need to open the app manually after a restart.
 *
 * Registered in AndroidManifest.xml with a BOOT_COMPLETED intent-filter. Requires the
 * RECEIVE_BOOT_COMPLETED permission (a standard, non-sensitive permission — it only tells
 * the app the phone finished starting up, no access to personal data).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (AppSettings.isWakeWordEnabled(context)) {
            val serviceIntent = Intent(context, WakeWordService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}

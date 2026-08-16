package com.crux.assistant

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.crux.assistant.data.AppSettings
import com.crux.assistant.databinding.ActivityMainBinding
import com.crux.assistant.ui.ContactMappingActivity
import com.crux.assistant.voice.WakeWordService

/**
 * MainActivity.kt
 *
 * The one screen of CRUX: a status card, a mic button with a pulsing ring while CRUX is
 * listening, the "always listen for Hey CRUX" switch, a "speak slowly" switch, and a
 * button into the contacts screen.
 *
 * Permission requests happen here, one at a time, each preceded by an in-app explanation
 * (see strings.xml perm_*_explainer), matching the MVP's existing pattern.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences
    private var pulseAnimator: AnimatorSet? = null

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startListeningWithPulse() }

    private val wakeWordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) startWakeWordService() else binding.wakeWordToggle.isChecked = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        prefs = getSharedPreferences("crux_prefs", MODE_PRIVATE)

        viewModel.onStatusUpdate = { text ->
            binding.statusText.text = text
            stopPulse()
        }

        binding.micButton.setOnClickListener { onMicTapped() }

        binding.manageContactsButton.setOnClickListener {
            startActivity(Intent(this, ContactMappingActivity::class.java))
        }

        setupWakeWordToggle()
        setupSpeedToggle()
    }

    private fun setupWakeWordToggle() {
        binding.wakeWordToggle.isChecked = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        binding.wakeWordToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, isChecked).apply()
            if (isChecked) requestWakeWordPermissionsThenStart() else stopWakeWordService()
        }
        if (binding.wakeWordToggle.isChecked) requestWakeWordPermissionsThenStart()
    }

    private fun setupSpeedToggle() {
        binding.speedToggle.isChecked = AppSettings.isSlowSpeech(this)
        binding.speedToggle.setOnCheckedChangeListener { _, isChecked ->
            viewModel.updateSpeechRate(isChecked)
        }
    }

    private fun onMicTapped() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListeningWithPulse()
        } else {
            binding.statusText.text = getString(R.string.perm_mic_explainer)
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListeningWithPulse() {
        binding.statusText.text = getString(R.string.listening_status)
        startPulse()
        viewModel.startListening()
    }

    /** Repeating scale + fade ring animation shown behind the mic button while listening. */
    private fun startPulse() {
        stopPulse()
        val ring = binding.pulseRing
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.alpha = 0.8f

        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.6f).apply {
            duration = 900; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.6f).apply {
            duration = 900; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        val fade = ObjectAnimator.ofFloat(ring, "alpha", 0.8f, 0f).apply {
            duration = 900; repeatCount = ObjectAnimator.INFINITE; interpolator = LinearInterpolator()
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, fade)
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.pulseRing.alpha = 0f
    }

    private fun requestWakeWordPermissionsThenStart() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startWakeWordService()
        } else {
            binding.statusText.text = getString(R.string.perm_wakeword_explainer)
            wakeWordPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopWakeWordService() {
        stopService(Intent(this, WakeWordService::class.java))
    }

    override fun onDestroy() {
        stopPulse()
        super.onDestroy()
    }

    private companion object {
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    }
}

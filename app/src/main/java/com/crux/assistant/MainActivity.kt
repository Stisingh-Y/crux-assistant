package com.crux.assistant

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.crux.assistant.command.Command
import com.crux.assistant.databinding.ActivityMainBinding
import com.crux.assistant.ui.ContactMappingActivity
import com.crux.assistant.voice.WakeWordService

/**
 * MainActivity.kt
 *
 * The one screen of CRUX: a status line, a mic button (manual fallback/toggle, always
 * works), the "always listen for Hey CRUX" switch, and a button to the contacts screen.
 *
 * Permission requests happen here, one at a time, each preceded by an in-app explanation
 * (see strings.xml perm_*_explainer), matching the MVP's existing pattern.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var prefs: SharedPreferences

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startListening() }

    private val wakeWordPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> if (results.values.all { it }) startWakeWordService() else binding.wakeWordToggle.isChecked = false }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        prefs = getSharedPreferences("crux_prefs", MODE_PRIVATE)

        viewModel.onStatusUpdate = { text -> binding.statusText.text = text }

        binding.micButton.setOnClickListener { onMicTapped() }

        binding.manageContactsButton.setOnClickListener {
            startActivity(Intent(this, ContactMappingActivity::class.java))
        }

        binding.wakeWordToggle.isChecked = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        binding.wakeWordToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, isChecked).apply()
            if (isChecked) requestWakeWordPermissionsThenStart() else stopWakeWordService()
        }

        // Resume wake-word service across app restarts if the user left it on.
        if (binding.wakeWordToggle.isChecked) requestWakeWordPermissionsThenStart()
    }

    private fun onMicTapped() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.startListening()
        } else {
            // In-app explanation shown via the status text before the system dialog appears.
            binding.statusText.text = getString(R.string.perm_mic_explainer)
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
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

    private companion object {
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    }
}

package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.fabiantorrestech.visualtimerplus.R

class EInkSettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var displayModeValue: TextView
    private lateinit var elapsedModeValue: TextView
    private lateinit var keepAwakeValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_settings)

        prefs = getSharedPreferences("visual_timer_prefs", MODE_PRIVATE)

        displayModeValue = findViewById(R.id.displayModeValue)
        elapsedModeValue = findViewById(R.id.elapsedModeValue)
        keepAwakeValue = findViewById(R.id.keepAwakeValue)

        updateDisplayValues()

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<RelativeLayout>(R.id.displayModeRow).setOnClickListener {
            val current = prefs.getString(PREF_DISPLAY_MODE, MODE_BARS) ?: MODE_BARS
            prefs.edit().putString(PREF_DISPLAY_MODE, if (current == MODE_BARS) MODE_RADIAL else MODE_BARS).apply()
            updateDisplayValues()
        }

        findViewById<RelativeLayout>(R.id.elapsedModeRow).setOnClickListener {
            val current = prefs.getString(PREF_ELAPSED_MODE, MODE_BLINK) ?: MODE_BLINK
            prefs.edit().putString(PREF_ELAPSED_MODE, if (current == MODE_BLINK) MODE_EXCLAMATION else MODE_BLINK).apply()
            updateDisplayValues()
        }

        findViewById<RelativeLayout>(R.id.keepAwakeRow).setOnClickListener {
            val current = prefs.getBoolean(PREF_KEEP_AWAKE, false)
            prefs.edit().putBoolean(PREF_KEEP_AWAKE, !current).apply()
            applyKeepAwake(!current)
            updateDisplayValues()
        }

        applyKeepAwake(prefs.getBoolean(PREF_KEEP_AWAKE, false))
    }

    private fun applyKeepAwake(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateDisplayValues() {
        displayModeValue.text = if (prefs.getString(PREF_DISPLAY_MODE, MODE_BARS) == MODE_BARS) "BARS" else "RADIAL"
        elapsedModeValue.text = if (prefs.getString(PREF_ELAPSED_MODE, MODE_BLINK) == MODE_BLINK) "BLINK" else "EXCLAMATION"
        keepAwakeValue.text = if (prefs.getBoolean(PREF_KEEP_AWAKE, false)) "ON" else "OFF"
    }

    companion object {
        const val PREF_DISPLAY_MODE = "eink_display_mode"
        const val PREF_ELAPSED_MODE = "eink_elapsed_mode"
        const val PREF_KEEP_AWAKE = "eink_keep_screen_awake"
        const val MODE_BARS = "bars"
        const val MODE_RADIAL = "radial"
        const val MODE_BLINK = "blink"
        const val MODE_EXCLAMATION = "exclamation"
    }
}

package com.fabiantorrestech.visualtimerplus.ui.eink

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.fabiantorrestech.visualtimerplus.R

class EInkSettingsActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var displayModeValue: TextView
    private lateinit var clockwiseDivider: View
    private lateinit var clockwiseRow: RelativeLayout
    private lateinit var clockwiseValue: TextView
    private lateinit var elapsedModeValue: TextView
    private lateinit var keepAwakeValue: TextView
    private lateinit var updateIntervalValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_eink_settings)

        prefs = getSharedPreferences("visual_timer_prefs", MODE_PRIVATE)

        displayModeValue = findViewById(R.id.displayModeValue)
        clockwiseDivider = findViewById(R.id.clockwiseDivider)
        clockwiseRow = findViewById(R.id.clockwiseRow)
        clockwiseValue = findViewById(R.id.clockwiseValue)
        elapsedModeValue = findViewById(R.id.elapsedModeValue)
        keepAwakeValue = findViewById(R.id.keepAwakeValue)
        updateIntervalValue = findViewById(R.id.updateIntervalValue)

        updateDisplayValues()

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<RelativeLayout>(R.id.displayModeRow).setOnClickListener {
            val current = prefs.getString(PREF_DISPLAY_MODE, MODE_BARS) ?: MODE_BARS
            prefs.edit().putString(PREF_DISPLAY_MODE, if (current == MODE_BARS) MODE_RADIAL else MODE_BARS).apply()
            updateDisplayValues()
        }

        clockwiseRow.setOnClickListener {
            val current = prefs.getBoolean(PREF_CLOCKWISE, true)
            prefs.edit().putBoolean(PREF_CLOCKWISE, !current).apply()
            updateDisplayValues()
        }

        findViewById<RelativeLayout>(R.id.elapsedModeRow).setOnClickListener {
            val current = prefs.getString(PREF_ELAPSED_MODE, MODE_BLINK) ?: MODE_BLINK
            prefs.edit().putString(PREF_ELAPSED_MODE, if (current == MODE_BLINK) MODE_EXCLAMATION else MODE_BLINK).apply()
            updateDisplayValues()
        }

        findViewById<RelativeLayout>(R.id.keepAwakeRow).setOnClickListener {
            val current = prefs.getBoolean(PREF_KEEP_AWAKE, true)
            prefs.edit().putBoolean(PREF_KEEP_AWAKE, !current).apply()
            applyKeepAwake(!current)
            updateDisplayValues()
        }

        findViewById<RelativeLayout>(R.id.updateIntervalRow).setOnClickListener {
            val current = prefs.getString(PREF_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL) ?: DEFAULT_UPDATE_INTERVAL
            val idx = UPDATE_INTERVALS.indexOf(current)
            val next = UPDATE_INTERVALS[(idx + 1) % UPDATE_INTERVALS.size]
            prefs.edit().putString(PREF_UPDATE_INTERVAL, next).apply()
            updateDisplayValues()
        }

        applyKeepAwake(prefs.getBoolean(PREF_KEEP_AWAKE, true))
    }

    private fun applyKeepAwake(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun updateDisplayValues() {
        val isRadial = prefs.getString(PREF_DISPLAY_MODE, MODE_BARS) == MODE_RADIAL
        displayModeValue.text = if (isRadial) "RADIAL" else "BARS"
        val radialVis = if (isRadial) View.VISIBLE else View.GONE
        clockwiseDivider.visibility = radialVis
        clockwiseRow.visibility = radialVis
        clockwiseValue.text = if (prefs.getBoolean(PREF_CLOCKWISE, true)) "CW" else "CCW"
        elapsedModeValue.text = if (prefs.getString(PREF_ELAPSED_MODE, MODE_BLINK) == MODE_BLINK) "BLINK" else "EXCLAMATION"
        keepAwakeValue.text = if (prefs.getBoolean(PREF_KEEP_AWAKE, true)) "ON" else "OFF"
        val interval = prefs.getString(PREF_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL) ?: DEFAULT_UPDATE_INTERVAL
        updateIntervalValue.text = if (interval == "60") "1M" else "${interval}S"
    }

    companion object {
        const val PREF_DISPLAY_MODE = "eink_display_mode"
        const val PREF_ELAPSED_MODE = "eink_elapsed_mode"
        const val PREF_KEEP_AWAKE = "eink_keep_screen_awake"
        const val PREF_CLOCKWISE = "eink_clockwise"
        const val PREF_UPDATE_INTERVAL = "eink_update_interval"
        const val DEFAULT_UPDATE_INTERVAL = "15"
        val UPDATE_INTERVALS = listOf("5", "10", "15", "30", "60")
        const val MODE_BARS = "bars"
        const val MODE_RADIAL = "radial"
        const val MODE_BLINK = "blink"
        const val MODE_EXCLAMATION = "exclamation"
    }
}

package com.dumbphone.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class DisplaySettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var gestureDetector: GestureDetector

    companion object {
        private val COLOUR_OPTIONS = arrayOf(
            "#7FBF3F" to "Green (classic)",
            "#FFFFFF" to "White",
            "#00BFFF" to "Blue",
            "#FF6B35" to "Orange",
            "#FF4081" to "Pink",
            "#B388FF" to "Purple",
            "#FFD600" to "Yellow",
            "#00E676" to "Mint",
            "#FF1744" to "Red",
            "#18FFFF" to "Cyan",
        )

        private val ICON_SIZE_OPTIONS = arrayOf(
            "Small" to PrefsManager.ICON_SIZE_SMALL,
            "Medium" to PrefsManager.ICON_SIZE_MEDIUM,
            "Large" to PrefsManager.ICON_SIZE_LARGE,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_settings)

        prefs = PrefsManager(this)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        // Swipe-down to go back
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dy = e2.y - (e1?.y ?: 0f)
                if (dy > 100 && abs(velocityY) > 200) {
                    finish()
                    return true
                }
                return false
            }
        })

        // Clock colour picker
        findViewById<LinearLayout>(R.id.btnClockColour).setOnClickListener {
            showColourPicker()
        }

        // Show seconds toggle
        val secondsToggle = findViewById<Switch>(R.id.switchShowSeconds)
        secondsToggle.isChecked = prefs.showSeconds
        secondsToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.showSeconds = isChecked
        }

        // 24-hour toggle
        val hour24Toggle = findViewById<Switch>(R.id.switch24Hour)
        hour24Toggle.isChecked = prefs.use24Hour
        hour24Toggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.use24Hour = isChecked
        }

        // Greyscale toggle (system-wide via bedtime mode / daltonizer)
        val greyscaleToggle = findViewById<Switch>(R.id.switchGreyscale)
        greyscaleToggle.isChecked = prefs.greyscaleMode
        greyscaleToggle.setOnCheckedChangeListener { _, isChecked ->
            if (setSystemGreyscale(isChecked)) {
                prefs.greyscaleMode = isChecked
            } else {
                greyscaleToggle.isChecked = !isChecked
            }
        }

        // Icon size picker
        findViewById<TextView>(R.id.btnIconSize).setOnClickListener {
            showIconSizePicker()
        }
        updateIconSizeLabel()

        // Show app labels toggle
        val labelsToggle = findViewById<Switch>(R.id.switchShowLabels)
        labelsToggle.isChecked = prefs.showAppLabels
        labelsToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.showAppLabels = isChecked
        }

        applyTheme()
        updateColourPreview()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun showColourPicker() {
        val names = COLOUR_OPTIONS.map { it.second }.toTypedArray()

        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Clock colour")
            .setItems(names) { _, which ->
                val hex = COLOUR_OPTIONS[which].first
                prefs.clockColour = Color.parseColor(hex)
                applyTheme()
                updateColourPreview()
            }
            .show()
    }

    private fun showIconSizePicker() {
        val names = ICON_SIZE_OPTIONS.map { it.first }.toTypedArray()

        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("App icon size")
            .setItems(names) { _, which ->
                prefs.appIconSize = ICON_SIZE_OPTIONS[which].second
                updateIconSizeLabel()
            }
            .show()
    }

    private fun updateIconSizeLabel() {
        val label = when (prefs.appIconSize) {
            PrefsManager.ICON_SIZE_SMALL -> "Small"
            PrefsManager.ICON_SIZE_LARGE -> "Large"
            else -> "Medium"
        }
        findViewById<TextView>(R.id.btnIconSize).text = "App icon size ($label) \u25b8"
    }

    private fun updateColourPreview() {
        val preview = findViewById<View>(R.id.colourPreview)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 4f
        bg.setColor(prefs.clockColour)
        preview.background = bg
    }

    /**
     * Toggle system-wide greyscale via the accessibility daltonizer.
     * This is the same mechanism used by Bedtime Mode / Wind Down.
     * Requires WRITE_SECURE_SETTINGS (granted once via ADB).
     * Falls back to opening Bedtime Mode settings if permission not granted.
     */
    private fun setSystemGreyscale(enabled: Boolean): Boolean {
        return try {
            Settings.Secure.putInt(
                contentResolver,
                "accessibility_display_daltonizer_enabled",
                if (enabled) 1 else 0
            )
            if (enabled) {
                // 0 = monochromacy (greyscale)
                Settings.Secure.putInt(
                    contentResolver,
                    "accessibility_display_daltonizer",
                    0
                )
            }
            true
        } catch (e: SecurityException) {
            // Try to open Bedtime Mode settings as fallback
            AlertDialog.Builder(this, R.style.NokiaDialog)
                .setTitle("Greyscale")
                .setMessage(
                    "To enable system-wide greyscale, you can:\n\n" +
                    "1. Open Bedtime Mode in Digital Wellbeing and enable greyscale there\n\n" +
                    "OR grant permission once via computer:\n" +
                    "adb shell pm grant com.dumbphone.launcher android.permission.WRITE_SECURE_SETTINGS"
                )
                .setPositiveButton("OPEN BEDTIME") { _, _ ->
                    openBedtimeSettings()
                }
                .setNegativeButton("CANCEL", null)
                .show()
            false
        }
    }

    private fun openBedtimeSettings() {
        // Try Digital Wellbeing bedtime settings
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.google.android.apps.wellbeing",
                    "com.google.android.apps.wellbeing.settings.WindDownSettingsActivity"
                )
            }
            startActivity(intent)
            return
        } catch (_: Exception) { }

        // Fallback: try Digital Wellbeing main activity
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.apps.wellbeing")
            if (intent != null) {
                startActivity(intent)
                return
            }
        } catch (_: Exception) { }

        // Last resort: open display settings
        startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
    }

    private fun applyTheme() {
        val fgColor = prefs.getFgColour()

        findViewById<View>(R.id.displaySettingsRoot).setBackgroundColor(Color.BLACK)
        findViewById<TextView>(R.id.displayTitle).setTextColor(fgColor)
        findViewById<TextView>(R.id.lblClockColour).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchShowSeconds).setTextColor(fgColor)
        findViewById<Switch>(R.id.switch24Hour).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchGreyscale).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnIconSize).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchShowLabels).setTextColor(fgColor)
    }
}

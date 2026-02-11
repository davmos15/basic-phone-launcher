package com.dumbphone.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

        // Greyscale toggle
        val greyscaleToggle = findViewById<Switch>(R.id.switchGreyscale)
        greyscaleToggle.isChecked = prefs.greyscaleMode
        greyscaleToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.greyscaleMode = isChecked
            applyTheme()
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

    private fun updateColourPreview() {
        val preview = findViewById<View>(R.id.colourPreview)
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 4f
        bg.setColor(prefs.clockColour)
        preview.background = bg
    }

    private fun applyTheme() {
        val fgColor = prefs.getFgColour()
        val dimColor = prefs.getDimColour()

        findViewById<View>(R.id.displaySettingsRoot).setBackgroundColor(Color.BLACK)
        findViewById<TextView>(R.id.displayTitle).setTextColor(fgColor)
        findViewById<TextView>(R.id.lblClockColour).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchShowSeconds).setTextColor(fgColor)
        findViewById<Switch>(R.id.switch24Hour).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchGreyscale).setTextColor(fgColor)
    }
}

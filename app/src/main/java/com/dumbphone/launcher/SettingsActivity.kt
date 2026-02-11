package com.dumbphone.launcher

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var rootLayout: LinearLayout
    private lateinit var gestureDetector: GestureDetector

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val weatherToggle = findViewById<Switch>(R.id.switchWeather)
        if (isGranted) {
            prefs.weatherEnabled = true
        } else {
            prefs.weatherEnabled = false
            weatherToggle.isChecked = false
            Toast.makeText(this, "Location permission needed for weather", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = PrefsManager(this)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        rootLayout = findViewById(R.id.settingsRootLayout)

        // Swipe-down to go back to home screen
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

        // Set as default launcher
        findViewById<TextView>(R.id.btnSetDefaultLauncher).setOnClickListener {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }

        // Navigate to Display settings
        findViewById<TextView>(R.id.btnDisplay).setOnClickListener {
            startActivity(Intent(this, DisplaySettingsActivity::class.java))
        }

        // Navigate to Apps settings
        findViewById<TextView>(R.id.btnApps).setOnClickListener {
            startActivity(Intent(this, AppsSettingsActivity::class.java))
        }

        // Weather toggle
        val weatherToggle = findViewById<Switch>(R.id.switchWeather)
        weatherToggle.isChecked = prefs.weatherEnabled
        weatherToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    prefs.weatherEnabled = true
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            } else {
                prefs.weatherEnabled = false
            }
        }

        // Focus mode toggle
        val focusToggle = findViewById<Switch>(R.id.switchFocusMode)
        focusToggle.isChecked = prefs.focusModeEnabled
        focusToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val nm = getSystemService(NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) {
                    focusToggle.isChecked = false
                    AlertDialog.Builder(this, R.style.NokiaDialog)
                        .setTitle("DND Permission")
                        .setMessage(
                            "To enable Focus Mode, DumbPhone needs Do Not Disturb access.\n\n" +
                            "On the next screen, find DumbPhone and enable it."
                        )
                        .setPositiveButton("GRANT ACCESS") { _, _ ->
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                        .setNegativeButton("CANCEL", null)
                        .show()
                } else {
                    prefs.focusModeEnabled = true
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            } else {
                prefs.focusModeEnabled = false
                val nm = getSystemService(NotificationManager::class.java)
                if (nm.isNotificationPolicyAccessGranted) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            }
        }

        // Exit to normal launcher
        findViewById<TextView>(R.id.btnExitDumbMode).setOnClickListener {
            showExitDialog()
        }

        applyTheme()
    }

    override fun onResume() {
        super.onResume()
        findViewById<Switch>(R.id.switchFocusMode).isChecked = prefs.focusModeEnabled
        findViewById<Switch>(R.id.switchWeather).isChecked = prefs.weatherEnabled
        applyTheme()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    // ── Dialogs ─────────────────────────────────────────────────────────

    private fun showExitDialog() {
        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Exit Dumb Mode")
            .setMessage(
                "To switch back to your normal launcher:\n\n" +
                "1. Go to Android Settings\n" +
                "2. Apps \u2192 Default Apps\n" +
                "3. Home App\n" +
                "4. Select your normal launcher\n\n" +
                "Open Android Settings now?"
            )
            .setPositiveButton("OPEN SETTINGS") { _, _ ->
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── Theme ───────────────────────────────────────────────────────────

    private fun applyTheme() {
        val fgColor = prefs.getFgColour()
        val dimColor = prefs.getDimColour()

        rootLayout.setBackgroundColor(Color.BLACK)
        findViewById<TextView>(R.id.settingsTitle).setTextColor(fgColor)
        findViewById<TextView>(R.id.sectionLauncher).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionSubScreens).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionFeatures).setTextColor(dimColor)
        findViewById<TextView>(R.id.btnSetDefaultLauncher).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnDisplay).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnApps).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchWeather).setTextColor(fgColor)
        findViewById<Switch>(R.id.switchFocusMode).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnExitDumbMode).setTextColor(Color.parseColor("#FF4444"))
    }
}

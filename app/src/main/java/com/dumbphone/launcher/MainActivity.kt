package com.dumbphone.launcher

import android.Manifest
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var menuHint: TextView
    private lateinit var rootLayout: LinearLayout
    private lateinit var gestureDetector: GestureDetector

    // Clock formatter cache (rebuilt when prefs change)
    private var cachedTimeFormat: SimpleDateFormat? = null
    private var cachedTimePattern: String? = null
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    // Weather cache
    private var cachedWeatherText: String? = null
    private var lastWeatherFetch: Long = 0
    private val weatherCacheMs = 30 * 60 * 1000L // 30 minutes

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchWeather()
        } else {
            weatherText.text = "Enable location for weather"
            weatherText.visibility = View.VISIBLE
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        // Make fullscreen with black system bars
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.BLACK
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.BLACK

        // Bind views
        rootLayout = findViewById(R.id.rootLayout)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        weatherText = findViewById(R.id.weatherText)
        menuHint = findViewById(R.id.menuHint)

        // Block back button - we are the home screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - we're the home screen
            }
        })

        // Set up click listeners
        findViewById<TextView>(R.id.btnMenu).setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        findViewById<TextView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Tap clock to open clock app
        clockText.setOnClickListener {
            openClockApp()
        }

        // Swipe-up gesture to open app drawer
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val dy = (e1?.y ?: 0f) - e2.y
                if (dy > 100 && abs(velocityY) > 200) {
                    startActivity(Intent(this@MainActivity, AppDrawerActivity::class.java))
                    return true
                }
                return false
            }
        })

        // Show first-run dialog: prompt to set as default launcher
        if (prefs.isFirstRun) {
            prefs.isFirstRun = false
            showSetAsLauncherDialog()
        }

        applyTheme()
    }

    // Use dispatchTouchEvent so swipe-up works even over child views
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        handler.post(clockRunnable)
        applyTheme()
        refreshWeather()
        applyFocusMode()
        enforceWallpaperOverride()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }

    private fun updateClock() {
        val now = Date()

        // Rebuild formatter only when pattern changes (prefs toggle)
        val pattern = buildString {
            append(if (prefs.use24Hour) "HH:mm" else "hh:mm")
            if (prefs.showSeconds) append(":ss")
            if (!prefs.use24Hour) append(" a")
        }
        if (pattern != cachedTimePattern) {
            cachedTimePattern = pattern
            cachedTimeFormat = SimpleDateFormat(pattern, Locale.getDefault())
        }
        clockText.text = cachedTimeFormat!!.format(now)
        dateText.text = dateFormat.format(now)
    }

    private fun openClockApp() {
        try {
            val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            if (clockIntent.resolveActivity(packageManager) != null) {
                startActivity(clockIntent)
            } else {
                Toast.makeText(this, "No clock app found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No clock app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val fgColor = prefs.getFgColour()
        val dimColor = prefs.getDimColour()

        rootLayout.setBackgroundColor(Color.BLACK)
        clockText.setTextColor(fgColor)
        dateText.setTextColor(dimColor)
        weatherText.setTextColor(dimColor)
        menuHint.setTextColor(dimColor)
        findViewById<TextView>(R.id.btnMenu).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnSettings).setTextColor(fgColor)
    }

    // ── Focus Mode (DND) ─────────────────────────────────────────────────

    private fun applyFocusMode() {
        if (prefs.focusModeEnabled) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
    }

    // ── Wallpaper Override ─────────────────────────────────────────────

    private var wallpaperOverrideApplied = false

    private fun enforceWallpaperOverride() {
        if (!prefs.overrideWallpaper) {
            wallpaperOverrideApplied = false
            return
        }
        if (wallpaperOverrideApplied) return
        try {
            val wm = WallpaperManager.getInstance(this)
            val blackBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLACK)
            }
            wm.setBitmap(blackBitmap)
            blackBitmap.recycle()
            wallpaperOverrideApplied = true
        } catch (_: Exception) { }
    }

    // ── Weather ──────────────────────────────────────────────────────────

    private fun refreshWeather() {
        if (!prefs.weatherEnabled) {
            weatherText.visibility = View.GONE
            return
        }

        // Show cached data immediately if available
        if (cachedWeatherText != null) {
            weatherText.text = cachedWeatherText
            weatherText.visibility = View.VISIBLE
        }

        // Skip fetch if cache is fresh
        if (System.currentTimeMillis() - lastWeatherFetch < weatherCacheMs && cachedWeatherText != null) {
            return
        }

        // Check location permission
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }

        fetchWeather()
    }

    private fun fetchWeather() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        @Suppress("MissingPermission")
        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

        if (location == null) {
            weatherText.text = "Waiting for location..."
            weatherText.visibility = View.VISIBLE
            return
        }

        Thread {
            val data = WeatherUtil.fetch(location.latitude, location.longitude)
            runOnUiThread {
                if (data != null) {
                    cachedWeatherText = "${data.temperature}${data.unit} \u00b7 ${data.condition}"
                    lastWeatherFetch = System.currentTimeMillis()
                    weatherText.text = cachedWeatherText
                } else {
                    weatherText.text = cachedWeatherText ?: "Weather unavailable"
                }
                weatherText.visibility = View.VISIBLE
            }
        }.start()
    }

    // ── Dialogs ──────────────────────────────────────────────────────────

    private fun showSetAsLauncherDialog() {
        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Welcome to DumbPhone")
            .setMessage(
                "Your phone is now in dumb mode.\n\n" +
                "\u2022 Press MENU or swipe up for your apps\n" +
                "\u2022 Press SETTINGS to configure\n" +
                "\u2022 Tap the clock to open alarms\n\n" +
                "Would you like to set DumbPhone as your default launcher?"
            )
            .setPositiveButton("SET AS DEFAULT") { _, _ ->
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            .setNegativeButton("LATER", null)
            .show()
    }
}

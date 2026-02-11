package com.dumbphone.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var menuHint: TextView
    private lateinit var rootLayout: LinearLayout
    private lateinit var widgetContainer: FrameLayout
    private lateinit var gestureDetector: GestureDetector

    // Widget hosting
    private lateinit var appWidgetHost: AppWidgetHost

    // Cached date formatters (avoid re-creating every second)
    private val timeFormatWithSeconds = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val timeFormatNoSeconds = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    companion object {
        private const val APPWIDGET_HOST_ID = 1024
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

        // Init widget hosting
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        // Bind views
        rootLayout = findViewById(R.id.rootLayout)
        clockText = findViewById(R.id.clockText)
        dateText = findViewById(R.id.dateText)
        menuHint = findViewById(R.id.menuHint)
        widgetContainer = findViewById(R.id.widgetContainer)

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

        // Show first-run hint
        if (prefs.isFirstRun) {
            showFirstRunDialog()
            prefs.isFirstRun = false
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
        restoreWidget()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(clockRunnable)
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    private fun updateClock() {
        val now = Date()
        val timeFormat = if (prefs.showSeconds) timeFormatWithSeconds else timeFormatNoSeconds
        clockText.text = timeFormat.format(now)
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
        val fgColor = prefs.clockColour
        val dimColor = prefs.getDimColour()

        rootLayout.setBackgroundColor(Color.BLACK)
        clockText.setTextColor(fgColor)
        dateText.setTextColor(dimColor)
        menuHint.setTextColor(dimColor)
        findViewById<TextView>(R.id.btnMenu).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnSettings).setTextColor(fgColor)
    }

    // ── Widget hosting ──────────────────────────────────────────────────

    private fun restoreWidget() {
        val widgetId = prefs.widgetId
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val widgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
            if (widgetInfo != null) {
                val hostView = appWidgetHost.createView(this, widgetId, widgetInfo)
                hostView.setAppWidget(widgetId, widgetInfo)
                widgetContainer.removeAllViews()
                widgetContainer.addView(hostView)
            } else {
                prefs.widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                widgetContainer.removeAllViews()
            }
        } else {
            widgetContainer.removeAllViews()
        }
    }

    private fun showFirstRunDialog() {
        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Welcome to DumbPhone")
            .setMessage(
                "Your phone is now in dumb mode.\n\n" +
                "\u2022 Press MENU or swipe up for your apps\n" +
                "\u2022 Press SETTINGS to configure\n" +
                "\u2022 Tap the clock to open alarms\n" +
                "\u2022 Add widgets from Settings\n\n" +
                "To switch back to your normal launcher, go to Settings \u2192 Apps \u2192 Default Apps \u2192 Home App."
            )
            .setPositiveButton("GOT IT", null)
            .show()
    }
}

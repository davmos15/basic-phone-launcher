package com.dumbphone.launcher

import android.app.NotificationManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var rootLayout: LinearLayout
    private lateinit var gestureDetector: GestureDetector

    // Widget hosting (for weather widget)
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var widgetConfigLauncher: ActivityResultLauncher<Intent>
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val APPWIDGET_HOST_ID = 1024
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

        // Init widget hosting
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        // Register activity result launchers for widget binding flow
        widgetConfigLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                saveWeatherWidget(pendingWidgetId)
            } else {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

        widgetBindLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                configureWidget(pendingWidgetId)
            } else {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }

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
                selectWeatherWidget()
            } else {
                removeWeatherWidget()
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
        // Re-check focus mode toggle in case user just granted DND permission
        findViewById<Switch>(R.id.switchFocusMode).isChecked = prefs.focusModeEnabled
        // Re-check weather toggle in case widget was removed externally
        findViewById<Switch>(R.id.switchWeather).isChecked = prefs.weatherEnabled
        applyTheme()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    // ── Weather widget management ────────────────────────────────────────

    private fun selectWeatherWidget() {
        // Remove old widget if any
        val currentId = prefs.widgetId
        if (currentId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(currentId)
            prefs.widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }

        // Filter for weather-related widgets
        val allProviders = appWidgetManager.installedProviders
        val weatherProviders = allProviders.filter { provider ->
            val name = provider.provider.className.lowercase()
            val label = provider.loadLabel(packageManager).lowercase()
            val pkg = provider.provider.packageName.lowercase()
            name.contains("weather") || label.contains("weather") ||
            name.contains("forecast") || label.contains("forecast") ||
            pkg.contains("weather")
        }

        // Fall back to all widgets if no weather-specific ones found
        val providers = if (weatherProviders.isNotEmpty()) weatherProviders else allProviders
        val title = if (weatherProviders.isNotEmpty()) "Select weather widget" else "Select widget (no weather widgets found)"

        if (providers.isEmpty()) {
            Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show()
            findViewById<Switch>(R.id.switchWeather).isChecked = false
            return
        }

        val labels = providers.map { it.loadLabel(packageManager) }.toTypedArray()
        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle(title)
            .setItems(labels) { _, which ->
                val provider = providers[which]
                bindWeatherWidget(provider)
            }
            .setNegativeButton("CANCEL") { _, _ ->
                // User cancelled, uncheck the toggle
                findViewById<Switch>(R.id.switchWeather).isChecked = false
            }
            .setOnCancelListener {
                findViewById<Switch>(R.id.switchWeather).isChecked = false
            }
            .show()
    }

    private fun bindWeatherWidget(provider: AppWidgetProviderInfo) {
        val widgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = widgetId

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
        if (bound) {
            configureWidget(widgetId)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            widgetBindLauncher.launch(bindIntent)
        }
    }

    private fun configureWidget(widgetId: Int) {
        val widgetInfo: AppWidgetProviderInfo? = appWidgetManager.getAppWidgetInfo(widgetId)
        if (widgetInfo?.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = widgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            widgetConfigLauncher.launch(configIntent)
        } else {
            saveWeatherWidget(widgetId)
        }
    }

    private fun saveWeatherWidget(widgetId: Int) {
        prefs.widgetId = widgetId
        prefs.weatherEnabled = true
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        Toast.makeText(this, "Weather widget added", Toast.LENGTH_SHORT).show()
    }

    private fun removeWeatherWidget() {
        val currentId = prefs.widgetId
        if (currentId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(currentId)
            prefs.widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
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

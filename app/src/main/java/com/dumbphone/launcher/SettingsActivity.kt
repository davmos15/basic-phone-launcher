package com.dumbphone.launcher

import android.app.NotificationManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var appListRecycler: RecyclerView
    private lateinit var rootLayout: LinearLayout
    private lateinit var gestureDetector: GestureDetector

    // Widget hosting
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingWidgetProvider: ComponentName? = null

    private lateinit var widgetConfigLauncher: ActivityResultLauncher<Intent>
    private lateinit var widgetBindLauncher: ActivityResultLauncher<Intent>

    // App list data
    private var allApps: List<ResolveInfo> = emptyList()
    private var filteredApps: List<ResolveInfo> = emptyList()
    private lateinit var appAdapter: AppToggleAdapter

    companion object {
        private const val APPWIDGET_HOST_ID = 1024

        // Colour presets for the picker
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

        // Register activity result launchers for widget flow
        widgetConfigLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                saveWidget(pendingWidgetId)
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

        // Focus mode toggle
        val focusToggle = findViewById<Switch>(R.id.switchFocusMode)
        focusToggle.isChecked = prefs.focusModeEnabled
        focusToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val nm = getSystemService(NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) {
                    // Need permission first
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

        // Widget buttons
        findViewById<TextView>(R.id.btnSelectWidget).setOnClickListener {
            selectWidget()
        }

        findViewById<TextView>(R.id.btnRemoveWidget).setOnClickListener {
            removeWidget()
        }

        // Exit to normal launcher
        findViewById<TextView>(R.id.btnExitDumbMode).setOnClickListener {
            showExitDialog()
        }

        // App list
        appListRecycler = findViewById(R.id.appListRecycler)
        appListRecycler.layoutManager = LinearLayoutManager(this)
        loadAllApps()

        // Search
        val searchInput = findViewById<EditText>(R.id.appSearchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })

        applyTheme()
        updateWidgetLabel()
        updateColourPreview()
    }

    override fun onResume() {
        super.onResume()
        // Re-check focus mode toggle in case user just granted DND permission
        val focusToggle = findViewById<Switch>(R.id.switchFocusMode)
        focusToggle.isChecked = prefs.focusModeEnabled
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

    private fun loadAllApps() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        allApps = pm.queryIntentActivities(mainIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        filteredApps = allApps
        appAdapter = AppToggleAdapter(filteredApps, pm)
        appListRecycler.adapter = appAdapter
    }

    private fun filterApps(query: String) {
        val pm = packageManager
        filteredApps = if (query.isBlank()) {
            allApps
        } else {
            val q = query.lowercase()
            allApps.filter {
                it.loadLabel(pm).toString().lowercase().contains(q) ||
                it.activityInfo.packageName.lowercase().contains(q)
            }
        }
        appAdapter = AppToggleAdapter(filteredApps, pm)
        appListRecycler.adapter = appAdapter
    }

    // ── Colour picker ───────────────────────────────────────────────────

    private fun showColourPicker() {
        val names = COLOUR_OPTIONS.map { it.second }.toTypedArray()

        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Clock colour")
            .setItems(names) { _, which ->
                val hex = COLOUR_OPTIONS[which].first
                prefs.clockColour = Color.parseColor(hex)
                applyTheme()
                updateColourPreview()
                loadAllApps()
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

    // ── Widget management ───────────────────────────────────────────────

    private fun selectWidget() {
        val currentId = prefs.widgetId
        if (currentId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(currentId)
            prefs.widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }

        // Show available widgets and let user pick
        val providers = appWidgetManager.installedProviders
        if (providers.isEmpty()) {
            Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = providers.map { it.loadLabel(packageManager) }.toTypedArray()
        AlertDialog.Builder(this, R.style.NokiaDialog)
            .setTitle("Select widget")
            .setItems(labels) { _, which ->
                val provider = providers[which]
                bindWidget(provider)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun bindWidget(provider: AppWidgetProviderInfo) {
        val widgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = widgetId
        pendingWidgetProvider = provider.provider

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
        if (bound) {
            configureWidget(widgetId)
        } else {
            // Ask user for permission to bind
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
            saveWidget(widgetId)
        }
    }

    private fun saveWidget(widgetId: Int) {
        prefs.widgetId = widgetId
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingWidgetProvider = null
        updateWidgetLabel()
        Toast.makeText(this, "Widget added", Toast.LENGTH_SHORT).show()
    }

    private fun removeWidget() {
        val currentId = prefs.widgetId
        if (currentId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            appWidgetHost.deleteAppWidgetId(currentId)
            prefs.widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            updateWidgetLabel()
            Toast.makeText(this, "Widget removed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No widget to remove", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateWidgetLabel() {
        val btn = findViewById<TextView>(R.id.btnSelectWidget)
        val widgetId = prefs.widgetId
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val info = appWidgetManager.getAppWidgetInfo(widgetId)
            val label = info?.loadLabel(packageManager) ?: "Unknown"
            btn.text = "Change widget ($label) \u25b8"
        } else {
            btn.text = "Select widget \u25b8"
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
        val fgColor = prefs.clockColour
        val dimColor = prefs.getDimColour()

        rootLayout.setBackgroundColor(Color.BLACK)
        findViewById<TextView>(R.id.settingsTitle).setTextColor(fgColor)
        findViewById<TextView>(R.id.sectionLauncher).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionDisplay).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionFocus).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionWidgets).setTextColor(dimColor)
        findViewById<TextView>(R.id.sectionApps).setTextColor(dimColor)
        findViewById<TextView>(R.id.btnSetDefaultLauncher).setTextColor(fgColor)
        findViewById<TextView>(R.id.lblClockColour).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnSelectWidget).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnRemoveWidget).setTextColor(fgColor)
        findViewById<TextView>(R.id.btnExitDumbMode).setTextColor(Color.parseColor("#FF4444"))

        val switchSeconds = findViewById<Switch>(R.id.switchShowSeconds)
        switchSeconds.setTextColor(fgColor)

        val switchFocus = findViewById<Switch>(R.id.switchFocusMode)
        switchFocus.setTextColor(fgColor)
    }

    // ── App Toggle Adapter ──────────────────────────────────────────────

    inner class AppToggleAdapter(
        private val apps: List<ResolveInfo>,
        private val pm: PackageManager
    ) : RecyclerView.Adapter<AppToggleAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.toggleAppIcon)
            val label: TextView = view.findViewById(R.id.toggleAppLabel)
            val packageText: TextView = view.findViewById(R.id.toggleAppPackage)
            val toggle: CheckBox = view.findViewById(R.id.toggleAppCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_toggle, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            val pkg = app.activityInfo.packageName
            val label = app.loadLabel(pm).toString()

            holder.icon.setImageDrawable(app.loadIcon(pm))
            holder.label.text = label
            holder.packageText.text = pkg

            // Avoid triggering the listener when setting checked state
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = prefs.isAppWhitelisted(pkg)

            holder.label.setTextColor(prefs.clockColour)
            holder.packageText.setTextColor(Color.parseColor("#555555"))

            holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefs.addWhitelistedApp(pkg)
                } else {
                    prefs.removeWhitelistedApp(pkg)
                }
            }

            holder.itemView.setOnClickListener {
                holder.toggle.isChecked = !holder.toggle.isChecked
            }
        }

        override fun getItemCount() = apps.size
    }
}

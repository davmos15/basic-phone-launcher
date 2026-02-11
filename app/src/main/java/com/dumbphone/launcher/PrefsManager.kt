package com.dumbphone.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Manages all launcher preferences:
 * - Whitelisted app packages
 * - Display preferences (clock colour, seconds)
 * - Widget ID
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dumbphone_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WHITELISTED_APPS = "whitelisted_apps"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_CLOCK_COLOUR = "clock_colour"
        private const val KEY_SHOW_SECONDS = "show_seconds"
        private const val KEY_WIDGET_ID = "home_widget_id"
        private const val KEY_FOCUS_MODE = "focus_mode_enabled"

        val DEFAULT_CLOCK_COLOUR = Color.parseColor("#7FBF3F") // Classic green

        // Default apps that are always useful
        val DEFAULT_APPS = setOf(
            "com.android.dialer",        // Phone
            "com.google.android.dialer",  // Google Phone
            "com.android.contacts",       // Contacts
            "com.google.android.apps.messaging", // Messages
            "com.android.mms",           // SMS
        )
    }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    var whitelistedApps: Set<String>
        get() = prefs.getStringSet(KEY_WHITELISTED_APPS, DEFAULT_APPS) ?: DEFAULT_APPS
        set(value) = prefs.edit().putStringSet(KEY_WHITELISTED_APPS, value).apply()

    var clockColour: Int
        get() = prefs.getInt(KEY_CLOCK_COLOUR, DEFAULT_CLOCK_COLOUR)
        set(value) = prefs.edit().putInt(KEY_CLOCK_COLOUR, value).apply()

    var showSeconds: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SECONDS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SECONDS, value).apply()

    var widgetId: Int
        get() = prefs.getInt(KEY_WIDGET_ID, -1) // -1 == AppWidgetManager.INVALID_APPWIDGET_ID
        set(value) = prefs.edit().putInt(KEY_WIDGET_ID, value).apply()

    var focusModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_FOCUS_MODE, value).apply()

    /** Returns a dimmed version of the clock colour for secondary text. */
    fun getDimColour(): Int {
        val r = Color.red(clockColour)
        val g = Color.green(clockColour)
        val b = Color.blue(clockColour)
        return Color.rgb((r * 0.45).toInt(), (g * 0.45).toInt(), (b * 0.45).toInt())
    }

    fun addWhitelistedApp(packageName: String) {
        val current = whitelistedApps.toMutableSet()
        current.add(packageName)
        whitelistedApps = current
    }

    fun removeWhitelistedApp(packageName: String) {
        val current = whitelistedApps.toMutableSet()
        current.remove(packageName)
        whitelistedApps = current
    }

    fun isAppWhitelisted(packageName: String): Boolean {
        return whitelistedApps.contains(packageName)
    }
}

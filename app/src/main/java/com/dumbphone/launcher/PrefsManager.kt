package com.dumbphone.launcher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dumbphone_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WHITELISTED_APPS = "whitelisted_apps"
        private const val KEY_FIRST_RUN = "first_run"
        private const val KEY_CLOCK_COLOUR = "clock_colour"
        private const val KEY_SHOW_SECONDS = "show_seconds"
        private const val KEY_USE_24_HOUR = "use_24_hour"
        private const val KEY_GREYSCALE = "greyscale_mode"
        private const val KEY_WEATHER_ENABLED = "weather_enabled"
        private const val KEY_FOCUS_MODE = "focus_mode_enabled"
        private const val KEY_APP_ICON_SIZE = "app_icon_size"
        private const val KEY_SHOW_APP_LABELS = "show_app_labels"
        private const val KEY_OVERRIDE_WALLPAPER = "override_wallpaper"

        val DEFAULT_CLOCK_COLOUR = Color.parseColor("#7FBF3F")

        val DEFAULT_APPS = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.contacts",
            "com.google.android.apps.messaging",
            "com.android.mms",
        )

        // Icon sizes: 0 = small, 1 = medium, 2 = large, 3 = extra large
        const val ICON_SIZE_SMALL = 0
        const val ICON_SIZE_MEDIUM = 1
        const val ICON_SIZE_LARGE = 2
        const val ICON_SIZE_EXTRA_LARGE = 3
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

    var use24Hour: Boolean
        get() = prefs.getBoolean(KEY_USE_24_HOUR, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_24_HOUR, value).apply()

    var greyscaleMode: Boolean
        get() = prefs.getBoolean(KEY_GREYSCALE, false)
        set(value) = prefs.edit().putBoolean(KEY_GREYSCALE, value).apply()

    var weatherEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEATHER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WEATHER_ENABLED, value).apply()

    var focusModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_FOCUS_MODE, value).apply()

    /** 0 = small, 1 = medium, 2 = large */
    var appIconSize: Int
        get() = prefs.getInt(KEY_APP_ICON_SIZE, ICON_SIZE_MEDIUM)
        set(value) = prefs.edit().putInt(KEY_APP_ICON_SIZE, value).apply()

    var showAppLabels: Boolean
        get() = prefs.getBoolean(KEY_SHOW_APP_LABELS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_APP_LABELS, value).apply()

    var overrideWallpaper: Boolean
        get() = prefs.getBoolean(KEY_OVERRIDE_WALLPAPER, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERRIDE_WALLPAPER, value).apply()

    /** Returns the foreground colour for UI elements. */
    fun getFgColour(): Int = clockColour

    /** Returns a dimmed version of the foreground colour for secondary text. */
    fun getDimColour(): Int {
        val fg = getFgColour()
        val r = Color.red(fg)
        val g = Color.green(fg)
        val b = Color.blue(fg)
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

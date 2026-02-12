package com.dumbphone.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherUtil {

    data class WeatherData(
        val temperature: Int,
        val unit: String,
        val condition: String
    )

    /** Fetch current weather from Open-Meteo. Must be called on a background thread. */
    fun fetch(latitude: Double, longitude: Double): WeatherData? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code&timezone=auto"
            )
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val responseCode = conn.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) return null

            val response = conn.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m").toInt()
            val code = current.getInt("weather_code")
            val unit = json.getJSONObject("current_units").getString("temperature_2m")

            WeatherData(temp, unit, codeToCondition(code))
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun codeToCondition(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Hail storm"
        else -> "Unknown"
    }
}

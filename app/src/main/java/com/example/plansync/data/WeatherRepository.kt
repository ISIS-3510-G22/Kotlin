package com.example.plansync.data

import android.util.ArrayMap
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherRepository {

    private val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=4.711&longitude=-74.0721&daily=precipitation_probability_max&timezone=America%2FBogota&forecast_days=16"

    suspend fun getRainForecast(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        val fresh = readCache(onlyIfFresh = true)
        if (fresh != null) {
            return@withContext Result.success(fresh)
        }

        val result = fetchForecast()
        result.onSuccess { forecast -> saveCache(forecast) }

        val stale = readCache(onlyIfFresh = false)
        if (result.isFailure && stale != null) {
            return@withContext Result.success(stale)
        }
        result
    }

    private fun fetchForecast(): Result<Map<String, Int>> = runCatching {
        val connection = URL(forecastUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        try {
            if (connection.responseCode != 200) throw Exception("Weather request failed.")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val daily = JSONObject(body).getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val chances = daily.getJSONArray("precipitation_probability_max")
            val forecast = mutableMapOf<String, Int>()
            for (i in 0 until dates.length()) {
                if (!chances.isNull(i)) {
                    forecast[dates.getString(i)] = chances.getInt(i)
                }
            }
            forecast
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(onlyIfFresh: Boolean): Map<String, Int>? = synchronized(rainCache) {
        if (rainCache.isEmpty()) return null
        val age = System.currentTimeMillis() - loadedAt
        if (onlyIfFresh && age > CACHE_TTL_MS) return null
        rainCache.toMap()
    }

    private fun saveCache(forecast: Map<String, Int>) = synchronized(rainCache) {
        rainCache.clear()
        rainCache.putAll(forecast)
        loadedAt = System.currentTimeMillis()
    }

    companion object {
        private const val CACHE_TTL_MS = 3 * 60 * 60 * 1000L
        private val rainCache = ArrayMap<String, Int>(16)
        private var loadedAt = 0L
    }
}

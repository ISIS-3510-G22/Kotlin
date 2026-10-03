package com.example.plansync.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WeatherRepository {

    private val forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=4.711&longitude=-74.0721&daily=precipitation_probability_max&timezone=America%2FBogota&forecast_days=16"

    suspend fun getRainForecast(): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
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
    }
}

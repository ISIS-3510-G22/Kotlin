package com.example.plansync.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

// Data layer — reads plan-creation trends from Firestore, falls back to local file.
class InsightsRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    data class PopularDay(val day: String, val count: Int)

    private val cacheFile = "popular_days.json"

    // Returns days sorted by count desc; empty if Firestore and cache are both unavailable.
    // Forces Source.SERVER so stale Firebase cache never hides updated BQ results.
    suspend fun getPopularDays(): List<PopularDay> {
        return try {
            val doc = withTimeout(8_000) {
                db.collection("insights").document("popular_days").get(Source.SERVER).await()
            }
            if (!doc.exists()) return readFromCache()
            val raw = (doc.get("days") as? Map<*, *>) ?: return readFromCache()
            val days = raw.mapNotNull { (k, v) ->
                val day = k as? String ?: return@mapNotNull null
                val count = (v as? Number)?.toInt() ?: return@mapNotNull null
                PopularDay(day, count)
            }.sortedByDescending { it.count }
            writeToCache(days)
            days
        } catch (_: Exception) {
            readFromCache()
        }
    }

    private suspend fun readFromCache(): List<PopularDay> {
        val json = storage.read(cacheFile) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) {
                val obj = array.getJSONObject(it)
                PopularDay(obj.getString("day"), obj.getInt("count"))
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun writeToCache(days: List<PopularDay>) {
        val array = JSONArray(days.map { JSONObject().put("day", it.day).put("count", it.count) })
        storage.write(cacheFile, array.toString())
    }
}

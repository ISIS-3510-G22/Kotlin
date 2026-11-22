package com.example.plansync.data

import android.util.LruCache
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class InsightsRepository(
    private val db: FirebaseFirestore = FirebaseProvider.firestore,
    private val storage: LocalStorageProvider = LocalStorageProvider
) {

    data class PopularDay(val day: String, val count: Int)

    data class AbandonmentStep(val step: Int, val count: Int)

    suspend fun getPopularDays(): List<PopularDay> {
        return try {
            val doc = withTimeout(8_000) {
                db.collection("insights").document("popular_days").get(Source.SERVER).await()
            }
            if (!doc.exists()) return readPopularDaysCache()
            val raw = (doc.get("days") as? Map<*, *>) ?: return readPopularDaysCache()
            val days = raw.mapNotNull { (k, v) ->
                val day   = k as? String ?: return@mapNotNull null
                val count = (v as? Number)?.toInt() ?: return@mapNotNull null
                PopularDay(day, count)
            }.sortedByDescending { it.count }
            writePopularDaysCache(days)
            days
        } catch (_: Exception) {
            readPopularDaysCache()
        }
    }

    suspend fun getTopAbandonmentStep(): AbandonmentStep? {
        abandonmentCache[ABANDONMENT_KEY]?.let { (ts, value) ->
            if (System.currentTimeMillis() - ts < CACHE_TTL_MS) return value
        }
        return try {
            val doc = withTimeout(8_000) {
                db.collection("insights").document("create_plan_abandonment").get(Source.SERVER).await()
            }
            if (!doc.exists()) return readAbandonmentCache()
            val step  = (doc.get("topStep") as? Number)?.toInt() ?: return readAbandonmentCache()
            val count = (doc.get("abandonCount") as? Number)?.toInt() ?: 0
            val result = AbandonmentStep(step, count)
            writeAbandonmentCache(result)
            abandonmentCache.put(ABANDONMENT_KEY, System.currentTimeMillis() to result)
            result
        } catch (_: Exception) {
            readAbandonmentCache()
        }
    }

    private suspend fun readPopularDaysCache(): List<PopularDay> {
        val json = storage.read("popular_days.json") ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            List(array.length()) {
                val obj = array.getJSONObject(it)
                PopularDay(obj.getString("day"), obj.getInt("count"))
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun writePopularDaysCache(days: List<PopularDay>) {
        val array = JSONArray(days.map { JSONObject().put("day", it.day).put("count", it.count) })
        storage.write("popular_days.json", array.toString())
    }

    private suspend fun readAbandonmentCache(): AbandonmentStep? {
        val json = storage.read("create_plan_abandonment.json") ?: return null
        return runCatching {
            val obj = JSONObject(json)
            AbandonmentStep(obj.getInt("step"), obj.getInt("count"))
        }.getOrNull()
    }

    private suspend fun writeAbandonmentCache(step: AbandonmentStep) {
        val json = JSONObject().put("step", step.step).put("count", step.count).toString()
        storage.write("create_plan_abandonment.json", json)
    }

    companion object {
        private val abandonmentCache = LruCache<String, Pair<Long, AbandonmentStep?>>(1)
        private const val ABANDONMENT_KEY = "abandonment_step"
        private const val CACHE_TTL_MS = 60L * 60 * 1000
    }
}

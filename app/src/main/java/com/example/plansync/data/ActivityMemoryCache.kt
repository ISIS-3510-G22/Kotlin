package com.example.plansync.data

import android.util.LruCache
import com.example.plansync.model.Activity

object ActivityMemoryCache {

    private const val MAX_ACTIVITIES = 50

    private val cache = LruCache<String, Activity>(MAX_ACTIVITIES)

    fun get(activityId: String): Activity? = cache.get(activityId)

    fun put(activity: Activity) {
        cache.put(activity.id, activity)
    }

    fun remove(activityId: String) {
        cache.remove(activityId)
    }

    fun refresh(activities: List<Activity>) {
        val cachedIds = cache.snapshot().keys
        activities.filter { it.id in cachedIds }.forEach(::put)
    }

    fun clear() = cache.evictAll()
}

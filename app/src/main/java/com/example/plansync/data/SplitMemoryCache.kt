package com.example.plansync.data

import android.util.LruCache
import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement

object SplitMemoryCache {

    private const val MAX_PLANS = 30
    private const val TTL_MS = 2 * 60 * 1000L

    private data class Entry<T>(val value: T, val savedAt: Long = System.currentTimeMillis())

    private val expenses = LruCache<String, Entry<List<Expense>>>(MAX_PLANS)
    private val settlements = LruCache<String, Entry<List<Settlement>>>(MAX_PLANS)

    fun getExpenses(planId: String): List<Expense>? = expenses.get(planId)?.freshValue()

    fun putExpenses(planId: String, list: List<Expense>) {
        expenses.put(planId, Entry(list))
    }

    fun getSettlements(planId: String): List<Settlement>? = settlements.get(planId)?.freshValue()

    fun putSettlements(planId: String, list: List<Settlement>) {
        settlements.put(planId, Entry(list))
    }

    fun invalidate(planId: String) {
        expenses.remove(planId)
        settlements.remove(planId)
    }

    fun clear() {
        expenses.evictAll()
        settlements.evictAll()
    }

    private fun <T> Entry<T>.freshValue(): T? =
        value.takeIf { System.currentTimeMillis() - savedAt < TTL_MS }
}

package com.example.plansync.service

import com.example.plansync.model.Expense
import com.example.plansync.model.Settlement
import com.example.plansync.model.Split
import java.util.Date
import java.util.concurrent.TimeUnit

object SplitCalculator {

    private const val PAYMENT_TOLERANCE = 0.01
    private const val GRACE_DAYS = 3

    fun shareOf(expense: Expense, debtorId: String): Double =
        if (expense.splitMethod == "custom") expense.customAmounts[debtorId] ?: 0.0
        else expense.value / expense.splitAmongIds.size

    fun buildSplits(myId: String, expenses: List<Expense>, settlements: List<Settlement>): List<Split> =
        splitsFor(myId, netOwedByMe(myId, expenses), settlements, iAmDebtor = true)

    fun buildOwedToMe(myId: String, expenses: List<Expense>, settlements: List<Settlement>): List<Split> =
        splitsFor(myId, netOwedByMe(myId, expenses).mapValues { -it.value }, settlements, iAmDebtor = false)

    private fun netOwedByMe(myId: String, expenses: List<Expense>): Map<String, Double> {
        val net = mutableMapOf<String, Double>()
        for (expense in expenses) {
            if (expense.splitAmongIds.isEmpty()) continue
            for (debtorId in expense.splitAmongIds) {
                if (debtorId == expense.paidById) continue
                val share = shareOf(expense, debtorId)
                if (debtorId == myId) {
                    net[expense.paidById] = (net[expense.paidById] ?: 0.0) + share
                } else if (expense.paidById == myId) {
                    net[debtorId] = (net[debtorId] ?: 0.0) - share
                }
            }
        }
        return net
    }

    private fun splitsFor(
        myId: String,
        net: Map<String, Double>,
        settlements: List<Settlement>,
        iAmDebtor: Boolean
    ): List<Split> {
        fun paid(fromId: String, toId: String) =
            settlements.filter { it.fromId == fromId && it.toId == toId }.sumOf { it.amount }

        val personIds = net.keys + settlements.flatMap { listOf(it.fromId, it.toId) }
        return personIds.filter { it.isNotBlank() && it != myId }.mapNotNull { personId ->
            val debtorId = if (iAmDebtor) myId else personId
            val creditorId = if (iAmDebtor) personId else myId
            val amount = (net[personId] ?: 0.0) + paid(creditorId, debtorId)
            if (amount <= PAYMENT_TOLERANCE) return@mapNotNull null
            val paidSoFar = paid(debtorId, creditorId)
            Split(personId, amount, paidSoFar, paidSoFar >= amount - PAYMENT_TOLERANCE)
        }
    }

    fun daysOverdue(planDate: Date?, now: Long = System.currentTimeMillis()): Int? {
        val date = planDate ?: return null
        val days = TimeUnit.MILLISECONDS.toDays(now - date.time).toInt() - GRACE_DAYS
        return days.takeIf { it > 0 }
    }
}

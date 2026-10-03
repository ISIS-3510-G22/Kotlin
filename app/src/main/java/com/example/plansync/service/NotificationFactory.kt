package com.example.plansync.service

import com.example.plansync.data.AnalyticsRepository
import com.example.plansync.model.Notification

object NotificationFactory {

    fun create(
        event: String,
        planName: String = "",
        personName: String = "",
        amount: Double = 0.0
    ): Notification? = when (event) {
        AnalyticsRepository.RSVP_CONFIRMED -> Notification.Rsvp(planName, "you're going")
        AnalyticsRepository.RSVP_DECLINED -> Notification.Rsvp(planName, "you're not going")
        AnalyticsRepository.RSVP_DEFERRED -> Notification.Rsvp(planName, "you'll decide later")
        AnalyticsRepository.INVITE_SENT -> Notification.Invite(personName)
        AnalyticsRepository.EXPENSE_ADDED -> Notification.Expense(planName, amount)
        else -> null
    }
}

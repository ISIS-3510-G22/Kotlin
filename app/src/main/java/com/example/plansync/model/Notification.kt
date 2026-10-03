package com.example.plansync.model

sealed class Notification(val title: String, val message: String) {

    data class Rsvp(val planName: String, val response: String) :
        Notification("RSVP updated", "$planName: $response")

    data class Invite(val inviteeName: String) :
        Notification("Invite sent", "You invited $inviteeName to your plan")

    data class Expense(val planName: String, val amount: Double) :
        Notification("New expense", "An expense of $${"%,.0f".format(amount)} was added to $planName")
}

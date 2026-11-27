package com.example.plansync.model

import java.util.UUID

sealed class PendingAction {
    abstract val id: String

    data class SaveActivity(
        val activityId: String,
        val isNew: Boolean,
        val name: String,
        val address: String,
        val price: Int,
        val tags: List<String>,
        val newCustomTags: List<String>,
        val notes: String,
        val visibility: ActivityVisibility,
        val lat: Double?,
        val lng: Double?,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class SetLiked(
        val activityId: String,
        val liked: Boolean,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class DeleteActivity(
        val activityId: String,
        val photoUrl: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class UpdateProfile(
        val name: String,
        val lastName: String,
        val phone: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class AddPaymentMethod(
        val method: PaymentMethod,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class DeletePaymentMethod(
        val method: PaymentMethod,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class SetRsvp(
        val planId: String,
        val rsvp: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class SendInvite(
        val planId: String,
        val contactId: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class CreatePlan(
        val planId: String = UUID.randomUUID().toString(),
        val name: String,
        val dateMillis: Long,
        val hour: Int,
        val minute: Int,
        val isPublic: Boolean,
        val activityIds: List<String>,
        val tags: List<String>,
        val origin: String?,
        val sourceId: String?,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class AddExpense(
        val planId: String,
        val expenseId: String,
        val name: String,
        val value: Double,
        val paidById: String,
        val splitAmongIds: List<String>,
        val createdAtMillis: Long,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class UpdateExpense(
        val planId: String,
        val expenseId: String,
        val name: String,
        val value: Double,
        val paidById: String,
        val resetSplit: Boolean = false,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class DeleteExpense(
        val planId: String,
        val expenseId: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class AddSettlement(
        val planId: String,
        val fromId: String,
        val toId: String,
        val amount: Double,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()

    data class DeleteMySettlementsTo(
        val planId: String,
        val fromId: String,
        val toId: String,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()
}

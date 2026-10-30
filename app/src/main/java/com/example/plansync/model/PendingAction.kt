package com.example.plansync.model

import java.util.UUID

sealed class PendingAction {
    abstract val id: String

    data class SetLiked(
        val activityId: String,
        val liked: Boolean,
        override val id: String = UUID.randomUUID().toString()
    ) : PendingAction()
}

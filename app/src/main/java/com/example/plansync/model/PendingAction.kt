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
}

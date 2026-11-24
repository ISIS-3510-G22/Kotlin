package com.example.plansync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val id: String,
    val planName: String,
    val selectedDateMillis: Long,
    val selectedHour: Int,
    val selectedMinute: Int,
    val locationText: String,
    val participantCount: Int,
    val isPublic: Boolean,
    val activityIds: String,
    val origin: String,
    val sourceId: String,
    val lastStep: Int,
    val updatedAt: Long
)

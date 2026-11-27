package com.example.plansync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts WHERE uid = :uid ORDER BY updatedAt DESC")
    suspend fun getAll(uid: String): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE id = :id AND uid = :uid LIMIT 1")
    suspend fun getById(id: String, uid: String): DraftEntity?

    @Query("SELECT * FROM drafts WHERE uid = :uid ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(uid: String): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}

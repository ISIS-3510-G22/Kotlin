package com.example.plansync.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DraftEntity?

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): DraftEntity?

    @Upsert
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)
}

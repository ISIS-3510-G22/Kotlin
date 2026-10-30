package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Local storage strategy — Room DB singleton for Plan Detail offline caching.
@Database(
    entities = [PlanEntity::class, PlanActivityEntity::class, ParticipantEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PlanDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao

    companion object {
        @Volatile private var instance: PlanDatabase? = null

        fun get(context: Context): PlanDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PlanDatabase::class.java,
                "plansync.db"
            ).build().also { instance = it }
        }
    }
}

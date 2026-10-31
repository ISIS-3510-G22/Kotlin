package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ActivityEntity::class, ActivityLikeEntity::class, ActivityTagEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ActivityDatabase : RoomDatabase() {

    abstract fun activityDao(): ActivityDao

    companion object {
        private lateinit var instance: ActivityDatabase

        fun init(context: Context) {
            if (::instance.isInitialized) return
            instance = Room.databaseBuilder(
                context.applicationContext,
                ActivityDatabase::class.java,
                "activities.db"
            ).build()
        }

        val dao: ActivityDao get() = instance.activityDao()
    }
}

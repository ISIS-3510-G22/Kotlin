package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DraftEntity::class], version = 1, exportSchema = false)
abstract class DraftDatabase : RoomDatabase() {

    abstract fun draftDao(): DraftDao

    companion object {
        private lateinit var instance: DraftDatabase

        fun init(context: Context) {
            if (::instance.isInitialized) return
            instance = Room.databaseBuilder(
                context.applicationContext,
                DraftDatabase::class.java,
                "drafts.db"
            ).build()
        }

        val dao: DraftDao get() = instance.draftDao()
    }
}

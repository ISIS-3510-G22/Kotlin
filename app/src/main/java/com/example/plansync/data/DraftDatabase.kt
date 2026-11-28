package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DraftEntity::class], version = 2, exportSchema = false)
abstract class DraftDatabase : RoomDatabase() {

    abstract fun draftDao(): DraftDao

    companion object {
        private lateinit var instance: DraftDatabase

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE drafts ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun init(context: Context) {
            if (::instance.isInitialized) return
            instance = Room.databaseBuilder(
                context.applicationContext,
                DraftDatabase::class.java,
                "drafts.db"
            ).addMigrations(MIGRATION_1_2).build()
        }

        val dao: DraftDao get() = instance.draftDao()
    }
}

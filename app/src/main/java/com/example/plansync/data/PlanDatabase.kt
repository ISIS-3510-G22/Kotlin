package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Local storage strategy — Room DB singleton for Plan Detail offline caching.
@Database(
    entities = [PlanEntity::class, PlanActivityEntity::class, ParticipantEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PlanDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao

    companion object {
        @Volatile private var instance: PlanDatabase? = null

        // Recreate plan_activities with composite primary key (planId, id).
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `plan_activities`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plan_activities` (
                        `planId` TEXT NOT NULL,
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `iconType` TEXT NOT NULL,
                        `price` INTEGER NOT NULL,
                        PRIMARY KEY(`planId`, `id`)
                    )
                """.trimIndent())
            }
        }

        fun get(context: Context): PlanDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PlanDatabase::class.java,
                "plansync.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

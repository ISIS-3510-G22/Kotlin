package com.example.plansync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CrewGroupEntity::class, CrewFriendEntity::class, CrewMemberEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CrewDatabase : RoomDatabase() {

    abstract fun crewDao(): CrewDao

    companion object {
        @Volatile private var instance: CrewDatabase? = null

        fun get(context: Context): CrewDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CrewDatabase::class.java,
                "crew.db"
            ).build().also { instance = it }
        }
    }
}

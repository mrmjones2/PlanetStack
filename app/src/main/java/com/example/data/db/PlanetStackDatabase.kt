package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.StackedProject

@Database(entities = [StackedProject::class], version = 1, exportSchema = false)
abstract class PlanetStackDatabase : RoomDatabase() {
    abstract fun stackedProjectDao(): StackedProjectDao

    companion object {
        @Volatile
        private var INSTANCE: PlanetStackDatabase? = null

        fun getDatabase(context: Context): PlanetStackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PlanetStackDatabase::class.java,
                    "planet_stack_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

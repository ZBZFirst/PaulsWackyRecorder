package com.example.templei.feature.screen4

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ColumnTemplateEntity::class,
        ColumnEntity::class,
        RowEntity::class,
        CellEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class Screen4Database : RoomDatabase() {
    abstract fun screen4Dao(): Screen4Dao

    companion object {
        @Volatile
        private var instance: Screen4Database? = null

        fun getInstance(context: Context): Screen4Database {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    Screen4Database::class.java,
                    "screen4_measurements.db",
                ).build().also { instance = it }
            }
        }
    }
}

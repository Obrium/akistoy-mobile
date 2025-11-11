package com.akiestoy.beacons.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.akiestoy.beacons.model.user.User
import com.akiestoy.beacons.model.Zone

/**
 * Base de datos principal de la aplicación
 */
@Database(
    entities = [User::class, PendingEvent::class, Zone::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun pendingEventDao(): PendingEventDao
    abstract fun zoneDao(): ZoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "akiestoy_database"
                )
                .fallbackToDestructiveMigration() // Por ahora, recrear DB si hay cambio de esquema
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

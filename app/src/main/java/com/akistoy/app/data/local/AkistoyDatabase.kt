package com.akistoy.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.akistoy.app.data.local.dao.TrustedBeaconDao
import com.akistoy.app.data.local.entity.TrustedBeaconEntity

@Database(
    entities = [TrustedBeaconEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AkistoyDatabase : RoomDatabase() {
    abstract fun trustedBeaconDao(): TrustedBeaconDao
}

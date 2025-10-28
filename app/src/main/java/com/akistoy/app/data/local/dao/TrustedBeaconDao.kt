package com.akistoy.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.akistoy.app.data.local.entity.TrustedBeaconEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedBeaconDao {

    @Query("SELECT * FROM trusted_beacons ORDER BY addedAt DESC")
    fun getAllTrustedBeacons(): Flow<List<TrustedBeaconEntity>>

    @Query("SELECT * FROM trusted_beacons WHERE isEnabled = 1")
    suspend fun getEnabledBeacons(): List<TrustedBeaconEntity>

    @Query("SELECT beaconId FROM trusted_beacons WHERE isEnabled = 1")
    suspend fun getEnabledBeaconIds(): List<String>

    @Query("SELECT uuid FROM trusted_beacons WHERE isEnabled = 1 AND uuid IS NOT NULL")
    suspend fun getEnabledBeaconUuids(): List<String>

    @Query("SELECT * FROM trusted_beacons WHERE beaconId = :beaconId LIMIT 1")
    suspend fun getBeaconById(beaconId: String): TrustedBeaconEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacon(beacon: TrustedBeaconEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacons(beacons: List<TrustedBeaconEntity>)

    @Update
    suspend fun updateBeacon(beacon: TrustedBeaconEntity)

    @Delete
    suspend fun deleteBeacon(beacon: TrustedBeaconEntity)

    @Query("DELETE FROM trusted_beacons WHERE beaconId = :beaconId")
    suspend fun deleteBeaconById(beaconId: String)

    @Query("DELETE FROM trusted_beacons")
    suspend fun deleteAllBeacons()

    @Query("UPDATE trusted_beacons SET isEnabled = :enabled WHERE beaconId = :beaconId")
    suspend fun setBeaconEnabled(beaconId: String, enabled: Boolean)
}

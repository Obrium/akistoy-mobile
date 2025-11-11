package com.akiestoy.beacons.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.akiestoy.beacons.model.Zone
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de zonas en la base de datos
 */
@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones")
    fun getAllZones(): Flow<List<Zone>>

    @Query("SELECT * FROM zones")
    suspend fun getAllZonesOnce(): List<Zone>

    @Query("SELECT * FROM zones WHERE id = :zoneId")
    suspend fun getZoneById(zoneId: String): Zone?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: Zone)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<Zone>)

    @Query("DELETE FROM zones")
    suspend fun deleteAll()

    @Query("DELETE FROM zones WHERE id = :zoneId")
    suspend fun deleteZone(zoneId: String)
}


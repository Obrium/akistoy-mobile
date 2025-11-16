package com.akiestoy.beacons.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.akiestoy.beacons.model.RegisteredBeacon
import kotlinx.coroutines.flow.Flow

/**
 * DAO para acceder a los beacons registrados en la base de datos
 */
@Dao
interface RegisteredBeaconDao {

    @Query("SELECT * FROM registered_beacons WHERE status = 'active'")
    fun getAllActiveBeacons(): Flow<List<RegisteredBeacon>>

    @Query("SELECT * FROM registered_beacons WHERE status = 'active'")
    suspend fun getAllActiveBeaconsOnce(): List<RegisteredBeacon>

    @Query("SELECT * FROM registered_beacons")
    suspend fun getAllBeaconsOnce(): List<RegisteredBeacon>

    @Query("SELECT * FROM registered_beacons WHERE zoneId = :zoneId AND status = 'active'")
    fun getBeaconsByZone(zoneId: String): Flow<List<RegisteredBeacon>>

    @Query("SELECT * FROM registered_beacons WHERE advUuid = :uuid AND major = :major AND minor = :minor LIMIT 1")
    suspend fun getBeaconByIdentifiers(uuid: String, major: Int, minor: Int): RegisteredBeacon?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacons(beacons: List<RegisteredBeacon>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeacon(beacon: RegisteredBeacon)

    @Query("DELETE FROM registered_beacons")
    suspend fun deleteAll()

    @Query("DELETE FROM registered_beacons WHERE zoneId = :zoneId")
    suspend fun deleteBeaconsByZone(zoneId: String)
}

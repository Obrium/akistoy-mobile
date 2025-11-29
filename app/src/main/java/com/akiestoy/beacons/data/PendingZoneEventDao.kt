package com.akiestoy.beacons.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para gestionar eventos de zona pendientes de envío
 * Usado para cola offline cuando no hay conexión a internet
 */
@Dao
interface PendingZoneEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PendingZoneEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PendingZoneEvent>)

    @Update
    suspend fun update(event: PendingZoneEvent)

    /**
     * Obtiene eventos reintentables (retryCount < 3) ordenados por antigüedad
     */
    @Query("SELECT * FROM pending_zone_events WHERE retryCount < 3 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getRetryable(limit: Int = 50): List<PendingZoneEvent>

    /**
     * Obtiene todos los eventos pendientes ordenados por timestamp original
     */
    @Query("SELECT * FROM pending_zone_events ORDER BY timestamp ASC")
    suspend fun getAll(): List<PendingZoneEvent>

    @Query("DELETE FROM pending_zone_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_zone_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_zone_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_zone_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_zone_events")
    fun countFlow(): Flow<Int>

    /**
     * Elimina eventos más antiguos que el timestamp dado (limpieza de eventos viejos)
     */
    @Query("DELETE FROM pending_zone_events WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Incrementa el contador de reintentos y actualiza lastRetryAt
     */
    @Query("UPDATE pending_zone_events SET retryCount = retryCount + 1, lastRetryAt = :timestamp WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, timestamp: Long = System.currentTimeMillis())
}

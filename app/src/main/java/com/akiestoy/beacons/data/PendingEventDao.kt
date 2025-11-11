package com.akiestoy.beacons.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para gestionar eventos pendientes de envío
 */
@Dao
interface PendingEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: PendingEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<PendingEvent>)

    @Update
    suspend fun update(event: PendingEvent)

    @Query("SELECT * FROM pending_events ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOldest(limit: Int = 50): List<PendingEvent>

    @Query("SELECT * FROM pending_events WHERE retryCount < 3 ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getRetryable(limit: Int = 50): List<PendingEvent>

    @Query("DELETE FROM pending_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM pending_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM pending_events")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_events")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM pending_events WHERE createdAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}

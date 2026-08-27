package com.perry.intervaltimer.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY lastUsedAtMillis DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: String): WorkoutEntity?

    @Upsert
    suspend fun upsert(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Query("UPDATE workouts SET lastUsedAtMillis = :timestamp WHERE id = :id")
    suspend fun touchLastUsed(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int
}

package com.perry.intervaltimer.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.UUID

@Entity(tableName = "workouts")
@TypeConverters(Converters::class)
data class WorkoutEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** Optional single warm-up phase before the repeating rounds begin. 0 = skip. */
    val warmupSeconds: Int = 0,
    /** The steps that repeat every round, e.g. [Work 40s, Rest 20s]. */
    val steps: List<IntervalStep>,
    val rounds: Int = 1,
    /** Optional single cool-down phase after the repeating rounds end. 0 = skip. */
    val cooldownSeconds: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastUsedAtMillis: Long? = null
) {
    /** Total workout length in seconds, not counting the pre-workout "get ready" countdown (a global setting). */
    fun totalDurationSeconds(): Int =
        warmupSeconds + steps.sumOf { it.durationSeconds } * rounds + cooldownSeconds
}

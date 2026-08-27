package com.perry.intervaltimer.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One editable phase within a workout, e.g. "Sprint" / WORK / 30s.
 * Stored as JSON inside [WorkoutEntity.stepsJson] via [Converters] rather than as a
 * separate Room table, since steps only ever exist in the context of their workout.
 */
@Serializable
data class IntervalStep(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val type: IntervalType,
    val durationSeconds: Int
) {
    companion object {
        fun default(type: IntervalType, durationSeconds: Int = 30): IntervalStep =
            IntervalStep(label = type.label, type = type, durationSeconds = durationSeconds)
    }
}

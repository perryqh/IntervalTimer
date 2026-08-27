package com.perry.intervaltimer.data

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSteps(steps: List<IntervalStep>): String = json.encodeToString(steps)

    @TypeConverter
    fun toSteps(data: String): List<IntervalStep> =
        if (data.isBlank()) emptyList() else json.decodeFromString(data)
}

package com.perry.intervaltimer.ui.theme

import androidx.compose.ui.graphics.Color
import com.perry.intervaltimer.data.IntervalType

val AccentGreen = Color(0xFF00E5A8)
val AccentGreenDark = Color(0xFF00B387)

val WorkColor = Color(0xFFFF5A5F)
val RestColor = Color(0xFF3D8BFD)
val WarmupColor = Color(0xFFFFC145)
val CooldownColor = Color(0xFF8E7CFF)
val PrepareColor = Color(0xFFAEB4BD)

val DarkBackground = Color(0xFF121417)
val DarkSurface = Color(0xFF1B1F23)

fun IntervalType.phaseColor(): Color = when (this) {
    IntervalType.PREPARE -> PrepareColor
    IntervalType.WARMUP -> WarmupColor
    IntervalType.WORK -> WorkColor
    IntervalType.REST -> RestColor
    IntervalType.COOLDOWN -> CooldownColor
}

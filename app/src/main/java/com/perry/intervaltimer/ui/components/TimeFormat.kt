package com.perry.intervaltimer.ui.components

/** "0:45" for anything under a minute is unusual for a workout timer; bare seconds read faster. */
fun formatSeconds(totalSeconds: Int): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return if (minutes > 0) "%d:%02d".format(minutes, seconds) else seconds.toString()
}

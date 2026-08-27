package com.perry.intervaltimer.data

/**
 * The kind of phase an interval step represents. PREPARE is synthesized by the timer
 * engine itself (from [com.perry.intervaltimer.data.TimerSettings.prepareSeconds]) and
 * never stored as part of a saved workout's step list.
 */
enum class IntervalType(val label: String) {
    PREPARE("Get Ready"),
    WARMUP("Warm Up"),
    WORK("Work"),
    REST("Rest"),
    COOLDOWN("Cool Down")
}

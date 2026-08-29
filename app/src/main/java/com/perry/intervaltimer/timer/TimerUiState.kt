package com.perry.intervaltimer.timer

import com.perry.intervaltimer.data.IntervalType

data class TimerUiState(
    val isActive: Boolean = false,
    val isRunning: Boolean = false,
    val isFinished: Boolean = false,
    val workoutId: String = "",
    val workoutName: String = "",
    val currentStepLabel: String = "",
    val currentStepType: IntervalType = IntervalType.WORK,
    val remainingSeconds: Int = 0,
    val currentStepTotalSeconds: Int = 0,
    val stepIndex: Int = 0,
    val totalSteps: Int = 0,
    val roundNumber: Int = 1,
    val totalRounds: Int = 1,
    val nextStepLabel: String? = null,
    val nextStepType: IntervalType? = null,
    val isCountdownWindow: Boolean = false,
    val elapsedTotalSeconds: Int = 0,
    val totalWorkoutSeconds: Int = 0
) {
    /** 0f..1f progress through the current phase, for a ring/bar. */
    val stepProgress: Float
        get() = if (currentStepTotalSeconds <= 0) 0f else
            1f - (remainingSeconds.toFloat() / currentStepTotalSeconds.toFloat()).coerceIn(0f, 1f)

    val workoutProgress: Float
        get() = if (totalWorkoutSeconds <= 0) 0f else
            (elapsedTotalSeconds.toFloat() / totalWorkoutSeconds.toFloat()).coerceIn(0f, 1f)
}

/** One-shot side-effect events; consumed by [com.perry.intervaltimer.timer.CueController]. */
sealed interface CueEvent {
    data class Tick(val secondsRemaining: Int) : CueEvent
    data class PhaseChange(val newType: IntervalType) : CueEvent
    data object Finished : CueEvent
}

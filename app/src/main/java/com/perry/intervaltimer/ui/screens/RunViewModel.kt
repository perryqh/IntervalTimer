package com.perry.intervaltimer.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.perry.intervaltimer.timer.TimerEngine
import com.perry.intervaltimer.timer.TimerService
import com.perry.intervaltimer.timer.TimerUiState
import kotlinx.coroutines.flow.StateFlow

class RunViewModel(
    private val appContext: Context,
    engine: TimerEngine,
    workoutId: String
) : ViewModel() {

    val uiState: StateFlow<TimerUiState> = engine.uiState

    init {
        val state = engine.uiState.value
        val alreadyThisWorkout = state.isActive && !state.isFinished && state.workoutId == workoutId
        if (!alreadyThisWorkout) {
            sendAction(TimerService.ACTION_START, workoutId)
        }
    }

    fun pause() = sendAction(TimerService.ACTION_PAUSE)
    fun resume() = sendAction(TimerService.ACTION_RESUME)
    fun skip() = sendAction(TimerService.ACTION_SKIP)
    fun stop() = sendAction(TimerService.ACTION_STOP)

    private fun sendAction(action: String, workoutId: String? = null) {
        val intent = Intent(appContext, TimerService::class.java).setAction(action)
        if (workoutId != null) intent.putExtra(TimerService.EXTRA_WORKOUT_ID, workoutId)
        ContextCompat.startForegroundService(appContext, intent)
    }
}

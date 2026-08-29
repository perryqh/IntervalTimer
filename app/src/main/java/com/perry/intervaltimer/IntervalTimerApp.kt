package com.perry.intervaltimer

import android.app.Application
import com.perry.intervaltimer.data.AppDatabase
import com.perry.intervaltimer.data.SettingsRepository
import com.perry.intervaltimer.data.WorkoutRepository
import com.perry.intervaltimer.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Small hand-rolled service locator instead of a DI framework — the object graph here is
 * tiny (one DB, two repositories, one timer engine) so Hilt would be pure ceremony.
 */
class IntervalTimerApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var workoutRepository: WorkoutRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var timerEngine: TimerEngine
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        workoutRepository = WorkoutRepository(db.workoutDao())
        settingsRepository = SettingsRepository(this)
        timerEngine = TimerEngine(applicationScope)

        applicationScope.launch {
            workoutRepository.seedIfEmpty()
        }
    }

    companion object {
        fun from(context: android.content.Context): IntervalTimerApp =
            context.applicationContext as IntervalTimerApp
    }
}

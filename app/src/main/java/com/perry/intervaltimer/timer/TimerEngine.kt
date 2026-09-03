package com.perry.intervaltimer.timer

import android.os.SystemClock
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.data.TimerSettings
import com.perry.intervaltimer.data.WorkoutEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Application-scoped, drift-resistant countdown engine. Runs entirely on [elapsedRealtimeMs]
 * (monotonic, survives wall-clock changes and screen-off) rather than accumulating tick durations,
 * so a slow tick loop or a paused CPU never causes the displayed time to drift from real time.
 * [elapsedRealtimeMs] defaults to [SystemClock.elapsedRealtime] and is only overridden in tests,
 * where it's wired to a [kotlinx.coroutines.test.TestCoroutineScheduler]'s virtual clock so the
 * tick loop's `delay()` calls and "elapsed time" stay in lockstep without real waiting.
 *
 * Owns no Android UI/service state — [com.perry.intervaltimer.timer.TimerService] observes
 * [uiState] and [cueEvents] and is responsible for the foreground notification and audio/vibration.
 */
class TimerEngine(
    private val scope: CoroutineScope,
    private val elapsedRealtimeMs: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    private val _cueEvents = MutableSharedFlow<CueEvent>(extraBufferCapacity = 8)
    val cueEvents: SharedFlow<CueEvent> = _cueEvents.asSharedFlow()

    private var tickJob: Job? = null
    private var settings: TimerSettings = TimerSettings()
    private var steps: List<RunnableStep> = emptyList()
    private var currentIndex = 0
    private var workoutId: String = ""
    private var workoutName: String = ""
    private var totalWorkoutSeconds: Int = 0

    private var phaseStartAtElapsedMs = 0L
    private var phaseDurationMs = 0L
    private var pausedRemainingMs: Long? = null
    private var lastAnnouncedSecond = Int.MIN_VALUE

    private data class RunnableStep(
        val label: String,
        val type: IntervalType,
        val durationSeconds: Int,
        /** 1-based round number for body steps, 0 for prepare/warmup/cooldown. */
        val roundNumber: Int,
        val totalRounds: Int
    )

    fun start(workout: WorkoutEntity, settings: TimerSettings) {
        tickJob?.cancel()
        this.settings = settings
        this.steps = buildSteps(workout, settings)
        this.workoutId = workout.id
        this.workoutName = workout.name
        this.totalWorkoutSeconds = steps.sumOf { it.durationSeconds }
        this.currentIndex = 0

        if (steps.isEmpty()) {
            this.workoutId = ""
            _uiState.value = TimerUiState()
            return
        }

        beginStep(announce = true)
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    fun pause() {
        if (pausedRemainingMs != null || steps.isEmpty() || _uiState.value.isFinished) return
        pausedRemainingMs = remainingMsNow()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun resume() {
        if (_uiState.value.isFinished) return
        val remaining = pausedRemainingMs ?: return
        phaseStartAtElapsedMs = elapsedRealtimeMs()
        phaseDurationMs = remaining
        pausedRemainingMs = null
        _uiState.update { it.copy(isRunning = true) }
    }

    fun skipToNext() {
        if (steps.isEmpty() || _uiState.value.isFinished) return
        val wasPaused = pausedRemainingMs != null
        advance()
        // advance() -> beginStep() always starts the new phase running; if the user had paused,
        // re-pause immediately so Skip doesn't silently resume the workout. Skip past the last
        // step ends the run (finish()), where "paused" no longer has meaning, so don't re-pause then.
        if (wasPaused && currentIndex < steps.size) {
            pause()
        }
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        steps = emptyList()
        currentIndex = 0
        workoutId = ""
        pausedRemainingMs = null
        _uiState.value = TimerUiState()
    }

    private fun buildSteps(workout: WorkoutEntity, settings: TimerSettings): List<RunnableStep> {
        val list = mutableListOf<RunnableStep>()
        if (settings.prepareSeconds > 0) {
            list += RunnableStep(IntervalType.PREPARE.label, IntervalType.PREPARE, settings.prepareSeconds, 0, 0)
        }
        if (workout.warmupSeconds > 0) {
            list += RunnableStep(IntervalType.WARMUP.label, IntervalType.WARMUP, workout.warmupSeconds, 0, 0)
        }
        if (workout.steps.isNotEmpty()) {
            for (round in 1..workout.rounds) {
                for (step in workout.steps) {
                    list += RunnableStep(step.label, step.type, step.durationSeconds, round, workout.rounds)
                }
            }
        }
        if (workout.cooldownSeconds > 0) {
            list += RunnableStep(IntervalType.COOLDOWN.label, IntervalType.COOLDOWN, workout.cooldownSeconds, 0, 0)
        }
        return list
    }

    private fun beginStep(announce: Boolean) {
        val step = steps[currentIndex]
        phaseStartAtElapsedMs = elapsedRealtimeMs()
        phaseDurationMs = step.durationSeconds * 1000L
        pausedRemainingMs = null
        lastAnnouncedSecond = Int.MIN_VALUE
        publishState()
        if (announce) {
            _cueEvents.tryEmit(CueEvent.PhaseChange(step.type))
        }
    }

    private fun tick() {
        if (steps.isEmpty() || pausedRemainingMs != null) return
        publishState()

        val remainingSeconds = ceilSeconds(remainingMsNow())
        if (remainingSeconds != lastAnnouncedSecond) {
            lastAnnouncedSecond = remainingSeconds
            // A 5-second lead-in on an 8-second phase eats most of it, so short phases (<=10s) cap
            // the lead-in at 3s — unless the user's own setting is already lower than that.
            val stepDurationSeconds = steps[currentIndex].durationSeconds
            val effectiveLeadSeconds = effectiveLeadSeconds(stepDurationSeconds)
            // Final lead-in window and the round-number milestones are announced independently —
            // the milestones are for giving a time check partway through a long interval, not for
            // the "about to end" cue, so they ignore the lead-in setting. The
            // `remainingSeconds > countdownLeadSeconds` guard just avoids double-announcing a value
            // (e.g. 10) that both ranges would otherwise cover. The `remainingSeconds != stepDurationSeconds`
            // guard skips a milestone that lands exactly on the phase's first tick (e.g. a 30s phase
            // hitting "30" immediately) — that's already covered by the phase-change announcement.
            if (remainingSeconds in 1..effectiveLeadSeconds ||
                (remainingSeconds in MILESTONE_SECONDS &&
                    remainingSeconds > settings.countdownLeadSeconds &&
                    remainingSeconds != stepDurationSeconds)
            ) {
                _cueEvents.tryEmit(CueEvent.Tick(remainingSeconds, steps[currentIndex].type))
            }
        }

        if (remainingMsNow() <= 0L) {
            advance()
        }
    }

    private fun advance() {
        currentIndex++
        if (currentIndex >= steps.size) {
            finish()
        } else {
            beginStep(announce = true)
        }
    }

    private fun finish() {
        tickJob?.cancel()
        tickJob = null
        _uiState.update {
            it.copy(
                isRunning = false,
                isFinished = true,
                remainingSeconds = 0,
                isCountdownWindow = false
            )
        }
        _cueEvents.tryEmit(CueEvent.Finished)
    }

    private fun remainingMsNow(): Long {
        val remaining = pausedRemainingMs
        if (remaining != null) return remaining
        val elapsed = elapsedRealtimeMs() - phaseStartAtElapsedMs
        return (phaseDurationMs - elapsed).coerceAtLeast(0L)
    }

    private fun publishState() {
        val step = steps.getOrNull(currentIndex) ?: return
        val remainingMs = remainingMsNow()
        val remainingSeconds = ceilSeconds(remainingMs)
        val next = steps.getOrNull(currentIndex + 1)
        val elapsedBefore = steps.take(currentIndex).sumOf { it.durationSeconds }
        val elapsedInStep = (step.durationSeconds - remainingSeconds).coerceAtLeast(0)

        _uiState.update {
            it.copy(
                isActive = true,
                isRunning = pausedRemainingMs == null,
                isFinished = false,
                workoutId = workoutId,
                workoutName = workoutName,
                currentStepLabel = step.label,
                currentStepType = step.type,
                remainingSeconds = remainingSeconds,
                currentStepTotalSeconds = step.durationSeconds,
                stepIndex = currentIndex,
                totalSteps = steps.size,
                roundNumber = step.roundNumber.coerceAtLeast(1),
                totalRounds = step.totalRounds.coerceAtLeast(1),
                nextStepLabel = next?.label,
                nextStepType = next?.type,
                isCountdownWindow = remainingSeconds in 1..effectiveLeadSeconds(step.durationSeconds),
                elapsedTotalSeconds = elapsedBefore + elapsedInStep,
                totalWorkoutSeconds = totalWorkoutSeconds
            )
        }
    }

    private fun effectiveLeadSeconds(stepDurationSeconds: Int): Int =
        if (stepDurationSeconds <= 10) minOf(settings.countdownLeadSeconds, 3)
        else settings.countdownLeadSeconds

    private fun ceilSeconds(millis: Long): Int = ((millis + 999L) / 1000L).toInt()

    companion object {
        private const val TICK_INTERVAL_MS = 100L
        private val MILESTONE_SECONDS = setOf(10, 20, 30, 40, 50, 60)
    }
}

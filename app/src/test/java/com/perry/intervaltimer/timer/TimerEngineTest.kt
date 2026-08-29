package com.perry.intervaltimer.timer

import com.perry.intervaltimer.data.IntervalStep
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.data.TimerSettings
import com.perry.intervaltimer.data.WorkoutEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers TimerEngine's cue-emission rules — the lead-in/milestone/suppression logic in tick()
 * is intricate enough now to be worth pinning down with tests. Runs entirely on virtual time:
 * elapsedRealtimeMs is wired to the TestCoroutineScheduler's clock, which stays in lockstep with
 * the tick loop's delay() calls, so a multi-minute workout "runs" instantly and deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimerEngineTest {

    private fun TestScope.newEngine(): TimerEngine =
        TimerEngine(scope = backgroundScope, elapsedRealtimeMs = { testScheduler.currentTime })

    private fun TestScope.collectEvents(engine: TimerEngine): List<CueEvent> {
        val events = mutableListOf<CueEvent>()
        backgroundScope.launch { engine.cueEvents.collect { events += it } }
        // engine.start() emits its first PhaseChange synchronously; without this the launched
        // collector wouldn't actually be subscribed yet (launch() only schedules it) and that
        // first event would be lost.
        runCurrent()
        return events
    }

    private fun workout(vararg steps: IntervalStep, rounds: Int = 1) =
        WorkoutEntity(name = "Test", steps = steps.toList(), rounds = rounds)

    private fun step(type: IntervalType, seconds: Int) =
        IntervalStep(label = type.label, type = type, durationSeconds = seconds)

    private fun TestScope.run(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `short phase caps the countdown lead-in at 3 seconds`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        // Work=8s is <=10s, so even the default 5s lead-in should be capped to 3s.
        engine.start(workout(step(IntervalType.WORK, 8)), TimerSettings(countdownLeadSeconds = 5, prepareSeconds = 0))
        run(8_500)

        val ticks = events.filterIsInstance<CueEvent.Tick>().map { it.secondsRemaining }
        assertEquals(listOf(3, 2, 1), ticks)
    }

    @Test
    fun `explicit lead-in below the 3s cap is still respected on a short phase`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        engine.start(workout(step(IntervalType.WORK, 8)), TimerSettings(countdownLeadSeconds = 2, prepareSeconds = 0))
        run(8_500)

        val ticks = events.filterIsInstance<CueEvent.Tick>().map { it.secondsRemaining }
        assertEquals(listOf(2, 1), ticks)
    }

    @Test
    fun `long phase announces milestones and the full lead-in, in descending order`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        // 25s passes through the 20s and 10s milestones plus the full 5s lead-in.
        engine.start(workout(step(IntervalType.WORK, 25)), TimerSettings(countdownLeadSeconds = 5, prepareSeconds = 0))
        run(25_500)

        val ticks = events.filterIsInstance<CueEvent.Tick>().map { it.secondsRemaining }
        assertEquals(listOf(20, 10, 5, 4, 3, 2, 1), ticks)
    }

    @Test
    fun `a milestone equal to the phase's own duration is suppressed`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        // 30s duration exactly matches a milestone, so "thirty" would double up with the
        // phase-change announcement and must be skipped; 20 and 10 still fire normally.
        engine.start(workout(step(IntervalType.WORK, 30)), TimerSettings(countdownLeadSeconds = 5, prepareSeconds = 0))
        run(30_500)

        val ticks = events.filterIsInstance<CueEvent.Tick>().map { it.secondsRemaining }
        assertEquals(listOf(20, 10, 5, 4, 3, 2, 1), ticks)
    }

    @Test
    fun `phase changes and finish fire once each, in step order`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        engine.start(
            workout(step(IntervalType.WORK, 5), step(IntervalType.REST, 4)),
            TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 3)
        )
        run(12_500)

        val phaseTypes = events.filterIsInstance<CueEvent.PhaseChange>().map { it.newType }
        assertEquals(listOf(IntervalType.PREPARE, IntervalType.WORK, IntervalType.REST), phaseTypes)
        assertEquals(1, events.count { it == CueEvent.Finished })
        assertTrue(engine.uiState.value.isFinished)
        assertFalse(engine.uiState.value.isRunning)
    }

    @Test
    fun `pause freezes the countdown and resume continues from where it left off`() = runTest {
        val engine = newEngine()
        engine.start(workout(step(IntervalType.WORK, 10)), TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0))

        run(4_000) // 4s elapsed -> 6s remaining
        assertEquals(6, engine.uiState.value.remainingSeconds)

        engine.pause()
        run(5_000) // time passes, but the countdown must not move while paused
        assertEquals(6, engine.uiState.value.remainingSeconds)
        assertFalse(engine.uiState.value.isRunning)

        engine.resume()
        run(1_000) // 1s after resuming -> 5s remaining, not 9s
        assertEquals(5, engine.uiState.value.remainingSeconds)
    }

    @Test
    fun `skipToNext jumps straight to the next step`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        engine.start(
            workout(step(IntervalType.WORK, 30), step(IntervalType.REST, 20)),
            TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0)
        )
        run(2_000)

        engine.skipToNext()
        runCurrent()

        assertEquals(IntervalType.REST, engine.uiState.value.currentStepType)
        assertEquals(20, engine.uiState.value.remainingSeconds)
        val phaseTypes = events.filterIsInstance<CueEvent.PhaseChange>().map { it.newType }
        assertEquals(listOf(IntervalType.WORK, IntervalType.REST), phaseTypes)
    }

    @Test
    fun `skip while paused stays paused on the next step`() = runTest {
        val engine = newEngine()
        engine.start(
            workout(step(IntervalType.WORK, 30), step(IntervalType.REST, 20)),
            TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0)
        )
        run(2_000)
        engine.pause()

        engine.skipToNext()
        runCurrent()

        assertEquals(IntervalType.REST, engine.uiState.value.currentStepType)
        assertEquals(20, engine.uiState.value.remainingSeconds)
        assertFalse(engine.uiState.value.isRunning)

        run(5_000)
        assertEquals(20, engine.uiState.value.remainingSeconds)
        assertFalse(engine.uiState.value.isRunning)
    }

    @Test
    fun `empty step list does not start`() = runTest {
        val engine = newEngine()
        engine.start(
            WorkoutEntity(name = "Empty", steps = emptyList()),
            TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0)
        )
        assertFalse(engine.uiState.value.isActive)
        assertEquals("", engine.uiState.value.workoutId)
    }

    @Test
    fun `warmup rounds and cooldown flatten in order`() = runTest {
        val engine = newEngine()
        val events = collectEvents(engine)
        val workout = WorkoutEntity(
            name = "Flatten",
            warmupSeconds = 2,
            steps = listOf(step(IntervalType.WORK, 2)),
            rounds = 2,
            cooldownSeconds = 2
        )
        engine.start(workout, TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0))
        assertEquals(workout.id, engine.uiState.value.workoutId)

        run(10_000)

        val phaseTypes = events.filterIsInstance<CueEvent.PhaseChange>().map { it.newType }
        assertEquals(
            listOf(IntervalType.WARMUP, IntervalType.WORK, IntervalType.WORK, IntervalType.COOLDOWN),
            phaseTypes
        )
        assertTrue(engine.uiState.value.isFinished)
        assertEquals(workout.id, engine.uiState.value.workoutId)
    }

    @Test
    fun `pause and skip are no-ops after finish`() = runTest {
        val engine = newEngine()
        engine.start(workout(step(IntervalType.WORK, 2)), TimerSettings(countdownLeadSeconds = 0, prepareSeconds = 0))
        run(3_000)
        assertTrue(engine.uiState.value.isFinished)

        engine.pause()
        engine.skipToNext()
        engine.resume()
        assertTrue(engine.uiState.value.isFinished)
        assertFalse(engine.uiState.value.isRunning)
    }
}

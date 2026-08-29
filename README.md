# Interval Timer

A personal interval-workout timer for Android — Kotlin + Jetpack Compose, no ads, no accounts,
nothing phoning home. Built to do what a good interval timer app does, plus the one thing most of
them hardcode: **how many seconds before an interval ends the countdown beeps start** is a setting,
not a constant.

## What's in it

- **Build your own interval workouts**: optional warm-up, a set of repeating steps (Work/Rest
  with custom labels), a round count, optional cool-down.
- **Configurable countdown lead-in**: Settings → "Countdown beeps start" (0–10s before each phase
  ends) and a separate "Get ready" lead-in before the workout begins.
- **Voice + ticks**: work/rest announcements and 10/20/…/60s time checks use recorded clips;
  the last few seconds of a phase are a rising tick tone. Vibration is limited to the lead-in
  window (and phase changes), not every milestone.
- **Runs in the background**: a foreground service keeps the countdown ticking, cueing, and
  vibrating with the screen off or another app open. Pause/resume/skip/stop from the notification,
  which also deep-links back to the live run. A partial wake lock is held while the timer is running
  so screen-off beeps are not skipped by Doze.
- **No ads, no accounts, no network permission at all.**

## Project layout

```
app/src/main/java/com/perry/intervaltimer/
  data/       Room entities/DAO, DataStore settings, repositories
  timer/      TimerEngine (drift-free countdown state machine), CueController (beep/voice/vibrate),
              TimerService (foreground service + notification + wake lock)
  ui/         Compose screens (workout list, edit, run, settings), navigation, theme
```

Architecture notes:
- No DI framework (Hilt, etc.) — the object graph is tiny (one DB, two repositories, one timer
  engine), all owned by `IntervalTimerApp` and handed to screens directly. See
  `ui/ViewModelFactory.kt` for the one bit of plumbing this needed.
- `TimerEngine` is application-scoped and knows nothing about Android UI or services — it's a
  plain state machine driven by `SystemClock.elapsedRealtime()` so it can't drift even if the
  100ms tick loop gets delayed. `TimerService` just observes it and turns its events into a
  notification + sound + vibration.
- Workout steps are stored as JSON inside the `workouts` Room table (via a `TypeConverter`) rather
  than a second table — they only ever exist in the context of their workout, so a join would be
  pure overhead.

## Building it

You'll need [Android Studio](https://developer.android.com/studio) (recent stable recommended) —
it bundles the Android SDK, an emulator, and everything else. First open:

1. `File → Open`, point it at this project's root folder (the one with `settings.gradle.kts`).
2. Let Gradle sync. First sync will download the Android Gradle Plugin, Kotlin, Compose, Room, etc.
   from Google's and Maven Central's repositories.
3. If sync complains about a missing SDK platform, click the offered "Install missing SDK
   package(s)" — it needs API 37 to compile and targets API 35.

Toolchain: **Gradle 9.5.0**, **Android Gradle Plugin 9.3.2**, **Kotlin 2.2.10**.

### Running on your phone

1. On the phone: Settings → About phone → tap "Build number" 7 times to unlock Developer Options,
   then Settings → Developer Options → enable USB debugging.
2. Plug the phone in, accept the "Allow USB debugging" prompt.
3. In Android Studio, pick your device from the device dropdown and hit Run (▶). No signing setup
   needed for this — debug builds install straight from Android Studio.
4. First launch will ask for notification permission (Android 13+) — grant it, or the running
   workout won't show a notification (it'll still run and beep, you just won't get pause/skip/stop
   controls from the lock screen).

You don't need a Play Console account, a keystore, or anything about "making it available" for
this — running it via USB debugging installs it permanently on your phone like any other app,
it just won't auto-update (you'll re-run from Android Studio when you change something).

### Tests

```
./gradlew :app:testDebugUnitTest
```

`TimerEngineTest` drives the countdown on virtual time (no real waiting) and covers cue-emission
rules, pause/resume, skip (including skip-while-paused), empty workouts, and warmup/rounds/cooldown
flattening.

## Known rough edges / things to tighten up next

- **Reordering steps** in the workout editor uses up/down arrow buttons, not drag-and-drop. Fine
  for the usual 2-4 step workouts; would want `reorderable`-style drag handling if you build
  longer sequences often.
- **No workout history** — it doesn't log completed sessions anywhere. Would be a natural next
  Room table (`completed_workouts`) if you want it.
- **Launcher icon** is a simple placeholder vector (a stopwatch glyph) — swap
  `app/src/main/res/drawable/ic_launcher_foreground.xml` for something nicer whenever you feel like it.
- The pause/skip/stop notification action icons all reuse the small status-bar icon rather than
  dedicated glyphs — cosmetic only, doesn't affect function.

## Extending it

- New interval types, colors, etc.: `data/IntervalType.kt` + `ui/theme/Color.kt`
  (`phaseColor()`).
- Changing what a "round" means or adding nested structures: `TimerEngine.buildSteps()` is the one
  place a `WorkoutEntity` gets flattened into the list the engine actually runs.
- Different cue sounds: `timer/CueController.kt` — voice clips are `R.raw.count_10`…`count_60`
  and `R.raw.phase_work` / `phase_rest`. Lead-in ticks are synthesized sine tones.

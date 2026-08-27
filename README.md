# Interval Timer

A personal interval-workout timer for Android — Kotlin + Jetpack Compose, no ads, no accounts,
nothing phoning home. Built to do what a good interval timer app does, plus the one thing most of
them hardcode: **how many seconds before an interval ends the countdown beeps start** is a setting,
not a constant.

## Status / how this was built

This project was scaffolded and hand-written in a cloud sandbox that has no Android SDK and no
network access to `dl.google.com` / Maven Central (both are blocked by the sandbox's egress
allowlist), so **it has not been compiled**. Every file was written carefully and reviewed by a
second independent pass specifically hunting for compile errors, API-version mismatches, and
logic bugs — but "reviewed by eye" is not "verified by a compiler." Treat the first build in
Android Studio as the real correctness check, and expect to fix at least small things (a stray
import, a Gradle version hiccup) on that first sync.

## What's in it

- **Build your own interval workouts**: optional warm-up, a set of repeating steps (Work/Rest/
  custom labels), a round count, optional cool-down.
- **Configurable countdown lead-in**: Settings → "Countdown beeps start" (0–10s before each phase
  ends) and a separate "Get ready" lead-in before the workout begins.
- **Runs in the background**: a foreground service keeps the countdown ticking, beeping (via
  `ToneGenerator`, no bundled sound files), and vibrating with the screen off or another app open,
  with pause/resume/skip/stop from the notification.
- **No ads, no accounts, no network permission at all.**

## Project layout

```
app/src/main/java/com/perry/intervaltimer/
  data/       Room entities/DAO, DataStore settings, repositories
  timer/      TimerEngine (drift-free countdown state machine), CueController (beep/vibrate),
              TimerService (foreground service + notification)
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

You'll need [Android Studio](https://developer.android.com/studio) (Koala or newer recommended) —
it bundles the Android SDK, an emulator, and everything else. First open:

1. `File → Open`, point it at this project's root folder (the one with `settings.gradle.kts`).
2. Let Gradle sync. First sync will download the Android Gradle Plugin, Kotlin, Compose, Room, etc.
   from Google's and Maven Central's repositories — this needs real internet access (not available
   in the sandbox this was built in), so do this from your normal machine.
3. If sync complains about a missing SDK platform, click the offered "Install missing SDK
   package(s)" — it'll fetch API 34 automatically.

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

### If Gradle sync fails on plugin resolution

The `gradle/wrapper/gradle-wrapper.properties` in this project points at Gradle 8.7, which is what
Android Gradle Plugin 8.5.2 expects. If Android Studio suggests a different (newer) AGP/Gradle
pairing during sync, it's fine to accept — just keep them compatible with each other per Google's
[AGP release notes](https://developer.android.com/build/releases/gradle-plugin).

## Known rough edges / things to tighten up next

- **Reordering steps** in the workout editor uses up/down arrow buttons, not drag-and-drop. Fine
  for the usual 2-4 step workouts; would want `reorderable`-style drag handling if you build
  longer sequences often.
- **No workout history** — it doesn't log completed sessions anywhere. Would be a natural next
  Room table (`completed_workouts`) if you want it.
- **No voice announcements** ("work", "rest") — just tones + vibration. `CueController` is the
  place to add `TextToSpeech` if you want that; the `CueEvent.PhaseChange(newType)` event already
  carries what's needed.
- **Launcher icon** is a simple placeholder vector I drew by hand (a stopwatch glyph) — swap
  `app/src/main/res/drawable/ic_launcher_foreground.xml` for something nicer whenever you feel like it.
- The pause/skip/stop notification action icons all reuse the small status-bar icon rather than
  dedicated glyphs — cosmetic only, doesn't affect function.

## Extending it

- New interval types, colors, etc.: `data/IntervalType.kt` + `ui/theme/Color.kt`
  (`phaseColor()`).
- Changing what a "round" means or adding nested structures: `TimerEngine.buildSteps()` is the one
  place a `WorkoutEntity` gets flattened into the list the engine actually runs.
- Different cue sounds: `timer/CueController.kt` — swap `ToneGenerator` tones or bring in real
  audio files via `MediaPlayer`/`SoundPool` if you want something less beepy.

package com.perry.intervaltimer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.data.TimerSettings
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns [CueEvent]s into beeps/voice + vibration pulses. Beeps use [ToneGenerator] instead of
 * bundled audio assets — no sound files to ship, and it respects the ringer/media volume the
 * user already has set. The voice countdown is the one exception: it plays recorded clips from
 * res/raw (via [SoundPool]) — count_<n> for the tick countdown, phase_<type> for phase changes
 * (e.g. phase_work.m4a) — looked up by name at runtime so the app builds and runs fine even
 * before those files exist. Ticks fall back to the beep only within the final lead-in window;
 * everything else stays silent if its clip is missing.
 */
class CueController(context: Context) {

    private val appContext = context.applicationContext

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, MAX_TONE_VOLUME)
    }.getOrNull()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()

    /** secondsRemaining -> loaded SoundPool id, or null if count_<n> wasn't found in res/raw. Covers
     *  the final 1-5 lead-in plus round-number milestones (10/20/.../60) for long intervals. */
    private val voiceSoundIds: Map<Int, Int?> = VOICE_SECONDS.associateWith { second ->
        val resId = appContext.resources.getIdentifier("count_$second", "raw", appContext.packageName)
        if (resId == 0) null else soundPool.load(appContext, resId, 1)
    }

    /** newType -> loaded SoundPool id, or null if phase_<type> wasn't found in res/raw. */
    private val phaseSoundIds: Map<IntervalType, Int?> = IntervalType.entries.associateWith { type ->
        val resId = appContext.resources.getIdentifier("phase_${type.name.lowercase()}", "raw", appContext.packageName)
        if (resId == 0) null else soundPool.load(appContext, resId, 1)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun handle(event: CueEvent, settings: TimerSettings) {
        when (event) {
            is CueEvent.Tick -> {
                val spokeIt = settings.voiceCountdownEnabled && playVoice(event.secondsRemaining)
                // The beep is only a fallback for the final lead-in window (historical behavior).
                // Milestone ticks (10/20/.../60s remaining) are voice-only — no clip means no cue,
                // rather than beeping every 10s through a long interval.
                val inBeepFallbackWindow = event.secondsRemaining in 1..settings.countdownLeadSeconds
                if (!spokeIt && inBeepFallbackWindow && settings.soundEnabled) beepForCountdown(event.secondsRemaining)
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 60))
            }
            is CueEvent.PhaseChange -> {
                // Phase announcements (work/rest/etc.) are always voiced when sound is on — unlike
                // the numeric tick countdown, they aren't gated behind the separate "Voice countdown"
                // setting, since knowing which phase just started matters regardless of that toggle.
                val spokeIt = settings.soundEnabled && playPhaseVoice(event.newType)
                if (!spokeIt && settings.soundEnabled) beep(ToneGenerator.TONE_PROP_BEEP2, 250)
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 150, 80, 150))
            }
            CueEvent.Finished -> {
                if (settings.soundEnabled) beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 200, 100, 200, 100, 400))
            }
        }
    }

    private fun beep(tone: Int, durationMs: Int) {
        toneGenerator?.startTone(tone, durationMs)
    }

    /**
     * A gentle synthesized "tick" whose pitch rises a semitone scale as the count nears zero —
     * subtler than routing through [ToneGenerator]'s DTMF tones (which are the same dual-frequency
     * tones a phone dial pad makes, and read as such). Two-digit remainders (e.g. 10, if
     * countdownLeadSeconds is set that high) fall back to the plain beep.
     */
    private fun beepForCountdown(secondsRemaining: Int) {
        if (secondsRemaining !in 1..9) {
            beep(ToneGenerator.TONE_PROP_BEEP, 120)
            return
        }
        val semitonesUp = 9 - secondsRemaining
        val frequencyHz = 440.0 * 2.0.pow(semitonesUp / 12.0)
        playSineTick(frequencyHz)
    }

    /** Short, soft sine-wave beep with a fade-in/out envelope so it doesn't click or sound harsh. */
    private fun playSineTick(frequencyHz: Double, durationMs: Int = 90, volume: Float = 0.35f) {
        val sampleRate = 44100
        val numSamples = durationMs * sampleRate / 1000
        val fadeSamples = (numSamples / 8).coerceAtLeast(1)
        val buffer = ShortArray(numSamples) { i ->
            val angle = 2.0 * Math.PI * i * frequencyHz / sampleRate
            val envelope = when {
                i < fadeSamples -> i.toDouble() / fadeSamples
                i > numSamples - fadeSamples -> (numSamples - i).toDouble() / fadeSamples
                else -> 1.0
            }
            (sin(angle) * envelope * volume * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(buffer, 0, buffer.size)
        track.play()
        mainHandler.postDelayed({ track.release() }, durationMs + 50L)
    }

    /** Returns true if a count_<secondsRemaining> clip was found and played. */
    private fun playVoice(secondsRemaining: Int): Boolean {
        val soundId = voiceSoundIds[secondsRemaining] ?: return false
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        return true
    }

    /** Returns true if a phase_<type> clip was found and played. */
    private fun playPhaseVoice(type: IntervalType): Boolean {
        val soundId = phaseSoundIds[type] ?: return false
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        return true
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun release() {
        toneGenerator?.release()
        soundPool.release()
    }

    private companion object {
        const val MAX_TONE_VOLUME = 90
        val VOICE_SECONDS = setOf(1, 2, 3, 4, 5, 10, 20, 30, 40, 50, 60)
    }
}

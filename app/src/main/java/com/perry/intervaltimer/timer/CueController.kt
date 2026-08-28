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
 * Turns [CueEvent]s into beeps/voice + vibration pulses. The final 1-9 lead-in countdown always
 * uses a synthesized tick tone (never voice); the round-number milestones (10/20/.../60s
 * remaining) and phase changes (work/rest/etc.) always try voice first. Voice clips live in
 * res/raw (via [SoundPool]) — count_<n>, phase_<type> (e.g. phase_work.m4a) — looked up by name
 * at runtime so the app builds and runs fine even before those files exist; a missing milestone
 * clip stays silent, a missing phase clip falls back to a beep.
 */
class CueController(context: Context) {

    private val appContext = context.applicationContext

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, MAX_TONE_VOLUME)
    }.getOrNull()

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            // USAGE_MEDIA (not USAGE_ALARM) so these mix with whatever music is playing instead of
            // ducking/pausing it — matches the STREAM_MUSIC the beeps below already use.
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()

    /** secondsRemaining -> loaded SoundPool id, or null if count_<n> wasn't found in res/raw. Only
     *  covers the round-number milestones (10/20/.../60) — the final 1-9 lead-in always uses the
     *  synthesized tick instead, never voice. */
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
                // Milestones (10/20/.../60) always try voice — playVoice() only has clips for those
                // seconds, so this is a no-op for 1-9 and they always fall through to the tick tone.
                val spokeIt = settings.soundEnabled && playVoice(event.secondsRemaining)
                // The beep is only a fallback for the final lead-in window; milestone ticks with a
                // missing clip stay silent rather than beeping every 10s through a long interval.
                val inBeepFallbackWindow = event.secondsRemaining in 1..settings.countdownLeadSeconds
                if (!spokeIt && inBeepFallbackWindow && settings.soundEnabled) beepForCountdown(event.secondsRemaining)
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 60))
            }
            is CueEvent.PhaseChange -> {
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
                // USAGE_MEDIA, for the same reason as the SoundPool above — mix with music, don't duck it.
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
        val VOICE_SECONDS = setOf(10, 20, 30, 40, 50, 60)
    }
}

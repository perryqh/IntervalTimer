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
import com.perry.intervaltimer.R
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.data.TimerSettings
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns [CueEvent]s into beeps/voice + vibration pulses. The final 1-9 lead-in countdown always
 * uses a synthesized tick tone (never voice); the round-number milestones (10/20/.../60s
 * remaining) and phase changes (work/rest) always try voice first. A missing milestone clip stays
 * silent; a missing phase clip falls back to a beep.
 */
class CueController(context: Context) {

    private val appContext = context.applicationContext

    @Volatile private var released = false

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

    private val voiceSoundIds: Map<Int, Int> = mapOf(
        10 to R.raw.count_10,
        20 to R.raw.count_20,
        30 to R.raw.count_30,
        40 to R.raw.count_40,
        50 to R.raw.count_50,
        60 to R.raw.count_60
    ).mapValues { (_, resId) -> soundPool.load(appContext, resId, 1) }

    private val phaseSoundIds: Map<IntervalType, Int> = mapOf(
        IntervalType.WORK to R.raw.phase_work,
        IntervalType.REST to R.raw.phase_rest
    ).mapValues { (_, resId) -> soundPool.load(appContext, resId, 1) }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeTracks = mutableListOf<AudioTrack>()

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
        if (released) return
        when (event) {
            is CueEvent.Tick -> {
                val spokeIt = settings.soundEnabled && playVoice(event.secondsRemaining)
                val inLeadInWindow = event.secondsRemaining in 1..settings.countdownLeadSeconds
                if (!spokeIt && inLeadInWindow && settings.soundEnabled) beepForCountdown(event.secondsRemaining)
                if (settings.vibrationEnabled && inLeadInWindow) vibrate(longArrayOf(0, 60))
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
        if (released) return
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
        if (released) return
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
        synchronized(activeTracks) { activeTracks += track }
        track.write(buffer, 0, buffer.size)
        track.play()
        mainHandler.postDelayed({
            runCatching { track.release() }
            synchronized(activeTracks) { activeTracks.remove(track) }
        }, durationMs + 50L)
    }

    private fun playVoice(secondsRemaining: Int): Boolean {
        if (released) return false
        val soundId = voiceSoundIds[secondsRemaining] ?: return false
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        return true
    }

    private fun playPhaseVoice(type: IntervalType): Boolean {
        if (released) return false
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
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(activeTracks) {
            activeTracks.forEach { runCatching { it.release() } }
            activeTracks.clear()
        }
        toneGenerator?.release()
        soundPool.release()
    }

    private companion object {
        const val MAX_TONE_VOLUME = 90
    }
}

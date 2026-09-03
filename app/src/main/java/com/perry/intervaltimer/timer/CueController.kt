package com.perry.intervaltimer.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
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
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns [CueEvent]s into beeps/voice + vibration pulses. The final 1-9 lead-in countdown always
 * uses a synthesized tick (metronome on work, gym chirp on rest; never voice); the round-number milestones (10/20/.../60s
 * remaining) and phase changes (work/rest) always try voice first. A missing milestone clip stays
 * silent; a missing phase clip falls back to a beep.
 */
class CueController(context: Context) {

    private val appContext = context.applicationContext

    @Volatile private var released = false

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, MAX_TONE_VOLUME)
    }.getOrNull()

    // MediaPlayer (not SoundPool): short speech clips through SoundPool were truncating final
    // consonants on device even as PCM WAV. One player is enough — cues never overlap.
    private var voicePlayer: MediaPlayer? = null

    private val milestoneResIds: Map<Int, Int> = mapOf(
        10 to R.raw.count_10,
        20 to R.raw.count_20,
        30 to R.raw.count_30,
        40 to R.raw.count_40,
        50 to R.raw.count_50,
        60 to R.raw.count_60
    )

    private val phaseResIds: Map<IntervalType, Int> = mapOf(
        IntervalType.WORK to R.raw.phase_work,
        IntervalType.REST to R.raw.phase_rest
    )

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
                if (!spokeIt && inLeadInWindow && settings.soundEnabled) {
                    beepForCountdown(event.secondsRemaining, event.phaseType)
                }
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
     * Lead-in ticks differ by phase: work uses a flat metronome click (urgency via volume /
     * length only); rest uses a two-tone gym-timer chirp, with a longer double-pip on 1.
     * Two-digit remainders fall back to a plain [ToneGenerator] beep.
     */
    private fun beepForCountdown(secondsRemaining: Int, phaseType: IntervalType) {
        if (secondsRemaining !in 1..9) {
            beep(ToneGenerator.TONE_PROP_BEEP, 120)
            return
        }
        when (phaseType) {
            IntervalType.REST, IntervalType.COOLDOWN, IntervalType.PREPARE ->
                playGymChirp(secondsRemaining)
            IntervalType.WORK, IntervalType.WARMUP ->
                playMetronomeClick(secondsRemaining)
        }
    }

    /** Wood-block style click: brief noise burst with exponential decay. Louder/longer near 1. */
    private fun playMetronomeClick(secondsRemaining: Int) {
        val sampleRate = 44100
        val isFinal = secondsRemaining == 1
        val durationMs = if (isFinal) 55 else 22
        val volume = if (isFinal) 0.55f else 0.28f + (5 - secondsRemaining.coerceAtMost(5)) * 0.04f
        val numSamples = durationMs * sampleRate / 1000
        val tau = if (isFinal) 0.012 else 0.006
        val rng = Random(secondsRemaining * 31)
        val buffer = ShortArray(numSamples) { i ->
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t / tau)
            // Band-limit-ish click: noise + a high transient sine
            val noise = (rng.nextDouble() * 2.0 - 1.0)
            val tick = sin(2.0 * Math.PI * 1800.0 * t)
            ((noise * 0.65 + tick * 0.35) * envelope * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        playPcm(buffer, sampleRate, durationMs)
    }

    /**
     * Two-tone "pip-pip": 1000 Hz then 1500 Hz. On the final second, play the pair twice with a
     * slightly longer second tone so "1" is unmistakable.
     */
    private fun playGymChirp(secondsRemaining: Int) {
        val sampleRate = 44100
        val isFinal = secondsRemaining == 1
        val pipMs = if (isFinal) 55 else 40
        val gapMs = 30
        val f1 = 1000.0
        val f2 = 1500.0
        val volume = 0.40f
        val reps = if (isFinal) 2 else 1

        fun toneSamples(freq: Double, durationMs: Int, vol: Float): ShortArray {
            val n = durationMs * sampleRate / 1000
            val fade = (n / 6).coerceAtLeast(1)
            return ShortArray(n) { i ->
                val angle = 2.0 * Math.PI * i * freq / sampleRate
                val envelope = when {
                    i < fade -> i.toDouble() / fade
                    i > n - fade -> (n - i).toDouble() / fade
                    else -> 1.0
                }
                (sin(angle) * envelope * vol * Short.MAX_VALUE).toInt().toShort()
            }
        }

        fun silence(ms: Int) = ShortArray(ms * sampleRate / 1000)

        val parts = mutableListOf<Short>()
        repeat(reps) { rep ->
            if (rep > 0) parts += silence(gapMs).asList()
            parts += toneSamples(f1, pipMs, volume).asList()
            parts += silence(gapMs).asList()
            parts += toneSamples(f2, if (isFinal) pipMs + 20 else pipMs, volume).asList()
        }
        val buffer = parts.toShortArray()
        val durationMs = buffer.size * 1000 / sampleRate
        playPcm(buffer, sampleRate, durationMs)
    }

    private fun playPcm(buffer: ShortArray, sampleRate: Int, durationMs: Int) {
        if (released || buffer.isEmpty()) return
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
        val resId = milestoneResIds[secondsRemaining] ?: return false
        return playVoiceRes(resId)
    }

    private fun playPhaseVoice(type: IntervalType): Boolean {
        val resId = phaseResIds[type] ?: return false
        return playVoiceRes(resId)
    }

    private fun playVoiceRes(resId: Int): Boolean {
        if (released) return false
        stopVoice()
        val player = MediaPlayer.create(appContext, resId) ?: return false
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setOnCompletionListener { done ->
            runCatching { done.release() }
            if (voicePlayer === done) voicePlayer = null
        }
        player.setOnErrorListener { broken, _, _ ->
            runCatching { broken.release() }
            if (voicePlayer === broken) voicePlayer = null
            true
        }
        voicePlayer = player
        player.start()
        return true
    }

    private fun stopVoice() {
        voicePlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        voicePlayer = null
    }

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun release() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        stopVoice()
        synchronized(activeTracks) {
            activeTracks.forEach { runCatching { it.release() } }
            activeTracks.clear()
        }
        toneGenerator?.release()
    }

    private companion object {
        const val MAX_TONE_VOLUME = 90
    }
}

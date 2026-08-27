package com.perry.intervaltimer.timer

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.perry.intervaltimer.data.TimerSettings

/**
 * Turns [CueEvent]s into short beeps + vibration pulses. Deliberately uses [ToneGenerator]
 * instead of bundled audio assets — no sound files to ship, and it respects the ringer/media
 * volume the user already has set.
 */
class CueController(context: Context) {

    private val appContext = context.applicationContext

    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_MUSIC, MAX_TONE_VOLUME)
    }.getOrNull()

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
                if (settings.soundEnabled) beep(ToneGenerator.TONE_PROP_BEEP, 120)
                if (settings.vibrationEnabled) vibrate(longArrayOf(0, 60))
            }
            is CueEvent.PhaseChange -> {
                if (settings.soundEnabled) beep(ToneGenerator.TONE_PROP_BEEP2, 250)
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

    private fun vibrate(pattern: LongArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    fun release() {
        toneGenerator?.release()
    }

    private companion object {
        const val MAX_TONE_VOLUME = 90
    }
}

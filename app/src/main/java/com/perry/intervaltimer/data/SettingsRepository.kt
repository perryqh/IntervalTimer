package com.perry.intervaltimer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * The knob most existing interval timer apps hardcode: how many seconds before the end of
 * a phase the tick/beep countdown starts. Configurable here on purpose.
 */
data class TimerSettings(
    val countdownLeadSeconds: Int = 5,
    val prepareSeconds: Int = 10,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val voiceCountdownEnabled: Boolean = false
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val COUNTDOWN_LEAD = intPreferencesKey("countdown_lead_seconds")
        val PREPARE_SECONDS = intPreferencesKey("prepare_seconds")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOICE_COUNTDOWN_ENABLED = booleanPreferencesKey("voice_countdown_enabled")
    }

    val settings: Flow<TimerSettings> = context.dataStore.data.map { prefs ->
        TimerSettings(
            countdownLeadSeconds = prefs[Keys.COUNTDOWN_LEAD] ?: 5,
            prepareSeconds = prefs[Keys.PREPARE_SECONDS] ?: 10,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            voiceCountdownEnabled = prefs[Keys.VOICE_COUNTDOWN_ENABLED] ?: false
        )
    }

    suspend fun setCountdownLeadSeconds(value: Int) {
        context.dataStore.edit { it[Keys.COUNTDOWN_LEAD] = value.coerceIn(0, 10) }
    }

    suspend fun setPrepareSeconds(value: Int) {
        context.dataStore.edit { it[Keys.PREPARE_SECONDS] = value.coerceIn(0, 60) }
    }

    suspend fun setSoundEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = value }
    }

    suspend fun setVibrationEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.VIBRATION_ENABLED] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = value }
    }

    suspend fun setVoiceCountdownEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.VOICE_COUNTDOWN_ENABLED] = value }
    }
}

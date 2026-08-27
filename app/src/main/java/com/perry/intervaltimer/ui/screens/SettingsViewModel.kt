package com.perry.intervaltimer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perry.intervaltimer.data.SettingsRepository
import com.perry.intervaltimer.data.TimerSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<TimerSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TimerSettings())

    fun setCountdownLeadSeconds(value: Int) = viewModelScope.launch { repository.setCountdownLeadSeconds(value) }
    fun setPrepareSeconds(value: Int) = viewModelScope.launch { repository.setPrepareSeconds(value) }
    fun setSoundEnabled(value: Boolean) = viewModelScope.launch { repository.setSoundEnabled(value) }
    fun setVibrationEnabled(value: Boolean) = viewModelScope.launch { repository.setVibrationEnabled(value) }
    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch { repository.setKeepScreenOn(value) }
}

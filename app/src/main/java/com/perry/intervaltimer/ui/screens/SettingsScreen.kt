package com.perry.intervaltimer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perry.intervaltimer.IntervalTimerApp
import com.perry.intervaltimer.ui.ViewModelFactory
import com.perry.intervaltimer.ui.components.StepperRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: IntervalTimerApp, onBack: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel(
        factory = ViewModelFactory { SettingsViewModel(app.settingsRepository) }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                StepperRow(
                    label = "Countdown beeps start",
                    value = settings.countdownLeadSeconds,
                    onValueChange = viewModel::setCountdownLeadSeconds,
                    minValue = 0,
                    maxValue = 10,
                    valueText = if (settings.countdownLeadSeconds == 0) "off" else "${settings.countdownLeadSeconds}s before"
                )
                Text(
                    "How many seconds before each interval ends the tick beeps/vibration kick in. " +
                        "Most timer apps hardcode this to 3 — set it to whatever actually gives you time to react.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Column {
                StepperRow(
                    label = "\"Get ready\" lead-in",
                    value = settings.prepareSeconds,
                    onValueChange = viewModel::setPrepareSeconds,
                    step = 5,
                    minValue = 0,
                    maxValue = 60,
                    valueText = if (settings.prepareSeconds == 0) "off" else "${settings.prepareSeconds}s"
                )
                Text(
                    "Countdown before a workout actually starts, so you have time to put the phone down.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            ToggleRow("Sound", settings.soundEnabled, viewModel::setSoundEnabled)
            ToggleRow("Vibration", settings.vibrationEnabled, viewModel::setVibrationEnabled)
            ToggleRow("Keep screen on while running", settings.keepScreenOn, viewModel::setKeepScreenOn)

            Column {
                Text("Voice", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The last few seconds of each interval use a short tick tone. Time checks at " +
                        "60, 50, 40, 30, 20, and 10 seconds remaining, plus work and rest announcements, " +
                        "use voice when Sound is on. Other phases (get ready, warm up, cool down) fall back to a beep.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

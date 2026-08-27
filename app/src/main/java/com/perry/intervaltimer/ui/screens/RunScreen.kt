package com.perry.intervaltimer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import android.app.Activity
import android.view.WindowManager
import com.perry.intervaltimer.data.TimerSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perry.intervaltimer.IntervalTimerApp
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.ui.ViewModelFactory
import com.perry.intervaltimer.ui.components.PhaseRing
import com.perry.intervaltimer.ui.components.formatSeconds
import com.perry.intervaltimer.ui.theme.phaseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(
    app: IntervalTimerApp,
    workoutId: String,
    onBack: () -> Unit,
    onFinishedDone: () -> Unit
) {
    val viewModel: RunViewModel = viewModel(
        factory = ViewModelFactory { RunViewModel(app.applicationContext, app.timerEngine, workoutId) }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timerSettings by app.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = TimerSettings())

    val view = LocalView.current
    DisposableEffect(timerSettings.keepScreenOn) {
        val window = (view.context as? Activity)?.window
        if (timerSettings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.workoutName.ifBlank { "Workout" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back (keeps running)")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (state.isFinished) {
                Text("Workout complete 🎉", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { viewModel.stop(); onFinishedDone() }) {
                    Text("Done")
                }
                return@Column
            }

            if (state.totalRounds > 1 && (state.currentStepType == IntervalType.WORK || state.currentStepType == IntervalType.REST)) {
                Text(
                    "Round ${state.roundNumber} / ${state.totalRounds}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                state.currentStepLabel.ifBlank { state.currentStepType.label },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = state.currentStepType.phaseColor()
            )
            Spacer(Modifier.height(24.dp))

            PhaseRing(progress = state.stepProgress, color = state.currentStepType.phaseColor()) {
                Text(formatSeconds(state.remainingSeconds), style = MaterialTheme.typography.displayLarge)
            }
            Spacer(Modifier.height(24.dp))

            state.nextStepLabel?.let { next ->
                Text(
                    "Next: $next",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(24.dp))
            }

            LinearProgressIndicator(
                progress = { state.workoutProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.stop(); onBack() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop", modifier = Modifier.size(28.dp))
                }
                FilledIconButton(
                    onClick = { if (state.isRunning) viewModel.pause() else viewModel.resume() },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (state.isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isRunning) "Pause" else "Resume",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { viewModel.skip() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Skip", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

package com.perry.intervaltimer.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perry.intervaltimer.IntervalTimerApp
import com.perry.intervaltimer.data.WorkoutEntity
import com.perry.intervaltimer.timer.TimerService
import com.perry.intervaltimer.timer.TimerUiState
import com.perry.intervaltimer.ui.Routes
import com.perry.intervaltimer.ui.ViewModelFactory
import com.perry.intervaltimer.ui.components.formatSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    app: IntervalTimerApp,
    onStartWorkout: (String) -> Unit,
    onEditWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel: WorkoutListViewModel = viewModel(
        factory = ViewModelFactory { WorkoutListViewModel(app.workoutRepository) }
    )
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val timerState by app.timerEngine.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<WorkoutEntity?>(null) }
    var pendingStart by remember { mutableStateOf<WorkoutEntity?>(null) }

    fun sendStop() {
        val intent = Intent(context, TimerService::class.java).setAction(TimerService.ACTION_STOP)
        ContextCompat.startForegroundService(context, intent)
    }

    fun tryStart(workout: WorkoutEntity) {
        if (timerState.isActive && !timerState.isFinished && timerState.workoutId != workout.id) {
            pendingStart = workout
        } else {
            onStartWorkout(workout.id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Interval Timer") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditWorkout(Routes.NEW_WORKOUT_ID) }) {
                Icon(Icons.Filled.Add, contentDescription = "New workout")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (timerState.isActive) {
                ActiveWorkoutBanner(
                    state = timerState,
                    onResume = { onStartWorkout(timerState.workoutId) },
                    onStop = { sendStop() }
                )
            }
            if (workouts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No workouts yet — tap + to build one", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(workouts, key = { it.id }) { workout ->
                        WorkoutCard(
                            workout = workout,
                            onStart = { tryStart(workout) },
                            onEdit = { onEditWorkout(workout.id) },
                            onDelete = { pendingDelete = workout }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete workout?") },
            text = { Text("Delete “${workout.name}”? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (timerState.isActive && timerState.workoutId == workout.id) sendStop()
                    viewModel.delete(workout)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    pendingStart?.let { workout ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Replace current workout?") },
            text = { Text("Stop the workout in progress and start ${workout.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingStart = null
                    onStartWorkout(workout.id)
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { pendingStart = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ActiveWorkoutBanner(
    state: TimerUiState,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.isFinished) "Workout complete" else state.workoutName,
                    style = MaterialTheme.typography.titleLarge
                )
                if (!state.isFinished) {
                    Text(
                        "${state.currentStepLabel} — ${formatSeconds(state.remainingSeconds)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            if (state.isFinished) {
                TextButton(onClick = onStop) { Text("Done") }
            } else {
                TextButton(onClick = onResume) { Text("Resume") }
                TextButton(onClick = onStop) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleLarge)
                Text(summarize(workout), style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${workout.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${workout.name}")
            }
            IconButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${workout.name}")
            }
        }
    }
}

private fun summarize(workout: WorkoutEntity): String {
    val stepsPart = workout.steps.joinToString(" / ") { "${it.label} ${formatSeconds(it.durationSeconds)}" }
    val roundsPart = if (workout.rounds > 1) "${workout.rounds} rounds of $stepsPart" else stepsPart
    return "$roundsPart • ${formatSeconds(workout.totalDurationSeconds())} total"
}

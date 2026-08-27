package com.perry.intervaltimer.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.perry.intervaltimer.data.WorkoutEntity
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
        if (workouts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No workouts yet — tap + to build one", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workouts, key = { it.id }) { workout ->
                    WorkoutCard(
                        workout = workout,
                        onClick = { onStartWorkout(workout.id) },
                        onEdit = { onEditWorkout(workout.id) },
                        onDelete = { viewModel.delete(workout) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
            Icon(Icons.Filled.PlayArrow, contentDescription = "Start ${workout.name}")
        }
    }
}

private fun summarize(workout: WorkoutEntity): String {
    val stepsPart = workout.steps.joinToString(" / ") { "${it.label} ${formatSeconds(it.durationSeconds)}" }
    val roundsPart = if (workout.rounds > 1) "${workout.rounds} rounds of $stepsPart" else stepsPart
    return "$roundsPart • ${formatSeconds(workout.totalDurationSeconds())} total"
}

package com.perry.intervaltimer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.perry.intervaltimer.IntervalTimerApp
import com.perry.intervaltimer.data.IntervalStep
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.ui.Routes
import com.perry.intervaltimer.ui.ViewModelFactory
import com.perry.intervaltimer.ui.components.StepperRow
import com.perry.intervaltimer.ui.theme.phaseColor

private val editableTypes = listOf(IntervalType.WORK, IntervalType.REST)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditScreen(
    app: IntervalTimerApp,
    workoutId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val editId = workoutId?.takeIf { it != Routes.NEW_WORKOUT_ID }
    val viewModel: WorkoutEditViewModel = viewModel(
        factory = ViewModelFactory { WorkoutEditViewModel(app.workoutRepository, editId) }
    )
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId == null) "New workout" else "Edit workout") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save(onSaved = { onSaved() }) }, enabled = draft.isValid) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Workout name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StepperRow(
                            label = "Warm up",
                            value = draft.warmupSeconds,
                            onValueChange = viewModel::updateWarmup,
                            step = 5,
                            valueText = if (draft.warmupSeconds == 0) "off" else "${draft.warmupSeconds}s"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StepperRow(
                            label = "Cool down",
                            value = draft.cooldownSeconds,
                            onValueChange = viewModel::updateCooldown,
                            step = 5,
                            valueText = if (draft.cooldownSeconds == 0) "off" else "${draft.cooldownSeconds}s"
                        )
                    }
                }
            }
            item {
                Text("Repeating steps", style = MaterialTheme.typography.titleLarge)
            }
            itemsIndexed(draft.steps) { index, step ->
                StepEditor(
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.steps.size - 1,
                    onMoveUp = { viewModel.moveStep(index, -1) },
                    onMoveDown = { viewModel.moveStep(index, 1) },
                    onLabelChange = { viewModel.updateStepLabel(step.id, it) },
                    onTypeChange = { viewModel.updateStepType(step.id, it) },
                    onDurationChange = { viewModel.updateStepDuration(step.id, it) },
                    onRemove = { viewModel.removeStep(step.id) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.addStep(IntervalType.WORK) }) { Text("+ Work step") }
                    TextButton(onClick = { viewModel.addStep(IntervalType.REST) }) { Text("+ Rest step") }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StepperRow(
                            label = "Rounds",
                            value = draft.rounds,
                            onValueChange = viewModel::updateRounds,
                            minValue = 1,
                            maxValue = 99
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepEditor(
    step: IntervalStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLabelChange: (String) -> Unit,
    onTypeChange: (IntervalType) -> Unit,
    onDurationChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                var typeMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = step.type.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = step.type.phaseColor())
                    )
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        editableTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = { onTypeChange(type); typeMenuExpanded = false }
                            )
                        }
                    }
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove step")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = step.label,
                    onValueChange = onLabelChange,
                    label = { Text("Label") },
                    modifier = Modifier.weight(1f)
                )
                var durationText by rememberSaveable(step.id) { mutableStateOf(step.durationSeconds.toString()) }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { text ->
                        if (text.isEmpty() || text.all { it.isDigit() }) {
                            durationText = text
                            text.toIntOrNull()?.let(onDurationChange)
                        }
                    },
                    label = { Text("Seconds") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

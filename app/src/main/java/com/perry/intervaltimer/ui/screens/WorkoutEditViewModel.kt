package com.perry.intervaltimer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perry.intervaltimer.data.IntervalStep
import com.perry.intervaltimer.data.IntervalType
import com.perry.intervaltimer.data.WorkoutEntity
import com.perry.intervaltimer.data.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class WorkoutDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val warmupSeconds: Int = 0,
    val steps: List<IntervalStep> = listOf(
        IntervalStep.default(IntervalType.WORK, 30),
        IntervalStep.default(IntervalType.REST, 15)
    ),
    val rounds: Int = 1,
    val cooldownSeconds: Int = 0,
    val loaded: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val lastUsedAtMillis: Long? = null
) {
    val isValid: Boolean get() = name.isNotBlank() && steps.isNotEmpty() && steps.all { it.durationSeconds > 0 }
}

class WorkoutEditViewModel(
    private val repository: WorkoutRepository,
    workoutId: String?
) : ViewModel() {

    private val _draft = MutableStateFlow(
        if (workoutId == null) WorkoutDraft() else WorkoutDraft(id = workoutId, loaded = false)
    )
    val draft: StateFlow<WorkoutDraft> = _draft.asStateFlow()

    init {
        if (workoutId != null) {
            viewModelScope.launch {
                repository.getWorkout(workoutId)?.let { w ->
                    _draft.value = WorkoutDraft(
                        id = w.id,
                        name = w.name,
                        warmupSeconds = w.warmupSeconds,
                        steps = w.steps,
                        rounds = w.rounds,
                        cooldownSeconds = w.cooldownSeconds,
                        loaded = true,
                        createdAtMillis = w.createdAtMillis,
                        lastUsedAtMillis = w.lastUsedAtMillis
                    )
                } ?: run {
                    _draft.update { it.copy(loaded = true) }
                }
            }
        }
    }

    fun updateName(name: String) = _draft.update { it.copy(name = name) }
    fun updateWarmup(seconds: Int) = _draft.update { it.copy(warmupSeconds = seconds.coerceIn(0, 3600)) }
    fun updateCooldown(seconds: Int) = _draft.update { it.copy(cooldownSeconds = seconds.coerceIn(0, 3600)) }
    fun updateRounds(rounds: Int) = _draft.update { it.copy(rounds = rounds.coerceIn(1, 99)) }

    fun addStep(type: IntervalType) = _draft.update { it.copy(steps = it.steps + IntervalStep.default(type)) }

    fun removeStep(stepId: String) = _draft.update { it.copy(steps = it.steps.filterNot { s -> s.id == stepId }) }

    fun updateStepLabel(stepId: String, label: String) = updateStep(stepId) { it.copy(label = label) }
    fun updateStepType(stepId: String, type: IntervalType) = updateStep(stepId) {
        val label = if (it.label == it.type.label) type.label else it.label
        it.copy(type = type, label = label)
    }
    fun updateStepDuration(stepId: String, seconds: Int) =
        updateStep(stepId) { it.copy(durationSeconds = seconds.coerceIn(1, 3600)) }

    private fun updateStep(stepId: String, transform: (IntervalStep) -> IntervalStep) {
        _draft.update { d -> d.copy(steps = d.steps.map { if (it.id == stepId) transform(it) else it }) }
    }

    fun moveStep(index: Int, delta: Int) {
        _draft.update { d ->
            val target = index + delta
            if (index !in d.steps.indices || target !in d.steps.indices) return@update d
            val list = d.steps.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            d.copy(steps = list)
        }
    }

    fun save(onSaved: (String) -> Unit) {
        val d = _draft.value
        if (!d.isValid) return
        viewModelScope.launch {
            repository.save(
                WorkoutEntity(
                    id = d.id,
                    name = d.name.trim(),
                    warmupSeconds = d.warmupSeconds,
                    steps = d.steps,
                    rounds = d.rounds,
                    cooldownSeconds = d.cooldownSeconds,
                    createdAtMillis = d.createdAtMillis,
                    lastUsedAtMillis = d.lastUsedAtMillis
                )
            )
            onSaved(d.id)
        }
    }
}

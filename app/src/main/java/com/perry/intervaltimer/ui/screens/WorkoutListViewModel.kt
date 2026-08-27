package com.perry.intervaltimer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.perry.intervaltimer.data.WorkoutEntity
import com.perry.intervaltimer.data.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutListViewModel(private val repository: WorkoutRepository) : ViewModel() {

    val workouts: StateFlow<List<WorkoutEntity>> = repository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(workout: WorkoutEntity) {
        viewModelScope.launch { repository.delete(workout) }
    }
}

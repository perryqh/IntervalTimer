package com.perry.intervaltimer.ui

object Routes {
    const val WORKOUT_LIST = "workouts"
    const val SETTINGS = "settings"
    const val EDIT_WORKOUT_PATTERN = "editWorkout/{workoutId}"
    const val RUN_WORKOUT_PATTERN = "run/{workoutId}"
    const val ARG_WORKOUT_ID = "workoutId"
    const val NEW_WORKOUT_ID = "new"

    fun editWorkout(id: String) = "editWorkout/$id"
    fun runWorkout(id: String) = "run/$id"
}

package com.perry.intervaltimer.data

import kotlinx.coroutines.flow.Flow

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeWorkouts(): Flow<List<WorkoutEntity>> = dao.observeAll()

    suspend fun getWorkout(id: String): WorkoutEntity? = dao.getById(id)

    suspend fun save(workout: WorkoutEntity) = dao.upsert(workout)

    suspend fun delete(workout: WorkoutEntity) = dao.delete(workout)

    suspend fun markUsed(id: String) = dao.touchLastUsed(id, System.currentTimeMillis())

    /** Seeds a couple of example workouts on first launch so the list isn't empty. */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        listOf(
            WorkoutEntity(
                name = "Tabata (20 on / 10 off x8)",
                steps = listOf(
                    IntervalStep.default(IntervalType.WORK, 20),
                    IntervalStep.default(IntervalType.REST, 10)
                ),
                rounds = 8
            ),
            WorkoutEntity(
                name = "HIIT 40/20 x10",
                warmupSeconds = 60,
                steps = listOf(
                    IntervalStep.default(IntervalType.WORK, 40),
                    IntervalStep.default(IntervalType.REST, 20)
                ),
                rounds = 10,
                cooldownSeconds = 60
            )
        ).forEach { dao.upsert(it) }
    }
}

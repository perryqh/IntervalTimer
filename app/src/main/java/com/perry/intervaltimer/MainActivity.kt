package com.perry.intervaltimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.perry.intervaltimer.ui.Routes
import com.perry.intervaltimer.ui.screens.RunScreen
import com.perry.intervaltimer.ui.screens.SettingsScreen
import com.perry.intervaltimer.ui.screens.WorkoutEditScreen
import com.perry.intervaltimer.ui.screens.WorkoutListScreen
import com.perry.intervaltimer.ui.theme.IntervalTimerTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* If denied, the workout still runs; the notification (and its controls) just won't show. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = IntervalTimerApp.from(this)

        setContent {
            IntervalTimerTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val navController = rememberNavController()
                AppNavHost(navController, app)
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, app: IntervalTimerApp) {
    NavHost(navController = navController, startDestination = Routes.WORKOUT_LIST) {
        composable(Routes.WORKOUT_LIST) {
            WorkoutListScreen(
                app = app,
                onStartWorkout = { id -> navController.navigate(Routes.runWorkout(id)) },
                onEditWorkout = { id -> navController.navigate(Routes.editWorkout(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(app = app, onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.EDIT_WORKOUT_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString(Routes.ARG_WORKOUT_ID)
            WorkoutEditScreen(
                app = app,
                workoutId = workoutId,
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.RUN_WORKOUT_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_WORKOUT_ID) { type = NavType.StringType })
        ) { backStackEntry ->
            val workoutId = backStackEntry.arguments?.getString(Routes.ARG_WORKOUT_ID) ?: return@composable
            RunScreen(
                app = app,
                workoutId = workoutId,
                onBack = { navController.popBackStack() },
                onFinishedDone = { navController.popBackStack(Routes.WORKOUT_LIST, inclusive = false) }
            )
        }
    }
}

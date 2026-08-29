package com.perry.intervaltimer.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.perry.intervaltimer.IntervalTimerApp
import com.perry.intervaltimer.MainActivity
import com.perry.intervaltimer.R
import com.perry.intervaltimer.data.TimerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the workout running (and audible) when the screen is off or
 * the app isn't in front. The actual countdown logic lives in [TimerEngine] on the
 * [IntervalTimerApp]; this service just starts/stops it, plays cues for it, and mirrors its
 * state into a persistent notification.
 */
class TimerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private lateinit var app: IntervalTimerApp
    private lateinit var cueController: CueController
    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager

    @Volatile private var latestSettings: TimerSettings = TimerSettings()
    private var autoStopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        app = IntervalTimerApp.from(this)
        cueController = CueController(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        createNotificationChannel()

        app.settingsRepository.settings
            .onEach { latestSettings = it }
            .launchIn(serviceScope)

        app.timerEngine.cueEvents
            .onEach { event -> cueController.handle(event, latestSettings) }
            .launchIn(serviceScope)

        app.timerEngine.uiState
            .map { NotificationSnapshot(it.currentStepLabel, it.remainingSeconds, it.isRunning, it.isFinished, it.isActive) }
            .distinctUntilChanged()
            .onEach { snapshot -> onStateSnapshot(snapshot) }
            .launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every entry point here is reached via Context.startForegroundService(), including
        // PAUSE/RESUME/SKIP/STOP control taps and a "Done" tap that can race the service's own
        // auto-stop a few seconds after finishing. If that happens Android spins up a brand-new
        // TimerService instance, and on API 31+ it *must* call startForeground() within seconds of
        // being started this way or the system throws ForegroundServiceDidNotStartInTimeException.
        // So startForeground unconditionally, first, then decide what to actually do — stopping
        // right after starting is fine.
        startForeground(NOTIFICATION_ID, buildNotification(currentContentText(), running = app.timerEngine.uiState.value.isRunning))

        when (intent?.action) {
            ACTION_START -> {
                autoStopJob?.cancel()
                autoStopJob = null
                val workoutId = intent.getStringExtra(EXTRA_WORKOUT_ID)
                if (workoutId == null) {
                    stopForegroundAndSelf()
                } else {
                    serviceScope.launch {
                        val workout = app.workoutRepository.getWorkout(workoutId)
                        if (workout == null) {
                            stopForegroundAndSelf()
                            return@launch
                        }
                        val settings = app.settingsRepository.settings.first()
                        latestSettings = settings
                        app.timerEngine.start(workout, settings)
                        if (!app.timerEngine.uiState.value.isActive) {
                            stopForegroundAndSelf()
                            return@launch
                        }
                        app.workoutRepository.markUsed(workoutId)
                    }
                }
            }
            ACTION_PAUSE -> {
                autoStopJob?.cancel()
                app.timerEngine.pause()
            }
            ACTION_RESUME -> {
                autoStopJob?.cancel()
                app.timerEngine.resume()
            }
            ACTION_SKIP -> {
                autoStopJob?.cancel()
                app.timerEngine.skipToNext()
            }
            ACTION_STOP -> {
                autoStopJob?.cancel()
                autoStopJob = null
                app.timerEngine.stop()
                stopForegroundAndSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun currentContentText(): String {
        val state = app.timerEngine.uiState.value
        return if (state.isActive) "${state.currentStepLabel} — ${state.remainingSeconds}s" else "Starting workout…"
    }

    private fun onStateSnapshot(snapshot: NotificationSnapshot) {
        updateWakeLock(snapshot.isActive && snapshot.isRunning && !snapshot.isFinished)
        if (!snapshot.isActive) return
        if (snapshot.isFinished) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification("Workout complete 🎉", running = false, finished = true))
            autoStopJob?.cancel()
            autoStopJob = serviceScope.launch {
                delay(4000)
                stopForegroundAndSelf()
            }
            return
        }
        val text = "${snapshot.label} — ${snapshot.remainingSeconds}s"
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, running = snapshot.isRunning))
    }

    private fun buildNotification(text: String, running: Boolean, finished: Boolean = false): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!finished)
            .setContentIntent(contentIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)

        if (!finished) {
            builder.addAction(R.drawable.ic_notification, "Stop", actionIntent(ACTION_STOP))
            if (running) {
                builder.addAction(R.drawable.ic_notification, "Pause", actionIntent(ACTION_PAUSE))
            } else {
                builder.addAction(R.drawable.ic_notification, "Resume", actionIntent(ACTION_RESUME))
            }
            builder.addAction(R.drawable.ic_notification, "Skip", actionIntent(ACTION_SKIP))
        }

        return builder.build()
    }

    private fun contentIntent(): PendingIntent {
        val workoutId = app.timerEngine.uiState.value.workoutId
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (workoutId.isNotBlank()) putExtra(EXTRA_WORKOUT_ID, workoutId)
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun actionIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateWakeLock(running: Boolean) {
        if (running) {
            val lock = wakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
            if (!lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS)
        } else {
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock ?: return
        if (lock.isHeld) lock.release()
    }

    private fun stopForegroundAndSelf() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cueController.release()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class NotificationSnapshot(
        val label: String,
        val remainingSeconds: Int,
        val isRunning: Boolean,
        val isFinished: Boolean,
        val isActive: Boolean
    )

    companion object {
        const val ACTION_START = "com.perry.intervaltimer.action.START"
        const val ACTION_PAUSE = "com.perry.intervaltimer.action.PAUSE"
        const val ACTION_RESUME = "com.perry.intervaltimer.action.RESUME"
        const val ACTION_SKIP = "com.perry.intervaltimer.action.SKIP"
        const val ACTION_STOP = "com.perry.intervaltimer.action.STOP"
        const val EXTRA_WORKOUT_ID = "workout_id"

        private const val CHANNEL_ID = "workout_timer"
        private const val NOTIFICATION_ID = 42
        private const val WAKE_LOCK_TAG = "IntervalTimer:Timer"
        private const val WAKE_LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
    }
}

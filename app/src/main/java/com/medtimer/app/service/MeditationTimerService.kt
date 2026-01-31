package com.medtimer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.medtimer.app.MainActivity
import com.medtimer.app.R
import com.medtimer.app.audio.BellPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MeditationTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "meditation_timer_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START_COUNTDOWN = "com.medtimer.app.START_COUNTDOWN"
        const val ACTION_START_MEDITATION = "com.medtimer.app.START_MEDITATION"
        const val ACTION_STOP = "com.medtimer.app.STOP"

        const val EXTRA_COUNTDOWN_SECONDS = "countdown_seconds"
        const val EXTRA_INTERVAL_SECONDS = "interval_seconds"
        const val EXTRA_NUM_INTERVALS = "num_intervals"
        const val EXTRA_WHITE_NOISE_VOLUME = "white_noise_volume"
    }

    enum class ServiceState {
        IDLE,
        COUNTDOWN,
        MEDITATING,
        FINISHED
    }

    interface TimerCallback {
        fun onTick(secondsRemaining: Int, state: ServiceState, intervalsCompleted: Int)
        fun onStateChange(state: ServiceState, sessionStartTime: LocalTime?, sessionDate: LocalDate?)
        fun onFinished()
    }

    private val binder = LocalBinder()
    private var callback: TimerCallback? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var bellPlayer: BellPlayer? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    private var currentState = ServiceState.IDLE
    private var countdownSeconds = 0
    private var intervalSeconds = 0
    private var numIntervals = 0
    private var currentSeconds = 0
    private var intervalsCompleted = 0
    private var whiteNoiseVolume = 0f
    private var sessionStartTime: LocalTime? = null
    private var sessionDate: LocalDate? = null

    inner class LocalBinder : Binder() {
        fun getService(): MeditationTimerService = this@MeditationTimerService
    }

    override fun onCreate() {
        super.onCreate()
        bellPlayer = BellPlayer(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_COUNTDOWN -> {
                countdownSeconds = intent.getIntExtra(EXTRA_COUNTDOWN_SECONDS, 10)
                intervalSeconds = intent.getIntExtra(EXTRA_INTERVAL_SECONDS, 420)
                numIntervals = intent.getIntExtra(EXTRA_NUM_INTERVALS, 4)
                whiteNoiseVolume = intent.getFloatExtra(EXTRA_WHITE_NOISE_VOLUME, 0f)
                startCountdown()
            }
            ACTION_STOP -> {
                stopTimer()
            }
        }
        return START_STICKY
    }

    fun setCallback(callback: TimerCallback?) {
        this.callback = callback
        // Send current state to new callback
        callback?.onTick(currentSeconds, currentState, intervalsCompleted)
    }

    fun getCurrentState(): ServiceState = currentState
    fun getCurrentSeconds(): Int = currentSeconds
    fun getIntervalsCompleted(): Int = intervalsCompleted
    fun getSessionStartTime(): LocalTime? = sessionStartTime
    fun getSessionDate(): LocalDate? = sessionDate

    private fun startCountdown() {
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification("Preparing to meditate..."))

        currentState = ServiceState.COUNTDOWN
        currentSeconds = countdownSeconds
        intervalsCompleted = 0
        sessionStartTime = null
        sessionDate = null

        callback?.onStateChange(currentState, null, null)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val startTime = SystemClock.elapsedRealtime()
            var lastSecond = countdownSeconds

            while (currentSeconds > 0 && currentState == ServiceState.COUNTDOWN) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val newSeconds = countdownSeconds - (elapsed / 1000).toInt()

                if (newSeconds != lastSecond && newSeconds >= 0) {
                    currentSeconds = newSeconds
                    lastSecond = newSeconds
                    callback?.onTick(currentSeconds, currentState, intervalsCompleted)
                    updateNotification("Starting in $currentSeconds seconds")
                }

                if (currentSeconds <= 0) break
                delay(100) // Check more frequently for accuracy
            }

            if (currentState == ServiceState.COUNTDOWN) {
                startMeditation()
            }
        }
    }

    private fun startMeditation() {
        bellPlayer?.playIntervalBell()

        if (whiteNoiseVolume > 0f) {
            bellPlayer?.startWhiteNoise(whiteNoiseVolume)
        }

        sessionStartTime = LocalTime.now()
        sessionDate = LocalDate.now()

        currentState = ServiceState.MEDITATING
        currentSeconds = intervalSeconds * numIntervals
        intervalsCompleted = 0

        callback?.onStateChange(currentState, sessionStartTime, sessionDate)

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val totalSeconds = intervalSeconds * numIntervals
            val startTime = SystemClock.elapsedRealtime()
            var lastSecond = totalSeconds
            var lastIntervalCheck = 0

            while (currentSeconds > 0 && currentState == ServiceState.MEDITATING) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val elapsedSeconds = (elapsed / 1000).toInt()
                val newSeconds = totalSeconds - elapsedSeconds

                if (newSeconds != lastSecond && newSeconds >= 0) {
                    currentSeconds = newSeconds
                    lastSecond = newSeconds
                    callback?.onTick(currentSeconds, currentState, intervalsCompleted)
                    updateNotification(formatTime(currentSeconds) + " remaining")
                }

                // Check for interval completion
                if (elapsedSeconds > lastIntervalCheck && elapsedSeconds % intervalSeconds == 0 && elapsedSeconds > 0) {
                    lastIntervalCheck = elapsedSeconds
                    intervalsCompleted = elapsedSeconds / intervalSeconds

                    if (intervalsCompleted >= numIntervals) {
                        // Final bell
                        bellPlayer?.playFinalBell()
                        finishSession()
                        return@launch
                    } else {
                        // Interval bell
                        bellPlayer?.playIntervalBell()
                    }
                }

                if (currentSeconds <= 0) break
                delay(100)
            }

            if (currentState == ServiceState.MEDITATING) {
                bellPlayer?.playFinalBell()
                finishSession()
            }
        }
    }

    fun setWhiteNoiseVolume(volume: Float) {
        whiteNoiseVolume = volume
        bellPlayer?.setWhiteNoiseVolume(volume)
    }

    // Pause the timer (used when stop dialog is shown)
    private var pausedAtSeconds: Int = 0
    private var isPaused = false

    fun pauseTimer() {
        if (currentState == ServiceState.MEDITATING && !isPaused) {
            isPaused = true
            pausedAtSeconds = currentSeconds
            timerJob?.cancel()
        }
    }

    fun resumeTimer() {
        if (currentState == ServiceState.MEDITATING && isPaused) {
            isPaused = false
            // Resume from where we paused
            currentSeconds = pausedAtSeconds
            resumeMeditationTimer()
        }
    }

    private fun resumeMeditationTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            val remainingSeconds = currentSeconds
            val startTime = SystemClock.elapsedRealtime()
            var lastSecond = remainingSeconds

            while (currentSeconds > 0 && currentState == ServiceState.MEDITATING && !isPaused) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                val elapsedSeconds = (elapsed / 1000).toInt()
                val newSeconds = remainingSeconds - elapsedSeconds

                if (newSeconds != lastSecond && newSeconds >= 0) {
                    currentSeconds = newSeconds
                    lastSecond = newSeconds
                    callback?.onTick(currentSeconds, currentState, intervalsCompleted)
                    updateNotification(formatTime(currentSeconds) + " remaining")
                }

                // Check for interval completion based on total elapsed time
                val totalElapsed = (intervalSeconds * numIntervals) - currentSeconds
                val expectedIntervals = totalElapsed / intervalSeconds
                if (expectedIntervals > intervalsCompleted) {
                    intervalsCompleted = expectedIntervals

                    if (intervalsCompleted >= numIntervals) {
                        bellPlayer?.playFinalBell()
                        finishSession()
                        return@launch
                    } else {
                        bellPlayer?.playIntervalBell()
                    }
                }

                if (currentSeconds <= 0) break
                delay(100)
            }

            if (currentState == ServiceState.MEDITATING && !isPaused) {
                bellPlayer?.playFinalBell()
                finishSession()
            }
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        bellPlayer?.stopWhiteNoise()
        currentState = ServiceState.IDLE
        callback?.onStateChange(currentState, sessionStartTime, sessionDate)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finishSession() {
        bellPlayer?.stopWhiteNoise()
        currentState = ServiceState.FINISHED
        callback?.onFinished()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MedTimer::MeditationWakeLock"
            )
        }
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hour max timeout
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Meditation Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows meditation timer progress"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meditation Timer")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(text))
    }

    private fun formatTime(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        bellPlayer?.release()
        releaseWakeLock()
        super.onDestroy()
    }
}

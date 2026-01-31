package com.medtimer.app

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.data.AppDatabase
import com.medtimer.app.data.Session
import com.medtimer.app.service.MeditationTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class TimerState {
    IDLE,
    COUNTDOWN,      // Initial countdown before meditation starts
    MEDITATING,     // Active meditation session
    FINISHED        // Session complete
}

data class MeditationUiState(
    val timerState: TimerState = TimerState.IDLE,
    val countdownSeconds: Int = 10,     // C: seconds before meditation
    val intervalMinutes: Int = 7,       // N: minutes between bells
    val numIntervals: Int = 4,          // K: number of intervals
    val currentSeconds: Int = 0,        // Current timer value
    val intervalsCompleted: Int = 0,
    val debugMode: Boolean = false,     // When true, N counts seconds instead of minutes
    val showStopDialog: Boolean = false,
    val sessionStartTime: LocalTime? = null,
    val sessionDate: LocalDate? = null,
    val whiteNoiseVolume: Float = 0f  // 0.0 = off, 1.0 = full volume
) {
    val totalMeditationSeconds: Int
        get() = if (debugMode) {
            intervalMinutes * numIntervals  // N is seconds in debug mode
        } else {
            intervalMinutes * numIntervals * 60
        }

    val intervalSeconds: Int
        get() = if (debugMode) intervalMinutes else intervalMinutes * 60
}

class MeditationViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val sessionDao = database.sessionDao()

    private val _uiState = MutableStateFlow(MeditationUiState())
    val uiState: StateFlow<MeditationUiState> = _uiState.asStateFlow()

    val sessions = sessionDao.getAllSessions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var timerService: MeditationTimerService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MeditationTimerService.LocalBinder
            timerService = localBinder.getService()
            serviceBound = true
            timerService?.setCallback(timerCallback)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerService = null
            serviceBound = false
        }
    }

    private val timerCallback = object : MeditationTimerService.TimerCallback {
        override fun onTick(secondsRemaining: Int, state: MeditationTimerService.ServiceState, intervalsCompleted: Int) {
            _uiState.value = _uiState.value.copy(
                currentSeconds = secondsRemaining,
                intervalsCompleted = intervalsCompleted
            )
        }

        override fun onStateChange(
            state: MeditationTimerService.ServiceState,
            sessionStartTime: LocalTime?,
            sessionDate: LocalDate?
        ) {
            val timerState = when (state) {
                MeditationTimerService.ServiceState.IDLE -> TimerState.IDLE
                MeditationTimerService.ServiceState.COUNTDOWN -> TimerState.COUNTDOWN
                MeditationTimerService.ServiceState.MEDITATING -> TimerState.MEDITATING
                MeditationTimerService.ServiceState.FINISHED -> TimerState.FINISHED
            }
            _uiState.value = _uiState.value.copy(
                timerState = timerState,
                sessionStartTime = sessionStartTime,
                sessionDate = sessionDate
            )
        }

        override fun onFinished() {
            saveSession()
            reset()
        }
    }

    fun setCountdownSeconds(value: Int) {
        if (_uiState.value.timerState == TimerState.IDLE) {
            _uiState.value = _uiState.value.copy(countdownSeconds = value)
        }
    }

    fun setIntervalMinutes(value: Int) {
        if (_uiState.value.timerState == TimerState.IDLE) {
            _uiState.value = _uiState.value.copy(intervalMinutes = value)
        }
    }

    fun setNumIntervals(value: Int) {
        if (_uiState.value.timerState == TimerState.IDLE) {
            _uiState.value = _uiState.value.copy(numIntervals = value)
        }
    }

    fun toggleDebugMode() {
        if (_uiState.value.timerState == TimerState.IDLE) {
            _uiState.value = _uiState.value.copy(debugMode = !_uiState.value.debugMode)
        }
    }

    fun setWhiteNoiseVolume(volume: Float) {
        _uiState.value = _uiState.value.copy(whiteNoiseVolume = volume.coerceIn(0f, 1f))
        // Update volume if service is running
        if (_uiState.value.timerState == TimerState.MEDITATING) {
            timerService?.setWhiteNoiseVolume(volume)
        }
    }

    fun start() {
        if (_uiState.value.timerState != TimerState.IDLE) return

        val context = getApplication<Application>()
        val state = _uiState.value

        _uiState.value = state.copy(
            timerState = TimerState.COUNTDOWN,
            currentSeconds = state.countdownSeconds,
            intervalsCompleted = 0,
            sessionStartTime = null,
            sessionDate = null
        )

        // Start and bind to the service
        val serviceIntent = Intent(context, MeditationTimerService::class.java).apply {
            action = MeditationTimerService.ACTION_START_COUNTDOWN
            putExtra(MeditationTimerService.EXTRA_COUNTDOWN_SECONDS, state.countdownSeconds)
            putExtra(MeditationTimerService.EXTRA_INTERVAL_SECONDS, state.intervalSeconds)
            putExtra(MeditationTimerService.EXTRA_NUM_INTERVALS, state.numIntervals)
            putExtra(MeditationTimerService.EXTRA_WHITE_NOISE_VOLUME, state.whiteNoiseVolume)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun requestStop() {
        if (_uiState.value.timerState == TimerState.MEDITATING) {
            timerService?.pauseTimer()
            _uiState.value = _uiState.value.copy(showStopDialog = true)
        } else if (_uiState.value.timerState == TimerState.COUNTDOWN) {
            // During countdown, just cancel without dialog
            stopService()
            reset()
        }
    }

    fun confirmStop(keepSession: Boolean) {
        _uiState.value = _uiState.value.copy(showStopDialog = false)
        if (keepSession) {
            saveSession()
        }
        stopService()
        reset()
    }

    fun dismissStopDialog() {
        _uiState.value = _uiState.value.copy(showStopDialog = false)
        timerService?.resumeTimer()
    }

    private fun saveSession() {
        val state = _uiState.value
        if (state.sessionStartTime != null && state.sessionDate != null) {
            val elapsedSeconds = state.totalMeditationSeconds - state.currentSeconds

            if (elapsedSeconds > 0) {
                viewModelScope.launch {
                    sessionDao.insert(
                        Session(
                            date = state.sessionDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            startTime = state.sessionStartTime.format(DateTimeFormatter.ISO_LOCAL_TIME),
                            elapsedSeconds = elapsedSeconds
                        )
                    )
                }
            }
        }
    }

    private fun stopService() {
        timerService?.stopTimer()
        unbindFromService()
    }

    private fun unbindFromService() {
        if (serviceBound) {
            timerService?.setCallback(null)
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // Service may already be unbound
            }
            serviceBound = false
            timerService = null
        }
    }

    private fun reset() {
        _uiState.value = _uiState.value.copy(
            timerState = TimerState.IDLE,
            currentSeconds = 0,
            intervalsCompleted = 0,
            showStopDialog = false,
            sessionStartTime = null,
            sessionDate = null
        )
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            sessionDao.delete(session)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            sessionDao.deleteAll()
        }
    }

    suspend fun exportSessionsCsv(): String {
        val allSessions = sessionDao.getAllSessionsList()
        val sb = StringBuilder()
        sb.appendLine(Session.csvHeader())
        allSessions.forEach { session ->
            sb.appendLine(session.toCsvRow())
        }
        return sb.toString()
    }

    override fun onCleared() {
        super.onCleared()
        unbindFromService()
    }
}

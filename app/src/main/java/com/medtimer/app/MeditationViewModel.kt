package com.medtimer.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.audio.BellPlayer
import com.medtimer.app.data.AppDatabase
import com.medtimer.app.data.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val sessionDate: LocalDate? = null
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
    private val bellPlayer = BellPlayer(application)

    private val _uiState = MutableStateFlow(MeditationUiState())
    val uiState: StateFlow<MeditationUiState> = _uiState.asStateFlow()

    val sessions = sessionDao.getAllSessions().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private var timerJob: Job? = null

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

    fun start() {
        if (_uiState.value.timerState != TimerState.IDLE) return

        _uiState.value = _uiState.value.copy(
            timerState = TimerState.COUNTDOWN,
            currentSeconds = _uiState.value.countdownSeconds,
            intervalsCompleted = 0,
            sessionStartTime = null,
            sessionDate = null
        )

        startCountdownTimer()
    }

    private fun startCountdownTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.currentSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    currentSeconds = _uiState.value.currentSeconds - 1
                )
            }
            // Countdown finished, start meditation
            startMeditation()
        }
    }

    private fun startMeditation() {
        bellPlayer.playIntervalBell()

        val now = LocalTime.now()
        val today = LocalDate.now()

        _uiState.value = _uiState.value.copy(
            timerState = TimerState.MEDITATING,
            currentSeconds = _uiState.value.totalMeditationSeconds,
            intervalsCompleted = 0,
            sessionStartTime = now,
            sessionDate = today
        )

        startMeditationTimer()
    }

    private fun startMeditationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val intervalSecs = _uiState.value.intervalSeconds
            var elapsed = 0

            while (_uiState.value.currentSeconds > 0) {
                delay(1000)
                elapsed++
                _uiState.value = _uiState.value.copy(
                    currentSeconds = _uiState.value.currentSeconds - 1
                )

                // Check if we've completed an interval
                if (elapsed > 0 && elapsed % intervalSecs == 0) {
                    val newIntervalsCompleted = _uiState.value.intervalsCompleted + 1
                    _uiState.value = _uiState.value.copy(
                        intervalsCompleted = newIntervalsCompleted
                    )

                    // Check if this is the final interval
                    if (newIntervalsCompleted >= _uiState.value.numIntervals) {
                        // Final bell
                        bellPlayer.playFinalBell()
                        finishSession(keepSession = true)
                        return@launch
                    } else {
                        // Interval bell
                        bellPlayer.playIntervalBell()
                    }
                }
            }

            // Timer reached zero (shouldn't happen normally, but handle it)
            bellPlayer.playFinalBell()
            finishSession(keepSession = true)
        }
    }

    fun requestStop() {
        if (_uiState.value.timerState == TimerState.MEDITATING) {
            timerJob?.cancel()
            _uiState.value = _uiState.value.copy(showStopDialog = true)
        } else if (_uiState.value.timerState == TimerState.COUNTDOWN) {
            // During countdown, just cancel without dialog
            timerJob?.cancel()
            reset()
        }
    }

    fun confirmStop(keepSession: Boolean) {
        _uiState.value = _uiState.value.copy(showStopDialog = false)
        finishSession(keepSession)
    }

    fun dismissStopDialog() {
        // Resume the timer
        _uiState.value = _uiState.value.copy(showStopDialog = false)
        startMeditationTimer()
    }

    private fun finishSession(keepSession: Boolean) {
        val state = _uiState.value

        if (keepSession && state.sessionStartTime != null && state.sessionDate != null) {
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

        reset()
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
        timerJob?.cancel()
        bellPlayer.release()
    }
}

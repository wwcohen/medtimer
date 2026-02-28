package com.medtimer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.medtimer.app.MeditationViewModel
import com.medtimer.app.R
import com.medtimer.app.TimerState
import com.medtimer.app.ui.components.NumberPicker
import com.medtimer.app.ui.components.TimerDisplay
import com.medtimer.app.ui.theme.ButtonStart
import com.medtimer.app.ui.theme.ButtonStop

@Composable
fun HomeScreen(
    viewModel: MeditationViewModel,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title and History button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ZenTimer",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onNavigateToHistory) {
                    Text(stringResource(R.string.history))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Timer display
            val timerLabel = when (uiState.timerState) {
                TimerState.IDLE -> "Ready"
                TimerState.COUNTDOWN -> "Starting in..."
                TimerState.MEDITATING -> "Meditating"
                TimerState.FINISHED -> "Complete"
            }

            val displaySeconds = when (uiState.timerState) {
                TimerState.IDLE -> uiState.totalMeditationSeconds
                else -> uiState.currentSeconds
            }

            TimerDisplay(
                seconds = displaySeconds,
                isCountdown = uiState.timerState == TimerState.COUNTDOWN,
                label = timerLabel,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Intervals info when meditating
            if (uiState.timerState == TimerState.MEDITATING) {
                Text(
                    text = "Interval ${uiState.intervalsCompleted + 1} of ${uiState.numIntervals}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Settings (only when idle)
            if (uiState.timerState == TimerState.IDLE) {
                // Countdown on its own row
                NumberPicker(
                    value = uiState.countdownSeconds,
                    onValueChange = viewModel::setCountdownSeconds,
                    minValue = 1,
                    maxValue = 30,
                    label = "Countdown (sec)",
                    enabled = uiState.timerState == TimerState.IDLE
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Intervals and Interval on second row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NumberPicker(
                        value = uiState.numIntervals,
                        onValueChange = viewModel::setNumIntervals,
                        minValue = 1,
                        maxValue = 10,
                        label = "Intervals",
                        enabled = uiState.timerState == TimerState.IDLE
                    )
                    NumberPicker(
                        value = uiState.intervalMinutes,
                        onValueChange = viewModel::setIntervalMinutes,
                        minValue = 1,
                        maxValue = 15,
                        label = if (uiState.debugMode) "Interval (sec)" else "Interval (min)",
                        enabled = uiState.timerState == TimerState.IDLE
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Total time info
                val totalMins = if (uiState.debugMode) {
                    uiState.intervalMinutes * uiState.numIntervals
                } else {
                    uiState.intervalMinutes * uiState.numIntervals
                }
                Text(
                    text = if (uiState.debugMode) {
                        "Total: ${totalMins} seconds (debug mode)"
                    } else {
                        "Total: ${totalMins} minutes"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Phone volume slider (visible when idle or meditating)
            if (uiState.timerState == TimerState.IDLE || uiState.timerState == TimerState.MEDITATING) {
                val context = LocalContext.current
                val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
                val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
                var phoneVolume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

                // Observe system volume changes (e.g. hardware buttons)
                DisposableEffect(Unit) {
                    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            phoneVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    }
                    context.contentResolver.registerContentObserver(
                        Settings.System.CONTENT_URI,
                        true,
                        observer
                    )
                    onDispose {
                        context.contentResolver.unregisterContentObserver(observer)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "Volume: ${phoneVolume * 100 / maxVolume}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
                            phoneVolume = maxVolume
                        },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("MAX", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Slider(
                    value = phoneVolume.toFloat(),
                    onValueChange = {
                        val newVolume = it.toInt()
                        phoneVolume = newVolume
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    steps = maxVolume - 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            // White noise volume slider (visible when idle or meditating)
            if (uiState.timerState == TimerState.IDLE || uiState.timerState == TimerState.MEDITATING) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (uiState.whiteNoiseVolume > 0f) {
                        "White Noise: ${(uiState.whiteNoiseVolume * 100).toInt()}%"
                    } else {
                        "White Noise: Off"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Slider(
                    value = uiState.whiteNoiseVolume,
                    onValueChange = { viewModel.setWhiteNoiseVolume(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Debug toggle (only when idle) - placed above Start button
            // Disabled for production - uncomment to enable debug mode UI
            // if (uiState.timerState == TimerState.IDLE) {
            //     OutlinedButton(
            //         onClick = { viewModel.toggleDebugMode() },
            //         modifier = Modifier.padding(bottom = 16.dp)
            //     ) {
            //         Text(
            //             text = if (uiState.debugMode) "Debug: ON" else "Debug: OFF",
            //             style = MaterialTheme.typography.labelMedium
            //         )
            //     }
            // }

            // Control buttons
            when (uiState.timerState) {
                TimerState.IDLE -> {
                    Button(
                        onClick = { viewModel.start() },
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonStart
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.start),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                TimerState.COUNTDOWN, TimerState.MEDITATING -> {
                    Button(
                        onClick = { viewModel.requestStop() },
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ButtonStop
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.stop),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                TimerState.FINISHED -> {
                    Text(
                        text = "Session Complete!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stop confirmation dialog
        if (uiState.showStopDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissStopDialog() },
                title = { Text(stringResource(R.string.session_stopped)) },
                text = { Text(stringResource(R.string.session_stopped_message)) },
                confirmButton = {
                    Button(onClick = { viewModel.confirmStop(keepSession = true) }) {
                        Text(stringResource(R.string.keep_session))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { viewModel.confirmStop(keepSession = false) }) {
                        Text(stringResource(R.string.discard_session))
                    }
                }
            )
        }
    }
}

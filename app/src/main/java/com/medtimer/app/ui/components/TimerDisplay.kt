package com.medtimer.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medtimer.app.ui.theme.TimerActive
import com.medtimer.app.ui.theme.TimerCountdown

@Composable
fun TimerDisplay(
    seconds: Int,
    isCountdown: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val minutes = seconds / 60
    val secs = seconds % 60
    val timeString = String.format("%02d:%02d", minutes, secs)

    val color = if (isCountdown) TimerCountdown else TimerActive

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = timeString,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light
            ),
            color = color
        )
    }
}

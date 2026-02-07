package com.medtimer.app.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medtimer.app.MeditationViewModel
import com.medtimer.app.R
import com.medtimer.app.data.Session
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MeditationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Calculate statistics
    val today = LocalDate.now()
    val stats7Days = calculateStats(sessions, today, 7)
    val stats30Days = calculateStats(sessions, today, 30)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.history)) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
            )
        )

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.no_sessions),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                // Export button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val csv = viewModel.exportSessionsCsv()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_TEXT, csv)
                                putExtra(Intent.EXTRA_SUBJECT, "ZenTimer Sessions")
                            }
                            context.startActivity(
                                Intent.createChooser(intent, "Export Sessions")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.export_csv))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Session list (last 14 items)
                val recentSessions = sessions.take(14)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(recentSessions, key = { it.id }) { session ->
                        SessionRow(session = session)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Statistics section
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                StatsSection(
                    label = "Last 7 days",
                    avgTime = stats7Days.avgMinutes,
                    percentage = stats7Days.percentDays
                )
                Spacer(modifier = Modifier.height(8.dp))
                StatsSection(
                    label = "Last 30 days",
                    avgTime = stats30Days.avgMinutes,
                    percentage = stats30Days.percentDays
                )
            }
        }
    }
}

private data class PeriodStats(
    val avgMinutes: Int,
    val percentDays: Int
)

private fun calculateStats(sessions: List<Session>, today: LocalDate, days: Int): PeriodStats {
    val startDate = today.minusDays(days.toLong() - 1)
    val sessionsInPeriod = sessions.filter { session ->
        try {
            val sessionDate = LocalDate.parse(session.date)
            !sessionDate.isBefore(startDate) && !sessionDate.isAfter(today)
        } catch (e: Exception) {
            false
        }
    }

    if (sessionsInPeriod.isEmpty()) {
        return PeriodStats(avgMinutes = 0, percentDays = 0)
    }

    val totalSeconds = sessionsInPeriod.sumOf { it.elapsedSeconds }
    val avgMinutes = (totalSeconds / sessionsInPeriod.size) / 60

    val daysWithSessions = sessionsInPeriod.map { it.date }.distinct().size
    val percentDays = (daysWithSessions * 100) / days

    return PeriodStats(avgMinutes = avgMinutes, percentDays = percentDays)
}

@Composable
private fun StatsSection(
    label: String,
    avgTime: Int,
    percentage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${avgTime}m avg, ${percentage}% days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SessionRow(
    session: Session,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${session.formattedDate}, ${session.formattedStartTime}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = session.formattedDuration,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


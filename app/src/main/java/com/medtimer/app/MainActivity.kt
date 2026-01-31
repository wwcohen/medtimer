package com.medtimer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medtimer.app.ui.HistoryScreen
import com.medtimer.app.ui.HomeScreen
import com.medtimer.app.ui.theme.MedTimerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedTimerTheme {
                MedTimerApp()
            }
        }
    }
}

@Composable
fun MedTimerApp() {
    val viewModel: MeditationViewModel = viewModel()
    var currentScreen by remember { mutableStateOf("home") }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (currentScreen) {
            "home" -> HomeScreen(
                viewModel = viewModel,
                onNavigateToHistory = { currentScreen = "history" },
                modifier = Modifier.padding(innerPadding)
            )
            "history" -> HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { currentScreen = "home" },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

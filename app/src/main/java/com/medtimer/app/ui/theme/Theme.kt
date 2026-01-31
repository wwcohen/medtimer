package com.medtimer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = SageGreen,
    onPrimary = Cream,
    primaryContainer = SageGreenLight,
    onPrimaryContainer = Charcoal,
    secondary = WarmBrown,
    onSecondary = Cream,
    secondaryContainer = TaupeLight,
    onSecondaryContainer = Charcoal,
    tertiary = Taupe,
    onTertiary = Cream,
    tertiaryContainer = TaupeLight,
    onTertiaryContainer = Charcoal,
    background = Cream,
    onBackground = Charcoal,
    surface = Surface,
    onSurface = Charcoal,
    surfaceVariant = CreamDark,
    onSurfaceVariant = CharcoalLight,
    outline = Taupe,
    outlineVariant = TaupeLight
)

@Composable
fun MedTimerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

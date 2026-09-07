package com.talom.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class TalomThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val TalomDarkScheme = darkColorScheme(
    primary = TalomDarkActive,
    onPrimary = TalomDarkOnActive,
    secondary = TalomDarkActive,
    onSecondary = TalomDarkOnActive,
    background = TalomDarkBackground,
    surface = TalomDarkSurface,
    surfaceVariant = TalomDarkSurfaceVariant,
    onBackground = TalomDarkOnSurface,
    onSurface = TalomDarkOnSurface,
    onSurfaceVariant = TalomDarkOnSurfaceVariant,
    outline = TalomDarkBorder,
    outlineVariant = TalomDarkBorder,
    surfaceContainerHighest = TalomDarkSurfaceVariant,
)

private val TalomLightScheme = lightColorScheme(
    primary = TalomLightActive,
    onPrimary = TalomLightOnActive,
    secondary = TalomLightActive,
    onSecondary = TalomLightOnActive,
    background = TalomLightBackground,
    surface = TalomLightSurface,
    surfaceVariant = TalomLightSurfaceVariant,
    onBackground = TalomLightOnSurface,
    onSurface = TalomLightOnSurface,
    onSurfaceVariant = TalomLightOnSurfaceVariant,
    outline = TalomLightBorder,
    outlineVariant = TalomLightBorder,
    surfaceContainerHighest = TalomLightSurfaceVariant,
)

@Composable
fun TalomTheme(
    themeMode: TalomThemeMode = TalomThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        TalomThemeMode.SYSTEM -> isSystemInDarkTheme()
        TalomThemeMode.LIGHT -> false
        TalomThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = if (darkTheme) TalomDarkScheme else TalomLightScheme,
        content = content,
    )
}

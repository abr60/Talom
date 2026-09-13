package com.talom.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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

private fun buildDynamicScheme(ctx: Context, dark: Boolean) = if (dark) {
    val ds = dynamicDarkColorScheme(ctx)
    TalomDarkScheme.copy(
        primary = ds.primary,
        onPrimary = ds.onPrimary,
        primaryContainer = ds.primaryContainer,
        onPrimaryContainer = ds.onPrimaryContainer,
        secondary = ds.secondary,
        onSecondary = ds.onSecondary,
        secondaryContainer = ds.secondaryContainer,
        onSecondaryContainer = ds.onSecondaryContainer,
        tertiary = ds.tertiary,
        onTertiary = ds.onTertiary,
        tertiaryContainer = ds.tertiaryContainer,
        onTertiaryContainer = ds.onTertiaryContainer,
        inversePrimary = ds.inversePrimary,
        inverseSurface = ds.inverseSurface,
        inverseOnSurface = ds.inverseOnSurface,
        surfaceTint = ds.surfaceTint,
        error = ds.error,
        onError = ds.onError,
        errorContainer = ds.errorContainer,
        onErrorContainer = ds.onErrorContainer,
    )
} else {
    val ls = dynamicLightColorScheme(ctx)
    TalomLightScheme.copy(
        primary = ls.primary,
        onPrimary = ls.onPrimary,
        primaryContainer = ls.primaryContainer,
        onPrimaryContainer = ls.onPrimaryContainer,
        secondary = ls.secondary,
        onSecondary = ls.onSecondary,
        secondaryContainer = ls.secondaryContainer,
        onSecondaryContainer = ls.onSecondaryContainer,
        tertiary = ls.tertiary,
        onTertiary = ls.onTertiary,
        tertiaryContainer = ls.tertiaryContainer,
        onTertiaryContainer = ls.onTertiaryContainer,
        inversePrimary = ls.inversePrimary,
        inverseSurface = ls.inverseSurface,
        inverseOnSurface = ls.inverseOnSurface,
        surfaceTint = ls.surfaceTint,
        error = ls.error,
        onError = ls.onError,
        errorContainer = ls.errorContainer,
        onErrorContainer = ls.onErrorContainer,
    )
}

@Composable
fun TalomTheme(
    followSystemTheme: Boolean = true,
    dynamicColors: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isSysDark = isSystemInDarkTheme()
    val darkTheme = if (followSystemTheme) isSysDark else !isSysDark
    val ctx = LocalContext.current
    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> buildDynamicScheme(ctx, darkTheme)
        darkTheme -> TalomDarkScheme
        else -> TalomLightScheme
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
        colorScheme = colorScheme,
        content = content,
    )
}

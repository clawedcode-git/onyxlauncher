package com.onyxlauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.onyxlauncher.domain.model.ThemeMode

private val OnyxDarkColorScheme = darkColorScheme(
    primary = OnyxPrimary,
    onPrimary = OnyxOnPrimary,
    primaryContainer = OnyxPrimaryContainer,
    onPrimaryContainer = OnyxPrimary,
    secondary = OnyxSecondary,
    tertiary = OnyxTertiary,
    background = OnyxSurface,
    surface = OnyxSurface,
    surfaceVariant = OnyxSurfaceVariant,
    onSurface = OnyxOnSurface,
    onSurfaceVariant = OnyxOnSurfaceVariant,
    outline = OnyxOutline,
    error = OnyxError,
)

private val OnyxLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
)

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

@Composable
fun OnyxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> OnyxDarkColorScheme
        else -> OnyxLightColorScheme
    }.let { scheme ->
        // AMOLED: force true black backgrounds
        if (themeMode == ThemeMode.AMOLED) {
            scheme.copy(background = Color.Black, surface = Color.Black)
        } else scheme
    }

    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

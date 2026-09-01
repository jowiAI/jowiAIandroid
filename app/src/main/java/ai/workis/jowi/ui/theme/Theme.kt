package ai.workis.jowi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val LightColorScheme = lightColorScheme(
    primary = Kiremit400,
    onPrimary = OnKiremitFill,
    secondary = Kiremit500,
    onSecondary = OnKiremitFill,
    background = WorkisLightColors.canvas,
    onBackground = WorkisLightColors.ink,
    surface = WorkisLightColors.surface,
    onSurface = WorkisLightColors.ink,
    onSurfaceVariant = WorkisLightColors.muted,
    outline = WorkisLightColors.border,
    error = WorkisLightColors.danger,
)

private val DarkColorScheme = darkColorScheme(
    primary = Kiremit400,
    onPrimary = OnKiremitFill,
    secondary = Kiremit500,
    onSecondary = OnKiremitFill,
    background = WorkisDarkColors.canvas,
    onBackground = WorkisDarkColors.ink,
    surface = WorkisDarkColors.surface,
    onSurface = WorkisDarkColors.ink,
    onSurfaceVariant = WorkisDarkColors.muted,
    outline = WorkisDarkColors.border,
    error = WorkisDarkColors.danger,
)

object WorkisTheme {
    val colors: WorkisColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWorkisColors.current
}

@Composable
fun JowiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val workisColors = if (darkTheme) WorkisDarkColors else WorkisLightColors
    CompositionLocalProvider(LocalWorkisColors provides workisColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = WorkisTypography,
            content = content,
        )
    }
}

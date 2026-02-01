package studio.modryn.memento.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Memento Theme - Dark, calm, invisible.
 * 
 * The theme reflects the Silent System philosophy:
 * - Pure black backgrounds for OLED efficiency
 * - Minimal color palette to reduce visual noise
 * - High contrast text for instant readability
 * - Soft accent colors that don't demand attention
 */
private val MementoDarkColorScheme = darkColorScheme(
    primary = MementoColors.Primary,
    onPrimary = MementoColors.Background,
    primaryContainer = MementoColors.PrimaryVariant,
    secondary = MementoColors.Secondary,
    onSecondary = MementoColors.Background,
    background = MementoColors.Background,
    onBackground = MementoColors.OnBackground,
    surface = MementoColors.Surface,
    onSurface = MementoColors.OnSurface,
    surfaceVariant = MementoColors.SurfaceVariant,
    onSurfaceVariant = MementoColors.OnSurfaceVariant,
    error = MementoColors.Error,
    onError = MementoColors.OnBackground,
)

@Composable
fun MementoTheme(
    darkTheme: Boolean = true, // Always dark - invisible butler aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = MementoDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MementoColors.Background.toArgb()
            window.navigationBarColor = MementoColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MementoTypography,
        content = content
    )
}

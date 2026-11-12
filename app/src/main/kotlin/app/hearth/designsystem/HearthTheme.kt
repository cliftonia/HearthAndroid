package app.hearth.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.hearth.domain.Appearance

/**
 * The one way in to the design system: `HearthTheme.colors.pine`, `HearthTheme.type.footnote`,
 * `HearthTheme.spacing.m`, `HearthTheme.shapes.card`, `HearthTheme.sizes.iconL`, `HearthTheme.strokes.hairline`.
 *
 * Only the colours change between light and dark, so only they are read from the composition.
 */
object HearthTheme {
    val colors: HearthColors
        @Composable get() = LocalHearthColors.current

    val type = HearthTypography()
    val spacing = HearthSpacing()
    val shapes = HearthShapes()
    val sizes = HearthSizes()
    val strokes = HearthStrokes()
}

/** Whether this choice of appearance comes out dark on this phone at this moment. */
@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

/** Gives everything inside it the app's colours, in their light or dark form. */
@Composable
fun HearthTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val colors = if (isDark) DarkColors else LightColors
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    // The stock components (menus, switches, fields, dialogs) take their colours from here.
    val scheme = base.copy(
        primary = colors.pine, onPrimary = colors.onPine, primaryContainer = colors.pineSoft, onPrimaryContainer = colors.ink,
        secondary = colors.pine, onSecondary = colors.onPine, secondaryContainer = colors.pineSoft, onSecondaryContainer = colors.ink,
        tertiary = colors.pine, onTertiary = colors.onPine,
        background = colors.paper, onBackground = colors.ink, surface = colors.paper, onSurface = colors.ink,
        surfaceVariant = colors.bubble, onSurfaceVariant = colors.mute, outline = colors.mute, outlineVariant = colors.line,
        error = colors.danger, surfaceTint = colors.pine,
        surfaceContainerLowest = colors.card, surfaceContainerLow = colors.card, surfaceContainer = colors.card,
        surfaceContainerHigh = colors.card, surfaceContainerHighest = colors.bubble,
    )
    CompositionLocalProvider(LocalHearthColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

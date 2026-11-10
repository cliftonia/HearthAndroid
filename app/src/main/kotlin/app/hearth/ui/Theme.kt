package app.hearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hearth.domain.Appearance

/** Pine green on stone paper. */
data class HearthColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val mute: Color,
    val line: Color,
    val pine: Color,
    val pineSoft: Color,
    val bubble: Color,
    val onPine: Color,
    val danger: Color,
    val online: Color,
)

private val LightColors = HearthColors(
    paper = Color(0xFFF3F5F1), card = Color(0xFFFFFFFF), ink = Color(0xFF18211E), mute = Color(0xFF6C7772), line = Color(0xFFDDE2DC),
    pine = Color(0xFF2C6B5A), pineSoft = Color(0xFFDCEAE4), bubble = Color(0xFFE6EBE5), onPine = Color(0xFFFFFFFF),
    danger = Color(0xFFC8553D), online = Color(0xFF3FA66B),
)

private val DarkColors = HearthColors(
    paper = Color(0xFF121715), card = Color(0xFF1B2220), ink = Color(0xFFE9EEEA), mute = Color(0xFF8B9691), line = Color(0xFF2A3330),
    pine = Color(0xFF6FC2A8), pineSoft = Color(0xFF1F3A32), bubble = Color(0xFF232C29), onPine = Color(0xFF0E1211),
    danger = Color(0xFFE0705A), online = Color(0xFF57C283),
)

private val LocalHearthColors = staticCompositionLocalOf { LightColors }

object HearthTheme {
    val colors: HearthColors
        @Composable get() = LocalHearthColors.current

    val brand = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val display = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp)
    val code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
    val math = TextStyle(fontFamily = FontFamily.Serif, fontSize = 19.sp, lineHeight = 26.sp)

    fun heading(level: Int): TextStyle = when (level) {
        1 -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
        2 -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp)
        else -> TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp)
    }
}

@Composable
fun Appearance.isDark(): Boolean = when (this) {
    Appearance.SYSTEM -> isSystemInDarkTheme()
    Appearance.LIGHT -> false
    Appearance.DARK -> true
}

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

/** The app's mark: a ring of embers on pine. */
@Composable
fun HearthMark(sideLength: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(sideLength)
            .background(HearthTheme.colors.pine, RoundedCornerShape(sideLength * 0.3f))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(sideLength * 0.28f).size(sideLength * 0.44f).border(sideLength * 0.09f, HearthTheme.colors.onPine, CircleShape))
    }
}

/** A round, borderless button holding one symbol. */
@Composable
fun HearthIconButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = HearthTheme.colors.ink, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

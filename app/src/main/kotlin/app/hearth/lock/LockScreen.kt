package app.hearth.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.hearth.R
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme
import app.hearth.shared.UnlockMethodKind

/**
 * Shown in place of everything else until the phone has confirmed who is holding it. The button is named for what the
 * phone will ask for, as the lock's switch in the settings is.
 */
@Composable
fun LockScreen(unlockMethod: UnlockMethodKind, onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HearthTheme.colors
    // The phone asks at once; the button is there in case the first asking is dismissed.
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        modifier.fillMaxSize().background(colors.paper).padding(HearthTheme.spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.gapM, Alignment.CenterVertically),
    ) {
        HearthMark(HearthTheme.sizes.markL)
        Text(stringResource(R.string.brand), style = HearthTheme.type.display, color = colors.ink)
        val title = when (unlockMethod) {
            UnlockMethodKind.BIOMETRIC -> R.string.lock_unlock_biometric
            UnlockMethodKind.SCREEN_LOCK, UnlockMethodKind.NONE -> R.string.lock_unlock_screen_lock
        }
        Button(onClick = onUnlock) { Text(stringResource(title), textAlign = TextAlign.Center) }
    }
}

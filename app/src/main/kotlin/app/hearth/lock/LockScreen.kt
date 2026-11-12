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
import app.hearth.R
import app.hearth.designsystem.HearthMark
import app.hearth.designsystem.HearthTheme

/** Shown in place of everything else until the phone has confirmed who is holding it. */
@Composable
fun LockScreen(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
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
        Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
    }
}

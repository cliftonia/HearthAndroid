package app.hearth.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.hearth.R

/** The title of a sheet that offers a choice, with the button that closes it at the leading edge. */
@Composable
fun HearthSheetHeader(title: String, onClose: () -> Unit) {
    val colors = HearthTheme.colors
    Box(Modifier.fillMaxWidth().padding(start = HearthTheme.spacing.xxxl, end = HearthTheme.spacing.xxxl, bottom = HearthTheme.spacing.xxxl)) {
        HearthRoundButton(
            icon = Icons.Outlined.Close,
            label = stringResource(R.string.common_close),
            containerColor = colors.card,
            contentColor = colors.ink,
            size = HearthTheme.sizes.touchTarget,
            iconSize = HearthTheme.sizes.iconS,
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onClose,
        )
        Text(
            title,
            color = colors.ink,
            style = HearthTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

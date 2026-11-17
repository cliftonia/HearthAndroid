package app.hearth.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * The title of a sheet that offers a choice, at the leading edge under the sheet's handle. There is no button to close
 * it: as on any bottom sheet, the handle, a tap beside it and the back button do that.
 */
@Composable
fun HearthSheetHeader(title: String) {
    Text(
        title,
        color = HearthTheme.colors.ink,
        style = HearthTheme.type.title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = HearthTheme.spacing.screen, end = HearthTheme.spacing.screen, bottom = HearthTheme.spacing.m)
            .semantics { heading() },
    )
}

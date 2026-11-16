package app.hearth.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.domain.ConversationExporter

/** Writes a chat out as Markdown for the share sheet, in the app's words for who said what and for a chat with no title. */
@Composable
fun rememberConversationExporter(): ConversationExporter {
    val user = stringResource(R.string.export_user)
    val brand = stringResource(R.string.brand)
    val untitled = stringResource(R.string.untitled)
    return remember(user, brand, untitled) { ConversationExporter(user, brand, untitled) }
}
